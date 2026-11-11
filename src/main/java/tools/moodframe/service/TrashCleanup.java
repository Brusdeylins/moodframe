package tools.moodframe.service;

import tools.moodframe.model.Chat;
import java.util.List;
import java.util.function.Consumer;

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
     * Gets days until auto deletion for a chat.
     * 
     * @param chat the chat to check
     * @return days until deletion
     */
    int getDaysUntilAutoDeletion(Chat chat);
    
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
     * Previews items that would be cleaned up.
     * 
     * @return list of chats to be cleaned
     */
    List<Chat> previewAutoCleanup();
    
    /**
     * Sets callback for cleanup operations.
     * 
     * @param callback the callback to invoke with the IDs of the deleted chats
     */
    void setCleanupCallback(Consumer<List<String>> callback);
    
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