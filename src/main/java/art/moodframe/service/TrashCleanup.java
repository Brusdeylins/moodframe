package art.moodframe.service;

import art.moodframe.model.Chat;
import java.time.LocalDateTime;

/**
 * Interface for trash cleanup operations.
 * 
 * @since 1.0.0
 */
public interface TrashCleanup {
    
    /**
     * Performs automatic cleanup of expired trash items.
     * 
     * @return number of items cleaned up
     */
    int performAutoCleanup();
    
    /**
     * Gets days until auto deletion for an item in the trash.
     * 
     * @param deletedAt when the item was moved to the trash
     * @return days until deletion
     */
    int getDaysUntilAutoDeletion(LocalDateTime deletedAt);
    
    /**
     * Gets retention message for a chat.
     * 
     * @param chat the chat to check
     * @return retention message
     */
    String getRetentionMessage(Chat chat);
    
    /**
     * Checks if a chat has expired.
     * 
     * @param chat the chat to check
     * @return true if expired
     */
    boolean isExpired(Chat chat);
    
    /**
     * Gets the retention period in days.
     * 
     * @return retention days
     */
    int getRetentionDays();
    
    /**
     * Enables or disables background cleanup.
     * 
     * @param enabled true to enable background cleanup
     */
    void setBackgroundCleanupEnabled(boolean enabled);
    
    /**
     * Shuts down the cleanup service.
     */
    void shutdown();
    
    /**
     * Forces immediate cleanup.
     * 
     * @return number of items cleaned up
     */
    int forceCleanup();
}