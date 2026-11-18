package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.util.ThemeManager;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The colored cloud that rises from the lower right corner of the main window (as on the splash screen), behind the
 * whole window content. The image is transparent around the cloud; the panels above it are transparent too
 * ({@code main-window.css}), while controls, the chat list and images keep their own background and cover it. It
 * follows the theme and the setting of the background image.
 *
 * @since 2.1.3
 */
final class CornerCloud {

    private static final double CLOUD_WIDTH = 1000.0;
    private static final double CLOUD_HEIGHT = 747.0;

    /*  the opacity of the theme colors of fields and buttons over the cloud  */
    private static final double CONTROL_ALPHA = 0.55;

    private final Node content;
    private final StackPane stack;
    private final ApplicationConfig config;
    private final ImageView cornerCloud;

    /*  the cloud for dark themes, and a deeper, more saturated one that stays visible on light themes  */
    private Image cloudDark;
    private Image cloudLight;

    /**
     * Puts the cloud behind the content of the window.
     *
     * @param content the content of the window, in a stack pane that takes the cloud as its lowest layer
     * @param config  tells whether the background image is on (may be null: on)
     */
    CornerCloud(Node content, ApplicationConfig config) {
        this.content = content;
        this.stack = (StackPane) content.getParent();
        this.config = config;
        cornerCloud = new ImageView(cloudImage(true));
        cornerCloud.setMouseTransparent(true);
        cornerCloud.setFocusTraversable(false);
        cornerCloud.setManaged(false);
        stack.getChildren().add(0, cornerCloud);
        stack.layoutBoundsProperty().addListener((obs, old, bounds) -> place(bounds));
        place(stack.getLayoutBounds());

        /*  follow a theme change at once, not only when the settings close  */
        ThemeManager.addThemeListener(this::update);

        /*  the theme colors resolve only once the window has its scene  */
        if (stack.getScene() != null)
            update();
        else
            stack.sceneProperty().addListener((obs, old, scene) -> {
                if (scene != null)
                    Platform.runLater(this::update);
            });
    }

    private Image cloudImage(boolean dark) {
        if (dark && cloudDark == null)
            cloudDark = new Image(getClass().getResourceAsStream("/images/corner-cloud.png"),
                    CLOUD_WIDTH, CLOUD_HEIGHT, true, true);
        if (!dark && cloudLight == null)
            cloudLight = new Image(getClass().getResourceAsStream("/images/corner-cloud-light.png"),
                    CLOUD_WIDTH, CLOUD_HEIGHT, true, true);
        return dark ? cloudDark : cloudLight;
    }

    private void place(Bounds bounds) {
        cornerCloud.relocate(bounds.getWidth() - CLOUD_WIDTH, bounds.getHeight() - CLOUD_HEIGHT);
    }

    /** Matches the strength of the cloud to the current theme, or hides it with the background image off. */
    void update() {
        update(ThemeManager.getCurrentTheme());
    }

    private void update(String themeName) {
        boolean dark = ThemeManager.isDarkTheme(themeName);
        List<Node> panels = new ArrayList<>(content.lookupAll(".see-through"));
        panels.addAll(content.lookupAll(".glass"));

        /*  switched off: no cloud, and fields with their normal, opaque theme colors  */
        boolean enabled = config == null || config.ui().isBackgroundImageEnabled();
        cornerCloud.setVisible(enabled);
        if (!enabled) {
            for (Node panel : panels)
                panel.setStyle(panel.getStyle().replaceAll("-color-bg-(default|subtle): [^;]*;\\s*", "").trim());
            return;
        }
        cornerCloud.setImage(cloudImage(dark));
        cornerCloud.setOpacity(dark ? 0.55 : 0.75);

        /*  fields and buttons over the cloud: the theme colors of their background at 55 %  */
        String fields = "-color-bg-default: " + themeColor("-color-bg-default", CONTROL_ALPHA) + "; "
                + "-color-bg-subtle: " + themeColor("-color-bg-subtle", CONTROL_ALPHA) + ";";
        /*  ".glass": the chat area, whose entries draw their own (now translucent) background  */
        for (Node panel : panels)
            panel.setStyle(panel.getStyle().replaceAll("-color-bg-(default|subtle): [^;]*;\\s*", "") + " " + fields);
    }

    /*  the current value of a looked-up theme color, as rgba with the given opacity  */
    private String themeColor(String token, double alpha) {
        Region probe = new Region();
        probe.setStyle("-fx-background-color: " + token + ";");
        stack.getChildren().add(probe);
        probe.applyCss();
        List<BackgroundFill> fills = probe.getBackground() != null ? probe.getBackground().getFills() : List.of();
        stack.getChildren().remove(probe);
        Color color = !fills.isEmpty() && fills.get(0).getFill() instanceof Color c ? c : Color.BLACK;
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.2f)", Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255), alpha);
    }
}
