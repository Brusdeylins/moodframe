package art.moodframe.service.library;

import art.moodframe.model.SavedImage;
import art.moodframe.repository.SavedImageRepository;
import art.moodframe.util.ImageSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The saved images of the library, e.g. logos, word marks or product photos, which the user attaches to an order again
 * and again: saved once with a name and a description, chosen later in the main window, by an AI agent over MCP and by
 * the brainstorm, without the user interface.
 *
 * <p>A saved image keeps a copy of its file in the folder of the saved images, named by its SHA-256, so the same file
 * is stored only once and stays when the file it came from is gone. An image in the trash keeps its file until it is
 * removed for good; saving its file again brings it back.</p>
 *
 * <p>Every successful write is reported as {@link LibraryEvents.Kind#SAVED_IMAGE}. The writes are serialized, so
 * parallel callers cannot take the same name or store the same file twice.</p>
 *
 * @since 2.1.4
 */
public class SavedImageService {

    private static final Logger logger = LoggerFactory.getLogger(SavedImageService.class);

    /** The file types a saved image may have, as the attachments take them. */
    public static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    /** What {@link #save} did. */
    public enum Outcome {
        /** The image is saved, or came back from the trash. */
        SAVED,
        /** An image in use has the same file already; nothing changed. */
        EXISTS,
        /** Another image in use has the name; nothing changed. */
        NAME_TAKEN,
        /** The file is no image or the library could not be written. */
        FAILED
    }

    /**
     * The outcome of {@link #save} with the image it concerns.
     *
     * @param outcome what happened
     * @param image   the saved image, for {@link Outcome#EXISTS} the one with the same file; null otherwise
     */
    public record Saved(Outcome outcome, SavedImage image) {
    }

    private final SavedImageRepository repository;
    private final Path folder;
    private final LibraryEvents events;
    private final RepositoryAccess access = new RepositoryAccess(logger);

    /**
     * Creates the service.
     *
     * @param repository the saved images
     * @param folder     the folder of their files, created by the first image saved
     * @param events     where the writes are reported
     */
    public SavedImageService(SavedImageRepository repository, Path folder, LibraryEvents events) {
        this.repository = repository;
        this.folder = folder;
        this.events = events;
    }

    /*  reports a successful write  */
    private boolean changed(boolean success, String id) {
        if (success)
            events.changed(LibraryEvents.Kind.SAVED_IMAGE, id);
        return success;
    }

    /**
     * Returns the saved images in use: the favorites first, then by name.
     *
     * @return the images
     */
    public List<SavedImage> getAll() {
        return access.call(repository::findAll, List.of(), "Failed to load the saved images");
    }

    /**
     * Returns the saved images in the trash.
     *
     * @return the images, the latest deletion first
     */
    public List<SavedImage> getDeleted() {
        return access.call(repository::findDeleted, List.of(), "Failed to load the saved images");
    }

    /**
     * Finds a saved image in use by its id or, failing that, by its name.
     *
     * @param reference the id or the name
     * @return the image, null if none in use has it
     */
    public SavedImage find(String reference) {
        if (reference == null || reference.isBlank())
            return null;
        return access.call(() -> repository.findById(reference).or(() -> byName(reference)).orElse(null), null,
                "Failed to load the saved image " + reference);
    }

    /*  the image of a name, empty if none or on a database error, which is logged  */
    private Optional<SavedImage> byName(String name) {
        return Optional.ofNullable(access.call(() -> repository.findByName(name).orElse(null), null,
                "Failed to load the saved image " + name));
    }

    /**
     * Checks if a saved image in use has this name.
     *
     * @param name the name
     * @return true if the name is taken
     */
    public boolean exists(String name) {
        return byName(name).isPresent();
    }

    /**
     * Gets the file of a saved image, which an order attaches.
     *
     * @param image the image
     * @return its file in the folder of the saved images
     */
    public Path fileOf(SavedImage image) {
        return folder.resolve(image.file());
    }

    /**
     * Finds the saved image in use with the same content as a file, read off the JavaFX thread.
     *
     * @param file the file
     * @return the image, null if none in use has this content or the file cannot be read
     */
    public SavedImage sameFile(Path file) {
        try {
            String hash = sha256(file);
            return access.call(() -> repository.findBySha256(hash).filter((SavedImage image) -> image.deletedAt()
                    == null).orElse(null), null, "Failed to look up the saved image of " + file);
        }
        catch (IOException e) {
            logger.warn("Failed to read {} for its saved image", file, e);
            return null;
        }
    }

    /**
     * Saves an image file under a name: copies it into the folder of the saved images and stores it. The same file is
     * stored only once: an image in use with it is answered as {@link Outcome#EXISTS}, one in the trash comes back
     * under the name. Reads and writes files, so it runs off the JavaFX thread.
     *
     * @param source      the image file, PNG, JPEG or WebP
     * @param name        the name, which no other image in use may have
     * @param description what it shows and when it fits, null or blank for none
     * @return what happened, with the image
     */
    public synchronized Saved save(Path source, String name, String description) {
        String trimmed = name != null ? name.trim() : "";
        String extension = extensionOf(source);
        ImageSize size = Files.isRegularFile(source) ? ImageSize.read(source) : null;
        if (trimmed.isEmpty() || !EXTENSIONS.contains(extension) || size == null) {
            logger.warn("Cannot save {} as '{}': no name, or no image", source, trimmed);
            return new Saved(Outcome.FAILED, null);
        }
        String hash;
        try {
            hash = sha256(source);
        }
        catch (IOException e) {
            logger.error("Failed to read {}", source, e);
            return new Saved(Outcome.FAILED, null);
        }

        SavedImage same = access.call(() -> repository.findBySha256(hash).orElse(null), null,
                "Failed to look up the saved image of " + source);
        if (same != null && same.deletedAt() == null)
            return new Saved(Outcome.EXISTS, same);
        if (exists(trimmed))
            return new Saved(Outcome.NAME_TAKEN, null);

        String text = description != null && !description.isBlank() ? description.trim() : null;
        String file = hash + "." + (extension.equals("jpeg") ? "jpg" : extension);
        if (!copy(source, file))
            return new Saved(Outcome.FAILED, null);

        /*  The file of an image in the trash brings it back under the new name  */
        if (same != null) {
            boolean restored = access.call(() -> repository.restore(same.id(), trimmed, text), false,
                    "Failed to restore the saved image " + same.id());
            return changed(restored, same.id()) ? new Saved(Outcome.SAVED, find(same.id()))
                    : new Saved(Outcome.FAILED, null);
        }
        SavedImage image = new SavedImage(UUID.randomUUID().toString(), trimmed, file, size.width(), size.height(),
                hash, text, false, LocalDateTime.now(), null);
        if (!changed(access.call(() -> repository.insert(image), false, "Failed to save the image " + trimmed),
                image.id())) {
            deleteFile(file);
            return new Saved(Outcome.FAILED, null);
        }
        return new Saved(Outcome.SAVED, image);
    }

    /*  copies a file into the folder of the saved images under a name, unless it is there already  */
    private boolean copy(Path source, String file) {
        try {
            Path target = folder.resolve(file);
            if (!Files.isRegularFile(target)) {
                Files.createDirectories(folder);
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }
        catch (IOException e) {
            logger.error("Failed to copy {} into {}", source, folder, e);
            return false;
        }
    }

    /**
     * Gives a saved image in use a new name.
     *
     * @param id   the id
     * @param name the new name, which no other image in use may have
     * @return true if it was renamed, false for a blank or taken name or an image not in use
     */
    public synchronized boolean rename(String id, String name) {
        String trimmed = name != null ? name.trim() : "";
        SavedImage owner = byName(trimmed).orElse(null);
        if (trimmed.isEmpty() || owner != null && !owner.id().equals(id))
            return false;
        return changed(access.call(() -> repository.rename(id, trimmed), false, "Failed to rename the saved image "
                + id), id);
    }

    /**
     * Gives a saved image in use a new description.
     *
     * @param id          the id
     * @param description what it shows and when it fits, null or blank for none
     * @return true if it was changed
     */
    public synchronized boolean describe(String id, String description) {
        String text = description != null && !description.isBlank() ? description.trim() : null;
        return changed(access.call(() -> repository.describe(id, text), false,
                "Failed to describe the saved image " + id), id);
    }

    /**
     * Marks a saved image in use as favorite, shown first in the choice, or not.
     *
     * @param id       the id
     * @param favorite whether it is a favorite
     * @return true if it was changed
     */
    public synchronized boolean setFavorite(String id, boolean favorite) {
        return changed(access.call(() -> repository.setFavorite(id, favorite), false,
                "Failed to change the favorite of the saved image " + id), id);
    }

    /**
     * Moves a saved image to the trash; its file stays until it is removed for good.
     *
     * @param id the id
     * @return true if it is in the trash now
     */
    public synchronized boolean moveToTrash(String id) {
        return changed(access.call(() -> repository.moveToTrash(id), false,
                "Failed to move the saved image to the trash: " + id), id);
    }

    /**
     * Takes a saved image out of the trash. If another image took its name meanwhile, it gets the next free one.
     *
     * @param id the id
     * @return true if it is in use again
     */
    public synchronized boolean restore(String id) {
        SavedImage image = access.call(() -> repository.findInTrash(id).orElse(null), null,
                "Failed to load the saved image " + id);
        if (image == null)
            return false;
        String name = freeName(image.name());
        return changed(access.call(() -> repository.restore(id, name, image.description()), false,
                "Failed to restore the saved image " + id), id);
    }

    /**
     * Removes a saved image in the trash for good, with its file; an image in use stays.
     *
     * @param id the id
     * @return true if it was in the trash and is gone
     */
    public synchronized boolean deletePermanently(String id) {
        SavedImage image = access.call(() -> repository.findInTrash(id).orElse(null), null,
                "Failed to load the saved image " + id);
        if (image == null || !access.call(() -> repository.delete(id), false,
                "Failed to remove the saved image " + id))
            return false;
        deleteFile(image.file());
        return changed(true, id);
    }

    /**
     * Returns the name unchanged if no saved image in use has it yet, else the name with the next free counter, e.g.
     * {@code "Logo (1)"}.
     *
     * @param name the wanted name
     * @return a name no saved image in use has
     */
    public String freeName(String name) {
        if (!exists(name))
            return name;
        int counter = 1;
        while (exists(name + " (" + counter + ")"))
            counter++;
        return name + " (" + counter + ")";
    }

    private void deleteFile(String file) {
        try {
            Files.deleteIfExists(folder.resolve(file));
        }
        catch (IOException e) {
            logger.warn("Failed to remove the file {} of a saved image", file, e);
        }
    }

    /*  the extension of a file in lowercase, empty without one  */
    private static String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    /**
     * Computes the SHA-256 of a file, which tells the same file again.
     *
     * @param file the file
     * @return the digest in lowercase hexadecimal
     * @throws IOException if the file cannot be read
     */
    static String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            for (int read = input.read(buffer); read >= 0; read = input.read(buffer))
                digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing in this Java runtime", e);
        }
    }
}
