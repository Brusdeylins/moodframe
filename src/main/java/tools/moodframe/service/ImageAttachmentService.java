package tools.moodframe.service;

import tools.moodframe.model.AttachedImage;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling image attachment operations including file validation,
 * Base64 encoding, and image processing for BFL FLUX API compatibility.
 * 
 * @since 1.0.0
 */
public class ImageAttachmentService implements ImageAttachment {
    
    private static final Logger logger = LoggerFactory.getLogger(ImageAttachmentService.class);
    
    // Provider-dependent file size limit (default: 10MB for BFL)
    private long maxFileSize = 10L * 1024 * 1024;

    /*  FLUX 3 rejects reference images above 16 megapixels (HTTP 400), larger ones are scaled down  */
    static final long MAX_PIXELS = 16_000_000L;

    // Supported image formats
    private static final String[] SUPPORTED_EXTENSIONS = {"png", "jpg", "jpeg", "webp"};
    
    /**
     * Sets the maximum file size for image attachments (provider-dependent).
     *
     * @param maxFileSize max file size in bytes
     */
    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    /**
     * Creates an AttachedImage from a file with full validation and metadata extraction.
     * This method performs synchronous operations and should be called from a background thread.
     * 
     * @param file the image file to attach
     * @return the AttachedImage instance with metadata
     * @throws IOException if file processing fails
     * @throws IllegalArgumentException if file is invalid
     */
    @Override
    public AttachedImage createAttachment(File file) throws IOException, IllegalArgumentException {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("File does not exist");
        }
        
        if (!file.isFile()) {
            throw new IllegalArgumentException("Path is not a file");
        }
        
        // Validate file extension
        String fileName = file.getName();
        String extension = getFileExtension(fileName).toLowerCase();
        if (!isSupportedFormat(extension)) {
            throw new IllegalArgumentException(
                "Unsupported file format '" + extension + "'. Supported formats: PNG, JPEG, WebP");
        }

        /*  Too large (e.g. a 4K PNG) or too many pixels: attach a JPEG copy within the limits  */
        if (file.length() > maxFileSize || pixelsOf(file) > MAX_PIXELS)
            file = shrinkToLimit(file);

        // Create initial AttachedImage
        AttachedImage attachment = new AttachedImage(file);
        
        // Extract image dimensions
        try {
            BufferedImage bufferedImage = ImageIO.read(file);
            if (bufferedImage == null) {
                throw new IllegalArgumentException("Cannot read image file - invalid or corrupted");
            }
            
            attachment.setWidth(bufferedImage.getWidth());
            attachment.setHeight(bufferedImage.getHeight());
            
        } catch (IOException e) {
            throw new IOException("Failed to read image dimensions: " + e.getMessage(), e);
        }
        
        return attachment;
    }

    /**
     * Gets the pixel count of an image from its header, without decoding it.
     *
     * @param file the image file
     * @return width times height, 0 if the header cannot be read
     */
    private static long pixelsOf(File file) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            var readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext())
                return 0;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return (long) reader.getWidth(0) * reader.getHeight(0);
            }
            finally {
                reader.dispose();
            }
        }
    }

    /**
     * Writes a JPEG copy of an image that exceeds the size or pixel limit, scaled to at most 16 megapixels and
     * further down only if the JPEG is still too large.
     *
     * @param file the image file
     * @return a temporary JPEG file within the size limit
     * @throws IOException if the image cannot be read or written
     * @since 1.9.3
     */
    File shrinkToLimit(File file) throws IOException {
        BufferedImage source = ImageIO.read(file);
        if (source == null)
            throw new IllegalArgumentException("Cannot read image file - invalid or corrupted");

        double scale = Math.min(1.0, Math.sqrt((double) MAX_PIXELS / ((long) source.getWidth() * source.getHeight())));
        for (int attempt = 0; attempt < 5; attempt++) {
            int width  = Math.max(1, (int) Math.floor(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.floor(source.getHeight() * scale));
            BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(source, 0, 0, width, height, null);
            graphics.dispose();

            File target = File.createTempFile("attachment-", ".jpg");
            target.deleteOnExit();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.92f);
            try (ImageOutputStream output = ImageIO.createImageOutputStream(target)) {
                writer.setOutput(output);
                writer.write(null, new IIOImage(rgb, null, null), param);
            }
            finally {
                writer.dispose();
            }

            if (target.length() <= maxFileSize) {
                logger.info("Attachment {} ({} bytes) re-encoded as JPEG {}x{} ({} bytes)",
                        file.getName(), file.length(), width, height, target.length());
                return target;
            }
            scale *= Math.sqrt((double) maxFileSize / target.length()) * 0.9;
            target.delete();
        }
        throw new IllegalArgumentException(String.format("File size %.1f MB exceeds maximum allowed size of %d MB",
                file.length() / (1024.0 * 1024.0), maxFileSize / (1024 * 1024)));
    }

    /**
     * Encodes an AttachedImage to Base64 format for API transmission.
     * This is a potentially expensive operation that should be performed on a background thread.
     * 
     * @param attachment the attachment to encode
     * @throws IOException if encoding fails
     */
    @Override
    public void encodeToBase64(AttachedImage attachment) throws IOException {
        if (attachment == null || attachment.getImageFile() == null) {
            throw new IllegalArgumentException("Invalid attachment");
        }
        
        File file = attachment.getImageFile();
        if (!file.exists()) {
            throw new IOException("Image file no longer exists: " + file.getAbsolutePath());
        }
        
        try {
            // Read file as bytes and encode to Base64
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);
            
            attachment.setBase64Data(base64Data);
            
            
        } catch (IOException e) {
            throw new IOException("Failed to encode image to Base64: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates an AttachedImage asynchronously with Base64 encoding.
     * This method returns a CompletableFuture for non-blocking operation.
     * 
     * @param file the image file to process
     * @return CompletableFuture that resolves to the processed AttachedImage
     */
    @Override
    public CompletableFuture<AttachedImage> createAttachmentAsync(File file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AttachedImage attachment = createAttachment(file);
                encodeToBase64(attachment);
                return attachment;
            } catch (Exception e) {
                logger.error("Failed to create attachment for {}", file.getName(), e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Creates a JavaFX Task for creating an attachment with progress reporting.
     * Useful for UI integration with progress indicators.
     * 
     * @param file the image file to process
     * @return Task that produces an AttachedImage
     */
    @Override
    public Task<AttachedImage> createAttachmentTask(File file) {
        return new Task<AttachedImage>() {
            @Override
            protected AttachedImage call() throws Exception {
                updateMessage("Validating image file...");
                updateProgress(0, 3);
                
                AttachedImage attachment = createAttachment(file);
                
                updateMessage("Encoding to Base64...");
                updateProgress(1, 3);
                
                encodeToBase64(attachment);
                
                updateMessage("Attachment ready");
                updateProgress(3, 3);
                
                return attachment;
            }
        };
    }
    
    /**
     * Creates a preview image for UI display from an AttachedImage.
     * 
     * @param attachment the attachment
     * @param maxWidth maximum width for the preview
     * @param maxHeight maximum height for the preview
     * @return JavaFX Image for UI display
     * @throws IOException if image loading fails
     */
    @Override
    public Image createPreviewImage(AttachedImage attachment, double maxWidth, double maxHeight) 
            throws IOException {
        if (attachment == null || attachment.getImageFile() == null) {
            throw new IllegalArgumentException("Invalid attachment");
        }
        
        File file = attachment.getImageFile();
        if (!file.exists()) {
            throw new IOException("Image file no longer exists");
        }
        
        try {
            // Load image with size constraints for preview
            return new Image(file.toURI().toString(), maxWidth, maxHeight, true, true);
        } catch (Exception e) {
            throw new IOException("Failed to create preview image: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validates if a file can be used as an attachment.
     * 
     * @param file the file to validate
     * @return validation result
     */
    @Override
    public ValidationResult validateFile(File file) {
        if (file == null || !file.exists()) {
            return new ValidationResult(false, "File does not exist");
        }
        
        if (!file.isFile()) {
            return new ValidationResult(false, "Path is not a file");
        }
        
        // Check file size
        long fileSize = file.length();
        if (fileSize > maxFileSize) {
            int maxMB = (int) (maxFileSize / (1024 * 1024));
            return new ValidationResult(false,
                String.format("File size %.1f MB exceeds %d MB limit", fileSize / (1024.0 * 1024.0), maxMB));
        }
        
        // Validate file extension
        String extension = getFileExtension(file.getName()).toLowerCase();
        if (!isSupportedFormat(extension)) {
            return new ValidationResult(false, 
                "Unsupported format '" + extension + "'. Use PNG, JPEG, or WebP");
        }
        
        // Try to read as image
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return new ValidationResult(false, "File is not a valid image");
            }
        } catch (IOException e) {
            return new ValidationResult(false, "Cannot read image file: " + e.getMessage());
        }
        
        return new ValidationResult(true, "File is valid");
    }
    
    /**
     * Checks if a file extension is supported.
     * 
     * @param extension the file extension (without dot)
     * @return true if supported
     */
    @Override
    public boolean isSupportedFormat(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        
        String lowerExt = extension.toLowerCase();
        for (String supported : SUPPORTED_EXTENSIONS) {
            if (supported.equals(lowerExt)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets the supported file extensions as a display string.
     * 
     * @return formatted extensions (e.g., "PNG, JPEG, WebP")
     */
    @Override
    public String getSupportedFormatsDisplay() {
        return String.join(", ", SUPPORTED_EXTENSIONS).toUpperCase();
    }
    
    /**
     * Creates a file extension filter string for JavaFX FileChooser.
     * 
     * @return filter string (e.g., "*.png;*.jpg;*.jpeg;*.webp")
     */
    @Override
    public String getFileChooserFilter() {
        StringBuilder filter = new StringBuilder();
        for (int i = 0; i < SUPPORTED_EXTENSIONS.length; i++) {
            if (i > 0) filter.append(";");
            filter.append("*.").append(SUPPORTED_EXTENSIONS[i]);
        }
        return filter.toString();
    }
    
    /**
     * Convenience method for attaching an image file (for drag and drop).
     * Combines createAttachment and encodeToBase64 in one synchronous call.
     * 
     * @param file the image file to attach
     * @return the processed AttachedImage with Base64 data
     * @throws IOException if processing fails
     */
    @Override
    public AttachedImage attachImage(File file) throws IOException {
        AttachedImage attachment = createAttachment(file);
        encodeToBase64(attachment);
        return attachment;
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
    
    // Keep ValidationResult for backward compatibility
    public static class ValidationResult extends ImageAttachment.ValidationResult {
        public ValidationResult(boolean valid, String message) {
            super(valid, message);
        }
        
        @Override
        public String toString() {
            return String.format("ValidationResult{valid=%s, message='%s'}", isValid(), getMessage());
        }
    }
}