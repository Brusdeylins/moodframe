package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.Mood;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.ui.component.FavoriteStar;
import art.moodframe.ui.dialog.TrashWindow;
import art.moodframe.util.I18nManager;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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
    record Nodes(ComboBox<art.moodframe.model.StylePresetItem> atmosphereComboBox, ToggleButton atmosphereLockButton,
                 ComboBox<art.moodframe.model.StylePresetItem> stylePresetsComboBox, ToggleButton styleLockButton,
                 ComboBox<art.moodframe.model.ColorStyleItem> colorStyleComboBox, ToggleButton colorLockButton,
                 ComboBox<art.moodframe.model.StylePresetItem> lightComboBox, ToggleButton lightLockButton,
                 ComboBox<art.moodframe.model.StylePresetItem> cameraComboBox, ToggleButton cameraLockButton) {
    }

    private final ComboBox<art.moodframe.model.StylePresetItem> atmosphereComboBox;
    private final ToggleButton atmosphereLockButton;
    private final ComboBox<art.moodframe.model.StylePresetItem> stylePresetsComboBox;
    private final ToggleButton styleLockButton;
    private final ComboBox<art.moodframe.model.ColorStyleItem> colorStyleComboBox;
    private final ToggleButton colorLockButton;
    private final ComboBox<art.moodframe.model.StylePresetItem> lightComboBox;
    private final ToggleButton lightLockButton;
    private final ComboBox<art.moodframe.model.StylePresetItem> cameraComboBox;
    private final ToggleButton cameraLockButton;

    /*  The lists of the text blocks by their type and the list of the palettes  */
    private final Map<art.moodframe.model.PromptBlock.Type,
            art.moodframe.ui.component.BlockChoice<art.moodframe.model.StylePresetItem>> blockChoices =
            new java.util.EnumMap<>(art.moodframe.model.PromptBlock.Type.class);
    private art.moodframe.ui.component.BlockChoice<art.moodframe.model.ColorStyleItem> colorChoice;

    private ApplicationConfig config;
    private PromptBlockService blockService;
    private ColorStyleService colorService;
    private FavoriteService favoriteService;
    private MoodService moodService;
    private TrashWindow.Opener trash;
    private StatusSink status;
    private Supplier<Window> window;
    private Runnable changed = () -> { };

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
     * @param status       shows what the part did
     * @param window       the window of the panel, owner of the dialogs
     * @param changed      learns of a change that the selection of a list does not report
     */
    void setUp(ApplicationConfig config, PromptBlockService blockService, ColorStyleService colorService,
               FavoriteService favorites, MoodService moods, TrashWindow.Opener trash, StatusSink status,
               Supplier<Window> window, Runnable changed) {
        this.config = config;
        this.blockService = blockService;
        this.colorService = colorService;
        this.favoriteService = favorites;
        this.moodService = moods;
        this.trash = trash;
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
    art.moodframe.ui.component.BlockChoice<art.moodframe.model.StylePresetItem> choice(
            art.moodframe.model.PromptBlock.Type type) {
        return blockChoices.get(type);
    }

    /** @return the list of the palettes, null before the set-up */
    art.moodframe.ui.component.BlockChoice<art.moodframe.model.ColorStyleItem> colorChoice() {
        return colorChoice;
    }

    /** Relabels the actions of the lists in the current language. */
    void updateTexts() {
        blockChoices.values().forEach(art.moodframe.ui.component.BlockChoice::updateTexts);
        if (colorChoice != null)
            colorChoice.updateTexts();
    }

    /**
     * The reloads of the lists in the order of the favorites, each keeping its choice, by their fields.
     *
     * @return the reload of each list by its field
     */
    Map<ComboBox<?>, Runnable> reloads() {
        Map<ComboBox<?>, Runnable> reloads = new java.util.LinkedHashMap<>();
        blockChoices.keySet().forEach((type) -> reloads.put(blockComboBox(type), () -> {
            ComboBox<art.moodframe.model.StylePresetItem> comboBox = blockComboBox(type);
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
     * {@link art.moodframe.ui.component.BlockChoice}).
     */
    private void setupBlockChoices() {
        java.util.Map<art.moodframe.model.PromptBlock.Type, ToggleButton> locks =
                java.util.Map.of(art.moodframe.model.PromptBlock.Type.STYLE, styleLockButton,
                        art.moodframe.model.PromptBlock.Type.ATMOSPHERE, atmosphereLockButton,
                        art.moodframe.model.PromptBlock.Type.LIGHT, lightLockButton,
                        art.moodframe.model.PromptBlock.Type.CAMERA, cameraLockButton);
        locks.forEach((type, lock) -> {
            ComboBox<art.moodframe.model.StylePresetItem> comboBox = blockComboBox(type);
            art.moodframe.ui.component.BlockChoice<art.moodframe.model.StylePresetItem> choice =
                    new art.moodframe.ui.component.BlockChoice<>(comboBox, lock,
                            (name) -> new art.moodframe.model.StylePresetItem(name, "", false),
                            (item) -> item.getBlock() != null,
                            new art.moodframe.ui.component.BlockChoice.Actions<>() {
                                @Override
                                public void edit(art.moodframe.model.StylePresetItem entry) {
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
            FavoriteStar.Favorites<art.moodframe.model.StylePresetItem> favorites = favorites(comboBox,
                    FavoriteService.Kind.of(type), BlockChoices::blockId);
            comboBox.setCellFactory((listView) -> new art.moodframe.ui.cell.StylePresetListCell(choice,
                    favorites));
            comboBox.setButtonCell(new art.moodframe.ui.cell.StylePresetListCell());
            blockChoices.put(type, choice);
            fillBlockChoice(type, null);
        });

        colorChoice = new art.moodframe.ui.component.BlockChoice<>(colorStyleComboBox, colorLockButton,
                (name) -> new art.moodframe.model.ColorStyleItem(name, java.util.Collections.emptyList(), true),
                (item) -> !"None".equals(item.getName()),
                new art.moodframe.ui.component.BlockChoice.Actions<>() {
                    @Override
                    public void edit(art.moodframe.model.ColorStyleItem entry) {
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
        FavoriteStar.Favorites<art.moodframe.model.ColorStyleItem> paletteFavorites = favorites(colorStyleComboBox,
                FavoriteService.Kind.COLOR, art.moodframe.model.ColorStyleItem::getId);
        colorStyleComboBox.setCellFactory((listView) -> new art.moodframe.ui.cell.ColorStyleListCell(
                paletteFavorites));
        colorStyleComboBox.setButtonCell(new art.moodframe.ui.cell.ColorStyleListCell());
        fillColorChoice(null);
    }

    /**
     * Fills the list of a text block type with "None" and the blocks: the favorites first, then the others, the styles
     * ordered by their groups behind a group of the favorites; each part alphabetically.
     *
     * @param type the type of the blocks
     * @param name the name of the block to select, null for "None"
     */
    void fillBlockChoice(art.moodframe.model.PromptBlock.Type type, String name) {
        List<art.moodframe.model.StylePresetItem> entries = new java.util.ArrayList<>();
        entries.add(new art.moodframe.model.StylePresetItem("None", "", false));
        art.moodframe.service.library.PromptBlockService service = blockService;
        boolean grouped = type == art.moodframe.model.PromptBlock.Type.STYLE;
        java.util.function.Predicate<art.moodframe.model.StylePresetItem> favorite = (item) -> item.getBlock() != null
                && isFavorite(FavoriteService.Kind.of(type), item.getBlock().getId());
        if (service == null)
            logger.warn("PromptBlockService not available, {} list only offers None", type);
        else
            entries.addAll(FavoriteService.order(service.getAll(type).stream()
                    .map(art.moodframe.model.StylePresetItem::new).toList(), favorite,
                    (item) -> grouped ? STYLE_GROUPS.indexOf(styleGroupKey(item)) : 0,
                    art.moodframe.model.StylePresetItem::getName));
        blockChoices.get(type).setEntries(entries, grouped ? (item) -> favorite.test(item) ? "block.group.favorites"
                : styleGroupKey(item) : null, name);
    }

    /*  the id of the block of a list item  */
    static String blockId(art.moodframe.model.StylePresetItem item) {
        return item.getBlock().getId();
    }

    /*  The headers of the style groups: the two media photo and art, then layout  */
    private static final List<String> STYLE_GROUPS = List.of("block.group.photo", "block.group.art",
            "block.group.layout");

    /** The header of the group of a style; a style of the user without group goes by its photographic flag. */
    private static String styleGroupKey(art.moodframe.model.StylePresetItem item) {
        art.moodframe.model.PromptBlock block = item.getBlock();
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
        List<art.moodframe.model.ColorStyleItem> entries = new java.util.ArrayList<>();
        entries.add(new art.moodframe.model.ColorStyleItem("None", java.util.Collections.emptyList(), true));
        art.moodframe.service.library.ColorStyleService service = colorService;
        if (service == null)
            logger.warn("ColorStyleService not available, color list only offers None");
        else
            entries.addAll(FavoriteService.order(service.getAllStyles().stream()
                    .map(art.moodframe.model.ColorStyle::toColorStyleItem).toList(),
                    (item) -> isFavorite(FavoriteService.Kind.COLOR, item.getId()), (item) -> 0,
                    art.moodframe.model.ColorStyleItem::getName));
        colorChoice.setEntries(entries, null, name);
    }

    /**
     * Loads all building block lists again, e.g. after an import or a restore from the trash; each keeps its
     * selection while its entry exists.
     */
    void refreshBlockChoices() {
        blockChoices.forEach((type, choice) -> {
            ComboBox<art.moodframe.model.StylePresetItem> comboBox = blockComboBox(type);
            fillBlockChoice(type, comboBox.getValue() != null ? comboBox.getValue().getName() : null);
        });
        if (colorChoice != null)
            fillColorChoice(colorStyleComboBox.getValue() != null ? colorStyleComboBox.getValue().getName() : null);
    }

    /**
     * The favorites of a list, for the stars of its rows; a switched favorite is stored at once, and the lists follow
     * it ({@link #reorderFavorites}).
     *
     * @param field the field whose open list shows the rows, null for a list in a dialog
     * @param kind  the kind of the entries
     * @param id    tells the id of an entry
     */
    <T> FavoriteStar.Favorites<T> favorites(ComboBox<?> field, FavoriteService.Kind kind,
            java.util.function.Function<T, String> id) {
        return new FavoriteStar.Favorites<>(field, (entry) -> isFavorite(kind, id.apply(entry)), (entry) -> {
            FavoriteService service = favoriteService;
            if (service != null)
                service.toggle(kind, id.apply(entry));
        });
    }

    /** Whether an entry is a favorite of the user; none without the service. */
    boolean isFavorite(FavoriteService.Kind kind, String id) {
        FavoriteService service = favoriteService;
        return service != null && service.isFavorite(kind, id);
    }

    ComboBox<art.moodframe.model.StylePresetItem> blockComboBox(art.moodframe.model.PromptBlock.Type type) {
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
     * A camera has a dialog of its own (see {@link #editCamera}).
     *
     * @param type  the type of the block
     * @param block the block to edit, null for a new one
     */
    String editBlock(art.moodframe.model.PromptBlock.Type type, art.moodframe.model.PromptBlock block) {
        art.moodframe.service.library.PromptBlockService service = blockService;
        if (service == null)
            return null;
        if (type == art.moodframe.model.PromptBlock.Type.CAMERA)
            return editCamera(service, block);

        art.moodframe.ui.dialog.BlockEditDialog dialog = new art.moodframe.ui.dialog.BlockEditDialog(type, block,
                blockService);
        dialog.initOwner(activeWindow());
        String[] storedId = { null };
        dialog.showAndWait().ifPresent((result) -> {
            boolean inPlace = block != null && result.save() == art.moodframe.ui.dialog.BlockEditDialog.Save.SAVE;
            art.moodframe.model.PromptBlock stored = new art.moodframe.model.PromptBlock(
                    inPlace ? block.getId() : null, type, result.name(), result.text(),
                    block != null ? block.getGroup() : null, result.photographic());
            if (block != null) {
                stored.setParams(block.getParams());
                stored.setSortOrder(block.getSortOrder());
            }
            boolean saved = inPlace ? service.updateCustom(stored)
                    : result.save() == art.moodframe.ui.dialog.BlockEditDialog.Save.SAVE ? service.addCustom(stored)
                    : service.addCopy(stored);
            if (saved) {
                fillBlockChoice(type, stored.getName());
                storedId[0] = stored.getId();
            }
            else
                status.status(I18nManager.getText("block.error.save", result.name()));
        });
        return storedId[0];
    }

    /**
     * Creates or edits a camera in its dialog of settings and selects the stored camera. A camera of the user is saved
     * in place or as a new one, a standard camera only as a new one.
     *
     * @param service the building blocks
     * @param block   the camera to edit, null for a new one
     */
    private String editCamera(art.moodframe.service.library.PromptBlockService service,
                              art.moodframe.model.PromptBlock block) {
        art.moodframe.ui.dialog.CameraEditDialog dialog = new art.moodframe.ui.dialog.CameraEditDialog(block,
                blockService);
        dialog.initOwner(activeWindow());
        String[] storedId = { null };
        dialog.showAndWait().ifPresent((result) -> {
            boolean inPlace = block != null && result.save() == art.moodframe.ui.dialog.BlockEditDialog.Save.SAVE;
            art.moodframe.model.PromptBlock stored = art.moodframe.service.library.PromptBlockService.camera(
                    inPlace ? block.getId() : null, result.name(), result.settings());
            if (block != null)
                stored.setSortOrder(block.getSortOrder());
            boolean saved = inPlace ? service.updateCustom(stored)
                    : result.save() == art.moodframe.ui.dialog.BlockEditDialog.Save.SAVE ? service.addCustom(stored)
                    : service.addCopy(stored);
            if (saved) {
                fillBlockChoice(art.moodframe.model.PromptBlock.Type.CAMERA, stored.getName());
                storedId[0] = stored.getId();

                /*  a camera changed in place keeps its name, so the list reports no change of the selection  */
                changed.run();
            }
            else
                status.status(I18nManager.getText("block.error.save", result.name()));
        });
        return storedId[0];
    }

    /**
     * Creates or edits a palette in its dialog and selects the stored palette. A palette of the user is saved in
     * place or as a new one, a default palette only as a new one.
     *
     * @param item the palette to edit, null for a new one
     */
    String editPalette(art.moodframe.model.ColorStyleItem item) {
        art.moodframe.service.library.ColorStyleService service = colorService;
        if (service == null)
            return null;

        art.moodframe.model.ColorStyle palette = item != null ? service.getStyleByName(item.getName()) : null;
        art.moodframe.ui.dialog.ColorStyleEditDialog dialog =
                new art.moodframe.ui.dialog.ColorStyleEditDialog(palette, colorService);
        dialog.initOwner(activeWindow());
        String[] storedId = { null };
        dialog.showAndWait().ifPresent((result) -> {
            art.moodframe.model.ColorStyle stored = new art.moodframe.model.ColorStyle(result.name(), result.colors(),
                    false);
            boolean save = result.save() == art.moodframe.ui.dialog.BlockEditDialog.Save.SAVE;
            boolean saved = palette != null && save ? service.updateCustomStyle(palette.getName(), stored)
                    : save ? service.addCustomStyle(stored) : service.addCopy(stored);
            if (saved) {
                fillColorChoice(stored.getName());
                art.moodframe.model.ColorStyle now = service.getStyleByName(stored.getName());
                storedId[0] = now != null ? now.getId() : "";
            }
            else
                status.status(I18nManager.getText("block.error.save", result.name()));
        });
        return storedId[0];
    }

    /*  the focused window of the app (a manage dialog, if open), so a nested dialog opens on top of it  */
    private javafx.stage.Window activeWindow() {
        return javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isFocused).findFirst()
                .orElse(window.get());
    }

    /**
     * Opens the "Manage…" dialog for the text blocks of the user of a type; the lists follow its changes.
     *
     * @param type the type of the blocks
     */
    private void manageBlocks(art.moodframe.model.PromptBlock.Type type) {
        art.moodframe.service.library.PromptBlockService service = blockService;
        if (service == null)
            return;

        String typeKey = type.name().toLowerCase(java.util.Locale.ROOT);
        new art.moodframe.ui.dialog.BlockManageDialog<>(window.get(),
                I18nManager.getText("block.manage.title." + typeKey), "moodframe-" + typeKey + ".json",
                new art.moodframe.ui.dialog.BlockManageDialog.Library<art.moodframe.model.StylePresetItem>() {
                    @Override
                    public List<art.moodframe.model.StylePresetItem> list() {
                        return service.getCustom(type).stream().map(art.moodframe.model.StylePresetItem::new).toList();
                    }

                    @Override
                    public String id(art.moodframe.model.StylePresetItem entry) {
                        return entry.getBlock().getId();
                    }

                    @Override
                    public String name(art.moodframe.model.StylePresetItem entry) {
                        return entry.getName();
                    }

                    @Override
                    public boolean delete(art.moodframe.model.StylePresetItem entry) {
                        return service.deleteCustom(id(entry));
                    }

                    @Override
                    public boolean reorder(List<art.moodframe.model.StylePresetItem> entries) {
                        return service.reorderCustom(type, entries.stream().map(this::id).toList());
                    }

                    @Override
                    public void exportTo(File file) throws IOException {
                        service.exportCustom(type, file);
                    }

                    @Override
                    public int importFrom(File file) throws IOException {
                        return service.importCustom(type, file);
                    }

                    @Override
                    public String create() {
                        return editBlock(type, null);
                    }

                    @Override
                    public String edit(art.moodframe.model.StylePresetItem entry) {
                        return editBlock(type, entry.getBlock());
                    }

                    @Override
                    public boolean editable() {
                        return true;
                    }
                }, (listView) -> new art.moodframe.ui.cell.StylePresetListCell(null, favorites(null,
                        FavoriteService.Kind.of(type), BlockChoices::blockId)), moodService, trash)
                .showAndWait();
    }

    /**
     * Opens the "Manage…" dialog for the palettes of the user; the lists follow its changes.
     */
    private void managePalettes() {
        art.moodframe.service.library.ColorStyleService service = colorService;
        if (service == null)
            return;

        new art.moodframe.ui.dialog.BlockManageDialog<>(window.get(),
                I18nManager.getText("block.manage.title.palette"), "moodframe-palettes.json",
                new art.moodframe.ui.dialog.BlockManageDialog.Library<art.moodframe.model.ColorStyleItem>() {
                    @Override
                    public List<art.moodframe.model.ColorStyleItem> list() {
                        return service.getCustomStyles().stream()
                                .map(art.moodframe.model.ColorStyle::toColorStyleItem).toList();
                    }

                    @Override
                    public String id(art.moodframe.model.ColorStyleItem entry) {
                        art.moodframe.model.ColorStyle palette = service.getStyleByName(entry.getName());
                        return palette != null ? palette.getId() : "";
                    }

                    @Override
                    public String name(art.moodframe.model.ColorStyleItem entry) {
                        return entry.getName();
                    }

                    @Override
                    public boolean delete(art.moodframe.model.ColorStyleItem entry) {
                        return service.deleteCustomStyle(entry.getName());
                    }

                    @Override
                    public boolean reorder(List<art.moodframe.model.ColorStyleItem> entries) {
                        return service.reorderCustomStyles(entries.stream().map(this::id).toList());
                    }

                    @Override
                    public void exportTo(File file) throws IOException {
                        if (!service.exportStyles(file))
                            throw new IOException("Failed to write " + file);
                    }

                    @Override
                    public int importFrom(File file) {
                        return service.importStyles(file, true);
                    }

                    @Override
                    public String create() {
                        return editPalette(null);
                    }

                    @Override
                    public String edit(art.moodframe.model.ColorStyleItem entry) {
                        return editPalette(entry);
                    }

                    @Override
                    public boolean editable() {
                        return true;
                    }
                }, (listView) -> new art.moodframe.ui.cell.ColorStyleListCell(favorites(null,
                        FavoriteService.Kind.COLOR, art.moodframe.model.ColorStyleItem::getId)), moodService, trash)
                .showAndWait();
    }

    /**
     * Sets up the locks of the building block fields, remembered. A locked field
     * keeps its choice when a mood or the brainstorm sets the fields; the lock shows while the pointer is over the
     * field, and always once locked (see main-window.css).
     */
    private void setupBlockLocks() {
        java.util.Map<ToggleButton, String> locks = java.util.Map.of(atmosphereLockButton, "atmosphere",
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
