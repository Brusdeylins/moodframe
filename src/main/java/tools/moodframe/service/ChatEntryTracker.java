package tools.moodframe.service;

import tools.moodframe.config.ApplicationConfig;
import tools.moodframe.model.ChatEntry;
import tools.moodframe.model.GeneratedImage;
import tools.moodframe.model.GenerationJobResult;
import tools.moodframe.repository.ChatRepository;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
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

    private final ApplicationConfig config;
    private final ChatRepository chatRepository;
    private final Executor executor;

    private Consumer<ChatEntry> onEntryUpdated;
    private Consumer<GeneratedImage> onImageUpdated;

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

    public void setOnEntryUpdated(Consumer<ChatEntry> callback) {
        this.onEntryUpdated = callback;
    }

    public void setOnImageUpdated(Consumer<GeneratedImage> callback) {
        this.onImageUpdated = callback;
    }

    /**
     * Notifies listeners of chat entry updates.
     */
    public void notifyEntryUpdated(ChatEntry chatEntry) {
        if (onEntryUpdated != null) {
            Platform.runLater(() -> {
                try {
                    onEntryUpdated.accept(chatEntry);
                } catch (Exception e) {
                    logger.error("Error in entry update callback", e);
                }
            });
        }
    }

    /**
     * Notifies listeners of image updates.
     */
    public void notifyImageUpdated(GeneratedImage image) {
        if (onImageUpdated != null) {
            Platform.runLater(() -> {
                try {
                    onImageUpdated.accept(image);
                } catch (Exception e) {
                    logger.error("Error in image update callback", e);
                }
            });
        }
    }

    /**
     * Marks a chat entry as failed with the given message, shows it and stores it.
     */
    public void markFailed(ChatEntry chatEntry, String errorMessage) {
        Platform.runLater(() -> {
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
                if (hasCached) {
                    // At least one image succeeded - mark as COMPLETED
                    chatEntry.setStatus(ChatEntry.Status.COMPLETED);
                } else if (allFailed) {
                    // All images failed - mark as FAILED
                    chatEntry.setStatus(ChatEntry.Status.FAILED);
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
     * Updates the ChatEntry cost with actual API cost if available.
     * Uses real API cost when provided, otherwise keeps the estimated cost.
     * Accumulates costs across all images in the entry.
     */
    public void updateCostFromApi(ChatEntry chatEntry, GenerationJobResult jobResult) {
        if (jobResult == null || chatEntry == null) {
            return;
        }

        Double apiCost = jobResult.getCost();
        if (apiCost != null && apiCost > 0) {
            // Get current estimated cost
            double currentCost = chatEntry.getCost();

            // Calculate the per-image estimated cost
            int imageCount = chatEntry.getImages().size();
            double estimatedPerImage = imageCount > 0 ? currentCost / imageCount : 0;

            // Calculate the difference between estimated and actual cost
            double costDifference = apiCost - estimatedPerImage;
            double percentageDiff = estimatedPerImage > 0 ? (costDifference / estimatedPerImage) * 100 : 0;

            // Log cost comparison if there's a significant difference (more than 0.01 cents)
            if (Math.abs(costDifference) > 0.0001) { // Avoid floating point comparison issues
                logger.info("Cost comparison - Estimated: ${}, Actual: ${}, Difference: ${} ({}{:.1f}%)",
                    String.format("%.4f", estimatedPerImage),
                    String.format("%.4f", apiCost),
                    String.format("%+.4f", costDifference),
                    costDifference > 0 ? "+" : "",
                    percentageDiff);
            }

            // Add the real API cost (subtract one estimated cost and add the real one)
            double updatedCost = currentCost - estimatedPerImage + apiCost;

            // Update on FX thread
            Platform.runLater(() -> {
                chatEntry.setCost(updatedCost);

                // Persist updated cost to database
                if (chatRepository != null) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            chatRepository.saveChatEntry(chatEntry);
                            if (config.getLoggingEnabled()) {
                                logger.info("Updated ChatEntry total cost from API: ${} (was ${} estimated)",
                                    String.format("%.4f", updatedCost),
                                    String.format("%.4f", currentCost));
                            }
                        } catch (Exception e) {
                            logger.error("Failed to save updated ChatEntry cost to database", e);
                        }
                    }, executor);
                }
            });
        }
    }
}
