package art.moodframe.service;

import art.moodframe.model.GeneratedImage;
import art.moodframe.repository.ChatRepository;
import art.moodframe.util.ImageSize;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Corrects the stored pixel size of an image from the header of its file when the image is first shown: an image
 * stored before may carry the requested size or 1024x1024 instead of the delivered one. Each image is checked once
 * per run; the file is read and a wrong size stored on a worker, the image itself is changed on the FX thread.
 *
 * @since 2.0.0
 */
public class ImageSizeCheck {

    private static final Logger logger = LoggerFactory.getLogger(ImageSizeCheck.class);

    private final ChatRepository chatRepository;
    private final Set<String> checked = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newSingleThreadExecutor((runnable) -> {
        Thread thread = new Thread(runnable, "ImageSizeCheck");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Creates the check.
     *
     * @param chatRepository where a corrected size is stored
     */
    public ImageSizeCheck(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    /**
     * Checks the size of a shown image against its file, once per run. Call on the FX thread.
     *
     * @param image the shown image
     * @param path  the path of its file, null if unknown
     */
    public void check(GeneratedImage image, String path) {
        if (path == null || image.isVideo() || !checked.add(image.getId()))
            return;
        String id = image.getId();
        int width  = image.getWidth();
        int height = image.getHeight();
        executor.execute(() -> {
            ImageSize size = correct(id, Path.of(path), width, height);
            if (size != null)
                Platform.runLater(() -> {
                    image.setImageWidth(size.width());
                    image.setImageHeight(size.height());
                    image.setWidth(size.width());
                    image.setHeight(size.height());
                });
        });
    }

    /**
     * Reads the size of the file of an image and stores it if the known size is missing or differs. Runs on a worker.
     *
     * @param imageId the image ID
     * @param file    the image file
     * @param width   the known width, 0 if missing
     * @param height  the known height, 0 if missing
     * @return the size of the file if the known one was wrong, null if it is right or the header cannot be read
     */
    ImageSize correct(String imageId, Path file, int width, int height) {
        ImageSize size = ImageSize.read(file);
        if (size == null || size.width() == width && size.height() == height)
            return null;
        try {
            chatRepository.updateImageSize(imageId, size.width(), size.height());
            logger.info("Corrected the size of image {} from {}x{} to {}x{}", imageId, width, height, size.width(),
                    size.height());
        }
        catch (SQLException e) {
            logger.warn("Failed to store the corrected size of image {}", imageId, e);
        }
        return size;
    }
}
