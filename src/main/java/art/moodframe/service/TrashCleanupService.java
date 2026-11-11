package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.model.Chat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing trash retention and automatic cleanup.
 * 
 * <p>This service handles the automatic deletion of the items of all groups
 * of the {@link TrashCenter} that have been in the trash for more than the
 * retention period (60 days by default).
 * Images are only deleted when chats are permanently removed from trash.</p>
 * 
 * <p><strong>Deletion Lifecycle:</strong></p>
 * <ol>
 * <li>User deletes chat → Soft delete (moves to trash, files retained)</li>
 * <li>Chat stays in trash for up to 60 days</li>
 * <li>Either: User manually deletes from trash OR auto-cleanup after 60 days</li>
 * <li>Only then: Database records AND image files are permanently deleted</li>
 * </ol>
 * 
 * <p><strong>Background Cleanup:</strong></p>
 * <ul>
 * <li>Automatic cleanup runs every 6 hours during application runtime</li>
 * <li>UI is automatically refreshed when expired items are deleted</li>
 * <li>The listeners of the {@link TrashCenter} learn which items are gone</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class TrashCleanupService implements TrashCleanup {
    
    private static final Logger logger = LoggerFactory.getLogger(TrashCleanupService.class);
    
    /**
     * Default retention period in days before automatic deletion.
     */
    public static final int DEFAULT_RETENTION_DAYS = 60;
    
    /**
     * Default cleanup interval in hours.
     */
    public static final int DEFAULT_CLEANUP_INTERVAL_HOURS = 6;
    
    private final TrashCenter trashCenter;
    private final int retentionDays;
    private final ScheduledExecutorService scheduler;
    private boolean backgroundCleanupEnabled = true;
    private final ApplicationConfig config;
    
    /**
     * Creates a new trash cleanup service with default retention period.
     * 
     * @param trashCenter the central trash with its groups
     */
    public TrashCleanupService(TrashCenter trashCenter) {
        this(trashCenter, DEFAULT_RETENTION_DAYS);
    }
    
    /**
     * Creates a new trash cleanup service with custom retention period.
     * 
     * @param trashCenter the central trash with its groups
     * @param retentionDays the number of days to retain deleted items
     */
    public TrashCleanupService(TrashCenter trashCenter, int retentionDays) {
        this.trashCenter = trashCenter;
        this.retentionDays = retentionDays;
        this.config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "TrashCleanupService");
            t.setDaemon(true); // Don't prevent JVM shutdown
            return t;
        });
        
        startBackgroundCleanup();
    }
    
    /**
     * Performs automatic cleanup of old deleted items.
     * This should be called on application startup.
     * 
     * @return the number of items permanently deleted
     */
    @Override
    public int performAutoCleanup() {
        return cleanupExpired();
    }

    /**
     * Deletes the items of all groups whose time in the trash is over.
     *
     * @return the number of items that are gone
     */
    private int cleanupExpired() {
        int successCount = 0;
        int failCount = 0;
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minus(retentionDays, ChronoUnit.DAYS);
            for (TrashGroup.Kind kind : TrashGroup.Kind.values()) {
                List<String> expiredIds = trashCenter.list(kind).stream()
                        .filter((entry) -> entry.deletedAt() != null && entry.deletedAt().isBefore(cutoffDate))
                        .map(TrashEntry::id)
                        .toList();
                if (expiredIds.isEmpty())
                    continue;

                logger.info("Auto-deleting {} expired items of the trash group {}", expiredIds.size(), kind);
                int deleted = trashCenter.deletePermanently(kind, expiredIds).size();
                successCount += deleted;
                failCount += expiredIds.size() - deleted;
            }

            if (successCount + failCount > 0 && config != null && config.getLoggingEnabled()) {
                logger.info("Trash cleanup completed: {} deleted, {} failed", successCount, failCount);
            }

            if (failCount > 0) {
                logger.warn("Some items could not be fully deleted. Manual cleanup may be required.");
            }
        } catch (Exception e) {
            logger.error("Failed to perform automatic trash cleanup", e);
        }
        return successCount;
    }
    
    /**
     * Gets the number of days remaining before a deleted item is auto-deleted.
     * 
     * @param deletedAt when the item was moved to the trash
     * @return days remaining, or -1 if the item is not deleted
     */
    @Override
    public int getDaysUntilAutoDeletion(LocalDateTime deletedAt) {
        if (deletedAt == null) {
            return -1; // Not in trash
        }
        
        long daysSinceDeletion = ChronoUnit.DAYS.between(deletedAt, LocalDateTime.now());
        int daysRemaining = retentionDays - (int) daysSinceDeletion;
        
        return Math.max(0, daysRemaining);
    }
    
    /**
     * Gets a user-friendly message about when a chat will be auto-deleted.
     * 
     * @param chat the deleted chat
     * @return a message describing the auto-deletion timeline
     */
    @Override
    public String getRetentionMessage(Chat chat) {
        int daysRemaining = getDaysUntilAutoDeletion(chat.getDeletedAt());
        
        if (daysRemaining < 0) {
            return "Not in trash";
        } else if (daysRemaining == 0) {
            return "Will be permanently deleted today";
        } else if (daysRemaining == 1) {
            return "Will be permanently deleted tomorrow";
        } else if (daysRemaining <= 7) {
            return String.format("Will be permanently deleted in %d days", daysRemaining);
        } else {
            return String.format("Will be permanently deleted in %d days", daysRemaining);
        }
    }
    
    /**
     * Checks if a deleted chat has exceeded the retention period.
     * 
     * @param chat the chat to check
     * @return true if the chat should be auto-deleted
     */
    @Override
    public boolean isExpired(Chat chat) {
        if (chat.getDeletedAt() == null) {
            return false;
        }
        
        LocalDateTime cutoffDate = LocalDateTime.now().minus(retentionDays, ChronoUnit.DAYS);
        return chat.getDeletedAt().isBefore(cutoffDate);
    }
    
    /**
     * Gets the configured retention period in days.
     * 
     * @return the retention period
     */
    @Override
    public int getRetentionDays() {
        return retentionDays;
    }
    
    /**
     * Starts the background cleanup timer.
     * Cleanup runs every 6 hours automatically.
     */
    private void startBackgroundCleanup() {
        if (!backgroundCleanupEnabled) {
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Background cleanup is disabled");
            }
            return;
        }
        
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Starting background trash cleanup - will run every {} hours", DEFAULT_CLEANUP_INTERVAL_HOURS);
        }
        
        // Schedule cleanup to run every 6 hours
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupExpired();
            } catch (Exception e) {
                logger.error("Error during scheduled trash cleanup", e);
            }
        }, DEFAULT_CLEANUP_INTERVAL_HOURS, DEFAULT_CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS);
    }
    
    /**
     * Enables or disables background cleanup.
     * 
     * @param enabled true to enable background cleanup, false to disable
     */
    @Override
    public void setBackgroundCleanupEnabled(boolean enabled) {
        this.backgroundCleanupEnabled = enabled;
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Background cleanup {}", enabled ? "enabled" : "disabled");
        }
    }
    
    /**
     * Shuts down the background cleanup service.
     * Should be called when the application is closing.
     */
    @Override
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Shutting down TrashCleanupService background tasks...");
            }
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("Background cleanup tasks did not terminate within 5 seconds, forcing shutdown");
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for background cleanup shutdown");
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Forces an immediate cleanup run outside of the scheduled interval.
     * 
     * @return the number of items permanently deleted
     */
    @Override
    public int forceCleanup() {
        return cleanupExpired();
    }
}