package art.moodframe.ui.cell;

import art.moodframe.model.FrameGroup;
import art.moodframe.service.FrameService;
import art.moodframe.util.I18nManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * Row of the left sidebar by frame: the name of the frame and the number of its images, below them the target aspect
 * ratio and resolution and the day of the latest use. A deleted frame keeps its name with the note "deleted"; the
 * settings of their own and the entries without copy close the list in italics.
 *
 * @since 2.0.0
 */
public class FrameGroupListCell extends ListCell<FrameGroup> {

    private final Label nameLabel = new Label();
    private final Label countLabel = new Label();
    private final Label targetLabel = new Label();
    private final Label lastUsedLabel = new Label();
    private final HBox detailRow;
    private final VBox content;

    /**
     * Creates a row.
     */
    public FrameGroupListCell() {
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        FontIcon imagesIcon = new FontIcon("mdi2i-image-multiple-outline");
        imagesIcon.setIconSize(12);
        imagesIcon.getStyleClass().addAll("ikonli-font-icon", "text-muted");
        countLabel.setGraphic(imagesIcon);
        countLabel.getStyleClass().addAll("text-muted", "text-small");

        targetLabel.getStyleClass().addAll("text-muted", "text-small");
        targetLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(targetLabel, Priority.ALWAYS);
        lastUsedLabel.getStyleClass().addAll("text-muted", "text-small");

        HBox headerRow = new HBox(6, nameLabel, countLabel);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        detailRow = new HBox(6, targetLabel, lastUsedLabel);
        detailRow.setAlignment(Pos.CENTER_LEFT);
        detailRow.managedProperty().bind(detailRow.visibleProperty());
        content = new VBox(3, headerRow, detailRow);

        /*  The name starts on the line of the chat names, behind the space of their grip  */
        content.setPadding(new Insets(4, 12, 4, 28));
    }

    /**
     * The name of a group as the list and the view of the group show it.
     *
     * @param group the group
     * @return the name of the frame, with the note "deleted" for a deleted frame, or the name of a fixed group
     */
    public static String nameOf(FrameGroup group) {
        return switch (group.kind()) {
            case FRAME -> group.deleted() ? I18nManager.getText("sidebar.group.deleted", group.name()) : group.name();
            case CUSTOM -> I18nManager.getText("sidebar.frames.custom");
            case NONE -> I18nManager.getText("sidebar.frames.none");
        };
    }

    @Override
    protected void updateItem(FrameGroup group, boolean empty) {
        super.updateItem(group, empty);
        setText(null);
        if (empty || group == null) {
            setGraphic(null);
            return;
        }

        boolean inUse = group.kind() == FrameGroup.Kind.FRAME && !group.deleted();
        nameLabel.setText(nameOf(group));
        nameLabel.setStyle(inUse ? "-fx-font-weight: bold;" : "-fx-font-style: italic;");
        countLabel.setText(String.valueOf(group.imageCount()));

        /*  A fixed group has no target of its own, a group without entries no latest use  */
        targetLabel.setText(group.aspectRatio() != null
                ? FrameService.describe(group.aspectRatio(), group.resolution()) : null);
        lastUsedLabel.setText(group.lastUsed() != null ? group.lastUsed().format(DateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM).withLocale(I18nManager.getCurrentLocale())) : null);
        detailRow.setVisible(group.lastUsed() != null);
        setGraphic(content);
    }
}
