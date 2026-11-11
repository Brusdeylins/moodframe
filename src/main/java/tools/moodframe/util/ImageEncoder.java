package tools.moodframe.util;

import tools.moodframe.config.ApplicationConfig;
import tools.moodframe.config.ServiceFactory;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for processing and encoding images for BFL API transmission.
 * 
 * <p>Handles image validation, resizing, format conversion, and Base64 encoding
 * for FLUX image-to-image generation workflows.</p>
 * 
 * <p><strong>Performance Characteristics:</strong></p>
 * <ul>
 * <li>Image resizing uses bicubic interpolation for high quality (slower but better)</li>
 * <li>Large images (>2048px) are automatically resized to reduce memory and bandwidth</li>
 * <li>Base64 encoding increases data size by ~33%</li>
 * <li>Memory usage: O(width × height × 4) bytes for image processing</li>
 * </ul>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Encode an image for BFL API
 * File imageFile = new File("path/to/image.jpg");
 * try {
 *     String base64 = ImageEncoder.encodeImageToBase64(imageFile);
 *     
 *     // Use in BFL API request
 *     ImageGenerationRequest request = new ImageGenerationRequest();
 *     request.setInputImage(base64);
 *     request.setPrompt("Transform this image into watercolor style");
 * } catch (IOException e) {
 *     // Handle encoding error
 * } catch (IllegalArgumentException e) {
 *     // Handle validation error (file too large, unsupported format, etc.)
 * }
 * }</pre>
 * 
 * <p><strong>Thread-safety:</strong> All methods are stateless and thread-safe.</p>
 * 
 * @since 1.0.0
 */
public class ImageEncoder {
    
    private static final Logger logger = LoggerFactory.getLogger(ImageEncoder.class);
    
    // BFL API limitations
    private static final int MAX_DIMENSION = 2048;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String[] SUPPORTED_FORMATS = {"png", "jpg", "jpeg", "webp"};
    
    /**
     * Encodes an image file to Base64 string for BFL API transmission.
     * 
     * @param imageFile the image file to encode
     * @return Base64 encoded image string
     * @throws IOException if image processing fails
     * @throws IllegalArgumentException if image is invalid or unsupported
     */
    public static String encodeImageToBase64(File imageFile) throws IOException {
        // Validate file
        validateImageFile(imageFile);
        
        // Load image
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new IOException("Could not read image file: " + imageFile.getName());
        }
        
        // Resize if needed
        BufferedImage processedImage = resizeImageIfNeeded(image);
        
        // Convert to Base64
        String base64 = convertToBase64(processedImage, getOutputFormat(imageFile));
        
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Image encoded successfully: {} -> {} characters", 
                imageFile.getName(), base64.length());
        }
        
        return base64;
    }
    
    /**
     * Validates that the image file meets BFL API requirements.
     */
    private static void validateImageFile(File imageFile) {
        if (imageFile == null || !imageFile.exists()) {
            throw new IllegalArgumentException("Image file does not exist");
        }
        
        if (imageFile.length() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(String.format(
                "Image file too large: %.1fMB (max 10MB)", 
                imageFile.length() / (1024.0 * 1024.0)));
        }
        
        String fileName = imageFile.getName().toLowerCase();
        boolean supportedFormat = false;
        for (String format : SUPPORTED_FORMATS) {
            if (fileName.endsWith("." + format)) {
                supportedFormat = true;
                break;
            }
        }
        
        if (!supportedFormat) {
            throw new IllegalArgumentException(String.format(
                "Unsupported image format. Supported: %s", 
                String.join(", ", SUPPORTED_FORMATS)));
        }
    }
    
    /**
     * Resizes image if it exceeds BFL API dimension limits.
     */
    private static BufferedImage resizeImageIfNeeded(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        
        // Check if resizing is needed
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            return original; // No resizing needed
        }
        
        // Calculate new dimensions maintaining aspect ratio
        double scale = Math.min((double) MAX_DIMENSION / width, (double) MAX_DIMENSION / height);
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);
        
        // Ensure dimensions are multiples of 32 (BFL API requirement)
        newWidth = (newWidth / 32) * 32;
        newHeight = (newHeight / 32) * 32;
        
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (config != null && config.getLoggingEnabled()) {
            logger.info("Resizing image: {}x{} -> {}x{} (scale: {:.2f})", 
                width, height, newWidth, newHeight, scale);
        }
        
        // Create resized image with high quality
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        
        // Apply high-quality rendering hints
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        
        return resized;
    }
    
    /**
     * Converts BufferedImage to Base64 string.
     */
    private static String convertToBase64(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Write image to byte array
        if (!ImageIO.write(image, format, baos)) {
            throw new IOException("Failed to encode image in format: " + format);
        }
        
        byte[] imageBytes = baos.toByteArray();
        
        // Encode to Base64
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        
        
        return base64;
    }
    
    /**
     * Determines the output format for encoding based on the input file.
     */
    private static String getOutputFormat(File imageFile) {
        String fileName = imageFile.getName().toLowerCase();
        
        if (fileName.endsWith(".png")) {
            return "png";
        } else if (fileName.endsWith(".webp")) {
            return "png"; // Convert WebP to PNG for better compatibility
        } else {
            return "jpeg"; // Default for JPG files
        }
    }
    
    /**
     * Validates that a Base64 string represents a valid image.
     * 
     * @param base64Image the Base64 encoded image
     * @return true if valid, false otherwise
     */
    public static boolean isValidBase64Image(String base64Image) {
        if (base64Image == null || base64Image.trim().isEmpty()) {
            return false;
        }
        
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Image);
            return decodedBytes.length > 0;
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid Base64 image data: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the estimated file size of a Base64 encoded image.
     * 
     * @param base64Image the Base64 encoded image
     * @return estimated size in bytes
     */
    public static long getEstimatedSize(String base64Image) {
        if (base64Image == null) {
            return 0;
        }
        
        // Base64 encoding increases size by ~33%
        // Remove any padding characters and calculate original size
        return (long) (base64Image.length() * 0.75);
    }
}