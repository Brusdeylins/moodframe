package art.moodframe.ui.controller;

import art.moodframe.model.SavedImage;
import art.moodframe.service.library.SavedImageService;
import art.moodframe.ui.dialog.SavedImageManageDialog;
import art.moodframe.ui.dialog.SavedImageSaveDialog;
import art.moodframe.util.ImageSize;
import javafx.application.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The saved images for the dialogs that save and manage them: the lists and names are read on the JavaFX thread,
 * every file read and write of the library runs on the worker of the window and completes on the JavaFX thread.
 */
final class SavedImageStore implements SavedImageManageDialog.Library {

    private final SavedImageService service;
    private final Executor worker;

    /**
     * Takes the saved images and the worker.
     *
     * @param service the saved images
     * @param worker  the worker of the window, off the JavaFX thread
     */
    SavedImageStore(SavedImageService service, Executor worker) {
        this.service = service;
        this.worker = worker;
    }

    @Override
    public List<SavedImage> list() {
        return service.getAll();
    }

    @Override
    public Path fileOf(SavedImage image) {
        return service.fileOf(image);
    }

    @Override
    public boolean exists(String name) {
        return service.exists(name);
    }

    @Override
    public CompletableFuture<SavedImageSaveDialog.Inspection> inspect(Path file) {
        return call(() -> new SavedImageSaveDialog.Inspection(service.sameFile(file), describe(file)));
    }

    @Override
    public CompletableFuture<SavedImageService.Saved> save(Path file, String name, String description) {
        return call(() -> service.save(file, name, description));
    }

    @Override
    public CompletableFuture<Boolean> edit(SavedImage image, String name, String description) {
        String id = image.id();
        boolean renamed = !name.equals(image.name());
        return call(() -> (!renamed || service.rename(id, name)) && service.describe(id, description));
    }

    @Override
    public CompletableFuture<Boolean> setFavorite(SavedImage image, boolean favorite) {
        String id = image.id();
        return call(() -> service.setFavorite(id, favorite));
    }

    @Override
    public CompletableFuture<Boolean> delete(SavedImage image) {
        String id = image.id();
        return call(() -> service.moveToTrash(id));
    }

    /*  size, format and file size of an image file, e.g. "512 × 512 · PNG · 48 KB"  */
    private static String describe(Path file) throws IOException {
        ImageSize size = ImageSize.read(file);
        String name = file.getFileName().toString();
        String format = name.substring(name.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        long bytes = Files.size(file);
        String length = bytes < 1024 * 1024 ? Math.max(1, Math.round(bytes / 1024.0)) + " KB"
                : String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return (size != null ? size.width() + " × " + size.height() + " · " : "")
                + (format.equals("JPG") ? "JPEG" : format) + " · " + length;
    }

    /*  runs a work on the worker and completes on the JavaFX thread  */
    private <T> CompletableFuture<T> call(Callable<T> work) {
        CompletableFuture<T> done = new CompletableFuture<>();
        worker.execute(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> done.complete(result));
            }
            catch (Exception e) {
                Platform.runLater(() -> done.completeExceptionally(e));
            }
        });
        return done;
    }
}
