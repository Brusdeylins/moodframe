package art.moodframe.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Domain model representing a generated image with metadata.
 * 
 * <p>This entity contains all information about a generated image,
 * including the URL, local file path, generation parameters, and metadata.</p>
 * 
 * <p><strong>Database Mapping:</strong> This entity maps to the {@code generated_images}
 * table in the SQLite database.</p>
 * 
 * @since 1.0.0
 */
public class GeneratedImage {
    
    /**
     * Download status enum for tracking image download progress.
     */
    public enum DownloadStatus {
        /** Image URL received but not downloaded yet */
        PENDING("Pending"),
        /** Currently downloading the image */
        DOWNLOADING("Downloading"),
        /** Image downloaded and cached locally */
        CACHED("Cached"),
        /** Download failed */
        FAILED("Failed");
        
        private final String displayName;
        
        DownloadStatus(String displayName) {
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
    
    private String id;
    private String chatEntryId;
    private String imageUrl;
    private String localFilePath;
    private String fileName;
    private final ObjectProperty<DownloadStatus> downloadStatus = new SimpleObjectProperty<>(DownloadStatus.PENDING);
    private LocalDateTime createdAt;
    private LocalDateTime downloadedAt;
    private long fileSizeBytes;
    private final IntegerProperty imageWidth = new SimpleIntegerProperty();
    private final IntegerProperty imageHeight = new SimpleIntegerProperty();
    private String mimeType;
    private int sortOrder;
    
    // Generation metadata
    private String generationJobId;
    private String prompt;
    private String model;
    private long seed;
    private int safetyTolerance;
    private boolean promptUpsampling;
    
    // Error tracking fields
    private String errorMessage;
    private String failureReason;
    private String apiStatus;
    
    // Additional generation parameters for recreation
    private String aspectRatio;
    private String stylePreset;
    private String format;
    private final IntegerProperty width = new SimpleIntegerProperty();
    private final IntegerProperty height = new SimpleIntegerProperty();
    
    // Soft delete tracking
    private boolean isInTrash = false;
    private LocalDateTime deletedAt;

    // Gemini Phase 2 Advanced Features - Cost tracking and metadata
    private Double baseCost = 0.0; // Base model generation cost
    private Double searchCost = 0.0;
    private Double reasoningCost = 0.0;
    private Integer thinkingTokens = 0;
    private String thoughtSummary;

    // video metadata
    private boolean isVideo = false;
    private int videoDurationSeconds;
    private String videoResolution;
    private boolean hasAudio = false;

    /**
     * Creates a new generated image with auto-generated UUID.
     */
    public GeneratedImage() {
        this(UUID.randomUUID().toString());
    }
    
    /**
     * Creates a generated image with the specified ID.
     * 
     * @param id the image ID
     */
    public GeneratedImage(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
        this.sortOrder = 0;
        this.fileSizeBytes = 0;
        this.imageWidth.set(0);
        this.imageHeight.set(0);
        this.seed = -1;
        this.safetyTolerance = 2;
        this.promptUpsampling = false;
    }
    
    /**
     * Builder pattern for GeneratedImage creation.
     */
    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String chatEntryId;
        private String imageUrl;
        private String localFilePath;
        private String fileName;
        private DownloadStatus downloadStatus = DownloadStatus.PENDING;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime downloadedAt;
        private long fileSizeBytes = 0;
        private int imageWidth = 0;
        private int imageHeight = 0;
        private String mimeType;
        private int sortOrder = 0;
        private String generationJobId;
        private String prompt;
        private String model;
        private long seed = -1;
        private int safetyTolerance = 2;
        private boolean promptUpsampling = false;
        
        // Error tracking fields
        private String errorMessage;
        private String failureReason;
        private String apiStatus;
        
        // Additional generation parameters
        private String aspectRatio;
        private String stylePreset;
        private String format;
        private int width = 0;
        private int height = 0;
        private boolean isInTrash = false;
        private LocalDateTime deletedAt;

        // Gemini Phase 2 cost tracking fields
        private Double baseCost = 0.0;
        private Double searchCost = 0.0;
        private Double reasoningCost = 0.0;
        private Integer thinkingTokens = 0;
        private String thoughtSummary;

        // video fields
        private boolean isVideo = false;
        private int videoDurationSeconds = 0;
        private String videoResolution;
        private boolean hasAudio = false;

        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder chatEntryId(String chatEntryId) {
            this.chatEntryId = chatEntryId;
            return this;
        }
        
        public Builder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }
        
        public Builder localFilePath(String localFilePath) {
            this.localFilePath = localFilePath;
            return this;
        }
        
        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }
        
        public Builder filename(String fileName) {
            this.fileName = fileName;
            return this;
        }
        
        public Builder downloadStatus(DownloadStatus downloadStatus) {
            this.downloadStatus = downloadStatus;
            return this;
        }
        
        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder downloadedAt(LocalDateTime downloadedAt) {
            this.downloadedAt = downloadedAt;
            return this;
        }
        
        public Builder fileSizeBytes(long fileSizeBytes) {
            this.fileSizeBytes = fileSizeBytes;
            return this;
        }
        
        public Builder imageWidth(int imageWidth) {
            this.imageWidth = imageWidth;
            return this;
        }
        
        public Builder width(int imageWidth) {
            this.imageWidth = imageWidth;
            return this;
        }
        
        public Builder imageHeight(int imageHeight) {
            this.imageHeight = imageHeight;
            return this;
        }
        
        public Builder height(int imageHeight) {
            this.imageHeight = imageHeight;
            return this;
        }
        
        public Builder isInTrash(boolean isInTrash) {
            this.isInTrash = isInTrash;
            return this;
        }

        public Builder deletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
            return this;
        }
        
        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }
        
        public Builder format(String format) {
            this.format = format;

            // Convert format to MIME type if needed
            switch (format.toLowerCase()) {
                case "png": this.mimeType = "image/png"; break;
                case "jpg":
                case "jpeg": this.mimeType = "image/jpeg"; break;
                case "webp": this.mimeType = "image/webp"; break;
                default: this.mimeType = "image/" + format.toLowerCase(); break;
            }
            return this;
        }
        
        public Builder sortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }
        
        public Builder generationJobId(String generationJobId) {
            this.generationJobId = generationJobId;
            return this;
        }
        
        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }
        
        public Builder model(String model) {
            this.model = model;
            return this;
        }
        
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }
        
        public Builder safetyTolerance(int safetyTolerance) {
            this.safetyTolerance = safetyTolerance;
            return this;
        }
        
        public Builder promptUpsampling(boolean promptUpsampling) {
            this.promptUpsampling = promptUpsampling;
            return this;
        }
        
        // Error tracking builder methods
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }
        
        public Builder apiStatus(String apiStatus) {
            this.apiStatus = apiStatus;
            return this;
        }
        
        // Additional generation parameter builder methods
        public Builder aspectRatio(String aspectRatio) {
            this.aspectRatio = aspectRatio;
            return this;
        }
        
        public Builder stylePreset(String stylePreset) {
            this.stylePreset = stylePreset;
            return this;
        }

        // Gemini Phase 2 cost tracking builder methods
        public Builder baseCost(Double baseCost) {
            this.baseCost = baseCost;
            return this;
        }

        public Builder searchCost(Double searchCost) {
            this.searchCost = searchCost;
            return this;
        }

        public Builder reasoningCost(Double reasoningCost) {
            this.reasoningCost = reasoningCost;
            return this;
        }

        public Builder thinkingTokens(Integer thinkingTokens) {
            this.thinkingTokens = thinkingTokens;
            return this;
        }

        public Builder thoughtSummary(String thoughtSummary) {
            this.thoughtSummary = thoughtSummary;
            return this;
        }

        public Builder isVideo(boolean isVideo) {
            this.isVideo = isVideo;
            return this;
        }

        public Builder videoDurationSeconds(int videoDurationSeconds) {
            this.videoDurationSeconds = videoDurationSeconds;
            return this;
        }

        public Builder videoResolution(String videoResolution) {
            this.videoResolution = videoResolution;
            return this;
        }

        public Builder hasAudio(boolean hasAudio) {
            this.hasAudio = hasAudio;
            return this;
        }

        // Note: format(), width(), height() builder methods already exist above

        public GeneratedImage build() {
            GeneratedImage image = new GeneratedImage(id);
            image.setChatEntryId(chatEntryId);
            image.setImageUrl(imageUrl);
            image.setLocalFilePath(localFilePath);
            image.setFileName(fileName);
            image.setDownloadStatus(downloadStatus);
            image.setCreatedAt(createdAt);
            image.setDownloadedAt(downloadedAt);
            image.setFileSizeBytes(fileSizeBytes);
            image.setImageWidth(imageWidth);
            image.setImageHeight(imageHeight);
            image.setMimeType(mimeType);
            image.setSortOrder(sortOrder);
            image.setGenerationJobId(generationJobId);
            image.setPrompt(prompt);
            image.setModel(model);
            image.setSeed(seed);
            image.setSafetyTolerance(safetyTolerance);
            image.setPromptUpsampling(promptUpsampling);
            
            // Set error tracking fields
            image.setErrorMessage(errorMessage);
            image.setFailureReason(failureReason);
            image.setApiStatus(apiStatus);
            
            // Set additional generation parameters
            image.setAspectRatio(aspectRatio);
            image.setStylePreset(stylePreset);
            if (format != null) image.setFormat(format);
            if (width > 0) image.setWidth(width);
            if (height > 0) image.setHeight(height);
            image.setIsInTrash(isInTrash);
            image.setDeletedAt(deletedAt);

            // Set Gemini Phase 2 cost tracking fields
            image.setBaseCost(baseCost);
            image.setSearchCost(searchCost);
            image.setReasoningCost(reasoningCost);
            image.setThinkingTokens(thinkingTokens);
            image.setThoughtSummary(thoughtSummary);

            // Set video fields
            image.setIsVideo(isVideo);
            image.setVideoDurationSeconds(videoDurationSeconds);
            image.setVideoResolution(videoResolution);
            image.setHasAudio(hasAudio);

            return image;
        }
    }
    
    /*  The download status is observed by the entry card and the images view, the four sizes by the shape of the
        tile in the entry card; every other value is plain  */

    public ObjectProperty<DownloadStatus> downloadStatusProperty() {
        return downloadStatus;
    }

    public IntegerProperty imageWidthProperty() {
        return imageWidth;
    }
    
    public IntegerProperty imageHeightProperty() {
        return imageHeight;
    }

    // Standard getters and setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getChatEntryId() {
        return chatEntryId;
    }
    
    public void setChatEntryId(String chatEntryId) {
        this.chatEntryId = chatEntryId;
    }
    
    public String getImageUrl() {
        return imageUrl;
    }
    
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
    
    public String getLocalFilePath() {
        return localFilePath;
    }
    
    public void setLocalFilePath(String localFilePath) {
        this.localFilePath = localFilePath;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public DownloadStatus getDownloadStatus() {
        return downloadStatus.get();
    }
    
    public void setDownloadStatus(DownloadStatus downloadStatus) {
        this.downloadStatus.set(downloadStatus);
        if (downloadStatus == DownloadStatus.CACHED) {
            setDownloadedAt(LocalDateTime.now());
        }
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getDownloadedAt() {
        return downloadedAt;
    }
    
    public void setDownloadedAt(LocalDateTime downloadedAt) {
        this.downloadedAt = downloadedAt;
    }
    
    public long getFileSizeBytes() {
        return fileSizeBytes;
    }
    
    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }
    
    public int getImageWidth() {
        return imageWidth.get();
    }
    
    public void setImageWidth(int imageWidth) {
        this.imageWidth.set(imageWidth);
    }
    
    public int getImageHeight() {
        return imageHeight.get();
    }
    
    public void setImageHeight(int imageHeight) {
        this.imageHeight.set(imageHeight);
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    
    /**
     * Gets the image format extracted from MIME type.
     * 
     * @return the format (e.g., "PNG", "JPEG")
     */
    public String getFormat() {
        // Prefer the explicit format property, fallback to mime type
        String explicitFormat = format;
        if (explicitFormat != null && !explicitFormat.isEmpty()) {
            return explicitFormat.toUpperCase();
        }
        
        String mime = getMimeType();
        if (mime == null) return "UNKNOWN";
        
        switch (mime.toLowerCase()) {
            case "image/png": return "PNG";
            case "image/jpeg": return "JPEG";
            case "image/jpg": return "JPG";
            case "image/webp": return "WEBP";
            default:
                // Extract format from mime type (e.g., "image/webp" -> "WEBP")
                String[] parts = mime.split("/");
                return parts.length > 1 ? parts[1].toUpperCase() : "UNKNOWN";
        }
    }
    
    public int getWidth() {
        // Prefer the explicit width property, fallback to imageWidth
        int explicitWidth = width.get();
        return explicitWidth > 0 ? explicitWidth : getImageWidth();
    }
    
    public int getHeight() {
        // Prefer the explicit height property, fallback to imageHeight
        int explicitHeight = height.get();
        return explicitHeight > 0 ? explicitHeight : getImageHeight();
    }
    
    public int getSortOrder() {
        return sortOrder;
    }
    
    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
    
    public String getGenerationJobId() {
        return generationJobId;
    }
    
    public void setGenerationJobId(String generationJobId) {
        this.generationJobId = generationJobId;
    }
    
    public String getPrompt() {
        return prompt;
    }
    
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public long getSeed() {
        return seed;
    }
    
    public void setSeed(long seed) {
        this.seed = seed;
    }
    
    public int getSafetyTolerance() {
        return safetyTolerance;
    }
    
    public void setSafetyTolerance(int safetyTolerance) {
        this.safetyTolerance = safetyTolerance;
    }
    
    public boolean isPromptUpsampling() {
        return promptUpsampling;
    }
    
    public void setPromptUpsampling(boolean promptUpsampling) {
        this.promptUpsampling = promptUpsampling;
    }
    
    /**
     * Gets the file size in a human-readable format.
     * 
     * @return formatted file size string
     */
    public String getFormattedFileSize() {
        long bytes = getFileSizeBytes();
        if (bytes == 0) return "Unknown";
        
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;
        double size = bytes;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.1f %s", size, units[unitIndex]);
    }
    
    // Error tracking getters/setters
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getApiStatus() { return apiStatus; }
    public void setApiStatus(String apiStatus) { this.apiStatus = apiStatus; }

    // Additional generation parameters getters/setters
    public String getAspectRatio() { return aspectRatio; }
    public void setAspectRatio(String aspectRatio) { this.aspectRatio = aspectRatio; }

    public String getStylePreset() { return stylePreset; }
    public void setStylePreset(String stylePreset) { this.stylePreset = stylePreset; }

    // Note: getFormat(), getWidth(), getHeight() methods already exist above
    public void setFormat(String format) { this.format = format; }

    public void setWidth(int width) { this.width.set(width); }
    public IntegerProperty widthProperty() { return width; }

    public void setHeight(int height) { this.height.set(height); }
    public IntegerProperty heightProperty() { return height; }
    
    /**
     * Tells whether the image came: downloaded, unlike the row of a failed or unfinished request, which holds no
     * picture. The trash counts only these.
     *
     * @return true for a downloaded image
     */
    public boolean isReal() {
        return getDownloadStatus() == DownloadStatus.CACHED;
    }

    public boolean isInTrash() { return isInTrash; }
    public void setIsInTrash(boolean isInTrash) { this.isInTrash = isInTrash; }

    /*  when the image went to the trash on its own or with its entry, null as long as it is in use  */
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    // Gemini Phase 2 Advanced Features - Cost tracking getters/setters
    public Double getBaseCost() { return baseCost; }
    public void setBaseCost(Double baseCost) { this.baseCost = baseCost; }

    public Double getSearchCost() { return searchCost; }
    public void setSearchCost(Double searchCost) { this.searchCost = searchCost; }

    public Double getReasoningCost() { return reasoningCost; }
    public void setReasoningCost(Double reasoningCost) { this.reasoningCost = reasoningCost; }

    public Integer getThinkingTokens() { return thinkingTokens; }
    public void setThinkingTokens(Integer thinkingTokens) { this.thinkingTokens = thinkingTokens; }

    public String getThoughtSummary() { return thoughtSummary; }
    public void setThoughtSummary(String thoughtSummary) { this.thoughtSummary = thoughtSummary; }

    // video metadata getters/setters
    public boolean isVideo() { return isVideo; }
    public void setIsVideo(boolean isVideo) { this.isVideo = isVideo; }

    public int getVideoDurationSeconds() { return videoDurationSeconds; }
    public void setVideoDurationSeconds(int seconds) { this.videoDurationSeconds = seconds; }

    public String getVideoResolution() { return videoResolution; }
    public void setVideoResolution(String resolution) { this.videoResolution = resolution; }

    public boolean hasAudio() { return hasAudio; }
    public void setHasAudio(boolean hasAudio) { this.hasAudio = hasAudio; }

    /**
     * Gets the image dimensions as a string.
     *
     * @return formatted dimensions (e.g., "1024x576")
     */
    public String getDimensions() {
        // Prefer width/height over imageWidth/imageHeight
        int w = getWidth() > 0 ? getWidth() : getImageWidth();
        int h = getHeight() > 0 ? getHeight() : getImageHeight();
        
        if (w > 0 && h > 0) {
            return w + "x" + h;
        }
        return "Unknown";
    }
    
    @Override
    public String toString() {
        return String.format("GeneratedImage{id='%s', chatEntryId='%s', status=%s, dimensions=%s, url='%s'}", 
            getId(), getChatEntryId(), getDownloadStatus(), getDimensions(),
            getImageUrl() != null ? getImageUrl().substring(0, Math.min(50, getImageUrl().length())) : "null");
    }
}