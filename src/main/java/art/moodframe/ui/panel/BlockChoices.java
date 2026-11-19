package art.moodframe.ui.panel;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.ui.cell.ColorStyleListCell;
import art.moodframe.ui.cell.StylePresetListCell;
import art.moodframe.ui.component.BlockChoice;
import art.moodframe.ui.component.ColorStyleItem;
import art.moodframe.ui.component.FavoriteStar;
import art.moodframe.ui.component.StylePresetItem;
import art.moodframe.ui.dialog.BlockEditDialog;
import art.moodframe.ui.dialog.BlockManageDialog;
import art.moodframe.ui.dialog.CameraEditDialog;
import art.moodframe.ui.dialog.ColorStyleEditDialog;
import art.moodframe.ui.dialog.TrashWindow;
import art.moodframe.ui.support.StatusSink;
import art.moodframe.util.I18nManager;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The building block fields of the group Mood: style, atmosphere, light and camera from the text blocks, the color
 * from the palettes, each with "None", the favorites first and the actions "Edit", "New" and "Manage", and their
 * locks; the dialogs that edit and manage the blocks and palettes of the user.
 */
final class BlockChoices {

    private static final Logger logger = LoggerFactory.getLogger(BlockChoices.class);

    /**
     * The nodes of {@code MoodFramePanel.fxml} the part works on.
     */
    record Nodes(ComboBox<StylePresetItem> atmosphereComboBox, ToggleButton atmosphereLockButton,
                 ComboBox<StylePresetItem> stylePresetsComboBox, ToggleButton styleLockButton,
                 ComboBox<ColorStyleItem> colorStyleComboBox, ToggleButton colorLockButton,
                 ComboBox<StylePresetItem> lightComboBox, ToggleButton lightLockButton,
                 ComboBox<StylePresetItem> cameraComboBox, ToggleButton cameraLockButton) {
    }

    private final ComboBox<StylePresetItem> atmosphereComboBox;
    private final ToggleButton atmosphereLockButton;
    private final ComboBox<StylePresetItem> stylePresetsComboBox;
    private final ToggleButton styleLockButton;
    private final ComboBox<ColorStyleItem> colorStyleComboBox;
    private final ToggleButton colorLockButton;
    private final ComboBox<StylePresetItem> lightComboBox;
    private final ToggleButton lightLockButton;
    private final ComboBox<StylePresetItem> cameraComboBox;
    private final ToggleButton cameraLockButton;

    /*  The lists of the text blocks by their type and the list of the palettes  */
    private final Map<PromptBlock.Type, BlockChoice<StylePresetItem>> blockChoices =
            new EnumMap<>(PromptBlock.Type.class);
    private BlockChoice<ColorStyleItem> colorChoice;

    private ApplicationConfig config;
    private PromptBlockService blockService;
    private ColorStyleService colorService;
    private FavoriteService favoriteService;
    private MoodService moodService;
    private TrashWindow.Opener trash;
    private PanelWorker writes;
    private StatusSink status;
    private Supplier<Window> window;
    private Runnable changed = () -> { };

    /*  the favorites switched in the lists whose write the worker has not done yet: shown as switched meanwhile  */
    private final Map<FavoriteService.Kind, Map<String, Boolean>> switching =
            new EnumMap<>(FavoriteService.Kind.class);

    /**
     * Takes the nodes of the part.
     *
     * @param nodes the nodes
     */
    BlockChoices(Nodes nodes) {
        this.atmosphereComboBox = nodes.atmosphereComboBox();
        this.atmosphereLockButton = nodes.atmosphereLockButton();
        this.stylePresetsComboBox = nodes.stylePresetsComboBox();
        this.styleLockButton = nodes.styleLockButton();
        this.colorStyleComboBox = nodes.colorStyleComboBox();
        this.colorLockButton = nodes.colorLockButton();
        this.lightComboBox = nodes.lightComboBox();
        this.lightLockButton = nodes.lightLockButton();
        this.cameraComboBox = nodes.cameraComboBox();
        this.cameraLockButton = nodes.cameraLockButton();
    }

    /**
     * Fills the lists and sets up the locks.
     *
     * @param config       remembers the locks (may be null)
     * @param blockService the text blocks (may be null: only "None")
     * @param colorService the palettes (may be null: only "None")
     * @param favorites    the favorites, listed first (may be null)
     * @param moods        names the moods that use a block before it is deleted (may be null)
     * @param trash        opens the trash on the blocks (may be null)
     * @param writes       stores the blocks, palettes and favorites off the JavaFX thread
     * @param status       shows what the part did
     * @param window       the window of the panel, owner of the dialogs
     * @param changed      learns of a change that the selection of a list does not report
     */
    void setUp(ApplicationConfig config, PromptBlockService blockService, ColorStyleService colorService,
               FavoriteService favorites, MoodService moods, TrashWindow.Opener trash, PanelWorker writes,
               StatusSink status, Supplier<Window> window, Runnable changed) {
        this.config = config;
        this.blockService = blockService;
        this.colorService = colorService;
        this.favoriteService = favorites;
        this.moodService = moods;
        this.trash = trash;
        this.writes = writes;
        this.status = status;
        this.window = window;
        this.changed = changed;
        setupBlockChoices();
        setupBlockLocks();
    }

    /**
     * The list of a type of text blocks.
     *
     * @param type the type
     * @return the list
     */
    BlockChoice<StylePresetItem> choice(PromptBlock.Type type) {
        return blockChoices.get(type);
    }

    /** @return the list of the palettes, null before the set-up */
    BlockChoice<ColorStyleItem> colorChoice() {
        return colorChoice;
    }

    /** Relabels the actions of the lists in the current language. */
    void updateTexts() {
        blockChoices.values().forEach(BlockChoice::updateTexts);
        if (colorChoice != null)
            colorChoice.updateTexts();
    }

    /**
     * The reloads of the lists in the order of the favorites, each keeping its choice, by their fields.
     *
     * @return the reload of each list by its field
     */
    Map<ComboBox<?>, Runnable> reloads() {
        Map<ComboBox<?>, Runnable> reloads = new LinkedHashMap<>();
        blockChoices.keySet().forEach((type) -> reloads.put(blockComboBox(type), () -> {
            ComboBox<StylePresetItem> comboBox = blockComboBox(type);
            fillBlockChoice(type, comboBox.getValue() != null ? comboBox.getValue().getName() : null);
        }));
        if (colorChoice != null)
            reloads.put(colorStyleComboBox, () -> fillColorChoice(colorStyleComboBox.getValue() != null
                    ? colorStyleComboBox.getValue().getName() : null));
        return reloads;
    }

    /**
     * Sets up the building block lists: style, atmosphere, light and camera from the blocks, the color from the
     * palettes. Each list holds "None" (the default) and the entries, the styles grouped into photo, art and layout;
     * its actions "Edit", "New" and "Manage" sit as icons left of its lock (see
     * {@link BlockChoice}).
     */
    private void setupBlockChoices() {
        Map<PromptBlock.Type, ToggleButton> locks =
                Map.of(PromptBlock.Type.STYLE, styleLockButton,
                        PromptBlock.Type.ATMOSPHERE, atmosphereLockButton,
                        PromptBlock.Type.LIGHT, lightLockButton,
                        PromptBlock.Type.CAMERA, cameraLockButton);
        locks.forEach((type, lock) -> {
            ComboBox<StylePresetItem> comboBox = blockComboBox(type);
            BlockChoice<StylePresetItem> choice =
                    new BlockChoice<>(comboBox, lock,
                            (name) -> new StylePresetItem(name, "", false),
                            (item) -> item.getBlock() != null,
                            new BlockChoice.Actions<>() {
                                @Override
                                public void edit(StylePresetItem entry) {
                                    editBlock(type, entry.getBlock());
                                }

                                @Override
                                public void create() {
                                    editBlock(type, null);
                                }

                                @Override
                                public void manage() {
                                    manageBlocks(type);
                                }
                            });
            FavoriteStar.Favorites<StylePresetItem> favorites = favorites(comboBox,
                    FavoriteService.Kind.of(type), BlockChoices::blockId);
            comboBox.setCellFactory((listView) -> new StylePresetListCell(choice,
                    favorites));
            comboBox.setButtonCell(new StylePresetListCell());
            blockChoices.put(type, choice);
            fillBlockChoice(type, null);
        });

        colorChoice = new BlockChoice<>(colorStyleComboBox, colorLockButton,
                (name) -> new ColorStyleItem(name, Collections.emptyList(), true),
                (item) -> !"None".equals(item.getName()),
                new BlockChoice.Actions<>() {
                    @Override
                    public void edit(ColorStyleItem entry) {
                        editPalette(entry);
                    }

                    @Override
                    public void create() {
                        editPalette(null);
                    }

                    @Override
                    public void manage() {
                        managePalettes();
                    }
                });
        FavoriteStar.Favorites<ColorStyleItem> paletteFavorites = favorites(colorStyleComboBox,
                FavoriteService.Kind.COLOR, ColorStyleItem::getId);
        colorStyleComboBox.setCellFactory((listView) -> new ColorStyleListCell(
                paletteFavorites));
        colorStyleComboBox.setButtonCell(new ColorStyleListCell());
        fillColorChoice(null);
    }

    /**
     * Fills the list of a text block type with "None" and the blocks: the favorites first, then the others, the styles
     * ordered by their groups behind a group of the favorites; each part alphabetically.
     *
     * @param type the type of the blocks
     * @param name the name of the block to select, null for "None"
     */
    void fillBlockChoice(PromptBlock.Type type, String name) {
        List<StylePresetItem> entries = new ArrayList<>();
        entries.add(new StylePresetItem("None", "", false));
        PromptBlockService service = blockService;
        boolean grouped = type == PromptBlock.Type.STYLE;
        Predicate<StylePresetItem> favorite = (item) -> item.getBlock() != null
                && isFavorite(FavoriteService.Kind.of(type), item.getBlock().getId());
        if (service == null)
            logger.warn("PromptBlockService not available, {} list only offers None", type);
        else
            entries.addAll(FavoriteService.order(service.getAll(type).stream()
                    .map(StylePresetItem::new).toList(), favorite,
                    (item) -> grouped ? STYLE_GROUPS.indexOf(styleGroupKey(item)) : 0,
                    StylePresetItem::getName));
        blockChoices.get(type).setEntries(entries, grouped ? (item) -> favorite.test(item) ? "block.group.favorites"
                : styleGroupKey(item) : null, name);
    }

    /*  the id of the block of a list item  */
    static String blockId(StylePresetItem item) {
        return item.getBlock().getId();
    }

    /*  The headers of the style groups: the two media photo and art, then layout  */
    private static final List<String> STYLE_GROUPS = List.of("block.group.photo", "block.group.art",
            "block.group.layout");

    /** The header of the group of a style; a style of the user without group goes by its photographic flag. */
    private static String styleGroupKey(StylePresetItem item) {
        PromptBlock block = item.getBlock();
        if (block == null)
            return null;
        return block.isLayout() ? "block.group.layout" : block.isPhotographic() ? "block.group.photo" : "block.group.art";
    }

    /**
     * Fills the color list with "None" and the palettes: the favorites first, then the others, each part
     * alphabetically.
     *
     * @param name the name of the palette to select, null for "None"
     */
    void fillColorChoice(String name) {
        List<ColorStyleItem> entries = new ArrayList<>();
        entries.add(new ColorStyleItem("None", Collections.emptyList(), true));
        ColorStyleService service = colorService;
        if (service == null)
            logger.warn("ColorStyleService not available, color list only offers None");
        else
            entries.addAll(FavoriteService.order(service.getAllStyles().stream()
                    .map(ColorStyleItem::of).toList(),
                    (item) -> isFavorite(FavoriteService.Kind.COLOR, item.getId()), (item) -> 0,
                    ColorStyleItem::getName));
        colorChoice.setEntries(entries, null, name);
    }

    /**
     * Loads all building block lists again, e.g. after an import or a restore from the trash; each keeps its
     * selection while its entry exists.
     */
    void refreshBlockChoices() {
        blockChoices.forEach((type, choice) -> {
            ComboBox<StylePresetItem> comboBox = blockComboBox(type);
            fillBlockChoice(type, comboBox.getValue() != null ? comboBox.getValue().getName() : null);
        });
        if (colorChoice != null)
            fillColorChoice(colorStyleComboBox.getValue() != null ? colorStyleComboBox.getValue().getName() : null);
    }

    /**
     * The favorites of a list, for the stars of its rows; a switched favorite shows at once and is stored by the
     * worker, and the lists follow its report of the change ({@code MoodFramePanelController.reorderFavorites}).
     *
     * @param field the field whose open list shows the rows, null for a list in a dialog
     * @param kind  the kind of the entries
     * @param id    tells the id of an entry
     */
    <T> FavoriteStar.Favorites<T> favorites(ComboBox<?> field, FavoriteService.Kind kind,
            Function<T, String> id) {
        return new FavoriteStar.Favorites<>(field, (entry) -> isFavorite(kind, id.apply(entry)),
                (entry) -> toggleFavorite(kind, id.apply(entry)));
    }

    /*  switches a favorite: shown as switched until the worker stored it, or failed to  */
    private void toggleFavorite(FavoriteService.Kind kind, String id) {
        FavoriteService service = favoriteService;
        if (service == null)
            return;
        boolean favorite = !isFavorite(kind, id);
        Map<String, Boolean> pending = switching.computeIfAbsent(kind, (key) -> new HashMap<>());
        pending.put(id, favorite);
        writes.run(() -> service.toggle(kind, id), !favorite, I18nManager.getText("favorite.error"), (now) -> {
            if (now != favorite)
                status.failure(I18nManager.getText("favorite.error"));
        }).whenComplete((now, failure) -> pending.remove(id, favorite));
    }

    /** Whether an entry is a favorite of the user, as switched in the lists; none without the service. */
    boolean isFavorite(FavoriteService.Kind kind, String id) {
        FavoriteService service = favoriteService;
        if (service == null || id == null)
            return false;
        Boolean switched = switching.getOrDefault(kind, Map.of()).get(id);
        return switched != null ? switched : service.isFavorite(kind, id);
    }

    ComboBox<StylePresetItem> blockComboBox(PromptBlock.Type type) {
        return switch (type) {
            case STYLE -> stylePresetsComboBox;
            case ATMOSPHERE -> atmosphereComboBox;
            case LIGHT -> lightComboBox;
            case CAMERA -> cameraComboBox;
        };
    }

    /**
     * Creates or edits a text building block in its dialog and selects the stored block. A block of the user is
     * saved in place or as a new block, a standard block only as a new one, which keeps the group of its origin.
     * A camera has a dialog of its own (see {@link #editCamera}). The worker stores the block.
     *
     * @param type  the type of the block
     * @param block the block to edit, null for a new one
     * @return completes on the JavaFX thread with the id of the stored block, null if none was stored
     */
    CompletableFuture<String> editBlock(PromptBlock.Type type, PromptBlock block) {
        PromptBlockService service = blockService;
        if (service == null)
            return CompletableFuture.completedFuture(null);
        if (type == PromptBlock.Type.CAMERA)
            return editCamera(service, block);

        BlockEditDialog dialog = new BlockEditDialog(type, block,
                blockService);
        dialog.initOwner(activeWindow());
        BlockEditDialog.Result result = dialog.showAndWait().orElse(null);
        if (result == null)
            return CompletableFuture.completedFuture(null);
        boolean inPlace = block != null && result.save() == BlockEditDialog.Save.SAVE;
        PromptBlock stored = new PromptBlock(
                inPlace ? block.getId() : null, type, result.name(), result.text(),
                block != null ? block.getGroup() : null, result.photographic());
        if (block != null) {
            stored.setParams(block.getParams());
            stored.setSortOrder(block.getSortOrder());
        }
        return storeBlock(service, stored, inPlace, result.save(), () -> fillBlockChoice(type, stored.getName()));
    }

    /**
     * Creates or edits a camera in its dialog of settings and selects the stored camera. A camera of the user is saved
     * in place or as a new one, a standard camera only as a new one. The worker stores the camera.
     *
     * @param service the building blocks
     * @param block   the camera to edit, null for a new one
     * @return completes on the JavaFX thread with the id of the stored camera, null if none was stored
     */
    private CompletableFuture<String> editCamera(PromptBlockService service, PromptBlock block) {
        CameraEditDialog dialog = new CameraEditDialog(block,
                blockService);
        dialog.initOwner(activeWindow());
        CameraEditDialog.Result result = dialog.showAndWait().orElse(null);
        if (result == null)
            return CompletableFuture.completedFuture(null);
        boolean inPlace = block != null && result.save() == BlockEditDialog.Save.SAVE;
        PromptBlock stored = PromptBlockService.camera(
                inPlace ? block.getId() : null, result.name(), result.settings());
        if (block != null)
            stored.setSortOrder(block.getSortOrder());
        return storeBlock(service, stored, inPlace, result.save(), () -> {
            fillBlockChoice(PromptBlock.Type.CAMERA, stored.getName());

            /*  a camera changed in place keeps its name, so the list reports no change of the selection  */
            changed.run();
        });
    }

    /*  stores a block of the user on the worker: in place, as a new block or as a copy; then shows it  */
    private CompletableFuture<String> storeBlock(PromptBlockService service,
                                                 PromptBlock stored, boolean inPlace,
                                                 BlockEditDialog.Save save, Runnable show) {
        String failure = I18nManager.getText("block.error.save", stored.getName());
        return writes.run(() -> inPlace ? service.updateCustom(stored)
                : save == BlockEditDialog.Save.SAVE ? service.addCustom(stored)
                : service.addCopy(stored), false, failure, (saved) -> {
            if (saved)
                show.run();
            else
                status.status(failure);
        }).thenApply((saved) -> saved ? stored.getId() : null);
    }

    /**
     * Creates or edits a palette in its dialog and selects the stored palette. A palette of the user is saved in
     * place or as a new one, a default palette only as a new one. The worker stores the palette.
     *
     * @param item the palette to edit, null for a new one
     * @return completes on the JavaFX thread with the id of the stored palette, null if none was stored
     */
    CompletableFuture<String> editPalette(ColorStyleItem item) {
        ColorStyleService service = colorService;
        if (service == null)
            return CompletableFuture.completedFuture(null);

        ColorStyle palette = item != null ? service.getStyleByName(item.getName()) : null;
        ColorStyleEditDialog dialog = new ColorStyleEditDialog(palette, colorService);
        dialog.initOwner(activeWindow());
        ColorStyleEditDialog.ColorStyleEditResult result = dialog.showAndWait().orElse(null);
        if (result == null)
            return CompletableFuture.completedFuture(null);
        ColorStyle stored = new ColorStyle(result.name(), result.colors(),
                false);
        boolean save = result.save() == BlockEditDialog.Save.SAVE;
        String failure = I18nManager.getText("block.error.save", result.name());
        return writes.run(() -> palette != null && save ? service.updateCustomStyle(palette.getName(), stored)
                : save ? service.addCustomStyle(stored) : service.addCopy(stored), false, failure, (saved) -> {
            if (saved)
                fillColorChoice(stored.getName());
            else
                status.status(failure);
        }).thenApply((saved) -> {
            if (!saved)
                return null;
            ColorStyle now = service.getStyleByName(stored.getName());
            return now != null ? now.getId() : "";
        });
    }

    /*  the focused window of the app (a manage dialog, if open), so a nested dialog opens on top of it  */
    private Window activeWindow() {
        return Window.getWindows().stream().filter(Window::isFocused).findFirst()
                .orElse(window.get());
    }

    /**
     * Opens the "Manage…" dialog for the text blocks of the user of a type; the lists follow its changes.
     *
     * @param type the type of the blocks
     */
    private void manageBlocks(PromptBlock.Type type) {
        PromptBlockService service = blockService;
        if (service == null)
            return;

        String typeKey = type.name().toLowerCase(Locale.ROOT);
        new BlockManageDialog<>(window.get(),
                I18nManager.getText("block.manage.title." + typeKey), "moodframe-" + typeKey + ".json",
                new BlockManageDialog.Library<StylePresetItem>() {
                    @Override
                    public List<StylePresetItem> list() {
                        return service.getCustom(type).stream().map(StylePresetItem::new).toList();
                    }

                    @Override
                    public String id(StylePresetItem entry) {
                        return entry.getBlock().getId();
                    }

                    @Override
                    public String name(StylePresetItem entry) {
                        return entry.getName();
                    }

                    @Override
                    public CompletableFuture<Boolean> delete(StylePresetItem entry) {
                        String id = id(entry);
                        return writes.call(() -> service.deleteCustom(id));
                    }

                    @Override
                    public CompletableFuture<Boolean> reorder(List<StylePresetItem> entries) {
                        List<String> ids = entries.stream().map(this::id).toList();
                        return writes.call(() -> service.reorderCustom(type, ids));
                    }

                    @Override
                    public void exportTo(File file) throws IOException {
                        service.exportCustom(type, file);
                    }

                    @Override
                    public CompletableFuture<Integer> importFrom(File file) {
                        return writes.call(() -> service.importCustom(type, file));
                    }

                    @Override
                    public CompletableFuture<String> create() {
                        return editBlock(type, null);
                    }

                    @Override
                    public CompletableFuture<String> edit(StylePresetItem entry) {
                        return editBlock(type, entry.getBlock());
                    }

                    @Override
                    public boolean editable() {
                        return true;
                    }
                }, (listView) -> new StylePresetListCell(null, favorites(null,
                        FavoriteService.Kind.of(type), BlockChoices::blockId)), moodService, trash)
                .showAndWait();
    }

    /**
     * Opens the "Manage…" dialog for the palettes of the user; the lists follow its changes.
     */
    private void managePalettes() {
        ColorStyleService service = colorService;
        if (service == null)
            return;

        new BlockManageDialog<>(window.get(),
                I18nManager.getText("block.manage.title.palette"), "moodframe-palettes.json",
                new BlockManageDialog.Library<ColorStyleItem>() {
                    @Override
                    public List<ColorStyleItem> list() {
                        return service.getCustomStyles().stream()
                                .map(ColorStyleItem::of).toList();
                    }

                    @Override
                    public String id(ColorStyleItem entry) {
                        ColorStyle palette = service.getStyleByName(entry.getName());
                        return palette != null ? palette.getId() : "";
                    }

                    @Override
                    public String name(ColorStyleItem entry) {
                        return entry.getName();
                    }

                    @Override
                    public CompletableFuture<Boolean> delete(ColorStyleItem entry) {
                        return writes.call(() -> service.deleteCustomStyle(entry.getName()));
                    }

                    @Override
                    public CompletableFuture<Boolean> reorder(List<ColorStyleItem> entries) {
                        return writes.call(() -> service.reorderCustomStyles(entries.stream().map(this::id).toList()));
                    }

                    @Override
                    public void exportTo(File file) throws IOException {
                        if (!service.exportStyles(file))
                            throw new IOException("Failed to write " + file);
                    }

                    @Override
                    public CompletableFuture<Integer> importFrom(File file) {
                        return writes.call(() -> service.importStyles(file));
                    }

                    @Override
                    public CompletableFuture<String> create() {
                        return editPalette(null);
                    }

                    @Override
                    public CompletableFuture<String> edit(ColorStyleItem entry) {
                        return editPalette(entry);
                    }

                    @Override
                    public boolean editable() {
                        return true;
                    }
                }, (listView) -> new ColorStyleListCell(favorites(null,
                        FavoriteService.Kind.COLOR, ColorStyleItem::getId)), moodService, trash)
                .showAndWait();
    }

    /**
     * Sets up the locks of the building block fields, remembered. A locked field
     * keeps its choice when a mood or the brainstorm sets the fields; the lock shows while the pointer is over the
     * field, and always once locked (see main-window.css).
     */
    private void setupBlockLocks() {
        Map<ToggleButton, String> locks = Map.of(atmosphereLockButton, "atmosphere",
                styleLockButton, "style", colorLockButton, "palette", lightLockButton, "light", cameraLockButton,
                "camera");
        locks.forEach((lock, field) -> {
            lock.selectedProperty().addListener((obs, oldVal, newVal) -> {
                ((FontIcon) lock.getGraphic()).setIconLiteral(newVal ? "mdi2l-lock" : "mdi2l-lock-open-variant-outline");
                if (config != null)
                    config.ui().setBlockLocked(field, newVal);
            });
            lock.setSelected(config != null && config.ui().isBlockLocked(field));
        });
    }
}
