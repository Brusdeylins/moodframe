package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.model.Chat;
import art.moodframe.repository.ChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Service for managing trash retention and automatic cleanup.
 * 
 * <p>This service handles the automatic deletion of chats that have been
 * in the trash for more than the retention period (60 days by default).
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
 * <li>UI is automatically refreshed when expired chats are deleted</li>
 * <li>Cleanup callback notifies UI components to reload chat lists</li>
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
    
    private final ChatRepository chatRepository;
    private final int retentionDays;
    private final ScheduledExecutorService scheduler;
    private Consumer<List<String>> cleanupCallback;
    private boolean backgroundCleanupEnabled = true;
    private final ApplicationConfig config;
    
    /**
     * Creates a new trash cleanup service with default retention period.
     * 
     * @param chatRepository the chat repository
     */
    public TrashCleanupService(ChatRepository chatRepository) {
        this(chatRepository, DEFAULT_RETENTION_DAYS);
    }
    
    /**
     * Creates a new trash cleanup service with custom retention period.
     * 
     * @param chatRepository the chat repository
     * @param retentionDays the number of days to retain deleted items
     */
    public TrashCleanupService(ChatRepository chatRepository, int retentionDays) {
        this.chatRepository = chatRepository;
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
     * Performs automatic cleanup of old deleted chats.
     * This should be called on application startup.
     * 
     * @return the number of chats permanently deleted
     */
    @Override
    public int performAutoCleanup() {
        return cleanupExpired().size();
    }

    /**
     * Deletes the chats whose time in the trash is over.
     *
     * @return the IDs of the chats that are gone
     */
    private List<String> cleanupExpired() {
        List<String> deletedIds = new java.util.ArrayList<>();
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minus(retentionDays, ChronoUnit.DAYS);
            List<Chat> expiredChats = findExpiredDeletedChats(cutoffDate);
            
            if (expiredChats.isEmpty()) {
                return deletedIds;
            }
            
            
            int successCount = 0;
            int failCount = 0;
            
            for (Chat chat : expiredChats) {
                try {
                    logger.info("Auto-deleting expired chat '{}' (deleted {} days ago)", 
                        chat.getName(), 
                        java.time.temporal.ChronoUnit.DAYS.between(chat.getDeletedAt(), LocalDateTime.now()));
                    
                    boolean success = chatRepository.deleteChatPermanently(chat.getId());
                    
                    if (success) {
                        successCount++;
                        deletedIds.add(chat.getId());
                        logger.info("Successfully auto-deleted expired chat: {} and all its images", chat.getName());
                    } else {
                        failCount++;
                        logger.warn("Failed to fully delete expired chat: {} (some files may remain)", 
                            chat.getName());
                    }
                    
                } catch (Exception e) {
                    failCount++;
                    logger.error("Error deleting expired chat: {}", chat.getName(), e);
                }
            }
            
            if (config != null && config.getLoggingEnabled()) {
                logger.info("Trash cleanup completed: {} deleted, {} failed", successCount, failCount);
            }
            
            if (failCount > 0) {
                logger.warn("Some chats could not be fully deleted. Manual cleanup may be required.");
            }
            
            return deletedIds;
            
        } catch (Exception e) {
            logger.error("Failed to perform automatic trash cleanup", e);
            return deletedIds;
        }
    }
    
    /**
     * Finds all deleted chats that have exceeded the retention period.
     * 
     * @param cutoffDate the cutoff date for retention
     * @return list of expired deleted chats
     * @throws SQLException if database query fails
     */
    private List<Chat> findExpiredDeletedChats(LocalDateTime cutoffDate) throws SQLException {
        List<Chat> allDeletedChats = chatRepository.findDeletedChats();
        List<Chat> expiredChats = new ArrayList<>();
        
        for (Chat chat : allDeletedChats) {
            if (chat.getDeletedAt() != null && chat.getDeletedAt().isBefore(cutoffDate)) {
                expiredChats.add(chat);
            }
        }
        
        return expiredChats;
    }
    
    /**
     * Gets the number of days remaining before a deleted chat is auto-deleted.
     * 
     * @param chat the deleted chat
     * @return days remaining, or -1 if chat is not deleted
     */
    @Override
    public int getDaysUntilAutoDeletion(Chat chat) {
        if (chat.getDeletedAt() == null) {
            return -1; // Not in trash
        }
        
        long daysSinceDeletion = ChronoUnit.DAYS.between(chat.getDeletedAt(), LocalDateTime.now());
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
        int daysRemaining = getDaysUntilAutoDeletion(chat);
        
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
     * Performs a dry run of the cleanup to see what would be deleted.
     * 
     * @return list of chats that would be deleted
     */
    @Override
    public List<Chat> previewAutoCleanup() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minus(retentionDays, ChronoUnit.DAYS);
            return findExpiredDeletedChats(cutoffDate);
        } catch (SQLException e) {
            logger.error("Failed to preview auto-cleanup", e);
            return new ArrayList<>();
        }
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
                List<String> deletedIds = cleanupExpired();

                // Notify UI components to refresh if callback is set
                if (!deletedIds.isEmpty() && cleanupCallback != null) {
                    cleanupCallback.accept(deletedIds);
                }
                
            } catch (Exception e) {
                logger.error("Error during scheduled trash cleanup", e);
            }
        }, DEFAULT_CLEANUP_INTERVAL_HOURS, DEFAULT_CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS);
    }
    
    /**
     * Sets a callback to be notified when automatic cleanup deletes chats.
     * This allows UI components to refresh their display.
     * 
     * @param callback callback that receives the IDs of the deleted chats
     */
    @Override
    public void setCleanupCallback(Consumer<List<String>> callback) {
        this.cleanupCallback = callback;
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
     * @return the number of chats permanently deleted
     */
    @Override
    public int forceCleanup() {
        List<String> deletedIds = cleanupExpired();
        if (!deletedIds.isEmpty() && cleanupCallback != null) {
            cleanupCallback.accept(deletedIds);
        }
        return deletedIds.size();
    }
}