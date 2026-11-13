package art.moodframe.ui.component;

import art.moodframe.config.ServiceFactory;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.service.ImageSizeCheck;
import art.moodframe.service.ThumbnailManager;
import art.moodframe.ui.BoxInset;
import art.moodframe.util.I18nManager;
import art.moodframe.util.VideoPoster;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.NumberBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableDoubleValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The images view of a chat or a group: all its images in a grid in the order of the chat, the newest last, without
 * text and without card. Each image keeps its aspect ratio within a square cell; the cells fill the width in as many
 * columns as the chosen size allows. While the pointer is over an image, the action bar shows at its top, its size
 * above the mood and frame of its entry at its bottom; a double click opens it in the full view. Images in generation
 * show as placeholders, failed ones as error tiles in their place.
 *
 * <p>The grid is a list of rows, so only the rows in view hold tiles, and the tiles show the thumbnails. It opens at
 * its end and stays there while images arrive, unless the user scrolled away from it.</p>
 *
 * @since 2.0.0
 */
public class ImageGrid extends ListView<List<ImageGrid.Item>> {

    private static final Logger logger = LoggerFactory.getLogger(ImageGrid.class);

    /*  The gap between the cells across and down; the cells start on the inset of the boxes and end on the right edge
        of the view switch (behind the 1 px border of the list), the vertical scroll bar lies right of them  */
    private static final double GAP = 10.0;
    private static final double LEFT_INSET = BoxInset.LEFT - 1.0;
    private static final double RIGHT_INSET = BoxInset.RIGHT - 1.0;
    private static final int MIN_COLUMNS = 2;
    private static final double TILE_RADIUS = 8.0;
    private static final double DOTS_MARGIN = 5.0;

    /*  The largest size of a tile to choose; a tile only gets larger to fill the width, up to the sharp size  */
    private static final double MAX_CHOSEN_SIZE = 480.0;

    /**
     * An image of the grid with the entry it belongs to.
     *
     * @param entry the entry
     * @param image the image
     */
    public record Item(ChatEntry entry, GeneratedImage image) {
    }

    private final Function<GeneratedImage, String> pathOf;
    private final DoubleProperty tileSize = new SimpleDoubleProperty(180.0);
    private final DoubleProperty maxTileSize = new SimpleDoubleProperty(ThumbnailManager.MAX_THUMBNAIL_SIZE);
    private final DoubleProperty cellSize = new SimpleDoubleProperty(180.0);
    private final ObjectProperty<ObservableList<ChatEntry>> entries = new SimpleObjectProperty<>();
    private final List<Item> images = new ArrayList<>();
    private final List<ObservableList<GeneratedImage>> watched = new ArrayList<>();
    private final ListChangeListener<ChatEntry> onEntriesChange = (change) -> requestRebuild();
    private final ListChangeListener<GeneratedImage> onImagesChange = (change) -> requestRebuild();
    private ObservableList<ChatEntry> followed;
    private int columns = 0;
    private boolean atEnd = true;

    private Consumer<Item> onOpen;
    private BiConsumer<Item, ImageActionBar.Action> onAction;
    private Consumer<ChatEntry> onReuse;
    private Consumer<Long> onSeedCopy;
    private Supplier<Boolean> isFluxModelSelected;

    /**
     * Creates the grid, empty until it gets the entries of a chat or a group.
     *
     * @param pathOf the file of an image, in the trash for a chat there
     */
    public ImageGrid(Function<GeneratedImage, String> pathOf) {
        this.pathOf = pathOf;
        getStyleClass().add("image-grid");
        getStylesheets().add(ImageGrid.class.getResource("/css/image-grid.css").toExternalForm());
        setFocusTraversable(false);
        setCellFactory((listView) -> new Row());

        /*  Every new list counts, also one equal to the last, like the empty lists of two new chats  */
        entries.addListener((obs) -> followEntries());
        widthProperty().addListener((obs) -> layoutRows(false));
        tileSize.addListener((obs) -> layoutRows(false));
        maxTileSize.addListener((obs) -> layoutRows(false));

        /*  Scrolling away from the end, by wheel or scroll bar, lets the grid stay where the user left it; while the
            user scrolls, the grid does not follow its end  */
        addEventFilter(ScrollEvent.SCROLL, (event) -> {
            atEnd = false;
            Platform.runLater(() -> atEnd = isAtEnd());
        });
        addEventFilter(MouseEvent.MOUSE_PRESSED, (event) -> atEnd = false);
        addEventFilter(MouseEvent.MOUSE_RELEASED, (event) -> Platform.runLater(() -> atEnd = isAtEnd()));
    }

    /*  The rows are counted anew first, so new rows at the end stay in view  */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (atEnd && lookup(".virtual-flow") instanceof VirtualFlow<?> flow)
            flow.setPosition(1.0);
    }

    /**
     * Whether the last row is in view, so the grid keeps it in view while images arrive.
     *
     * @return true if the last row is in view or the grid shows nothing yet
     */
    private boolean isAtEnd() {
        if (!(lookup(".virtual-flow") instanceof VirtualFlow<?> flow))
            return true;
        IndexedCell<?> last = flow.getLastVisibleCell();
        return last == null || last.getIndex() == flow.getCellCount() - 1
                && last.getLayoutY() + last.getHeight() <= flow.getHeight() + 1.0;
    }

    /**
     * The number of columns: tiles at least as wide as the chosen size fill the width, never wider than the
     * thumbnails allow, and at least two of them.
     *
     * @param width the width of a row
     * @param size the chosen size of a tile
     * @param maxSize the largest size a tile shows sharp
     * @return the number of columns
     */
    static int columns(double width, double size, double maxSize) {
        int fitting = (int) Math.floor((width + GAP) / (size + GAP));
        int sharp = (int) Math.ceil((width + GAP) / (maxSize + GAP));
        return Math.max(MIN_COLUMNS, Math.max(fitting, sharp));
    }

    /**
     * The largest size a tile shows sharp on the screen the window is on: the size of the thumbnails in screen pixels,
     * 800 on a screen of scale 1 and 400 on a Retina screen.
     *
     * @param outputScale the scale of the screen
     * @return the size in screen pixels, following the scale
     */
    public static DoubleBinding sharpSize(ObservableDoubleValue outputScale) {
        return Bindings.createDoubleBinding(() -> ThumbnailManager.MAX_THUMBNAIL_SIZE / outputScale.get(),
                outputScale);
    }

    /**
     * The largest size of a tile to choose on the screen the window is on: the sharp size, at most 480, so 400 on a
     * Retina screen and 480 on a screen of scale 1.
     *
     * @param outputScale the scale of the screen
     * @return the size in screen pixels, following the scale
     */
    public static NumberBinding largestChoice(ObservableDoubleValue outputScale) {
        return Bindings.min(MAX_CHOSEN_SIZE, sharpSize(outputScale));
    }

    /**
     * The entries of the chat or the group, the oldest first, null for none; the grid shows their images in this
     * order and follows their changes.
     *
     * @return the entries
     */
    public ObjectProperty<ObservableList<ChatEntry>> entriesProperty() {
        return entries;
    }

    /** Shows the images of the new entries at the end and follows their changes instead of those of the last ones. */
    private void followEntries() {
        if (followed != null)
            followed.removeListener(onEntriesChange);
        followed = entries.get();
        if (followed != null)
            followed.addListener(onEntriesChange);
        atEnd = true;
        rebuild();
    }

    /**
     * The images of the grid in their order, for the full view.
     *
     * @return the images, the oldest first as in the chat
     */
    public List<Item> getImages() {
        return Collections.unmodifiableList(images);
    }

    /**
     * The chosen size of a tile, which the columns follow.
     *
     * @return the size in screen pixels
     */
    public DoubleProperty tileSizeProperty() {
        return tileSize;
    }

    /**
     * The largest size a tile shows sharp: the size of the thumbnails in screen pixels.
     *
     * @return the size in screen pixels
     */
    public DoubleProperty maxTileSizeProperty() {
        return maxTileSize;
    }

    /** Collects the images of the entries again, on the FX thread, as the images of a running request change. */
    private void requestRebuild() {
        if (Platform.isFxApplicationThread())
            rebuild();
        else
            Platform.runLater(this::rebuild);
    }

    /** Collects the images of the entries in the order of the chat, the oldest entry first. */
    private void rebuild() {
        watched.forEach((list) -> list.removeListener(onImagesChange));
        watched.clear();
        images.clear();
        if (followed != null) {
            for (ChatEntry entry : followed) {
                entry.getImages().addListener(onImagesChange);
                watched.add(entry.getImages());
                for (GeneratedImage image : entry.getImages())
                    images.add(new Item(entry, image));
            }
        }
        layoutRows(true);
    }

    /**
     * Sizes the cells to fill the width and splits the images into rows anew once their number per row changed.
     *
     * @param imagesChanged whether the images changed, so the rows are split anew in any case
     */
    private void layoutRows(boolean imagesChanged) {
        double width = getWidth() - snappedLeftInset() - snappedRightInset() - LEFT_INSET - RIGHT_INSET;
        int count = columns(width, tileSize.get(), maxTileSize.get());
        cellSize.set(Math.max(1.0, (width - GAP * (count - 1)) / count));
        setFixedCellSize(cellSize.get() + GAP);
        if (!imagesChanged && count == columns)
            return;
        columns = count;
        List<List<Item>> rows = new ArrayList<>();
        for (int i = 0; i < images.size(); i += count)
            rows.add(List.copyOf(images.subList(i, Math.min(i + count, images.size()))));
        getItems().setAll(rows);
    }

    /** A row of the grid with its tiles, shown anew once the status of one of its images changes. */
    private final class Row extends ListCell<List<Item>> {

        private final HBox tiles = new HBox(GAP);
        private final List<Runnable> releases = new ArrayList<>();

        /*  The cells keep their fractional size, so the last column ends exactly on the right edge  */
        Row() {
            tiles.setPadding(new Insets(GAP / 2, 0, GAP / 2, LEFT_INSET));
            tiles.setSnapToPixel(false);
        }

        @Override
        protected void updateItem(List<Item> row, boolean empty) {
            super.updateItem(row, empty);
            show(empty ? null : row);
        }

        private void show(List<Item> row) {
            releases.forEach(Runnable::run);
            releases.clear();
            tiles.getChildren().clear();
            if (row == null) {
                setGraphic(null);
                return;
            }
            for (Item item : row) {
                tiles.getChildren().add(createCell(item));

                /*  A finished or failed image takes the place of its placeholder  */
                ChangeListener<GeneratedImage.DownloadStatus> onStatus = (obs, oldStatus, newStatus) ->
                        Platform.runLater(() -> {
                            if (row.equals(getItem()))
                                show(row);
                        });
                item.image().downloadStatusProperty().addListener(onStatus);
                releases.add(() -> item.image().downloadStatusProperty().removeListener(onStatus));
            }
            setGraphic(tiles);
        }
    }

    /**
     * Creates the square cell of an image: the image in its aspect ratio with rounded corners, the action bar at its
     * top and the size line above the dot line at its bottom while the pointer is over it, a placeholder while it is
     * generated, or an error tile with the message in its tooltip once it failed.
     */
    private StackPane createCell(Item item) {
        StackPane cell = new StackPane();
        cell.prefWidthProperty().bind(cellSize);
        cell.prefHeightProperty().bind(cellSize);
        cell.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        cell.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        GeneratedImage image = item.image();
        if (image.getDownloadStatus() == GeneratedImage.DownloadStatus.FAILED) {
            FontIcon icon = new FontIcon("mdi2a-alert-circle-outline");
            icon.setIconSize(24);
            StackPane failed = placeholder(icon);
            failed.getStyleClass().add("image-grid-failed");
            Tooltip.install(failed, new Tooltip(ChatEntryCard.failure(image)));
            cell.getChildren().add(failed);
            return cell;
        }
        if (image.getDownloadStatus() != GeneratedImage.DownloadStatus.CACHED) {
            ProgressIndicator progress = new ProgressIndicator();
            progress.setMaxSize(32, 32);
            cell.getChildren().add(placeholder(progress));
            return cell;
        }

        ImageView view = new ImageView();
        view.fitWidthProperty().bind(cellSize);
        view.fitHeightProperty().bind(cellSize);
        view.setPreserveRatio(true);
        view.setSmooth(true);

        /*  The tile is as large as the image, its corners rounded by a clip  */
        StackPane tile = new StackPane(view);
        tile.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(2 * TILE_RADIUS);
        clip.setArcHeight(2 * TILE_RADIUS);
        clip.widthProperty().bind(tile.widthProperty());
        clip.heightProperty().bind(tile.heightProperty());
        tile.setClip(clip);
        if (image.isVideo()) {
            FontIcon play = new FontIcon("mdi2p-play-circle-outline");
            play.setIconSize(32);
            play.getStyleClass().add("image-grid-play");
            tile.getChildren().add(play);
        }

        ImageActionBar actionBar = new ImageActionBar((action) -> {
            if (onAction != null)
                onAction.accept(item, action);
        });
        actionBar.setAvailable(true, image.isVideo());
        actionBar.attachTo(tile);
        HBox dots = createDots(item.entry());
        attachDots(dots, tile);
        ImageSizeLabel size = new ImageSizeLabel(image);
        size.attachTo(tile, dots);

        ChatEntryCard.installDetails(tile, view, image, size);
        tile.setOnContextMenuRequested((event) -> {
            createMenu(item).show(tile, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        tile.setOnMouseClicked((event) -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && onOpen != null)
                onOpen.accept(item);
        });

        cell.getChildren().add(tile);
        load(image, view, cell);
        return cell;
    }

    /** A placeholder filling a cell, with a progress or an icon. */
    private static StackPane placeholder(Node content) {
        StackPane placeholder = new StackPane(content);
        placeholder.getStyleClass().add("image-grid-placeholder");
        Rectangle clip = new Rectangle();
        clip.setArcWidth(2 * TILE_RADIUS);
        clip.setArcHeight(2 * TILE_RADIUS);
        clip.widthProperty().bind(placeholder.widthProperty());
        clip.heightProperty().bind(placeholder.heightProperty());
        placeholder.setClip(clip);
        return placeholder;
    }

    /**
     * Loads the thumbnail of an image, or the poster of a video, into its view; an image whose file is gone shows a
     * placeholder. A thumbnail too small for its tile on the screen is created anew at full size.
     */
    private void load(GeneratedImage image, ImageView view, StackPane cell) {
        Runnable missing = () -> {
            FontIcon icon = new FontIcon("mdi2i-image-off-outline");
            icon.setIconSize(24);
            cell.getChildren().setAll(placeholder(icon));
        };
        String path = pathOf.apply(image);
        if (path == null) {
            missing.run();
            return;
        }
        if (image.isVideo()) {
            VideoPoster.load(Path.of(path))
                    .whenComplete((poster, error) -> Platform.runLater(() -> {
                        if (poster != null)
                            view.setImage(poster);
                        else
                            missing.run();
                    }));
            return;
        }

        /*  The first display corrects a missing or wrong size from the file, the tooltip reads it when shown  */
        ImageSizeCheck sizeCheck = ServiceFactory.getInstance().getService(ImageSizeCheck.class);
        if (sizeCheck != null)
            sizeCheck.check(image, path);

        ThumbnailManager thumbnails = ServiceFactory.getInstance().getService(ThumbnailManager.class);
        if (thumbnails == null) {
            view.setImage(new Image(new File(path).toURI().toString(), ThumbnailManager.MAX_THUMBNAIL_SIZE,
                    ThumbnailManager.MAX_THUMBNAIL_SIZE, true, true, true));
            return;
        }
        String thumbnailPath = thumbnails.getThumbnailPath(path);
        SharpThumbnail.follow(view, path, thumbnails);
        Image cached = thumbnails.getFromCacheOnly(thumbnailPath);
        if (cached != null) {
            view.setImage(cached);
            return;
        }

        /*  A missing thumbnail is created first  */
        CompletableFuture.supplyAsync(() -> {
            Image loaded = thumbnails.getCachedImage(thumbnailPath);
            return loaded != null ? loaded
                    : thumbnails.getCachedImage(thumbnails.getThumbnail(path).join().getAbsolutePath());
        }).whenComplete((loaded, error) -> Platform.runLater(() -> {
            if (loaded != null)
                view.setImage(loaded);
            else {
                logger.debug("No thumbnail for {}", path, error);
                missing.run();
            }
        }));
    }

    /**
     * The line of an image in the full view: the mood, the frame and the model of its entry.
     *
     * @param entry the entry of the image
     * @return the line, for example "Golden Coast · Landscape Print · FLUX.2 [pro]"
     */
    public static String caption(ChatEntry entry) {
        EntryBadge mood = EntryBadge.moodName(entry.getMood(), styleOf(entry));
        EntryBadge frame = EntryBadge.frameName(entry.getFrame(), entry.getSize());
        EntryBadge model = EntryBadge.model(entry.getModel());
        return EntryBadge.join(mood != null ? mood.text() : null, frame != null ? frame.text() : null,
                model != null ? model.text() : null);
    }

    /** The style of an entry; one from before 2.0 may carry it at its first image only (BFL). */
    private static String styleOf(ChatEntry entry) {
        if (entry.getStyle() == null && !entry.getImages().isEmpty())
            return entry.getImages().get(0).getStylePreset();
        return entry.getStyle();
    }

    /**
     * The mood and the frame of an entry as a small line with two colored dots, hollow and in italics for a mixture
     * or a setting of its own.
     *
     * @return the line, null for an entry without either
     */
    private static HBox createDots(ChatEntry entry) {
        HBox dots = new HBox();
        dots.getStyleClass().add("image-dots");
        addDot(dots, EntryBadge.moodName(entry.getMood(), styleOf(entry)), "image-dot-mood");
        addDot(dots, EntryBadge.frameName(entry.getFrame(), entry.getSize()), "image-dot-frame");
        return dots.getChildren().isEmpty() ? null : dots;
    }

    private static void addDot(HBox dots, EntryBadge badge, String kind) {
        if (badge == null)
            return;
        Circle dot = new Circle(3.5);
        dot.getStyleClass().addAll("image-dot", kind);
        Label label = new Label(badge.text(), dot);
        if (badge.mixture())
            label.getStyleClass().add("image-dot-mixture");
        dots.getChildren().add(label);
    }

    /**
     * Shows the dot line at the bottom center of an image while the pointer is over it, at most as wide as the image;
     * on a narrow image the longer name gives way first. The tile does not lay out the line, so that it never widens a
     * narrow image.
     */
    private static void attachDots(HBox dots, StackPane tile) {
        if (dots == null)
            return;
        dots.setManaged(false);
        tile.getChildren().add(dots);
        dots.visibleProperty().bind(tile.hoverProperty());
        dots.visibleProperty().addListener((obs, wasVisible, isVisible) -> {
            if (isVisible) {
                dots.getChildren().forEach((label) -> ((Label) label).setPrefWidth(Region.USE_COMPUTED_SIZE));
                dots.applyCss();
                List<Label> labels = dots.getChildren().stream().map(Label.class::cast)
                        .sorted(Comparator.comparingDouble((label) -> label.prefWidth(-1))).toList();
                double names = labels.stream().mapToDouble((label) -> label.prefWidth(-1)).sum();
                double room = tile.getWidth() - 2 * DOTS_MARGIN - (dots.prefWidth(-1) - names);
                for (int i = 0; room < names && i < labels.size(); i++) {
                    double share = Math.min(labels.get(i).prefWidth(-1), Math.floor(room / (labels.size() - i)));
                    labels.get(i).setPrefWidth(share);
                    room -= share;
                }
                double width = Math.min(dots.prefWidth(-1), tile.getWidth() - 2 * DOTS_MARGIN);
                dots.resize(width, dots.prefHeight(width));
                dots.relocate(Math.round((tile.getWidth() - width) / 2),
                        tile.getHeight() - dots.getHeight() - DOTS_MARGIN);
            }
        });
    }

    /** The context menu of an image: "Reuse" its entry and, for a FLUX image with a seed, "Copy seed". */
    private ContextMenu createMenu(Item item) {
        MenuItem reuse = new MenuItem(I18nManager.getText("entry.reuse"));
        reuse.setOnAction((action) -> {
            if (onReuse != null)
                onReuse.accept(item.entry());
        });
        ContextMenu menu = new ContextMenu(reuse);

        /*  FLUX seeds can be negative (XOR of random longs), so check != 0  */
        long seed = item.image().getSeed();
        String model = item.image().getModel();
        if (onSeedCopy != null && seed != 0 && model != null && !model.toLowerCase().contains("gemini")) {
            MenuItem copySeed = new MenuItem(I18nManager.getText("tooltip.copy.seed"));
            copySeed.setOnAction((action) -> onSeedCopy.accept(seed));
            copySeed.setDisable(isFluxModelSelected == null || !isFluxModelSelected.get());
            menu.getItems().add(copySeed);
        }
        return menu;
    }

    /**
     * Sets the callback of a double click on an image, which opens the full view.
     *
     * @param onOpen the callback that receives the image
     */
    public void setOnOpen(Consumer<Item> onOpen) {
        this.onOpen = onOpen;
    }

    /**
     * Sets the callback of the action bar of an image.
     *
     * @param onAction the callback that receives the image and the action
     */
    public void setOnAction(BiConsumer<Item, ImageActionBar.Action> onAction) {
        this.onAction = onAction;
    }

    /**
     * Sets the callback of "Reuse" in the context menu of an image.
     *
     * @param onReuse the callback that receives the entry of the image
     */
    public void setOnReuse(Consumer<ChatEntry> onReuse) {
        this.onReuse = onReuse;
    }

    /**
     * Sets the callback of "Copy seed" in the context menu of an image, which takes effect while a FLUX model is
     * chosen.
     *
     * @param onSeedCopy the callback that receives the seed
     * @param isFluxModelSelected whether a FLUX model is chosen
     */
    public void setOnSeedCopy(Consumer<Long> onSeedCopy, Supplier<Boolean> isFluxModelSelected) {
        this.onSeedCopy = onSeedCopy;
        this.isFluxModelSelected = isFluxModelSelected;
    }
}
