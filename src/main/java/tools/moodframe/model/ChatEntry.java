package tools.moodframe.model;

import javafx.beans.property.*;
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
    
    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty chatId = new SimpleStringProperty();
    private final StringProperty userPrompt = new SimpleStringProperty();
    private final StringProperty enhancedPrompt = new SimpleStringProperty();
    private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.PENDING);
    private final StringProperty errorMessage = new SimpleStringProperty();
    private final ObjectProperty<LocalDateTime> createdAt = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> completedAt = new SimpleObjectProperty<>();
    private final IntegerProperty sortOrder = new SimpleIntegerProperty();
    private final ObservableList<GeneratedImage> images = FXCollections.observableArrayList();
    
    // Generation parameters
    private final StringProperty model = new SimpleStringProperty();
    private final StringProperty size = new SimpleStringProperty();
    private final StringProperty format = new SimpleStringProperty();
    private final StringProperty style = new SimpleStringProperty(); // Style preset name
    private final BooleanProperty promptUpsampling = new SimpleBooleanProperty();
    private final IntegerProperty safetyTolerance = new SimpleIntegerProperty();
    private final LongProperty seed = new SimpleLongProperty();
    private final DoubleProperty cost = new SimpleDoubleProperty(); // Estimated cost for this generation
    private final DoubleProperty deletedCost = new SimpleDoubleProperty(0.0); // Cost of deleted images
    private final ObjectProperty<LocalDateTime> deletedAt = new SimpleObjectProperty<>();
    
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
        this.id.set(id);
        this.createdAt.set(LocalDateTime.now());
        this.sortOrder.set(0);
        this.promptUpsampling.set(false);
        this.safetyTolerance.set(2);
        this.seed.set(-1);
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
            entry.setPromptUpsampling(promptUpsampling);
            entry.setSafetyTolerance(safetyTolerance);
            entry.setSeed(seed);
            entry.setCost(cost);
            entry.setDeletedCost(deletedCost);
            entry.setDeletedAt(deletedAt);
            return entry;
        }
    }
    
    // Property getters for JavaFX binding
    
    public StringProperty idProperty() {
        return id;
    }
    
    public StringProperty chatIdProperty() {
        return chatId;
    }
    
    public StringProperty userPromptProperty() {
        return userPrompt;
    }
    
    public StringProperty enhancedPromptProperty() {
        return enhancedPrompt;
    }
    
    public ObjectProperty<Status> statusProperty() {
        return status;
    }
    
    public StringProperty errorMessageProperty() {
        return errorMessage;
    }
    
    public ObjectProperty<LocalDateTime> createdAtProperty() {
        return createdAt;
    }
    
    public ObjectProperty<LocalDateTime> completedAtProperty() {
        return completedAt;
    }
    
    public IntegerProperty sortOrderProperty() {
        return sortOrder;
    }
    
    public StringProperty modelProperty() {
        return model;
    }
    
    public StringProperty sizeProperty() {
        return size;
    }
    
    public StringProperty formatProperty() {
        return format;
    }
    
    public BooleanProperty promptUpsamplingProperty() {
        return promptUpsampling;
    }
    
    public IntegerProperty safetyToleranceProperty() {
        return safetyTolerance;
    }
    
    public LongProperty seedProperty() {
        return seed;
    }
    
    // Standard getters and setters
    
    public String getId() {
        return id.get();
    }
    
    public void setId(String id) {
        this.id.set(id);
    }
    
    public String getChatId() {
        return chatId.get();
    }
    
    public void setChatId(String chatId) {
        this.chatId.set(chatId);
    }
    
    public String getUserPrompt() {
        return userPrompt.get();
    }
    
    public void setUserPrompt(String userPrompt) {
        this.userPrompt.set(userPrompt);
    }
    
    public String getEnhancedPrompt() {
        return enhancedPrompt.get();
    }
    
    public void setEnhancedPrompt(String enhancedPrompt) {
        this.enhancedPrompt.set(enhancedPrompt);
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
        return errorMessage.get();
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage.set(errorMessage);
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt.get();
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt.set(createdAt);
    }
    
    public LocalDateTime getCompletedAt() {
        return completedAt.get();
    }
    
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt.set(completedAt);
    }
    
    public int getSortOrder() {
        return sortOrder.get();
    }
    
    public void setSortOrder(int sortOrder) {
        this.sortOrder.set(sortOrder);
    }
    
    public String getModel() {
        return model.get();
    }
    
    public void setModel(String model) {
        this.model.set(model);
    }
    
    public String getSize() {
        return size.get();
    }
    
    public void setSize(String size) {
        this.size.set(size);
    }
    
    public String getFormat() {
        return format.get();
    }

    public void setFormat(String format) {
        this.format.set(format);
    }

    public String getStyle() {
        return style.get();
    }

    public void setStyle(String style) {
        this.style.set(style);
    }

    public StringProperty styleProperty() {
        return style;
    }

    public boolean isPromptUpsampling() {
        return promptUpsampling.get();
    }
    
    public void setPromptUpsampling(boolean promptUpsampling) {
        this.promptUpsampling.set(promptUpsampling);
    }
    
    public int getSafetyTolerance() {
        return safetyTolerance.get();
    }
    
    public void setSafetyTolerance(int safetyTolerance) {
        this.safetyTolerance.set(safetyTolerance);
    }
    
    public long getSeed() {
        return seed.get();
    }
    
    public void setSeed(long seed) {
        this.seed.set(seed);
    }
    
    public double getCost() {
        return cost.get();
    }
    
    public void setCost(double cost) {
        this.cost.set(cost);
    }
    
    public DoubleProperty costProperty() {
        return cost;
    }
    
    public double getDeletedCost() {
        return deletedCost.get();
    }
    
    public void setDeletedCost(double deletedCost) {
        this.deletedCost.set(deletedCost);
    }
    
    public DoubleProperty deletedCostProperty() {
        return deletedCost;
    }
    
    public LocalDateTime getDeletedAt() {
        return deletedAt.get();
    }
    
    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt.set(deletedAt);
    }
    
    public ObjectProperty<LocalDateTime> deletedAtProperty() {
        return deletedAt;
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
     * Removes a generated image from this entry.
     * 
     * @param image the image to remove
     * @return true if the image was removed
     */
    public boolean removeImage(GeneratedImage image) {
        return images.remove(image);
    }
    
    /**
     * Checks if this entry is currently processing.
     * 
     * @return true if status is PROCESSING
     */
    public boolean isProcessing() {
        return status.get() == Status.PROCESSING;
    }
    
    /**
     * Checks if this entry has completed successfully.
     * 
     * @return true if status is COMPLETED
     */
    public boolean isCompleted() {
        return status.get() == Status.COMPLETED;
    }
    
    /**
     * Checks if this entry has failed.
     * 
     * @return true if status is FAILED
     */
    public boolean isFailed() {
        return status.get() == Status.FAILED;
    }
    
    /**
     * Marks this entry as failed with an error message.
     * 
     * @param errorMessage the error message
     */
    public void markAsFailed(String errorMessage) {
        setStatus(Status.FAILED);
        setErrorMessage(errorMessage);
    }
    
    /**
     * Gets the effective prompt (enhanced if available, otherwise user prompt).
     * 
     * @return the prompt to use for generation
     */
    public String getEffectivePrompt() {
        String enhanced = getEnhancedPrompt();
        return (enhanced != null && !enhanced.isEmpty()) ? enhanced : getUserPrompt();
    }
    
    @Override
    public String toString() {
        return String.format("ChatEntry{id='%s', chatId='%s', status=%s, images=%d, prompt='%s'}", 
            getId(), getChatId(), getStatus(), images.size(), 
            getUserPrompt() != null ? getUserPrompt().substring(0, Math.min(50, getUserPrompt().length())) : "null");
    }
}