package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.GeneratedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Writes generated images to the images directory: decodes Base64 answers, downloads the result of a BFL
 * job, names and detects the files. The methods touch no JavaFX property, so they may run on a worker thread;
 * applying the result to the image object the UI is bound to is {@link StoredImage#applyTo} on the FX thread.
 *
 * @since 1.9.1
 */
public class ImageFileStore {

    private static final Logger logger = LoggerFactory.getLogger(ImageFileStore.class);

    private final ApplicationConfig config;
    private final HttpClient downloadClient;

    /** The written file of a generated image, to be applied to the image object on the FX thread. */
    public record StoredImage(Path path, String filename, String format, String mimeType, int width, int height,
                               long size) {

        /** Applies the written file to its image object: file, size, format and dimensions. Call on the FX thread. */
        public void applyTo(GeneratedImage image) {
        if (format() != null) {
            image.setFormat(format());
            image.setMimeType(mimeType());
        }
        if (width() > 0) {
            image.setImageWidth(width());
            image.setImageHeight(height());
            image.setWidth(width());
            image.setHeight(height());
        }
        image.setDownloadStatus(GeneratedImage.DownloadStatus.CACHED);
        image.setLocalFilePath(path().toString());
        image.setFileName(filename());
        image.setFileSizeBytes(size());
        }
    }

    /**
     * Creates the store.
     *
     * @param config         the application configuration (images directory, logging)
     * @param downloadClient the client for the download of finished BFL jobs
     */
    public ImageFileStore(ApplicationConfig config, HttpClient downloadClient) {
        this.config = config;
        this.downloadClient = downloadClient;
    }

    /**
     * Downloads the image of a finished BFL job into the images directory. Touches no JavaFX property, so it
     * runs on a worker.
     *
     * @param generatedImage the image with the URL, read for the URL and the file name only
     * @return the written file; its format is null if the bytes are neither PNG nor JPEG
     * @throws Exception if there is no URL, the server does not answer 200, or the file cannot be written
     */
    public StoredImage download(GeneratedImage generatedImage) throws Exception {
        String imageUrl = generatedImage.getImageUrl();
        if (imageUrl == null || imageUrl.trim().isEmpty())
            throw new java.io.IOException("No image URL available for download: " + generatedImage.getId());

        Path imagesDir = Paths.get(config.getImagesPath());
        Files.createDirectories(imagesDir);

        HttpRequest downloadRequest = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(60)) // Longer timeout for image download
                .GET()
                .build();
        HttpResponse<byte[]> downloadResponse = downloadClient.send(
                downloadRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (downloadResponse.statusCode() != 200)
            throw new java.io.IOException("Failed to download image: HTTP " + downloadResponse.statusCode());

        byte[] imageBytes = downloadResponse.body();

        // Detect actual format from bytes so extension, format and UI match reality
        String detectedFormat = detectFormat(imageBytes);
        String format = null;
        String mimeType = null;
        String filename;
        if ("UNKNOWN".equals(detectedFormat)) {
            filename = filename(generatedImage);
        } else {
            filename = filenameWithoutExtension(generatedImage) + "." + detectedFormat.toLowerCase();
            format = detectedFormat;
            mimeType = "image/" + detectedFormat.toLowerCase();
        }
        Path imagePath = imagesDir.resolve(filename);
        Files.write(imagePath, imageBytes);

        // Extract actual image dimensions from the saved file
        int width = 0;
        int height = 0;
        try {
            java.awt.image.BufferedImage bufferedImage = javax.imageio.ImageIO.read(imagePath.toFile());
            if (bufferedImage != null) {
                width = bufferedImage.getWidth();
                height = bufferedImage.getHeight();
            } else {
                logger.warn("Could not read image dimensions from: {}", imagePath);
            }
        } catch (Exception e) {
            logger.error("Failed to extract image dimensions", e);
        }

        if (config.getLoggingEnabled())
            logger.info("Image download completed: {} ({}x{}, {} bytes)", filename, width, height,
                    Files.size(imagePath));
        return new StoredImage(imagePath, filename, format, mimeType, width, height, Files.size(imagePath));
    }

    /**
     * Decodes a Base64 image and writes it to the images directory as PNG (the Gemini API offers no output
     * format parameter, so another format is re-encoded). Touches no JavaFX property, so it may run on a worker.
     *
     * @param base64Image    the Base64 encoded image data
     * @param generatedImage the placeholder, read for the file name only
     * @return the written file
     * @throws java.io.IOException if the file cannot be written
     */
    public StoredImage writeBase64(String base64Image, GeneratedImage generatedImage)
            throws java.io.IOException {
        byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Image);
        String detectedFormat = detectFormat(imageBytes);

        if (!"PNG".equals(detectedFormat)) {
            try {
                java.awt.image.BufferedImage decoded = javax.imageio.ImageIO.read(
                        new java.io.ByteArrayInputStream(imageBytes));
                if (decoded != null) {
                    java.io.ByteArrayOutputStream pngBytes = new java.io.ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(decoded, "png", pngBytes);
                    imageBytes = pngBytes.toByteArray();
                    logger.info("Converted Gemini image from {} to PNG", detectedFormat);
                    detectedFormat = "PNG";
                } else {
                    logger.warn("Could not decode {} image for PNG conversion, keeping original format",
                            detectedFormat);
                }
            } catch (Exception e) {
                logger.warn("PNG conversion failed, keeping original format {}", detectedFormat, e);
            }
        }

        String fileExtension = detectedFormat.toLowerCase();
        Path imagesDir = Paths.get(config.getImagesPath());
        Files.createDirectories(imagesDir);
        String filename = filenameWithoutExtension(generatedImage) + "." + fileExtension;
        Path imagePath = imagesDir.resolve(filename);
        Files.write(imagePath, imageBytes);

        int width = 0;
        int height = 0;
        try {
            java.awt.image.BufferedImage bufferedImage = javax.imageio.ImageIO.read(imagePath.toFile());
            if (bufferedImage != null) {
                width = bufferedImage.getWidth();
                height = bufferedImage.getHeight();
                logger.info("Extracted Gemini image dimensions: {}x{}", width, height);
            } else {
                logger.warn("Could not read image dimensions from: {}", imagePath);
            }
        } catch (Exception e) {
            logger.error("Failed to extract image dimensions", e);
        }

        return new StoredImage(imagePath, filename, detectedFormat, "image/" + fileExtension, width, height,
                Files.size(imagePath));
    }

    /**
     * Generates image filename with extension using UUID for guaranteed uniqueness.
     * Format: flux_{timestamp}_{model}_{uuid-short}.{format}
     *
     * This approach ensures:
     * - No filename collisions (UUID is globally unique)
     * - Works for all providers (BFL, Gemini, future APIs)
     * - No dependency on seeds (which can be 0, repeated, or user-specified)
     * - No need for counters or state management
     */
    public static String filename(GeneratedImage generatedImage) {
        String timestamp = generatedImage.getCreatedAt()
                .toString()
                .replaceAll("[:\\-\\.]", "")
                .replaceAll("T", "_")
                .substring(0, 15); // YYYYMMDD_HHMMSS

        String format = generatedImage.getFormat() != null ? generatedImage.getFormat() : "png";

        // Use first 8 characters of UUID for uniqueness (extremely low collision probability)
        String shortUuid = generatedImage.getId().substring(0, 8);

        return String.format("flux_%s_%s_%s.%s",
                timestamp,
                generatedImage.getModel().replaceAll("[^a-zA-Z0-9]", ""),
                shortUuid,
                format);
    }

    /**
     * Generates image filename without extension using UUID for guaranteed uniqueness.
     * Format: flux_{timestamp}_{model}_{uuid-short}
     *
     * This approach ensures:
     * - No filename collisions (UUID is globally unique)
     * - Works for all providers (BFL, Gemini, future APIs)
     * - No dependency on seeds (which can be 0, repeated, or user-specified)
     * - No need for counters or state management
     */
    public static String filenameWithoutExtension(GeneratedImage generatedImage) {
        String timestamp = generatedImage.getCreatedAt()
                .toString()
                .replaceAll("[:\\-\\.]", "")
                .replaceAll("T", "_")
                .substring(0, 15); // YYYYMMDD_HHMMSS

        // Use first 8 characters of UUID for uniqueness (extremely low collision probability)
        String shortUuid = generatedImage.getId().substring(0, 8);

        return String.format("flux_%s_%s_%s",
                timestamp,
                generatedImage.getModel().replaceAll("[^a-zA-Z0-9]", ""),
                shortUuid);
    }

    /**
     * Detects image format from byte array by checking magic bytes.
     *
     * @param imageBytes the image data
     * @return "JPEG" or "PNG" or "UNKNOWN"
     */
    public static String detectFormat(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length < 4) {
            return "UNKNOWN";
        }

        // Check PNG magic bytes: 89 50 4E 47 (‰PNG)
        if (imageBytes[0] == (byte) 0x89 && imageBytes[1] == (byte) 0x50 &&
            imageBytes[2] == (byte) 0x4E && imageBytes[3] == (byte) 0x47) {
            return "PNG";
        }

        // Check JPEG magic bytes: FF D8 FF
        if (imageBytes[0] == (byte) 0xFF && imageBytes[1] == (byte) 0xD8 && imageBytes[2] == (byte) 0xFF) {
            return "JPEG";
        }

        return "UNKNOWN";
    }
}
