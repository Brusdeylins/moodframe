package art.moodframe.ui.cell;

import art.moodframe.model.MoodGroup;
import art.moodframe.ui.BoxInset;
import art.moodframe.util.I18nManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Row of the left sidebar by mood: the name of the mood, the number of its images and a strip of the colors of its
 * palette. A deleted mood keeps its name with the note "deleted"; the combinations of their own and the entries
 * without blocks close the list in italics.
 *
 * @since 2.0.0
 */
public class MoodGroupListCell extends ListCell<MoodGroup> {

    private final Label nameLabel = new Label();
    private final Label countLabel = new Label();
    private final HBox colorStrip = new HBox();
    private final VBox content;

    /**
     * Creates a row.
     */
    public MoodGroupListCell() {
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        FontIcon imagesIcon = new FontIcon("mdi2i-image-multiple-outline");
        imagesIcon.setIconSize(12);
        imagesIcon.getStyleClass().addAll("ikonli-font-icon", "text-muted");
        countLabel.setGraphic(imagesIcon);
        countLabel.getStyleClass().addAll("text-muted", "text-small");

        HBox headerRow = new HBox(6, nameLabel, countLabel);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        colorStrip.managedProperty().bind(colorStrip.visibleProperty());
        content = new VBox(3, headerRow, colorStrip);

        /*  The name starts on the inset of the boxes, like the chat names  */
        content.setPadding(new Insets(4, 12, 4, BoxInset.LEFT - BoxInset.LIST));
    }

    /**
     * The name of a group as the list and the view of the group show it.
     *
     * @param group the group
     * @return the name of the mood, with the note "deleted" for a deleted mood, or the name of a fixed group
     */
    public static String nameOf(MoodGroup group) {
        return switch (group.kind()) {
            case MOOD -> group.deleted() ? I18nManager.getText("sidebar.group.deleted", group.name()) : group.name();
            case CUSTOM -> I18nManager.getText("sidebar.moods.custom");
            case NONE -> I18nManager.getText("sidebar.moods.none");
        };
    }

    @Override
    protected void updateItem(MoodGroup group, boolean empty) {
        super.updateItem(group, empty);
        setText(null);
        if (empty || group == null) {
            setGraphic(null);
            return;
        }

        boolean inUse = group.kind() == MoodGroup.Kind.MOOD && !group.deleted();
        nameLabel.setText(nameOf(group));
        nameLabel.setStyle(inUse ? "-fx-font-weight: bold;" : "-fx-font-style: italic;");
        countLabel.setText(String.valueOf(group.imageCount()));

        /*  The colors share the width of the strip evenly  */
        colorStrip.getChildren().clear();
        for (String color : group.colors()) {
            Region stripe = new Region();
            stripe.setStyle("-fx-background-color: " + color + ";");
            stripe.setMinHeight(4);
            stripe.setPrefSize(0, 4);
            stripe.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(stripe, Priority.ALWAYS);
            colorStrip.getChildren().add(stripe);
        }
        colorStrip.setVisible(!group.colors().isEmpty());
        setGraphic(content);
    }
}
