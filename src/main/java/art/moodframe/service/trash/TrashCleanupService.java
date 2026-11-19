package art.moodframe.service.trash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
 * <li>Automatic cleanup runs every 6 hours during application runtime, from {@link #start()} on</li>
 * <li>UI is automatically refreshed when expired items are deleted</li>
 * <li>The listeners of the {@link TrashCenter} learn which items are gone</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class TrashCleanupService {

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
    private ScheduledExecutorService scheduler;  // from start() on, guarded by this

    /**
     * Creates a new trash cleanup service with default retention period.
     *
     * @param trashCenter the central trash with its groups
     */
    public TrashCleanupService(TrashCenter trashCenter) {
        this(trashCenter, DEFAULT_RETENTION_DAYS);
    }

    /**
     * Creates a new trash cleanup service with custom retention period. It cleans up nothing until it is
     * {@link #start() started} or asked to.
     *
     * @param trashCenter the central trash with its groups
     * @param retentionDays the number of days to retain deleted items
     */
    public TrashCleanupService(TrashCenter trashCenter, int retentionDays) {
        this.trashCenter = trashCenter;
        this.retentionDays = retentionDays;
    }

    /**
     * Performs automatic cleanup of old deleted items.
     * This should be called on application startup.
     *
     * @return the number of items permanently deleted
     */
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

                /*  An item with parts expires part by part, as its parts went to the trash one by one  */
                List<String> expiredIds = new ArrayList<>();
                for (TrashEntry entry : trashCenter.list(kind)) {
                    if (entry.parts().isEmpty() && entry.deletedAt() != null && entry.deletedAt().isBefore(cutoffDate))
                        expiredIds.add(entry.id());
                    for (TrashEntry.Part part : entry.parts())
                        if (part.deletedAt() != null && part.deletedAt().isBefore(cutoffDate))
                            expiredIds.add(part.id());
                }
                if (expiredIds.isEmpty())
                    continue;

                logger.info("Auto-deleting {} expired items of the trash group {}", expiredIds.size(), kind);
                int deleted = trashCenter.deletePermanently(kind, expiredIds).size();
                successCount += deleted;
                failCount += expiredIds.size() - deleted;
            }

            if (successCount + failCount > 0) {
                logger.debug("Trash cleanup completed: {} deleted, {} failed", successCount, failCount);
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
    public int getDaysUntilAutoDeletion(LocalDateTime deletedAt) {
        if (deletedAt == null) {
            return -1; // Not in trash
        }

        long daysSinceDeletion = ChronoUnit.DAYS.between(deletedAt, LocalDateTime.now());
        int daysRemaining = retentionDays - (int) daysSinceDeletion;

        return Math.max(0, daysRemaining);
    }

    /**
     * Starts the background cleanup timer, once per program start: the cleanup runs every 6 hours, the first time
     * 6 hours from now. A second call changes nothing.
     */
    public synchronized void start() {
        if (scheduler != null)
            return;
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "TrashCleanupService");
            t.setDaemon(true); // Don't prevent JVM shutdown
            return t;
        });

        logger.debug("Starting background trash cleanup - will run every {} hours", DEFAULT_CLEANUP_INTERVAL_HOURS);

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
     * Shuts down the background cleanup service.
     * Should be called when the application is closing.
     */
    public void shutdown() {
        ScheduledExecutorService scheduler;
        synchronized (this) {
            scheduler = this.scheduler;
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.debug("Shutting down TrashCleanupService background tasks...");
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
    public int forceCleanup() {
        return cleanupExpired();
    }
}