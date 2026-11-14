package art.moodframe.ui.component;

import art.moodframe.model.CameraSettings.Aperture;
import art.moodframe.model.CameraSettings.Iso;
import art.moodframe.model.CameraSettings.Lens;
import art.moodframe.util.I18nManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Example picture of one stage of a camera setting, generated with a subject that names no place, so the
 * surroundings are made up.
 *
 * <p>A picture is loaded in the background on its first use and kept as long as the example; a stage without picture
 * shows a placeholder. A camera model has no picture of its own, the one of its type applies. Without a stage the
 * frame stays empty with a hint, in the same size.</p>
 *
 * @since 2.0.0
 */
public class CameraExample extends VBox {

    private static final double SIZE = 220;

    /*  the pictures loaded so far, the key null for the picture without camera  */
    private final Map<Enum<?>, Image> images = new HashMap<>();
    private final ImageView view = new ImageView();
    private final FontIcon placeholder = new FontIcon("mdi2i-image-off-outline");
    private final Label empty = new Label();
    private final Label caption = new Label();
    private final Label hint = new Label();
    private Enum<?> stage;

    /**
     * Creates the example, showing the empty frame.
     */
    public CameraExample() {
        super(4);
        view.setFitWidth(SIZE - 2);
        view.setFitHeight(SIZE - 2);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        placeholder.getStyleClass().add("ikonli-font-icon");
        placeholder.setIconSize(48);
        empty.getStyleClass().addAll("text-small", "text-muted");
        empty.setWrapText(true);
        empty.setTextAlignment(TextAlignment.CENTER);
        empty.setPadding(new Insets(0, 24, 0, 24));
        StackPane frame = new StackPane(placeholder, view, empty);
        frame.setMinSize(SIZE, SIZE);
        frame.setMaxSize(SIZE, SIZE);

        /*  the frame stays visible while empty; the picture fits inside its border  */
        frame.setStyle("-fx-border-color: -color-border-default;");
        caption.getStyleClass().add("text-small");
        hint.getStyleClass().addAll("text-small", "text-muted");
        hint.setWrapText(true);
        hint.setTextAlignment(TextAlignment.CENTER);
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(SIZE);
        getChildren().addAll(frame, caption, hint);
        updateTexts();
        clear();
    }

    /** Sets the caption and the hints in the current language. */
    public void updateTexts() {
        empty.setText(I18nManager.getText("camera.dialog.example.empty"));
        hint.setText(I18nManager.getText("camera.dialog.example.hint"));
        caption.setText(caption(stage));
    }

    /**
     * Shows the picture of a stage.
     *
     * @param stage the stage, a value of a setting, null for the picture without camera
     */
    public void show(Enum<?> stage) {
        this.stage = stage;
        Image image = images.computeIfAbsent(stage, this::load);
        view.setImage(image);
        placeholder.setVisible(image == null || image.isError());
        empty.setVisible(false);
        caption.setText(caption(stage));
        caption.setVisible(true);
    }

    /**
     * Shows the empty frame with the hint to point at an option; the caption keeps its space, so nothing moves.
     */
    public void clear() {
        view.setImage(null);
        placeholder.setVisible(false);
        empty.setVisible(true);
        caption.setVisible(false);
    }

    private static String caption(Enum<?> stage) {
        return stage == null ? I18nManager.getText("camera.dialog.example.any")
                : I18nManager.getText("camera.dialog.example", name(stage));
    }

    /**
     * The resource of the picture of a stage, for example {@code /images/camera/lens-MM_85.jpg}.
     *
     * @param stage the stage, null for the picture without camera
     * @return the resource name
     */
    static String resource(Enum<?> stage) {
        return "/images/camera/" + (stage == null ? "any" : setting(stage) + "-" + stage.name()) + ".jpg";
    }

    /*  the picture in the background, null without resource; a picture that fails to load shows the placeholder  */
    private Image load(Enum<?> stage) {
        URL url = CameraExample.class.getResource(resource(stage));
        if (url == null)
            return null;
        Image image = new Image(url.toExternalForm(), true);
        image.errorProperty().addListener((obs, oldVal, error) -> {
            if (view.getImage() == image)
                placeholder.setVisible(error);
        });
        return image;
    }

    /*  the name of a stage as the dialog shows it: focal length, f-number and ISO as numbers, the rest by its text  */
    static String name(Enum<?> stage) {
        return switch (stage) {
            case Lens lens when !lens.isSpecial() -> lens.millimeters() + " mm";
            case Aperture aperture -> aperture.label();
            case Iso iso -> iso.label();
            default -> I18nManager.getText("camera." + setting(stage) + "." + stage.name().toLowerCase(Locale.ROOT));
        };
    }

    /*  the setting of a stage by the name of its type, for example "lens"  */
    private static String setting(Enum<?> stage) {
        return stage.getDeclaringClass().getSimpleName().toLowerCase(Locale.ROOT);
    }
}
