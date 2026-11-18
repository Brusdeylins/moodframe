package art.moodframe.service.generation;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationJobResult;
import art.moodframe.repository.ChatRepository;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Keeps a chat entry in step with its images while a request runs: tells the UI about changes, marks an entry
 * failed, settles its status and cost once all images are finished, and stores the result. The change of the
 * entry itself happens on the FX thread, the database writes on a worker.
 *
 * @since 1.9.1
 */
public class ChatEntryTracker {

    private static final Logger logger = LoggerFactory.getLogger(ChatEntryTracker.class);

    /*  BFL reports its prices in credits of one cent  */
    private static final double USD_PER_BFL_CREDIT = 0.01;

    private final ApplicationConfig config;
    private final ChatRepository chatRepository;
    private final Executor executor;

    private final List<Consumer<ChatEntry>> entryListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<GeneratedImage>> imageListeners = new CopyOnWriteArrayList<>();

    /**
     * Creates the tracker.
     *
     * @param config         the application configuration (logging)
     * @param chatRepository where entries are stored
     * @param executor       runs the database writes
     */
    public ChatEntryTracker(ApplicationConfig config, ChatRepository chatRepository, Executor executor) {
        this.config = config;
        this.chatRepository = chatRepository;
        this.executor = executor;
    }

    /** Adds a listener for chat entry updates, called on the FX thread. */
    public void addEntryListener(Consumer<ChatEntry> listener) {
        entryListeners.add(listener);
    }

    public void removeEntryListener(Consumer<ChatEntry> listener) {
        entryListeners.remove(listener);
    }

    /** Adds a listener for image updates, called on the FX thread. */
    public void addImageListener(Consumer<GeneratedImage> listener) {
        imageListeners.add(listener);
    }

    public void removeImageListener(Consumer<GeneratedImage> listener) {
        imageListeners.remove(listener);
    }

    /**
     * Notifies listeners of chat entry updates.
     */
    public void notifyEntryUpdated(ChatEntry chatEntry) {
        dispatch(entryListeners, chatEntry, "entry");
    }

    /**
     * Notifies listeners of image updates.
     */
    public void notifyImageUpdated(GeneratedImage image) {
        dispatch(imageListeners, image, "image");
    }

    /*  Calls every listener on the FX thread; one that fails does not keep the others from their call  */
    private static <T> void dispatch(List<Consumer<T>> listeners, T value, String kind) {
        if (listeners.isEmpty())
            return;
        Platform.runLater(() -> {
            for (Consumer<T> listener : listeners) {
                try {
                    listener.accept(value);
                } catch (Exception e) {
                    logger.error("Error in {} update callback", kind, e);
                }
            }
        });
    }

    /**
     * Marks a chat entry as failed with the given message, shows it and stores it.
     */
    public void markFailed(ChatEntry chatEntry, String errorMessage) {
        Platform.runLater(() -> {
            if (chatEntry.getStatus() == ChatEntry.Status.CANCELLED)
                return;
            chatEntry.setStatus(ChatEntry.Status.FAILED);
            chatEntry.setErrorMessage(errorMessage);

            // Notify UI immediately
            notifyEntryUpdated(chatEntry);
        });

        // Persist failed status to database asynchronously (don't block calling thread)
        CompletableFuture.runAsync(() -> {
            if (chatRepository != null) {
                try {
                    chatRepository.saveChatEntry(chatEntry);
                    // ChatEntry status updated to FAILED in database
                } catch (Exception e) {
                    logger.error("Failed to update ChatEntry failed status in database: {}", chatEntry.getId(), e);
                }
            }
        }, executor);
    }

    /**
     * Cancels a running entry on the FX thread: its images not finished yet fail, the entry ends as cancelled and is
     * stored. It keeps its estimated cost, as the provider may have started already.
     *
     * @param chatEntry the entry
     * @return true if it was pending or processing
     */
    public boolean cancel(ChatEntry chatEntry) {
        if (chatEntry.getStatus() != ChatEntry.Status.PENDING && chatEntry.getStatus() != ChatEntry.Status.PROCESSING)
            return false;
        List<GeneratedImage> stopped = chatEntry.getImages().stream()
                .filter((GeneratedImage image) -> image.getDownloadStatus() != GeneratedImage.DownloadStatus.CACHED
                        && image.getDownloadStatus() != GeneratedImage.DownloadStatus.FAILED).toList();
        stopped.forEach((GeneratedImage image) -> {
            image.setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
            image.setErrorMessage("Cancelled");
        });
        chatEntry.setStatus(ChatEntry.Status.CANCELLED);
        chatEntry.setCompletedAt(LocalDateTime.now());
        CompletableFuture.runAsync(() -> {
            try {
                chatRepository.saveChatEntry(chatEntry);
                for (GeneratedImage image : stopped)
                    chatRepository.saveGeneratedImage(image);
            } catch (Exception e) {
                logger.error("Failed to store the cancelled entry {}", chatEntry.getId(), e);
            }
        }, executor);
        notifyEntryUpdated(chatEntry);
        return true;
    }

    /**
     * Checks if all images in a ChatEntry are finished (either CACHED or FAILED).
     * If all images are finished, updates the ChatEntry status and completion time.
     *
     * This method should be called whenever an individual image status changes to ensure
     * the parent ChatEntry status stays synchronized with its images.
     *
     * @param chatEntry the ChatEntry to check and potentially update
     */
    public void refresh(ChatEntry chatEntry) {
        if (chatEntry.getImages() == null || chatEntry.getImages().isEmpty()) {
            return;
        }

        // Check if ALL images are finished (either CACHED or FAILED)
        int cachedCount = 0;
        int failedCount = 0;
        int totalCount = chatEntry.getImages().size();

        for (GeneratedImage img : chatEntry.getImages()) {
            GeneratedImage.DownloadStatus status = img.getDownloadStatus();

            if (status == GeneratedImage.DownloadStatus.CACHED) {
                cachedCount++;
            } else if (status == GeneratedImage.DownloadStatus.FAILED) {
                failedCount++;
            }
        }

        // Only update ChatEntry status if ALL images are finished
        final boolean allFinished = (cachedCount + failedCount) == totalCount;
        final boolean hasCached = cachedCount > 0;
        final boolean allFailed = failedCount == totalCount;

        if (allFinished) {
            // First, recalculate total cost from all images (handles $0.00 for blocked images)
            recalculateCost(chatEntry);

            Platform.runLater(() -> {
                /*  A cancelled entry keeps its state, also if an image still arrives  */
                if (chatEntry.getStatus() == ChatEntry.Status.CANCELLED)
                    return;
                if (hasCached) {
                    // At least one image succeeded - mark as COMPLETED
                    chatEntry.setStatus(ChatEntry.Status.COMPLETED);
                } else if (allFailed) {
                    // All images failed - mark as FAILED
                    chatEntry.setStatus(ChatEntry.Status.FAILED);

                    /*  The entry tells why, with the distinct reasons of its images (the provider's answer)  */
                    if (chatEntry.getErrorMessage() == null || chatEntry.getErrorMessage().isBlank())
                        chatEntry.setErrorMessage(chatEntry.getImages().stream().map(GeneratedImage::getErrorMessage)
                                .filter((String message) -> message != null && !message.isBlank()).distinct()
                                .reduce((String first, String next) -> first + "; " + next).orElse(null));
                }

                chatEntry.setCompletedAt(LocalDateTime.now());

                // Save to database
                try {
                    chatRepository.saveChatEntry(chatEntry);
                    logger.info("Updated ChatEntry status to {} (all images finished)", chatEntry.getStatus());
                } catch (Exception e) {
                    logger.error("Failed to save ChatEntry status update", e);
                }

                notifyEntryUpdated(chatEntry);
            });
        }
    }

    /**
     * Recalculates the total cost for a ChatEntry from all its images.
     * This ensures accurate cost tracking when images have individual costs (Gemini)
     * or when images fail (blocked content = $0.00).
     *
     * @param chatEntry the ChatEntry to update
     */
    private void recalculateCost(ChatEntry chatEntry) {
        if (chatEntry.getImages() == null || chatEntry.getImages().isEmpty()) {
            return;
        }

        double totalCost = 0.0;

        for (GeneratedImage img : chatEntry.getImages()) {
            // Base generation cost
            Double baseCost = img.getBaseCost();
            if (baseCost != null) {
                totalCost += baseCost;
            }

            // Google Search cost (Gemini feature)
            Double searchCost = img.getSearchCost();
            if (searchCost != null) {
                totalCost += searchCost;
            }

            // Reasoning/thinking cost (Gemini feature)
            Double reasoningCost = img.getReasoningCost();
            if (reasoningCost != null) {
                totalCost += reasoningCost;
            }
        }

        final double finalTotalCost = totalCost;

        // Update ChatEntry cost
        Platform.runLater(() -> {
            chatEntry.setCost(finalTotalCost);

            // Save to database
            try {
                chatRepository.saveChatEntry(chatEntry);
                logger.info("Recalculated ChatEntry total cost: ${} ({} images)",
                        String.format("%.4f", finalTotalCost),
                        chatEntry.getImages().size());
            } catch (Exception e) {
                logger.error("Failed to save recalculated ChatEntry cost", e);
            }
        });
    }

    /**
     * Takes the price BFL reports for a finished job as the base cost of its image, which keeps its estimate
     * otherwise; the entry gets the sum of its images once all are finished. Call on the FX thread.
     *
     * @param image     the image of the job
     * @param jobResult the result of the job, its cost in BFL credits
     */
    public void updateCostFromApi(GeneratedImage image, GenerationJobResult jobResult) {
        if (jobResult == null || image == null)
            return;
        Double credits = jobResult.getCost();
        if (credits == null || credits <= 0)
            return;
        double apiCost = credits * USD_PER_BFL_CREDIT;
        if (config.getLoggingEnabled())
            logger.info("BFL cost of image {}: ${} (estimated ${})", image.getId(), String.format("%.4f", apiCost),
                    String.format("%.4f", image.getBaseCost() != null ? image.getBaseCost() : 0.0));
        image.setBaseCost(apiCost);
    }
}
