package art.moodframe.ui.entry;

import art.moodframe.model.GeneratedImage;
import art.moodframe.util.I18nManager;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * The details of an image in the tooltip of its tile, which the entry cards and the images view share: seed, size and
 * costs, or why the image failed.
 *
 * @since 2.1.3
 */
final class ImageDetails {

    private ImageDetails() {
    }

    /**
     * Installs the tooltip of an image, read anew whenever the pointer enters its tile: the size line over the image
     * may give way and the size be corrected meanwhile. An image without any details gets no tooltip.
     *
     * @param tile  the pane holding the image, with its size line attached
     * @param view  the view of the image
     * @param image the image
     * @param size  the size line of the image
     */
    static void install(Pane tile, Node view, GeneratedImage image, ImageSizeLabel size) {
        Tooltip details = new Tooltip();
        details.setShowDelay(Duration.millis(500));
        tile.hoverProperty().addListener((obs, wasOver, isOver) -> {
            if (isOver) {
                details.setText(text(image, !size.isVisible()));
                Tooltip.uninstall(view, details);
                if (!details.getText().isEmpty())
                    Tooltip.install(view, details);
            }
        });
    }

    /**
     * The details of an image for its tooltip: its seed, of a video its resolution, duration and audio; below the
     * cost of a Gemini image with web search or reasoning. The size and format of an image lead only on request.
     *
     * @param generatedImage the generated image
     * @param withSize       whether to name the size and format of an image, as its size line gives way
     * @return the details, one per line, empty if there are none
     */
    static String text(GeneratedImage generatedImage, boolean withSize) {
        List<String> details = new ArrayList<>();
        if (generatedImage.isVideo())
            details.add(videoLine(generatedImage));
        else {
            String size = ImageSizeLabel.text(generatedImage);
            if (withSize && size != null)
                details.add(size);

            /*  A Gemini image has no seed (0), one of FLUX may be negative  */
            long seed = generatedImage.getSeed();
            if (seed != 0)
                details.add(I18nManager.getText("image.detail.seed", String.valueOf(seed)));
        }

        /*  Gemini: the cost of web search and reasoning beside the base cost, if any  */
        double searchCost = generatedImage.getSearchCost() != null ? generatedImage.getSearchCost() : 0.0;
        double reasoningCost = generatedImage.getReasoningCost() != null ? generatedImage.getReasoningCost() : 0.0;
        if (searchCost > 0 || reasoningCost > 0) {
            double baseCost = generatedImage.getBaseCost() != null ? generatedImage.getBaseCost() : 0.0;
            if (baseCost > 0)
                details.add(I18nManager.getText("cost.detail.base", I18nManager.formatCost(baseCost)));
            if (searchCost > 0)
                details.add(I18nManager.getText("cost.detail.search", I18nManager.formatCost(searchCost)));
            if (reasoningCost > 0) {
                Integer thinkingTokens = generatedImage.getThinkingTokens();
                details.add(thinkingTokens != null && thinkingTokens > 0
                        ? I18nManager.getText("cost.detail.reasoning.tokens", I18nManager.formatCost(reasoningCost),
                                thinkingTokens)
                        : I18nManager.getText("cost.detail.reasoning", I18nManager.formatCost(reasoningCost)));
            }
            details.add(I18nManager.getText("cost.detail.total",
                    I18nManager.formatCost(baseCost + searchCost + reasoningCost)));
        }
        return String.join("\n", details);
    }

    /**
     * Why an image failed, for the tooltip of its tile.
     *
     * @param generatedImage the failed image
     * @return its error message or reason, else the failed status
     */
    static String failure(GeneratedImage generatedImage) {
        String message = generatedImage.getErrorMessage();
        if (message == null || message.isBlank())
            message = generatedImage.getFailureReason();
        return message != null && !message.isBlank() ? message : I18nManager.getText("entry.status.failed");
    }

    /**
     * The line of a video: its format, resolution, duration and whether it has sound.
     *
     * @param video the video
     * @return the line, like {@code MP4 • 1080p • 8s • Audio}
     */
    static String videoLine(GeneratedImage video) {
        String resolution = video.getVideoResolution() != null ? video.getVideoResolution() : "";
        String line = String.format("MP4 • %s • %ds", resolution, video.getVideoDurationSeconds());
        return video.hasAudio() ? line + " • " + I18nManager.getText("video.detail.audio") : line;
    }
}
