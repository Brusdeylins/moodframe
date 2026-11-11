package art.moodframe.ui.dialog;

import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.BrainstormContext;
import art.moodframe.service.CameraPromptBuilder;
import art.moodframe.service.MoodAnalysisService;
import art.moodframe.ui.component.ColorBox;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dialog "Mood from image": the user drops, pastes or picks an image, the {@link MoodAnalysisService} finds its
 * atmosphere, style, light, colors and camera, and the user decides per building block what goes into the fields of
 * the main window, optionally saved as a new mood.
 *
 * <p>Each row shows "None", an entry of the list or a new block from the image with an editable name; the checkbox
 * adopts the block, a new one into the list of the user. A field locked in the main window is not adopted. Without a
 * language model only the colors come from the image, and the dialog says so.</p>
 *
 * @since 2.0.0
 */
public class MoodImageDialog extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(MoodImageDialog.class);

    private static final double DIALOG_WIDTH = 520;
    private static final double DIALOG_HEIGHT = 780;
    private static final double IMAGE_WIDTH = 480;
    private static final double IMAGE_HEIGHT = 260;
    /** The longest side an image file is loaded with; the analysis needs no more. */
    private static final double LOAD_SIDE = 1024;
    private static final String NONE_ITEM_KEY = "brainstorm.none";
    private static final KeyCombination PASTE = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);
    /** The rows, in the order of the fields of the main window. */
    private static final List<Mood.Field> FIELDS = List.of(Mood.Field.ATMOSPHERE, Mood.Field.STYLE, Mood.Field.COLOR,
            Mood.Field.LIGHT, Mood.Field.CAMERA);

    /**
     * The values adopted into the main window.
     *
     * @param blocks the adopted building blocks by field: an entry of the lists with its id, or a new block for the
     *               list of the user; a field that is not adopted is missing
     * @param saveMood whether the fields are saved as a new mood afterwards
     */
    public record Result(Map<Mood.Field, BrainstormDraft.Block> blocks, boolean saveMood) {
    }

    private final MoodAnalysisService service;
    private final BrainstormContext context;
    private final Consumer<Result> onApply;
    private final Map<Mood.Field, BlockRow> rows = new EnumMap<>(Mood.Field.class);
    private final ImageView imageView = new ImageView();
    private final Label placeholder = new Label();
    private final Label statusLabel = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final CheckBox saveMoodCheck = new CheckBox();
    private final Button applyButton = new Button();
    /** Counts the analyses, so the answer for an image that was replaced meanwhile is dropped. */
    private int analysisCount;

    /**
     * Creates the dialog.
     *
     * @param owner the owner window
     * @param service the analysis of the image
     * @param context the lists of the building blocks and the fields locked in the main window
     * @param imageFile the image to analyze at once, null to let the user give one
     * @param onApply receives the adopted values
     */
    public MoodImageDialog(Window owner, MoodAnalysisService service, BrainstormContext context, File imageFile,
            Consumer<Result> onApply) {
        this.service = service;
        this.context = context;
        this.onApply = onApply;

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(I18nManager.getText("mood.image.title"));

        /*  The image field takes a dropped file or image; a click picks a file  */
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(IMAGE_WIDTH - 4);
        imageView.setFitHeight(IMAGE_HEIGHT - 4);
        placeholder.setText(I18nManager.getText("mood.image.drop"));
        placeholder.getStyleClass().addAll("text-muted", "text-small");
        placeholder.setWrapText(true);
        StackPane imageField = new StackPane(placeholder, imageView);
        imageField.setPrefSize(IMAGE_WIDTH, IMAGE_HEIGHT);
        imageField.setMinHeight(IMAGE_HEIGHT);
        imageField.setStyle("-fx-background-color: -color-bg-default; -fx-background-radius: 4px; "
                + "-fx-border-color: -color-border-muted; -fx-border-width: 1px; -fx-border-style: dashed; "
                + "-fx-border-radius: 4px; -fx-cursor: hand;");
        imageField.setOnDragOver((DragEvent event) -> {
            if (event.getDragboard().hasFiles() || event.getDragboard().hasImage())
                event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        imageField.setOnDragDropped((DragEvent event) -> {
            Dragboard board = event.getDragboard();
            event.setDropCompleted(board.hasFiles() && !board.getFiles().isEmpty() ? loadFile(board.getFiles().get(0))
                    : board.hasImage() && takeImage(board.getImage()));
            event.consume();
        });
        imageField.setOnMouseClicked((event) -> chooseFile());

        Button pasteButton = new Button(I18nManager.getText("mood.image.paste"), icon("mdi2c-clipboard-arrow-down-outline"));
        pasteButton.setOnAction((event) -> paste());
        Button fileButton = new Button(I18nManager.getText("mood.image.file"), icon("mdi2f-folder-open-outline"));
        fileButton.setOnAction((event) -> chooseFile());
        HBox imageActions = new HBox(6.0, pasteButton, fileButton);

        progress.setMaxSize(16.0, 16.0);
        progress.setVisible(false);
        progress.managedProperty().bind(progress.visibleProperty());
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("text-small");
        HBox statusRow = new HBox(8.0, progress, statusLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        VBox rowBox = new VBox(10.0);
        for (Mood.Field field : FIELDS) {
            BlockRow row = new BlockRow(field);
            rows.put(field, row);
            rowBox.getChildren().add(row.node);
        }
        saveMoodCheck.setText(I18nManager.getText("brainstorm.result.save.mood"));
        saveMoodCheck.setTooltip(new Tooltip(I18nManager.getText("brainstorm.result.save.mood.tooltip")));

        VBox content = new VBox(10.0, imageField, imageActions, statusRow, rowBox, saveMoodCheck);
        content.setPadding(new Insets(15.0));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        applyButton.setText(I18nManager.getText("brainstorm.adopt"));
        applyButton.getStyleClass().add("accent");
        applyButton.setDefaultButton(true);
        applyButton.setDisable(true);
        applyButton.setOnAction((event) -> apply());
        Button cancelButton = new Button(I18nManager.getText("button.cancel"));
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction((event) -> close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(6.0, spacer, cancelButton, applyButton);
        buttons.setPadding(new Insets(0, 15.0, 15.0, 15.0));

        /*  Shortcut+V pastes an image, unless a text field takes it  */
        Scene scene = new Scene(new VBox(10.0, scroll, buttons), DIALOG_WIDTH, DIALOG_HEIGHT);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, (KeyEvent event) -> {
            if (PASTE.match(event) && !(scene.getFocusOwner() instanceof TextInputControl)) {
                paste();
                event.consume();
            }
        });
        setScene(scene);
        setMinWidth(420);
        setMinHeight(480);

        if (owner != null) {
            setX(owner.getX() + (owner.getWidth() - DIALOG_WIDTH) / 2);
            setY(owner.getY() + Math.max(0, (owner.getHeight() - DIALOG_HEIGHT) / 2));
        }
        if (imageFile != null)
            loadFile(imageFile);
    }

    private static FontIcon icon(String literal) {
        FontIcon icon = new FontIcon(literal);
        icon.setIconSize(14);
        icon.getStyleClass().add("ikonli-font-icon");
        return icon;
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nManager.getText("filechooser.image.title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18nManager.getText("mood.image.filter"),
                "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File file = chooser.showOpenDialog(this);
        if (file != null)
            loadFile(file);
    }

    /** Takes the image of the clipboard, else its first file. */
    private void paste() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasImage())
            takeImage(clipboard.getImage());
        else if (clipboard.hasFiles() && !clipboard.getFiles().isEmpty())
            loadFile(clipboard.getFiles().get(0));
        else
            statusLabel.setText(I18nManager.getText("attachment.paste.empty"));
    }

    /** Loads an image file scaled down and analyzes it; false if it cannot be read. */
    private boolean loadFile(File file) {
        return takeImage(new Image(file.toURI().toString(), LOAD_SIDE, LOAD_SIDE, true, true));
    }

    /** Shows an image and analyzes it; false if it cannot be read. */
    private boolean takeImage(Image image) {
        if (image == null || image.isError() || image.getPixelReader() == null || image.getWidth() < 1) {
            statusLabel.setText(I18nManager.getText("mood.image.unreadable"));
            return false;
        }
        imageView.setImage(image);
        placeholder.setVisible(false);
        analyze(image);
        return true;
    }

    /** Analyzes the image in the background and shows what was found; an earlier analysis still running is dropped. */
    private void analyze(Image image) {
        int run = ++analysisCount;
        progress.setVisible(true);
        statusLabel.setText(I18nManager.getText("mood.image.analyzing"));
        applyButton.setDisable(true);
        service.analyze(toBufferedImage(image), context).whenComplete(
                (MoodAnalysisService.Analysis analysis, Throwable error) -> Platform.runLater(() -> {
                    if (run != analysisCount)
                        return;
                    progress.setVisible(false);
                    applyButton.setDisable(false);
                    if (error != null) {
                        logger.error("Image analysis failed", error);
                        statusLabel.setText(I18nManager.getText("mood.image.unreadable"));
                        return;
                    }
                    statusLabel.setText(analysis.notice() != null ? analysis.notice()
                            : I18nManager.getText("mood.image.done"));
                    for (Mood.Field field : FIELDS)
                        rows.get(field).show(analysis.blocks().get(field));
                }));
    }

    private static BufferedImage toBufferedImage(Image image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] argb = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, argb, 0, width);
        return buffered;
    }

    /**
     * Adopts the ticked blocks. A new block needs a name; if an entry of its list has the name already, the user is
     * asked whether to take the next free name, else the dialog stays open.
     */
    private void apply() {
        Map<Mood.Field, BrainstormDraft.Block> blocks = new EnumMap<>(Mood.Field.class);
        for (BlockRow row : rows.values()) {
            BrainstormDraft.Block block = row.adopted();
            if (block != null && block.isNew()) {
                String name = block.name();
                List<String> taken = row.names();
                if (name.isEmpty()) {
                    row.nameField.requestFocus();
                    return;
                }
                if (taken.contains(name)) {
                    String free = name;
                    for (int counter = 1; taken.contains(free); counter++)
                        free = name + " (" + counter + ")";
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                            I18nManager.getText("brainstorm.block.taken", name, free), ButtonType.YES, ButtonType.NO);
                    alert.initOwner(this);
                    alert.setHeaderText(null);
                    if (alert.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                        row.nameField.requestFocus();
                        return;
                    }
                    row.nameField.setText(free);
                    block = row.adopted();
                }
            }
            if (block != null)
                blocks.put(row.field, block);
        }
        onApply.accept(new Result(blocks, saveMoodCheck.isSelected()));
        close();
    }

    /**
     * The row of a building block: "None", a new block from the image (its name editable, its text, colors or camera
     * fragment shown below) or an entry of the list. The checkbox adopts the block, a new one into the list of the
     * user; for a field locked in the main window it stays off.
     */
    private final class BlockRow {

        private final Mood.Field field;
        private final ChoiceBox<String> box = new ChoiceBox<>();
        private final CheckBox check = new CheckBox();
        private final TextField nameField = new TextField();
        private final Label textLabel = new Label();
        private final HBox colorBoxes = new HBox(3.0);
        private final VBox node;
        private final String newItem = I18nManager.getText("mood.image.new");
        private BrainstormDraft.Block proposed;

        BlockRow(Mood.Field field) {
            this.field = field;
            box.setMaxWidth(Double.MAX_VALUE);
            box.getItems().add(I18nManager.getText(NONE_ITEM_KEY));
            box.getItems().addAll(names());
            box.setValue(box.getItems().get(0));
            nameField.setPromptText(I18nManager.getText("block.dialog.name.placeholder"));
            textLabel.setWrapText(true);
            textLabel.getStyleClass().addAll("text-muted", "text-small");
            VBox newBox = new VBox(4.0, nameField, field == Mood.Field.COLOR ? colorBoxes : textLabel);
            newBox.setPadding(new Insets(0, 0, 0, 12.0));
            newBox.managedProperty().bind(newBox.visibleProperty());
            newBox.setVisible(false);
            if (locked()) {
                check.setDisable(true);
                check.setTooltip(new Tooltip(I18nManager.getText("brainstorm.block.locked")));
            }

            String typeKey = field == Mood.Field.COLOR ? "palette" : field.name().toLowerCase(Locale.ROOT);
            Label title = new Label(I18nManager.getText("block.type." + typeKey));
            title.getStyleClass().addAll("text-bold", "text-small");
            HBox line = new HBox(8.0, box, check);
            line.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(box, Priority.ALWAYS);
            node = new VBox(3.0, title, line, newBox);
            box.valueProperty().addListener((obs, old, value) -> {
                boolean isNewItem = newItem.equals(value);
                newBox.setVisible(isNewItem);
                check.setText(isNewItem ? I18nManager.getText("brainstorm.block.mylist") : "");
                check.setSelected(!isNone() && !locked());
            });
        }

        /** The names of the entries of the list of the field. */
        List<String> names() {
            return field == Mood.Field.COLOR ? context.colorStyles().stream().map(ColorStyle::getName).toList()
                    : context.blocks(field).stream().map(PromptBlock::getName).toList();
        }

        private boolean locked() {
            return context.locked().contains(field);
        }

        private boolean isNone() {
            return box.getValue() == null || I18nManager.getText(NONE_ITEM_KEY).equals(box.getValue());
        }

        /**
         * Shows a block: an entry of the list by id, else by name, or a new block; "None" for null or an entry that
         * is not listed. The checkbox is ticked for a block, unless the field is locked.
         */
        void show(BrainstormDraft.Block block) {
            box.getItems().remove(newItem);
            proposed = block != null && block.isNew() && (field == Mood.Field.COLOR ? !block.colors().isEmpty()
                    : field == Mood.Field.CAMERA ? block.settings() != null : block.text() != null) ? block : null;
            String value = I18nManager.getText(NONE_ITEM_KEY);
            if (proposed != null) {
                box.getItems().add(1, newItem);
                nameField.setText(proposed.name());

                /*  a new camera shows the fragment of its settings  */
                textLabel.setText(proposed.settings() != null ? CameraPromptBuilder.build(proposed.settings(), true)
                        : proposed.text());
                colorBoxes.getChildren().setAll(proposed.colors().stream().map(ColorBox::new).toList());
                value = newItem;
            }
            else if (block != null && !block.isNew()) {
                String name = context.nameOf(field, block.id());
                name = name != null ? name : block.name();
                if (box.getItems().contains(name))
                    value = name;
            }
            box.setValue(value);
            check.setSelected(!isNone() && !locked());
        }

        /** The block to adopt, null if the checkbox is off or the row shows "None". */
        BrainstormDraft.Block adopted() {
            if (!check.isSelected() || isNone())
                return null;
            if (newItem.equals(box.getValue()))
                return new BrainstormDraft.Block(null, nameField.getText().trim(), proposed.text(), proposed.colors(),
                        proposed.settings(), true);
            return new BrainstormDraft.Block(context.idOf(field, box.getValue()), box.getValue(), null, List.of(),
                    false);
        }
    }
}
