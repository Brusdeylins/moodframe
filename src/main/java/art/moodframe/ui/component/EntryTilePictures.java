package art.moodframe.ui.component;

import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.service.chat.ChatHistoryService;
import art.moodframe.service.chat.ImageSizeCheck;
import art.moodframe.service.chat.ThumbnailManager;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;

/**
 * Loads the pictures of the image tiles of a chat entry: the thumbnail from the cache or created in the background,
 * the original as a fallback, in the trash for a chat there, the poster of a video and a placeholder. A loaded picture
 * opens the full view on a click.
 */
final class EntryTilePictures {

    private static final Logger logger = LoggerFactory.getLogger(EntryTilePictures.class);

    private final ChatEntry chatEntry;
    private final boolean isChatDeleted;
    private final ChatHistoryService history;
    private final ImageSources sources;
    private final IntConsumer onOpen;

    /**
     * Creates the loader of the pictures of an entry.
     *
     * @param chatEntry     the entry
     * @param isChatDeleted whether its chat lies in the trash
     * @param history       finds the files of the images (may be null: where they were generated)
     * @param sources       the thumbnails and the size check
     * @param onOpen        opens the full view on the image of an index
     */
    EntryTilePictures(ChatEntry chatEntry, boolean isChatDeleted, ChatHistoryService history, ImageSources sources,
            IntConsumer onOpen) {
        this.chatEntry = chatEntry;
        this.isChatDeleted = isChatDeleted;
        this.history = history;
        this.sources = sources;
        this.onOpen = onOpen;
    }

    /**
     * Gets the actual file path for an image, considering if it's in trash: the image itself as the history finds it,
     * else where it was generated; its thumbnail in the trash for an image there, else in the thumbnails folder.
     *
     * @param generatedImage the generated image
     * @param isThumbnail    whether to get thumbnail path instead of image path
     * @return the actual path (either active or trash path)
     */
    String getActualImagePath(GeneratedImage generatedImage, boolean isThumbnail) {
        String originalPath = generatedImage.getLocalFilePath();
        if (originalPath == null)
            return null;
        String found = history != null ? history.actualImagePath(generatedImage, isChatDeleted) : null;
        String imagePath = found != null ? found : originalPath;
        if (!isThumbnail)
            return imagePath;

        ThumbnailManager thumbnailManager = getThumbnailManager();
        String thumbnailPath = thumbnailManager != null ? thumbnailManager.getThumbnailPath(originalPath) : null;
        if (thumbnailPath == null || !isChatDeleted && imagePath.equals(originalPath))
            return thumbnailPath != null ? thumbnailPath : originalPath;
        String trashed = history != null ? history.actualThumbnailPath(thumbnailPath) : null;
        return trashed != null ? trashed : imagePath;
    }

    /**
     * Loads thumbnail image asynchronously for optimal performance.
     *
     * @param generatedImage    the generated image
     * @param imageView         the image view component
     * @param loadingOverlay    the loading overlay
     * @param progressIndicator the progress indicator
     * @param imageIndex        the image index for click handling
     */
    void loadThumbnailImage(GeneratedImage generatedImage, ImageView imageView,
            VBox loadingOverlay, ProgressIndicator progressIndicator, int imageIndex) {

        String localPath = getActualImagePath(generatedImage, false);
        ThumbnailManager thumbnailManager = getThumbnailManager();

        /*  The first display corrects a missing or wrong size from the file, the tile and tooltip follow it  */
        ImageSizeCheck sizeCheck = sources.sizeCheck();
        if (sizeCheck != null)
            sizeCheck.check(generatedImage, localPath);

        if (thumbnailManager == null) {
            // Fallback to original image loading if ThumbnailManager not available
            loadOriginalImage(generatedImage, imageView, imageIndex);
            return;
        }

        // Get the actual thumbnail path (considering trash)
        String thumbnailPath = getActualImagePath(generatedImage, true);

        /*  A thumbnail too small for its tile on the screen is created anew at full size  */
        SharpThumbnail.follow(imageView, localPath, sources);

        // FAST PATH: Check if thumbnail is already in memory cache (no disk I/O!)
        if (thumbnailPath != null) {
            javafx.scene.image.Image cachedImage = sources.cache().getIfCached(thumbnailPath);
            if (cachedImage != null) {
                // Image is in cache - set immediately, no disk access needed
                setImageWithHandlers(imageView, cachedImage, imageIndex);
                return;
            }
        }

        // SLOW PATH: Not in cache, need to load from disk (async)
        if (isChatDeleted) {
            // For deleted chats, load original image from trash (async)
            loadOriginalImage(generatedImage, imageView, imageIndex);
            return;
        }

        // Load thumbnail async - the cache will handle whether file exists
        CompletableFuture.supplyAsync(() -> thumbnailPath != null
                        ? sources.cache().get(thumbnailPath) : null)
                .whenComplete((image, error) -> {
                    if (image != null)
                        Platform.runLater(() -> setImageWithHandlers(imageView, image, imageIndex));
                    else if (error != null)
                        showOriginalImage(generatedImage, imageView, loadingOverlay, progressIndicator, imageIndex,
                                localPath, error);
                    else
                        generateThumbnail(generatedImage, imageView, loadingOverlay, progressIndicator, imageIndex,
                                localPath, thumbnailManager);
                });
    }

    /*  Creates a missing thumbnail and shows it; the stages follow the creation, no thread waits for it  */
    private void generateThumbnail(GeneratedImage generatedImage, ImageView imageView, VBox loadingOverlay,
            ProgressIndicator progressIndicator, int imageIndex, String localPath, ThumbnailManager thumbnailManager) {
        thumbnailManager.getThumbnail(localPath)
                .thenApplyAsync((thumbnailFile) -> thumbnailFile != null && thumbnailFile.exists()
                        ? sources.cache().get(thumbnailFile.getAbsolutePath()) : null)
                .whenComplete((image, error) -> {
                    if (image == null) {
                        showOriginalImage(generatedImage, imageView, loadingOverlay, progressIndicator, imageIndex,
                                localPath, error);
                        return;
                    }
                    Platform.runLater(() -> {
                        loadingOverlay.setVisible(false);
                        progressIndicator.setVisible(false);
                        setImageWithHandlers(imageView, image, imageIndex);
                    });
                });
    }

    /*  Shows the original image in place of a thumbnail that could not be loaded or created  */
    private void showOriginalImage(GeneratedImage generatedImage, ImageView imageView, VBox loadingOverlay,
            ProgressIndicator progressIndicator, int imageIndex, String localPath, Throwable error) {
        if (error != null)
            logger.warn("Failed to load thumbnail for: {}", localPath, error);
        Platform.runLater(() -> {
            loadingOverlay.setVisible(false);
            progressIndicator.setVisible(false);
            loadOriginalImage(generatedImage, imageView, imageIndex);
        });
    }

    /*  The ThumbnailManager of the application, null without thumbnails  */
    private ThumbnailManager getThumbnailManager() {
        return sources.thumbnails();
    }

    /**
     * Loads an image from the specified file path.
     *
     * @param imagePath  the path to the image file
     * @param imageView  the image view component
     * @param imageIndex the image index for click handling
     */
    private void loadImageFromPath(String imagePath, ImageView imageView, int imageIndex) {
        try {
            ThumbnailManager thumbnailManager = getThumbnailManager();

            // FAST PATH: Check cache first - no disk I/O, no thread switching
            javafx.scene.image.Image cachedImage = thumbnailManager != null
                    ? sources.cache().getIfCached(imagePath) : null;

            if (cachedImage != null) {
                // Image is in cache - set immediately (no disk I/O, no blocking)
                setImageWithHandlers(imageView, cachedImage, imageIndex);
                return;
            }

            // SLOW PATH: Not in cache - check file exists and load async
            // This only happens on first load, not during scrolling
            CompletableFuture.runAsync(() -> {
                File imageFile = new File(imagePath);
                if (!imageFile.exists()) {
                    Platform.runLater(() -> {
                        createPlaceholderImage(null, imageView, "File Not Found", 0.3);
                        imageView.setOnMouseClicked(null);
                        imageView.setCursor(javafx.scene.Cursor.DEFAULT);
                    });
                    return;
                }

                javafx.scene.image.Image image = thumbnailManager != null
                        ? sources.cache().get(imagePath)
                        : new javafx.scene.image.Image(imageFile.toURI().toString(),
                                ThumbnailManager.MAX_THUMBNAIL_SIZE, ThumbnailManager.MAX_THUMBNAIL_SIZE, true, true,
                                false);

                Platform.runLater(() -> {
                    if (image != null) {
                        setImageWithHandlers(imageView, image, imageIndex);
                    } else {
                        createPlaceholderImage(null, imageView, "Load Error", 0.3);
                        imageView.setOnMouseClicked(null);
                        imageView.setCursor(javafx.scene.Cursor.DEFAULT);
                    }
                });
            });
        } catch (Exception e) {
            logger.error("Failed to load image from: {}", imagePath, e);
            createPlaceholderImage(null, imageView, "Load Error", 0.3);
            imageView.setOnMouseClicked(null);
            imageView.setCursor(javafx.scene.Cursor.DEFAULT);
        }
    }

    /**
     * Sets an image on the ImageView and configures click handlers.
     */
    private void setImageWithHandlers(ImageView imageView, javafx.scene.image.Image image, int imageIndex) {
        imageView.setImage(image);

        // Enable click handler and cursor for successfully loaded images; a right click opens the context menu
        imageView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && imageIndex >= 0) {
                onOpen.accept(imageIndex);
            }
        });
        imageView.setCursor(javafx.scene.Cursor.HAND);
    }

    /**
     * Fallback method to load original image (used when thumbnail system
     * unavailable).
     *
     * @param generatedImage the generated image
     * @param imageView      the image view component
     * @param imageIndex     the image index for click handling
     */
    private void loadOriginalImage(GeneratedImage generatedImage, ImageView imageView, int imageIndex) {
        // Use getActualImagePath to handle trash directory properly
        String localPath = getActualImagePath(generatedImage, false);
        if (localPath == null) {
            createPlaceholderImage(generatedImage, imageView, "Image not found", 0.3);
            return;
        }

        // Delegate to loadImageFromPath which uses the LRU cache
        // This avoids loading full-size originals (which was causing performance issues)
        loadImageFromPath(localPath, imageView, imageIndex);
    }

    /**
     * Creates a placeholder image with status text.
     *
     * @param generatedImage the generated image
     * @param imageView      the image view to update
     * @param statusText     the status text to display
     * @param opacity        the opacity level (0.0 to 1.0)
     */
    void createPlaceholderImage(GeneratedImage generatedImage, ImageView imageView,
            String statusText, double opacity) {
        // Ensure this runs on FX Application Thread
        if (!javafx.application.Platform.isFxApplicationThread()) {
            javafx.application.Platform
                    .runLater(() -> createPlaceholderImage(generatedImage, imageView, statusText, opacity));
            return;
        }

        try {
            javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(400, 400);
            javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

            // Set background color based on image ID hash for variety
            int hash = generatedImage.getId().hashCode();
            double hue = ((hash % 360) + 360) % 360; // Ensure positive
            javafx.scene.paint.Color baseColor = javafx.scene.paint.Color.hsb(hue, 0.3, 0.8);
            javafx.scene.paint.Color color = baseColor.deriveColor(0, 1.0, 1.0, opacity);
            gc.setFill(color);
            gc.fillRect(0, 0, 400, 400);

            // Add status text only if not empty
            if (statusText != null && !statusText.trim().isEmpty()) {
                // Use darker color for error messages for better readability
                boolean isError = statusText.toLowerCase().contains("error") ||
                        statusText.toLowerCase().contains("failed") ||
                        statusText.toLowerCase().contains("blocked");
                gc.setFill(isError ? javafx.scene.paint.Color.DARKRED : javafx.scene.paint.Color.DARKGRAY);
                gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
                gc.setFont(javafx.scene.text.Font.font("System", 14));

                // Split text at periods for multi-line display
                String[] lines = statusText.split("\\. ");
                double lineHeight = 20;
                double totalHeight = lines.length * lineHeight;
                double startY = 200 - (totalHeight / 2) + (lineHeight / 2);

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    // Add period back if it was removed by split (except for last line if it didn't
                    // have one)
                    if (!line.endsWith(".") && (i < lines.length - 1 || statusText.endsWith("."))) {
                        line += ".";
                    }
                    gc.fillText(line, 200, startY + (i * lineHeight));
                }
            }

            javafx.scene.image.WritableImage placeholder = new javafx.scene.image.WritableImage(400, 400);
            canvas.snapshot(null, placeholder);
            imageView.setImage(placeholder);

        } catch (Exception e) {
            logger.warn("Failed to create image placeholder for status: {}", statusText, e);
        }
    }

    /**
     * Creates a video placeholder with a play icon for video entries.
     * Shows a dark background with a centered play triangle and video metadata.
     *
     * @param generatedImage the video GeneratedImage
     * @param imageView      the image view to set the placeholder on
     * @param imageIndex     the image index for click handling
     */
    void createVideoPlaceholder(GeneratedImage generatedImage, ImageView imageView, int imageIndex) {
        if (!javafx.application.Platform.isFxApplicationThread()) {
            javafx.application.Platform.runLater(() -> createVideoPlaceholder(generatedImage, imageView, imageIndex));
            return;
        }

        try {
            javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(400, 400);
            javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

            // Dark gradient background
            gc.setFill(javafx.scene.paint.Color.rgb(20, 20, 30));
            gc.fillRect(0, 0, 400, 400);

            // Subtle gradient overlay
            javafx.scene.paint.LinearGradient gradient = new javafx.scene.paint.LinearGradient(
                    0, 0, 0, 1, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                    new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.rgb(40, 40, 60, 0.5)),
                    new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.rgb(10, 10, 20, 0.8)));
            gc.setFill(gradient);
            gc.fillRect(0, 0, 400, 400);

            // Play button circle
            gc.setFill(javafx.scene.paint.Color.rgb(255, 255, 255, 0.15));
            gc.fillOval(150, 150, 100, 100);
            gc.setStroke(javafx.scene.paint.Color.rgb(255, 255, 255, 0.6));
            gc.setLineWidth(2);
            gc.strokeOval(150, 150, 100, 100);

            // Play triangle
            gc.setFill(javafx.scene.paint.Color.rgb(255, 255, 255, 0.9));
            double[] xPoints = {185, 185, 225};
            double[] yPoints = {175, 225, 200};
            gc.fillPolygon(xPoints, yPoints, 3);

            // Video info text
            gc.setFill(javafx.scene.paint.Color.rgb(200, 200, 200));
            gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            gc.setFont(javafx.scene.text.Font.font("System", 13));

            String resolution = generatedImage.getVideoResolution() != null ? generatedImage.getVideoResolution() : "";
            int duration = generatedImage.getVideoDurationSeconds();
            boolean audio = generatedImage.hasAudio();

            String info = String.format("MP4 • %s • %ds%s", resolution, duration, audio ? " • Audio" : "");
            gc.fillText(info, 200, 280);

            javafx.scene.image.WritableImage placeholder = new javafx.scene.image.WritableImage(400, 400);
            canvas.snapshot(null, placeholder);
            setImageWithHandlers(imageView, placeholder, imageIndex);

        } catch (Exception e) {
            logger.warn("Failed to create video placeholder", e);
            createPlaceholderImage(generatedImage, imageView, "Video", 0.3);
        }

        // The poster (a frame of the video) replaces the drawn placeholder as soon as it exists
        String videoPath = getActualImagePath(generatedImage, false);
        if (videoPath != null) {
            art.moodframe.ui.support.VideoPoster.load(java.nio.file.Path.of(videoPath))
                    .thenAccept((javafx.scene.image.Image poster) -> Platform.runLater(() ->
                            setImageWithHandlers(imageView, withPlayOverlay(poster, generatedImage), imageIndex)))
                    .exceptionally(error -> {
                        logger.debug("No poster for video {}: {}", videoPath, error.getMessage());
                        return null;
                    });
        }
    }

    /** Draws the play button and the video info over a poster frame. */
    private javafx.scene.image.Image withPlayOverlay(javafx.scene.image.Image poster, GeneratedImage video) {
        double width = poster.getWidth();
        double height = poster.getHeight();
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(width, height);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.drawImage(poster, 0, 0);

        gc.setFill(new javafx.scene.paint.LinearGradient(0, 0, 0, 1, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0.6, javafx.scene.paint.Color.rgb(0, 0, 0, 0)),
                new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.rgb(0, 0, 0, 0.7))));
        gc.fillRect(0, 0, width, height);

        double cx = width / 2;
        double cy = height / 2;
        gc.setFill(javafx.scene.paint.Color.rgb(0, 0, 0, 0.45));
        gc.fillOval(cx - 40, cy - 40, 80, 80);
        gc.setStroke(javafx.scene.paint.Color.rgb(255, 255, 255, 0.8));
        gc.setLineWidth(2);
        gc.strokeOval(cx - 40, cy - 40, 80, 80);
        gc.setFill(javafx.scene.paint.Color.rgb(255, 255, 255, 0.95));
        gc.fillPolygon(new double[] {cx - 12, cx - 12, cx + 20}, new double[] {cy - 20, cy + 20, cy}, 3);

        gc.setFill(javafx.scene.paint.Color.rgb(230, 230, 230));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setFont(javafx.scene.text.Font.font("System", 13));
        String resolution = video.getVideoResolution() != null ? video.getVideoResolution() : "";
        gc.fillText(String.format("MP4 • %s • %ds%s", resolution, video.getVideoDurationSeconds(),
                video.hasAudio() ? " • Audio" : ""), cx, height - 12);

        javafx.scene.image.WritableImage result = new javafx.scene.image.WritableImage((int) width, (int) height);
        javafx.scene.SnapshotParameters parameters = new javafx.scene.SnapshotParameters();
        parameters.setFill(javafx.scene.paint.Color.BLACK);
        canvas.snapshot(parameters, result);
        return result;
    }
}
