package art.moodframe.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain model representing a single entry in a chat conversation.
 * 
 * <p>This entity represents a message exchange between user and AI,
 * including the generated images and metadata.</p>
 * 
 * <p><strong>Database Mapping:</strong> This entity maps to the {@code chat_entries}
 * table in the SQLite database.</p>
 * 
 * @since 1.0.0
 */
public class ChatEntry {
    
    /**
     * Status enum representing the state of a chat entry.
     */
    public enum Status {
        /** Initial state, no processing started */
        PENDING("Pending"),
        /** Currently being processed by the API */
        PROCESSING("Processing"),
        /** Generation completed successfully */
        COMPLETED("Completed"),
        /** Generation failed with error */
        FAILED("Failed"),
        /** Request was cancelled by user */
        CANCELLED("Cancelled");
        
        private final String displayName;
        
        Status(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Who placed the order of the entry: the user over the send button or an AI agent over the MCP server.
     */
    public enum Source {
        /** The send button of the main window, also for every entry from before */
        UI,
        /** The tool {@code generate} of the MCP server */
        MCP
    }
    
    private String id;
    private String chatId;
    private final StringProperty userPrompt = new SimpleStringProperty();
    private String enhancedPrompt;
    private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.PENDING);
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private int sortOrder;
    private final ObservableList<GeneratedImage> images = FXCollections.observableArrayList();
    
    // Generation parameters
    private String model;
    private String size;
    private String format;
    private String style; // Style preset name
    private EntryMood mood;
    private EntryFrame frame;
    private Source source = Source.UI;
    private boolean promptUpsampling;
    private int safetyTolerance;
    private long seed;
    private double cost; // Estimated cost for this generation
    private double deletedCost = 0.0; // Cost of deleted images
    private LocalDateTime deletedAt;
    
    /**
     * Creates a new chat entry with auto-generated UUID.
     */
    public ChatEntry() {
        this(UUID.randomUUID().toString());
    }
    
    /**
     * Creates a chat entry with the specified ID.
     * 
     * @param id the entry ID
     */
    public ChatEntry(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
        this.sortOrder = 0;
        this.promptUpsampling = false;
        this.safetyTolerance = 2;
        this.seed = -1;
    }
    
    /**
     * Builder pattern for ChatEntry creation.
     */
    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String chatId;
        private String userPrompt;
        private String enhancedPrompt;
        private Status status = Status.PENDING;
        private String errorMessage;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime completedAt;
        private int sortOrder = 0;
        private String model = "flux-dev";
        private String size = "1024x576";
        private String format = "png";
        private String style = "None";
        private EntryMood mood = null;
        private EntryFrame frame = null;
        private Source source = Source.UI;
        private boolean promptUpsampling = false;
        private int safetyTolerance = 2;
        private long seed = -1;
        private double cost = 0.0;
        private double deletedCost = 0.0;
        private LocalDateTime deletedAt = null;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder chatId(String chatId) {
            this.chatId = chatId;
            return this;
        }
        
        public Builder userPrompt(String userPrompt) {
            this.userPrompt = userPrompt;
            return this;
        }
        
        public Builder enhancedPrompt(String enhancedPrompt) {
            this.enhancedPrompt = enhancedPrompt;
            return this;
        }
        
        public Builder status(Status status) {
            this.status = status;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }
        
        public Builder sortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }
        
        public Builder model(String model) {
            this.model = model;
            return this;
        }
        
        public Builder size(String size) {
            this.size = size;
            return this;
        }
        
        public Builder format(String format) {
            this.format = format;
            return this;
        }
        
        public Builder promptUpsampling(boolean promptUpsampling) {
            this.promptUpsampling = promptUpsampling;
            return this;
        }
        
        public Builder safetyTolerance(int safetyTolerance) {
            this.safetyTolerance = safetyTolerance;
            return this;
        }
        
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        public Builder style(String style) {
            this.style = style;
            return this;
        }

        public Builder mood(EntryMood mood) {
            this.mood = mood;
            return this;
        }

        public Builder frame(EntryFrame frame) {
            this.frame = frame;
            return this;
        }

        public Builder source(Source source) {
            this.source = source;
            return this;
        }

        public Builder cost(double cost) {
            this.cost = cost;
            return this;
        }
        
        public Builder deletedCost(double deletedCost) {
            this.deletedCost = deletedCost;
            return this;
        }
        
        public Builder deletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
            return this;
        }
        
        public ChatEntry build() {
            ChatEntry entry = new ChatEntry(id);
            entry.setChatId(chatId);
            entry.setUserPrompt(userPrompt);
            entry.setEnhancedPrompt(enhancedPrompt);
            entry.setStatus(status);
            entry.setErrorMessage(errorMessage);
            entry.setCreatedAt(createdAt);
            entry.setCompletedAt(completedAt);
            entry.setSortOrder(sortOrder);
            entry.setModel(model);
            entry.setSize(size);
            entry.setFormat(format);
            entry.setStyle(style);
            entry.setMood(mood);
            entry.setFrame(frame);
            entry.setSource(source);
            entry.setPromptUpsampling(promptUpsampling);
            entry.setSafetyTolerance(safetyTolerance);
            entry.setSeed(seed);
            entry.setCost(cost);
            entry.setDeletedCost(deletedCost);
            entry.setDeletedAt(deletedAt);
            return entry;
        }
    }
    
    /*  The prompt, the status and the images are observed by the entry card ({@code ui.entry.ChatEntryCard}),
        the images also by the images view; every other value is plain  */

    public StringProperty userPromptProperty() {
        return userPrompt;
    }

    public ObjectProperty<Status> statusProperty() {
        return status;
    }

    // Standard getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getChatId() {
        return chatId;
    }
    
    public void setChatId(String chatId) {
        this.chatId = chatId;
    }
    
    public String getUserPrompt() {
        return userPrompt.get();
    }
    
    public void setUserPrompt(String userPrompt) {
        this.userPrompt.set(userPrompt);
    }
    
    public String getEnhancedPrompt() {
        return enhancedPrompt;
    }
    
    public void setEnhancedPrompt(String enhancedPrompt) {
        this.enhancedPrompt = enhancedPrompt;
    }
    
    public Status getStatus() {
        return status.get();
    }
    
    public void setStatus(Status status) {
        this.status.set(status);
        if (status == Status.COMPLETED) {
            setCompletedAt(LocalDateTime.now());
        }
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
    
    public int getSortOrder() {
        return sortOrder;
    }
    
    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
    
    /**
     * Gets the model of the entry as stored: its fixed id since 2.1.3 ({@link GenerationModel#entryModelOf(String)}),
     * the English display name with price and tagline in older entries; read it with
     * {@link GenerationModel#fromEntryModel(String)}, show it with {@code ModelLabels.entryName}.
     *
     * @return the stored model
     */
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public String getSize() {
        return size;
    }
    
    public void setSize(String size) {
        this.size = size;
    }
    
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    /** The mood and the building blocks the entry was generated with, null for an entry without any. */
    public EntryMood getMood() {
        return mood;
    }

    public void setMood(EntryMood mood) {
        this.mood = mood;
    }

    /** The frame and the target the entry was generated with, null for an entry from before the frames were copied. */
    public EntryFrame getFrame() {
        return frame;
    }

    public void setFrame(EntryFrame frame) {
        this.frame = frame;
    }

    /** Who placed the order of the entry, UI for every entry from before. */
    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public boolean isPromptUpsampling() {
        return promptUpsampling;
    }
    
    public void setPromptUpsampling(boolean promptUpsampling) {
        this.promptUpsampling = promptUpsampling;
    }
    
    public int getSafetyTolerance() {
        return safetyTolerance;
    }
    
    public void setSafetyTolerance(int safetyTolerance) {
        this.safetyTolerance = safetyTolerance;
    }
    
    public long getSeed() {
        return seed;
    }
    
    public void setSeed(long seed) {
        this.seed = seed;
    }
    
    public double getCost() {
        return cost;
    }
    
    public void setCost(double cost) {
        this.cost = cost;
    }

    public double getDeletedCost() {
        return deletedCost;
    }
    
    public void setDeletedCost(double deletedCost) {
        this.deletedCost = deletedCost;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
    
    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public ObservableList<GeneratedImage> getImages() {
        return images;
    }
    
    /**
     * Adds a generated image to this entry.
     * 
     * @param image the image to add
     */
    public void addImage(GeneratedImage image) {
        image.setChatEntryId(this.getId());
        images.add(image);
    }
    
    /**
     * Checks if this entry has failed.
     * 
     * @return true if status is FAILED
     */
    public boolean isFailed() {
        return status.get() == Status.FAILED;
    }
    
    @Override
    public String toString() {
        return String.format("ChatEntry{id='%s', chatId='%s', status=%s, images=%d, prompt='%s'}", 
            getId(), getChatId(), getStatus(), images.size(), 
            getUserPrompt() != null ? getUserPrompt().substring(0, Math.min(50, getUserPrompt().length())) : "null");
    }
}