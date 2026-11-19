package art.moodframe.service.generation;

import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.repository.ChatRepository;
import art.moodframe.service.provider.ProviderException;
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
 * entry itself happens on the FX thread (see {@link FxModel}), the database writes on a worker of their own, in the
 * order they were asked for, after the change they store. The listeners learn of an entry that ended once it is
 * stored with its images, so a query of the library they start finds them.
 *
 * @since 1.9.1
 */
public class ChatEntryTracker {

    private static final Logger logger = LoggerFactory.getLogger(ChatEntryTracker.class);

    private final ChatRepository chatRepository;
    private final Executor executor;

    private final List<Consumer<ChatEntry>> entryListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<GeneratedImage>> imageListeners = new CopyOnWriteArrayList<>();

    /**
     * Creates the tracker.
     *
     * @param chatRepository where entries and images are stored
     * @param executor       runs the database writes; a single thread keeps them in their order
     */
    public ChatEntryTracker(ChatRepository chatRepository, Executor executor) {
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
        FxModel.later(() -> {
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
     * Stores an image on the worker of the database writes. Call it after the change it stores, e.g. at the end of a
     * {@link FxModel#change(Runnable) change}: the write sees the change and keeps its place after the writes before.
     *
     * @param image the image
     * @return completes once it is stored, exceptionally if that failed (which is logged)
     */
    public CompletableFuture<Void> saveImage(GeneratedImage image) {
        return CompletableFuture.runAsync(() -> {
            if (chatRepository == null)
                return;
            try {
                chatRepository.saveGeneratedImage(image);
            } catch (Exception e) {
                logger.error("Failed to save the image {}", image.getId(), e);
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /**
     * Stores a chat entry on the worker of the database writes, like {@link #saveImage(GeneratedImage)}.
     *
     * @param chatEntry the entry
     * @return completes once it is stored, exceptionally if that failed (which is logged)
     */
    public CompletableFuture<Void> saveEntry(ChatEntry chatEntry) {
        return CompletableFuture.runAsync(() -> {
            if (chatRepository == null)
                return;
            try {
                chatRepository.saveChatEntry(chatEntry);
            } catch (Exception e) {
                logger.error("Failed to save the chat entry {}", chatEntry.getId(), e);
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /**
     * Gets the message of a failure for an entry: in English for an entry of the MCP server, whose client reads it,
     * else in the language of the user interface.
     *
     * @param failure   the failure
     * @param chatEntry the entry
     * @return the message
     */
    public static String errorText(Throwable failure, ChatEntry chatEntry) {
        return ProviderException.messageOf(failure,
                chatEntry != null && chatEntry.getSource() == ChatEntry.Source.MCP);
    }

    /**
     * Marks a chat entry as failed with the given message, stores it and then shows it.
     */
    public void markFailed(ChatEntry chatEntry, String errorMessage) {
        FxModel.change(() -> {
            if (chatEntry.getStatus() == ChatEntry.Status.CANCELLED)
                return;
            chatEntry.setStatus(ChatEntry.Status.FAILED);
            chatEntry.setErrorMessage(errorMessage);
            notifyWhenStored(saveEntry(chatEntry), chatEntry);
        });
    }

    /*  Tells the listeners of an entry that ended once the writes asked for before are done, failed or not: the
        worker runs them in order, so the images of the entry are stored as well  */
    private void notifyWhenStored(CompletableFuture<Void> stored, ChatEntry chatEntry) {
        stored.whenComplete((Void done, Throwable failure) -> notifyEntryUpdated(chatEntry));
    }

    /**
     * Cancels a running entry on the FX thread: its images not finished yet fail, the entry ends as cancelled and is
     * stored, then shown. It keeps its estimated cost, as the provider may have started already.
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
        CompletableFuture<Void> stored = saveEntry(chatEntry);
        for (GeneratedImage image : stopped)
            stored = saveImage(image);
        notifyWhenStored(stored, chatEntry);
        return true;
    }

    /**
     * Checks if all images in a ChatEntry are finished (either CACHED or FAILED). If so, the entry gets the cost of
     * its images (a blocked image costs nothing), its final status and completion time, in a change queued after
     * the current one, and is stored; the listeners learn of it once it is stored. Call it on the FX thread whenever
     * the status of an image changed.
     *
     * @param chatEntry the ChatEntry to check and potentially update
     */
    public void refresh(ChatEntry chatEntry) {
        if (chatEntry.getImages() == null || chatEntry.getImages().isEmpty()) {
            return;
        }

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

        /*  Only an entry whose images are all finished is settled  */
        if (cachedCount + failedCount != totalCount)
            return;
        final boolean hasCached = cachedCount > 0;
        final boolean allFailed = failedCount == totalCount;

        FxModel.change(() -> {
            double totalCost = totalCost(chatEntry);
            chatEntry.setCost(totalCost);
            logger.info("Recalculated ChatEntry total cost: ${} ({} images)", String.format("%.4f", totalCost),
                    chatEntry.getImages().size());

            /*  A cancelled entry keeps its state, also if an image still arrives; its cost is stored  */
            if (chatEntry.getStatus() == ChatEntry.Status.CANCELLED) {
                saveEntry(chatEntry);
                return;
            }
            if (hasCached) {
                chatEntry.setStatus(ChatEntry.Status.COMPLETED);
            } else if (allFailed) {
                chatEntry.setStatus(ChatEntry.Status.FAILED);

                /*  The entry tells why, with the distinct reasons of its images (the provider's answer)  */
                if (chatEntry.getErrorMessage() == null || chatEntry.getErrorMessage().isBlank())
                    chatEntry.setErrorMessage(chatEntry.getImages().stream().map(GeneratedImage::getErrorMessage)
                            .filter((String message) -> message != null && !message.isBlank()).distinct()
                            .reduce((String first, String next) -> first + "; " + next).orElse(null));
            }
            chatEntry.setCompletedAt(LocalDateTime.now());
            logger.info("Updated ChatEntry status to {} (all images finished)", chatEntry.getStatus());
            notifyWhenStored(saveEntry(chatEntry), chatEntry);
        });
    }

    /*  the sum of the base, Google Search and reasoning costs of the images of an entry  */
    private static double totalCost(ChatEntry chatEntry) {
        double totalCost = 0.0;
        for (GeneratedImage img : chatEntry.getImages()) {
            if (img.getBaseCost() != null)
                totalCost += img.getBaseCost();
            if (img.getSearchCost() != null)
                totalCost += img.getSearchCost();
            if (img.getReasoningCost() != null)
                totalCost += img.getReasoningCost();
        }
        return totalCost;
    }

    /**
     * Takes the price the provider reports for a finished job as the base cost of its image, which keeps its estimate
     * otherwise; the entry gets the sum of its images once all are finished. Call on the FX thread.
     *
     * @param image        the image of the job
     * @param reportedCost the price in US dollars, null if the provider reports none
     */
    public void updateCost(GeneratedImage image, Double reportedCost) {
        if (reportedCost == null || image == null)
            return;
        if (logger.isDebugEnabled())
            logger.debug("Reported cost of image {}: ${} (estimated ${})", image.getId(),
                    String.format("%.4f", reportedCost),
                    String.format("%.4f", image.getBaseCost() != null ? image.getBaseCost() : 0.0));
        image.setBaseCost(reportedCost);
    }
}
