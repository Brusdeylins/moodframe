package art.moodframe.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain model representing a chat conversation.
 * 
 * <p>
 * This entity represents a chat container that holds multiple chat entries.
 * It supports soft deletion for trash/restore functionality.
 * </p>
 * 
 * <p>
 * <strong>Database Mapping:</strong> This entity maps to the {@code chats}
 * table in the SQLite database. It serves as the parent entity for all
 * chat entries in the session.
 * </p>
 * 
 * <p>
 * <strong>External References:</strong>
 * </p>
 * <ul>
 * <li><a href="https://docs.bfl.ai/models">BFL AI Models Documentation</a></li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class Chat {

    private String id;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private int sortOrder;
    private double deletedCost = 0.0;
    private final List<ChatEntry> entries = new ArrayList<>();

    /**
     * Creates a new chat with auto-generated UUID.
     */
    public Chat() {
        this(UUID.randomUUID().toString());
    }

    /**
     * Creates a chat with the specified ID.
     * 
     * @param id the chat ID
     */
    public Chat(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.sortOrder = 0;
    }

    /**
     * Builder pattern for Chat creation.
     */
    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String name = "New Chat";
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();
        private LocalDateTime deletedAt = null;
        private int sortOrder = 0;
        private double deletedCost = 0.0;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder deletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
            return this;
        }

        public Builder sortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }
        
        public Builder deletedCost(double deletedCost) {
            this.deletedCost = deletedCost;
            return this;
        }

        public Chat build() {
            Chat chat = new Chat(id);
            chat.setName(name);
            chat.setCreatedAt(createdAt);
            chat.setUpdatedAt(updatedAt);
            chat.setDeletedAt(deletedAt);
            chat.setSortOrder(sortOrder);
            chat.setDeletedCost(deletedCost);
            return chat;
        }
    }

    // Standard getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        this.updatedAt = LocalDateTime.now();
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
    
    public double getDeletedCost() {
        return deletedCost;
    }
    
    public void setDeletedCost(double deletedCost) {
        this.deletedCost = deletedCost;
    }

    /** The entries, a plain list: the views show copies of it ({@code ChatLibrary.entriesOf}), none observes it. */
    public List<ChatEntry> getEntries() {
        return entries;
    }

    /**
     * Checks if this chat is deleted (soft delete).
     * 
     * @return true if the chat is in trash
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Marks this chat as deleted (soft delete).
     */
    public void markAsDeleted() {
        setDeletedAt(LocalDateTime.now());
    }

    /**
     * Restores this chat from trash.
     */
    public void restore() {
        setDeletedAt(null);
    }

    /**
     * Adds a new chat entry to this chat.
     * 
     * @param entry the chat entry to add
     */
    public void addEntry(ChatEntry entry) {
        entry.setChatId(this.getId());
        entries.add(entry);
        setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Removes a chat entry from this chat.
     * 
     * @param entry the chat entry to remove
     * @return true if the entry was removed
     */
    public boolean removeEntry(ChatEntry entry) {
        boolean removed = entries.remove(entry);
        if (removed) {
            setUpdatedAt(LocalDateTime.now());
        }
        return removed;
    }

    @Override
    public String toString() {
        return String.format("Chat{id='%s', name='%s', entries=%d, deleted=%s}",
                getId(), getName(), entries.size(), isDeleted());
    }
}