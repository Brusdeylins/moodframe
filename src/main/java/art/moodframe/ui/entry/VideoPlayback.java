package art.moodframe.ui.entry;

import art.moodframe.ui.support.MediaSupport;
import art.moodframe.ui.support.SystemOpen;
import art.moodframe.ui.support.VideoPoster;
import art.moodframe.ui.support.VideoRemedy;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * The playback of a video in the full view ({@link ImagePreviewOverlay}): the media view on the display layer, the
 * play/pause button and, where JavaFX cannot play the video, the fallback with the poster of the video, a hint and a
 * button for the video player of the system (under Linux also what helps). The overlay places the nodes and shows the
 * image and the poster through its {@link Host}.
 */
final class VideoPlayback {

    private static final Logger logger = LoggerFactory.getLogger(VideoPlayback.class);

    /** What the playback needs from the overlay that shows it. */
    interface Host {

        /** The area between the buttons the video fits into. */
        Bounds safeArea();

        /** Hides the image while the video plays. */
        void hideImage();

        /** Hides the zoom button, which a video does not take. */
        void hideZoom();

        /**
         * Shows the poster of a video JavaFX cannot play in place of the image, fitted like an image.
         *
         * @param poster the poster, or null for none yet
         */
        void showPoster(Image poster);

        /** Gives the focus back to the overlay, for its keys. */
        void focus();

        /** Shows a message in the status bar of the window. */
        void status(String message);
    }

    private final Region overlay;
    private final Region displayLayer;
    private final Host host;

    private final MediaView mediaView = new MediaView();
    private MediaPlayer mediaPlayer;
    private final Button playPauseButton = new Button();
    private boolean active;

    /*  The fallback of a video JavaFX cannot play, for the file it shows (null while it is hidden)  */
    private final HBox fallback;
    private final FontIcon fallbackIcon = new FontIcon("mdi2v-video-off-outline");
    private final Label fallbackHint = new Label();
    private final Button fallbackButton = new Button();
    private File fallbackVideo;

    /*  What helps under Linux: the command to copy, with a note for the Debian family  */
    private final Label fallbackRemedy = new Label();
    private final HBox fallbackCommandBox;
    private final Label fallbackCommand = new Label();
    private final Button fallbackCopyButton = new Button();
    private final Label fallbackRemedyNote = new Label();

    /**
     * Creates the nodes of the playback, all hidden.
     *
     * @param overlay      the overlay, whose size the first fit of a video takes before its layout
     * @param displayLayer the layer the media view lies on, whose device pixels the video snaps to
     * @param host         shows the image and the poster
     */
    VideoPlayback(Region overlay, Region displayLayer, Host host) {
        this.overlay = overlay;
        this.displayLayer = displayLayer;
        this.host = host;

        mediaView.setPreserveRatio(false);
        mediaView.setSmooth(true);
        mediaView.setVisible(false);
        mediaView.setManaged(false);

        // Play/Pause button for video playback (bottom center, initially hidden)
        FontIcon playIcon = new FontIcon("mdi2p-play");
        playIcon.setIconSize(24);
        playIcon.setIconColor(Color.WHITE);
        playPauseButton.setGraphic(playIcon);
        playPauseButton.getStyleClass().addAll("icon-button", "preview-button");
        playPauseButton.setTooltip(new Tooltip("Play / Pause"));
        playPauseButton.setVisible(false);
        playPauseButton.setOnAction(e -> {
            toggle();
            host.focus();
        });

        /*  The fallback of a video shows its icon only as long as there is no poster  */
        fallbackIcon.setIconSize(48);
        fallbackIcon.setIconColor(Color.WHITE);
        fallbackHint.setWrapText(true);
        fallbackHint.setMaxWidth(420.0);
        fallbackHint.setTextAlignment(TextAlignment.CENTER);
        fallbackHint.setStyle("-fx-text-fill: white;");
        fallbackRemedy.setWrapText(true);
        fallbackRemedy.setMaxWidth(420.0);
        fallbackRemedy.setTextAlignment(TextAlignment.CENTER);
        fallbackRemedy.setAlignment(Pos.CENTER);
        fallbackRemedy.setStyle("-fx-text-fill: white;");

        /*  The command never wraps, the icon next to it copies just the command  */
        fallbackCommand.setMinWidth(Region.USE_PREF_SIZE);
        fallbackCommand.setStyle("-fx-text-fill: white; -fx-font-family: Monospaced; "
                + "-fx-background-color: rgba(255, 255, 255, 0.15); -fx-background-radius: 4; -fx-padding: 2 6 2 6;");
        FontIcon copyIcon = new FontIcon("mdi2c-content-copy");
        copyIcon.setIconSize(16);
        copyIcon.setIconColor(Color.WHITE);
        fallbackCopyButton.setGraphic(copyIcon);
        fallbackCopyButton.getStyleClass().add("icon-button");
        fallbackCommandBox = new HBox(6.0, fallbackCommand, fallbackCopyButton);
        fallbackCommandBox.setAlignment(Pos.CENTER);
        fallbackRemedyNote.setWrapText(true);
        fallbackRemedyNote.setMaxWidth(420.0);
        fallbackRemedyNote.setTextAlignment(TextAlignment.CENTER);
        fallbackRemedyNote.setAlignment(Pos.CENTER);
        fallbackRemedyNote.setStyle("-fx-text-fill: white;");
        VBox fallbackBox = new VBox(10.0, fallbackIcon, fallbackHint, fallbackRemedy, fallbackCommandBox,
                fallbackRemedyNote, fallbackButton);
        fallbackBox.setAlignment(Pos.CENTER);
        fallbackBox.setMaxWidth(Region.USE_PREF_SIZE);
        fallbackBox.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-background-radius: 12; "
                + "-fx-padding: 12 18 12 18;");
        fallback = new HBox(fallbackBox);
        fallback.setAlignment(Pos.CENTER);
        fallback.setPickOnBounds(false);
        fallback.setVisible(false);

        fallbackButton.setOnAction((e) -> {
            if (fallbackVideo != null)
                SystemOpen.open(fallbackVideo.toPath());
            host.focus();
        });
        fallbackCopyButton.setOnAction((e) -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(fallbackCommand.getText());
            Clipboard.getSystemClipboard().setContent(content);
            host.status(I18nManager.getText("video.fallback.remedy.copied"));
            host.focus();
        });
    }

    /** The view of the video, for the display layer. */
    MediaView mediaView() {
        return mediaView;
    }

    /** The play/pause button, for the bottom center of the navigation layer. */
    Button playPauseButton() {
        return playPauseButton;
    }

    /** The fallback of a video JavaFX cannot play, for the navigation layer. */
    HBox fallback() {
        return fallback;
    }

    /** Whether a video shows, played or as its fallback. */
    boolean isActive() {
        return active;
    }

    /**
     * Loads a video file for playback using MediaView.
     *
     * @param videoFile the video file to play
     */
    void load(File videoFile) {
        // Stop any previous video
        stop();

        /*  Once JavaFX Media proved unusable on this system, every video goes straight to its fallback  */
        if (!MediaSupport.isUsable()) {
            showFallback(videoFile, null);
            return;
        }

        try {
            Media media = new Media(videoFile.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);

            /*  An error while opening or playing, e.g. under Linux without a plugin for the ffmpeg of the system  */
            MediaPlayer player = mediaPlayer;
            media.setOnError(() -> {
                if (mediaPlayer == player)
                    showFallback(videoFile, media.getError());
            });
            mediaPlayer.setOnError(() -> {
                if (mediaPlayer == player)
                    showFallback(videoFile, player.getError());
            });

            // Hide image, show video
            host.hideImage();
            mediaView.setVisible(true);
            active = true;

            // Fit video to the safe area
            Bounds area = host.safeArea();
            mediaView.setFitWidth(overlay.getWidth() > 0 ? area.getWidth() : 800);
            mediaView.setFitHeight(overlay.getHeight() > 0 ? area.getHeight() : 600);

            // Center the video manually since managed=false
            mediaPlayer.setOnReady(this::fit);

            // Show play/pause button, hide zoom button
            playPauseButton.setVisible(true);
            host.hideZoom();

            // Update play/pause icon when status changes
            mediaPlayer.setOnPlaying(() -> updatePlayPauseIcon(true));
            mediaPlayer.setOnPaused(() -> updatePlayPauseIcon(false));
            mediaPlayer.setOnStopped(() -> updatePlayPauseIcon(false));
            mediaPlayer.setOnEndOfMedia(() -> {
                mediaPlayer.seek(javafx.util.Duration.ZERO);
                mediaPlayer.pause();
                updatePlayPauseIcon(false);
            });

            // Auto-play
            mediaPlayer.play();

            logger.info("Video loaded for playback: {}", videoFile.getName());

        } catch (Exception e) {
            showFallback(videoFile, e);
        }
    }

    /**
     * Fits the video into the safe area between the buttons, never above its own size, centered in it; once its
     * player is ready and after every resize.
     */
    void fit() {
        if (!active || mediaPlayer == null)
            return;
        Bounds area = host.safeArea();
        double videoWidth = mediaView.getFitWidth();
        double videoHeight = mediaView.getFitHeight();

        // Recalculate with actual media dimensions to maintain aspect ratio
        Media media = mediaPlayer.getMedia();
        double mediaWidth = media.getWidth();
        double mediaHeight = media.getHeight();
        if (mediaWidth > 0 && mediaHeight > 0) {
            double scaleW = area.getWidth() / mediaWidth;
            double scaleH = area.getHeight() / mediaHeight;
            double scale = Math.min(scaleW, scaleH);
            scale = Math.min(scale, 1.0); // Don't upscale
            videoWidth = displayLayer.snapPositionX(mediaWidth * scale);
            videoHeight = displayLayer.snapPositionY(mediaHeight * scale);
            mediaView.setFitWidth(videoWidth);
            mediaView.setFitHeight(videoHeight);
        }

        /*  On device pixels like the image, for the frame to lie flush against it  */
        mediaView.setLayoutX(displayLayer.snapPositionX(area.getCenterX() - videoWidth / 2));
        mediaView.setLayoutY(displayLayer.snapPositionY(area.getCenterY() - videoHeight / 2));
    }

    /**
     * Shows the poster of a video which JavaFX cannot play, with a hint and a button for the video player of the
     * system; play/pause stays off, closing and paging work as before.
     *
     * @param videoFile the video file
     * @param error the error of JavaFX Media, or null if JavaFX Media is known to be unusable
     */
    private void showFallback(File videoFile, Throwable error) {
        stop();
        if (error != null) {
            MediaSupport.failed(error);
            logger.warn("JavaFX cannot play the video {}: {}", videoFile.getAbsolutePath(), error.getMessage());
        }
        active = true;
        fallbackVideo = videoFile;
        host.showPoster(null);
        fallbackIcon.setVisible(true);
        fallbackIcon.setManaged(true);
        updateFallbackTexts();
        fallback.setVisible(true);

        /*  What helps under Linux is detected once in the background, the hint shows it as soon as it is known  */
        if (!MediaSupport.isUsable())
            VideoRemedy.detect().thenRun(() -> Platform.runLater(() -> {
                if (fallbackVideo != null)
                    updateFallbackTexts();
            }));

        /*  The poster arrives later, when the overlay may already show another file  */
        VideoPoster.load(videoFile.toPath())
                .whenComplete((poster, failure) -> Platform.runLater(() -> {
                    if (poster == null || !videoFile.equals(fallbackVideo))
                        return;
                    fallbackIcon.setVisible(false);
                    fallbackIcon.setManaged(false);
                    host.showPoster(poster);
                }));
    }

    /** Relabels the fallback in the current language, if it shows. */
    void relabel() {
        if (fallbackVideo != null)
            updateFallbackTexts();
    }

    /*  Without any way to open the video, the hint names its file instead of offering the button  */
    private void updateFallbackTexts() {
        boolean opens = SystemOpen.isAvailable();
        fallbackHint.setText(opens
                ? I18nManager.getText("video.fallback.hint")
                : I18nManager.getText("video.fallback.hint.file", fallbackVideo.getAbsolutePath()));
        fallbackButton.setText(I18nManager.getText("video.fallback.open"));
        fallbackButton.setVisible(opens);
        fallbackButton.setManaged(opens);

        /*  Only a system without video support gets to know what helps, macOS and Windows get nothing  */
        VideoRemedy.Remedy remedy = MediaSupport.isUsable() ? null : VideoRemedy.detect().getNow(null);
        String remedyText = remedy == null ? null : switch (remedy.kind()) {
            case ARCH -> I18nManager.getText("video.fallback.remedy.arch");
            case DEBIAN_MISSING -> I18nManager.getText("video.fallback.remedy.debian.missing");
            case DEBIAN_NEWER -> I18nManager.getText("video.fallback.remedy.debian.newer",
                    String.valueOf(remedy.version()));
            case OTHER -> I18nManager.getText("video.fallback.remedy.other",
                    String.valueOf(VideoRemedy.MIN_LIBAVCODEC), String.valueOf(VideoRemedy.MAX_LIBAVCODEC));
        };
        String command = remedy == null ? null : remedy.kind().command();

        /*  The ffmpeg of the Debian family may be too new as well, which only shows after installing it  */
        String note = remedy != null && remedy.kind() == VideoRemedy.Kind.DEBIAN_MISSING
                ? I18nManager.getText("video.fallback.remedy.debian.missing.note") : null;
        fallbackRemedy.setText(remedyText);
        fallbackRemedy.setVisible(remedyText != null);
        fallbackRemedy.setManaged(remedyText != null);
        fallbackCommand.setText(command);
        fallbackCopyButton.setTooltip(new Tooltip(I18nManager.getText("video.fallback.remedy.copy")));
        fallbackCommandBox.setVisible(command != null);
        fallbackCommandBox.setManaged(command != null);
        fallbackRemedyNote.setText(note);
        fallbackRemedyNote.setVisible(note != null);
        fallbackRemedyNote.setManaged(note != null);
    }

    /**
     * Stops and disposes the current video playback.
     */
    void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        mediaView.setMediaPlayer(null);
        mediaView.setVisible(false);
        active = false;
        playPauseButton.setVisible(false);
        fallback.setVisible(false);
        fallbackVideo = null;
    }

    /** Hides the video for an image that was decoded meanwhile. */
    void hide() {
        mediaView.setVisible(false);
        active = false;
        playPauseButton.setVisible(false);
    }

    /**
     * Toggles video playback between play and pause.
     */
    void toggle() {
        if (mediaPlayer == null) return;

        MediaPlayer.Status status = mediaPlayer.getStatus();
        if (status == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.play();
        }
    }

    /**
     * Updates the play/pause button icon.
     *
     * @param isPlaying true if video is currently playing
     */
    private void updatePlayPauseIcon(boolean isPlaying) {
        Platform.runLater(() -> {
            if (playPauseButton.getGraphic() instanceof FontIcon icon) {
                icon.setIconLiteral(isPlaying ? "mdi2p-pause" : "mdi2p-play");
                icon.setIconColor(Color.WHITE);
            }
        });
    }
}
