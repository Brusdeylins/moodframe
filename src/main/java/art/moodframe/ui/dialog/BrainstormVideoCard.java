package art.moodframe.ui.dialog;

import art.moodframe.service.BrainstormService;
import art.moodframe.service.YouTubeInfoService;
import art.moodframe.util.I18nManager;
import art.moodframe.util.YouTubeClip;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The card of a YouTube video under the chat message that links it: preview image, title, length and the time range
 * as orange chip. A click on the chip changes the range, a click on the rest plays the clip in the browser. With a
 * range, the preview is the one of YouTube nearest to the middle of the range (see {@link YouTubeClip#thumbnailUrl}).
 *
 * @since 2.0.0
 */
final class BrainstormVideoCard extends HBox {

    private static final double IMAGE_WIDTH = 128.0;
    private static final double IMAGE_HEIGHT = 72.0;
    private static final String CHIP_STYLE = "-fx-background-color: #ff6b4a; -fx-background-radius: 10; "
            + "-fx-padding: 1 8 1 8; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold;";

    private final BrainstormService service;
    private final ImageView preview = new ImageView();
    private final Label titleLabel = new Label();
    private final Label lengthLabel = new Label();
    private final Label chip = new Label();
    private YouTubeClip clip;
    private YouTubeInfoService.Info info;

    /**
     * Creates the card and loads title, length and preview in the background.
     *
     * @param clip the linked video with its range
     * @param service the brainstorm service, which looks up the video
     * @param onChipClicked called when the user clicks the range to change it
     * @param openInBrowser opens a URL in the browser
     */
    BrainstormVideoCard(YouTubeClip clip, BrainstormService service, Consumer<BrainstormVideoCard> onChipClicked,
            Consumer<String> openInBrowser) {
        super(10.0);
        this.clip = clip;
        this.service = service;

        preview.setFitWidth(IMAGE_WIDTH);
        preview.setFitHeight(IMAGE_HEIGHT);
        FontIcon play = new FontIcon("mdi2p-play-circle");
        play.setIconSize(28);
        StackPane image = new StackPane(preview, play);
        image.setMinSize(IMAGE_WIDTH, IMAGE_HEIGHT);
        image.setMaxSize(IMAGE_WIDTH, IMAGE_HEIGHT);

        /*  The theme colors icons over their color in code: the play icon takes the theme color, set to white here  */
        image.setStyle("-fx-background-color: black; -fx-background-radius: 6; -color-fg-default: white;");

        titleLabel.setText("youtu.be/" + clip.videoId());
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(240.0);
        titleLabel.getStyleClass().add("text-bold");
        FontIcon youTube = new FontIcon("mdi2y-youtube");
        youTube.setIconSize(16);
        youTube.getStyleClass().add("danger");
        lengthLabel.getStyleClass().addAll("text-muted", "text-small");
        HBox meta = new HBox(6.0, youTube, lengthLabel);
        meta.setAlignment(Pos.CENTER_LEFT);

        chip.setStyle(CHIP_STYLE);
        chip.setCursor(Cursor.HAND);
        chip.setTooltip(new Tooltip(I18nManager.getText("brainstorm.video.range.tooltip")));
        chip.setOnMouseClicked(event -> {
            event.consume();
            onChipClicked.accept(this);
        });

        VBox text = new VBox(4.0, titleLabel, meta, chip);
        getChildren().addAll(image, text);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(8.0));
        setMaxWidth(420.0);
        setStyle("-fx-background-color: -color-bg-subtle; -fx-background-radius: 10;");
        setCursor(Cursor.HAND);
        Tooltip.install(this, new Tooltip(I18nManager.getText("brainstorm.video.open.tooltip")));
        setOnMouseClicked(event -> openInBrowser.accept(this.clip.watchUrl()));
        showRange();

        service.videoInfo(clip.videoId()).thenAccept((YouTubeInfoService.Info found) -> Platform.runLater(() -> {
            info = found;
            if (!found.title().isEmpty())
                titleLabel.setText(found.title());
            lengthLabel.setText(found.durationSeconds() > 0 ? YouTubeClip.formatTime(found.durationSeconds()) : "");
            loadPreview();
        }));
    }

    /** The video with the range the card shows. */
    YouTubeClip clip() {
        return clip;
    }

    /**
     * Shows another range of the video, with the preview for it.
     *
     * @param changed the same video with the new range
     */
    void setClip(YouTubeClip changed) {
        clip = changed;
        showRange();
        if (info != null)
            loadPreview();
    }

    private void showRange() {
        chip.setText(clip.hasRange() ? clip.rangeLabel() : I18nManager.getText("brainstorm.video.whole"));
    }

    /** Loads the preview for the range; if YouTube has none, the normal preview of the video. */
    private void loadPreview() {
        String url = clip.thumbnailUrl(info.durationSeconds());
        service.videoImage(url)
                .exceptionallyCompose((Throwable error) -> url.equals(clip.defaultThumbnailUrl())
                        ? CompletableFuture.<byte[]>failedFuture(error)
                        : service.videoImage(clip.defaultThumbnailUrl()))
                .thenAccept((byte[] bytes) -> Platform.runLater(() -> showPreview(bytes)));
    }

    /** Shows a preview image; the black bars of the 4:3 previews of YouTube are cut off. */
    private void showPreview(byte[] bytes) {
        Image image = new Image(new ByteArrayInputStream(bytes));
        if (image.isError())
            return;

        boolean letterboxed = Math.abs(image.getHeight() / image.getWidth() - 0.75) < 0.01;
        preview.setViewport(letterboxed ? new Rectangle2D(0, image.getHeight() / 8, image.getWidth(),
                image.getHeight() * 3 / 4) : null);
        preview.setImage(image);
    }
}
