package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryGroup;
import art.moodframe.model.GeneratedImage;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.ui.component.ImagePreviewOverlay;
import art.moodframe.util.I18nManager;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

/**
 * The full view of the chat view ({@link ChatViewController}): the images of an entry in the
 * {@link ImagePreviewOverlay}, with the steps to the previous and the next entry that has an image to show, or the
 * images of the images view as a gallery.
 */
final class EntryFullView {

    private static final Logger logger = LoggerFactory.getLogger(EntryFullView.class);

    /** What the full view needs from the chat view: the shown entries and where their images go. */
    interface Host {

        /** The open chat, null for none. */
        Chat chat();

        /** The open group, null for none. */
        EntryGroup group();

        /** The entries the chat view shows, null for none. */
        List<ChatEntry> shownEntries();

        /** The file of an image, in the trash for a chat there. */
        String imagePath(GeneratedImage image, boolean isChatDeleted);

        /** Starts a new prompt from the file of an image. */
        void edit(String path);

        /** Copies the file of an image into the Downloads folder. */
        void download(String path);

        /** Shows a status message. */
        void status(String message);
    }

    private final Host host;
    private ImagePreviewOverlay overlay;

    /*  The entry whose images the full view shows, for the steps to its neighbors  */
    private ChatEntry shownEntry;

    /**
     * Creates the full view on the host pane of the window, hidden.
     *
     * @param config      tells whether the frame of the full view turns
     * @param overlayHost the pane the full view covers
     * @param host        the chat view
     */
    EntryFullView(ApplicationConfig config, StackPane overlayHost, Host host) {
        this.host = host;
        try {
            overlay = new ImagePreviewOverlay(config);

            // Add overlay to the main stack pane (covers entire window except header)
            overlayHost.getChildren().add(overlay);
            overlay.setOnCloseRequested((nothing) -> overlay.setVisible(false));

            // Set up chat navigation callbacks
            overlay.setOnPrevChatRequested(() -> showNeighbor(-1));
            overlay.setOnNextChatRequested(() -> showNeighbor(1));
            overlay.setOnEditThisRequested(host::edit);
            overlay.setOnDownloadRequested(host::download);

            // Forward overlay status messages (e.g. download feedback) to the footer
            overlay.setOnStatusMessage(host::status);
        } catch (Exception e) {
            logger.error("Failed to setup image preview overlay", e);
        }
    }

    /** Whether the full view shows. */
    boolean isShowing() {
        return overlay != null && overlay.isVisible();
    }

    /** Closes the full view, if open. */
    void close() {
        if (isShowing())
            overlay.setVisible(false);
    }

    /** Closes the full view and stops it listening to the language, once the window closes. */
    void dispose() {
        close();
        if (overlay != null)
            I18nManager.removeChangeListener(overlay);
    }

    /**
     * Pages through images of the images view, each with its line.
     *
     * @param paths    the files of the images
     * @param captions the line of each image
     * @param start    the index of the image to start with
     */
    void showGallery(List<String> paths, List<String> captions, int start) {
        if (overlay != null)
            overlay.showGallery(paths, captions, start);
    }

    /**
     * Shows the images of an entry whose file is there.
     *
     * @param entry      the chat entry containing images to display
     * @param startIndex the index of the image to start with
     */
    void showEntry(ChatEntry entry, int startIndex) {
        if (overlay == null || entry == null || ChatLibrary.imagesOf(entry).isEmpty())
            return;

        // Extract image paths from chat entry, checking both active and trash locations
        List<String> imagePaths = ChatLibrary.imagesOf(entry).stream()
                .map(this::pathOf)
                .filter(path -> path != null && !path.isEmpty())
                .filter(path -> new File(path).exists()) // Only include existing files
                .toList();
        if (imagePaths.isEmpty())
            return;

        // Ensure startIndex is within bounds of valid images
        int safeStartIndex = Math.max(0, Math.min(startIndex, imagePaths.size() - 1));
        overlay.showImages(imagePaths, safeStartIndex);

        // Track the current chat entry for navigation
        shownEntry = entry;
        overlay.updateChatNavigationButtons(hasNeighbor(-1), hasNeighbor(1));
    }

    /*  the file of an image of the open chat, in the trash for a chat there  */
    private String pathOf(GeneratedImage image) {
        Chat chat = host.chat();
        return host.imagePath(image, chat != null && chat.isDeleted());
    }

    /**
     * Shows the first image of the previous or the next entry that has one whose file is there: of the open group, or
     * of the open chat.
     *
     * @param step -1 for the previous entry, 1 for the next
     */
    private void showNeighbor(int step) {
        if (host.chat() == null && host.group() == null || shownEntry == null)
            return;
        List<ChatEntry> entries = host.group() != null ? host.shownEntries() : ChatLibrary.entriesOf(host.chat());
        if (entries == null)
            return;
        int currentIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getId().equals(shownEntry.getId())) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex == -1)
            return;
        for (int i = currentIndex + step; i >= 0 && i < entries.size(); i += step) {
            int first = firstShownImage(entries.get(i));
            if (first >= 0) {
                showEntry(entries.get(i), first);
                return;
            }
        }
    }

    /**
     * Whether a previous or a next shown entry has an image whose file is there.
     *
     * @param step -1 for the previous entry, 1 for the next
     */
    private boolean hasNeighbor(int step) {
        List<ChatEntry> entries = host.shownEntries();
        if (host.chat() == null && host.group() == null || entries == null || shownEntry == null)
            return false;
        int currentIndex = entries.indexOf(shownEntry);
        for (int i = currentIndex + step; i >= 0 && i < entries.size(); i += step) {
            if (firstShownImage(entries.get(i)) >= 0)
                return true;
        }
        return false;
    }

    /**
     * The index of the first image of an entry the full view can show: downloaded, with its file there (also in the
     * trash). A finished entry may hold failed images as well.
     *
     * @param entry the chat entry
     * @return the index, -1 for none
     */
    private int firstShownImage(ChatEntry entry) {
        if (entry == null)
            return -1;
        List<GeneratedImage> images = ChatLibrary.imagesOf(entry);
        for (int i = 0; i < images.size(); i++) {
            GeneratedImage image = images.get(i);
            if (image.getDownloadStatus() != GeneratedImage.DownloadStatus.CACHED)
                continue;
            String path = pathOf(image);
            if (path != null && !path.isEmpty() && new File(path).exists())
                return i;
        }
        return -1;
    }
}
