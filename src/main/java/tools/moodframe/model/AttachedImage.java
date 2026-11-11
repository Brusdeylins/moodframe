package tools.moodframe.model;

import java.io.File;

/**
 * Represents an image attachment for image-to-image editing workflows.
 * 
 * <p>This model contains the image file reference, metadata, and the Base64 encoded
 * data required for API transmission to BFL FLUX models that support image attachments.</p>
 * 
 * @since 1.0.0
 */
public class AttachedImage {

    private File imageFile;
    private String fileName;
    private String mimeType;
    private long fileSizeBytes;
    private int width;
    private int height;
    private String base64Data;

    /**
     * Creates a new AttachedImage instance.
     * 
     * @param imageFile the source image file
     * @param fileName the original filename
     * @param mimeType the MIME type (e.g., "image/png", "image/jpeg")
     * @param fileSizeBytes the file size in bytes
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @param base64Data the Base64 encoded image data for API transmission
     */
    public AttachedImage(File imageFile, String fileName, String mimeType, 
                        long fileSizeBytes, int width, int height, String base64Data) {
        this.imageFile = imageFile;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.fileSizeBytes = fileSizeBytes;
        this.width = width;
        this.height = height;
        this.base64Data = base64Data;
    }
    
    /**
     * Creates a new AttachedImage from a file with automatic metadata extraction.
     * Base64 encoding must be set separately using setBase64Data().
     * 
     * @param imageFile the source image file
     */
    public AttachedImage(File imageFile) {
        this.imageFile = imageFile;
        this.fileName = imageFile.getName();
        this.fileSizeBytes = imageFile.length();
        
        // Determine MIME type from file extension
        String extension = getFileExtension(fileName).toLowerCase();
        this.mimeType = switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> "image/jpeg"; // Default fallback
        };
    }
    
    /**
     * Gets the source image file.
     * 
     * @return the image file
     */
    public File getImageFile() {
        return imageFile;
    }
    
    /**
     * Sets the source image file.
     * 
     * @param imageFile the image file
     */
    public void setImageFile(File imageFile) {
        this.imageFile = imageFile;
        if (imageFile != null) {
            this.fileName = imageFile.getName();
            this.fileSizeBytes = imageFile.length();
        }
    }
    
    /**
     * Gets the original filename.
     * 
     * @return the filename
     */
    public String getFileName() {
        return fileName;
    }
    
    /**
     * Sets the filename.
     * 
     * @param fileName the filename
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    /**
     * Gets the MIME type.
     * 
     * @return the MIME type (e.g., "image/png")
     */
    public String getMimeType() {
        return mimeType;
    }
    
    /**
     * Sets the MIME type.
     * 
     * @param mimeType the MIME type
     */
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    
    /**
     * Gets the file size in bytes.
     * 
     * @return the file size
     */
    public long getFileSizeBytes() {
        return fileSizeBytes;
    }
    
    /**
     * Sets the file size in bytes.
     * 
     * @param fileSizeBytes the file size
     */
    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }
    
    /**
     * Gets the image width in pixels.
     * 
     * @return the width
     */
    public int getWidth() {
        return width;
    }
    
    /**
     * Sets the image width in pixels.
     * 
     * @param width the width
     */
    public void setWidth(int width) {
        this.width = width;
    }
    
    /**
     * Gets the image height in pixels.
     * 
     * @return the height
     */
    public int getHeight() {
        return height;
    }
    
    /**
     * Sets the image height in pixels.
     * 
     * @param height the height
     */
    public void setHeight(int height) {
        this.height = height;
    }
    
    /**
     * Gets the Base64 encoded image data for API transmission.
     * 
     * @return the Base64 encoded data, or null if not yet encoded
     */
    public String getBase64Data() {
        return base64Data;
    }
    
    /**
     * Sets the Base64 encoded image data.
     * 
     * @param base64Data the Base64 encoded data
     */
    public void setBase64Data(String base64Data) {
        this.base64Data = base64Data;
    }
    
    /**
     * Checks if the attachment is ready for API transmission.
     * 
     * @return true if Base64 data is available
     */
    public boolean isReadyForTransmission() {
        return base64Data != null && !base64Data.trim().isEmpty();
    }
    
    /**
     * Gets a human-readable file size string.
     * 
     * @return formatted file size (e.g., "2.5 MB")
     */
    public String getFormattedFileSize() {
        if (fileSizeBytes < 1024) {
            return fileSizeBytes + " B";
        } else if (fileSizeBytes < 1024 * 1024) {
            return String.format("%.1f KB", fileSizeBytes / 1024.0);
        } else {
            return String.format("%.1f MB", fileSizeBytes / (1024.0 * 1024.0));
        }
    }
    
    /**
     * Gets the image dimensions as a formatted string.
     * 
     * @return dimensions string (e.g., "1024x768")
     */
    public String getFormattedDimensions() {
        return width + "x" + height;
    }
    
    /**
     * Checks if the file size is within the API limits (max 10MB).
     * 
     * @return true if file size is acceptable
     */
    public boolean isFileSizeValid() {
        return fileSizeBytes <= 10 * 1024 * 1024; // 10MB limit
    }
    
    /**
     * Checks if the MIME type is supported.
     * 
     * @return true if MIME type is PNG, JPEG, or WebP
     */
    public boolean isMimeTypeSupported() {
        return mimeType != null && (
            mimeType.equals("image/png") ||
            mimeType.equals("image/jpeg") ||
            mimeType.equals("image/webp")
        );
    }
    
    /**
     * Validates the attachment for API compatibility.
     * 
     * @return true if the attachment is valid for API transmission
     */
    public boolean isValid() {
        return imageFile != null && 
               imageFile.exists() && 
               isFileSizeValid() && 
               isMimeTypeSupported() &&
               width > 0 && 
               height > 0;
    }
    
    /**
     * Gets validation error messages if the attachment is invalid.
     * 
     * @return error message, or null if valid
     */
    public String getValidationError() {
        if (imageFile == null || !imageFile.exists()) {
            return "Image file does not exist";
        }
        if (!isFileSizeValid()) {
            return "File size exceeds 10MB limit (" + getFormattedFileSize() + ")";
        }
        if (!isMimeTypeSupported()) {
            return "Unsupported file format. Use PNG, JPEG, or WebP";
        }
        if (width <= 0 || height <= 0) {
            return "Invalid image dimensions";
        }
        return null; // Valid
    }
    
    /**
     * Extracts file extension from filename.
     * 
     * @param fileName the filename
     * @return the file extension without the dot
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1);
    }
    
    @Override
    public String toString() {
        return String.format("AttachedImage{fileName='%s', size=%s, dimensions=%s, mimeType='%s', ready=%s}", 
                           fileName, getFormattedFileSize(), getFormattedDimensions(), 
                           mimeType, isReadyForTransmission());
    }
}