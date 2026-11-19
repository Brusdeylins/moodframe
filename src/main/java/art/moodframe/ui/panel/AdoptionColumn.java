package art.moodframe.ui.panel;

import art.moodframe.model.Mood;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The checkboxes of an adoption into another window, as the brainstorm adopts into the main window, in a column right
 * of the fields of the groups Mood and Frame, where the lock stands above them; below each building block field a
 * place for a block proposed anew. The groups keep their fields in their order, only their actions and those of the
 * fields go, and so do the locks, but those of the fields locked in the other window: they show closed, their
 * checkboxes stay off. The column only shows the checkboxes; what they adopt decides its host.
 *
 * @since 2.1.4
 */
public final class AdoptionColumn {

    /*  the gap between a field and its checkbox  */
    private static final double GAP = 8.0;

    /** The fields of the groups that can be adopted, in their order. */
    public enum Field {
        MOOD, ATMOSPHERE, STYLE, COLOR, LIGHT, CAMERA, FRAME, ASPECT_RATIO, RESOLUTION;

        /**
         * The field of a building block.
         *
         * @param field the field as the mood names it
         * @return the field
         */
        public static Field of(Mood.Field field) {
            return valueOf(field.name());
        }
    }

    /**
     * The nodes of {@code MoodFramePanel.fxml} the column works on.
     *
     * @param moodRow        the row of the mood
     * @param blockFields    the building block fields, each a grid of label row, field and hints
     * @param locks          the locks of the building block fields
     * @param frameRow       the row of the frame
     * @param aspectRatioRow the row of the aspect ratio
     * @param resolutionRow  the row of the resolution
     * @param actions        the actions of the groups and of the fields, hidden
     * @param values         the value of each field, whose change by the user the host learns
     */
    record Nodes(HBox moodRow, Map<Mood.Field, GridPane> blockFields, Map<Mood.Field, ToggleButton> locks,
                 HBox frameRow, HBox aspectRatioRow, HBox resolutionRow, List<Node> actions,
                 Map<Field, ObservableValue<?>> values) {
    }

    private final Map<Field, CheckBox> checks = new EnumMap<>(Field.class);
    private final Map<Mood.Field, VBox> slots = new EnumMap<>(Mood.Field.class);
    private final Set<Mood.Field> locked;

    /**
     * Puts the checkboxes beside the fields and hides the actions and the open locks.
     *
     * @param nodes    the nodes of the groups
     * @param locked   the building block fields locked in the other window
     * @param applying whether the groups set fields themselves, e.g. the blocks of a chosen mood: no change of the user
     * @param changed  learns of a field the user changed
     */
    AdoptionColumn(Nodes nodes, Set<Mood.Field> locked, BooleanSupplier applying, Consumer<Field> changed) {
        this.locked = locked.isEmpty() ? EnumSet.noneOf(Mood.Field.class) : EnumSet.copyOf(locked);
        for (Field field : Field.values())
            checks.put(field, new CheckBox());

        for (Node action : nodes.actions()) {
            action.setVisible(false);
            action.setManaged(false);
        }
        place(nodes.moodRow(), checks.get(Field.MOOD));
        place(nodes.frameRow(), checks.get(Field.FRAME));
        place(nodes.aspectRatioRow(), checks.get(Field.ASPECT_RATIO));
        place(nodes.resolutionRow(), checks.get(Field.RESOLUTION));
        nodes.blockFields().forEach((Mood.Field field, GridPane grid) -> {
            CheckBox check = checks.get(Field.of(field));

            /*  the label row with the lock and the hints span the column of the checkbox, so the lock stands above it  */
            for (Node node : grid.getChildren())
                if (GridPane.getRowIndex(node) == null || GridPane.getRowIndex(node) != 1)
                    GridPane.setColumnSpan(node, 2);
            grid.setHgap(GAP);
            grid.getColumnConstraints().add(new ColumnConstraints());
            grid.add(check, 1, 1);

            VBox slot = new VBox(4.0);
            slot.visibleProperty().bind(Bindings.isNotEmpty(slot.getChildren()));
            slot.managedProperty().bind(slot.visibleProperty());
            grid.add(slot, 0, 3, 2, 1);
            slots.put(field, slot);

            ToggleButton lock = nodes.locks().get(field);
            lock.setSelected(this.locked.contains(field));
            lock.setDisable(true);
            lock.setVisible(this.locked.contains(field));
            check.setDisable(this.locked.contains(field));
        });
        nodes.values().forEach((Field field, ObservableValue<?> value) -> value.addListener((obs, oldVal, newVal) -> {
            if (!applying.getAsBoolean())
                changed.accept(field);
        }));
    }

    /*  puts a checkbox at the end of a row, the gap to the field as in the grids of the building blocks  */
    private static void place(HBox row, CheckBox check) {
        HBox.setMargin(check, new Insets(0, 0, 0, Math.max(0, GAP - row.getSpacing())));
        row.getChildren().add(check);
    }

    /**
     * The checkbox of a field.
     *
     * @param field the field
     * @return its checkbox
     */
    public CheckBox check(Field field) {
        return checks.get(field);
    }

    /**
     * The place below a building block field, e.g. for a block proposed anew; hidden while it is empty.
     *
     * @param field the building block field
     * @return the place, shown once it has content
     */
    public VBox slot(Mood.Field field) {
        return slots.get(field);
    }

    /**
     * Tells whether a building block field is locked in the other window, so it adopts nothing.
     *
     * @param field the building block field
     * @return whether it is locked
     */
    public boolean isLocked(Mood.Field field) {
        return locked.contains(field);
    }
}
