package art.moodframe.ui.entry;

import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.chat.ModelLock;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The images of a chat entry in generation: placeholders at once in the size of the finished images, an error tile;
 * the badge "MCP" of an entry an AI agent made. The configuration the card reads lives in a throwaway
 * {@code user.home}.
 */
class ChatEntryCardTest {

    @TempDir
    static Path home;

    private static String realHome;

    /*  The card holds controls, which need the toolkit  */
    @BeforeAll
    static void startToolkit() throws Exception {
        realHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);
    }

    @AfterAll
    static void restoreHome() {
        System.setProperty("user.home", realHome);
    }

    /*  A step on the FX thread, after the steps the card queued before  */
    private static <T> T onFxThread(Supplier<T> step) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            result.set(step.get());
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        return result.get();
    }

    /*  The card of a new entry, as the chat shows it right after sending: without images yet, 900 px wide  */
    private static ChatEntryCard show(ChatEntry entry) {
        ChatEntryCard card = new ChatEntryCard(entry);
        new Scene(new VBox(card), 900, 2000);
        return card;
    }

    /*  The placeholders the request adds for the images it asks for (RequestResponseManager)  */
    private static List<GeneratedImage> request(ChatEntry entry, int count) {
        List<GeneratedImage> images = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            GeneratedImage image = new GeneratedImage.Builder().chatEntryId(entry.getId())
                    .downloadStatus(GeneratedImage.DownloadStatus.PENDING).sortOrder(i).build();
            entry.getImages().add(image);
            images.add(image);
        }
        return images;
    }

    /*  The tiles in the row of images, the last child of the card  */
    private static int tiles(ChatEntryCard card) {
        return ((Pane) card.getChildren().get(card.getChildren().size() - 1)).getChildren().size();
    }

    private static long shown(ChatEntryCard card, String selector) {
        return card.lookupAll(selector).stream().filter(Node::isVisible).count();
    }

    /*  The height of the card once laid out twice, as the images take their width from the card  */
    private static double height(ChatEntryCard card) {
        for (int i = 0; i < 2; i++) {
            card.getParent().applyCss();
            card.getParent().layout();
        }
        return card.getHeight();
    }

    /*  Whether every tile shows its image  */
    private static boolean loaded(ChatEntryCard card) {
        return ((Pane) card.getChildren().get(card.getChildren().size() - 1)).getChildren().stream()
                .allMatch((tile) -> ((StackPane) tile).getChildren().get(0) instanceof ImageView view
                        && view.getImage() != null);
    }

    private static Path png(Path dir, int width, int height) throws Exception {
        Path file = dir.resolve("image.png");
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
        return file;
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 4 })
    void placeholdersShowAtOnceAndTheImagesTakeTheirPlaceWithoutChangingTheHeight(int count, @TempDir Path dir)
            throws Exception {
        Path file = png(dir, 1600, 900);
        ChatEntry entry = new ChatEntry.Builder().userPrompt("a lighthouse at dusk").model("Nano Banana Pro")
                .size("16:9").build();
        ChatEntryCard card = onFxThread(() -> show(entry));
        double empty = onFxThread(() -> height(card));
        List<GeneratedImage> images = onFxThread(() -> request(entry, count));
        double[] generating = onFxThread(() -> new double[] { tiles(card),
                shown(card, ".entry-image-placeholder"), height(card) });
        assertEquals(count, generating[0]);
        assertEquals(count, generating[1]);
        assertTrue(generating[2] > empty + 50);

        /*  Each image arrives in its place, as the image store saves it  */
        onFxThread(() -> {
            for (GeneratedImage image : images) {
                image.setWidth(1600);
                image.setHeight(900);
                image.setLocalFilePath(file.toString());
                image.setDownloadStatus(GeneratedImage.DownloadStatus.CACHED);
            }
            return null;
        });
        double[] arrived = onFxThread(() -> new double[] { tiles(card),
                shown(card, ".entry-image-placeholder"), height(card) });
        assertEquals(count, arrived[0]);
        assertEquals(0, arrived[1]);
        assertEquals(generating[2], arrived[2]);

        /*  The files load in the background, into tiles of the same size  */
        for (int i = 0; i < 100 && !onFxThread(() -> loaded(card)); i++)
            Thread.sleep(50);
        assertTrue(onFxThread(() -> loaded(card)));
        assertEquals(generating[2], onFxThread(() -> height(card)));
    }

    @Test
    void aFailedImageShowsAnErrorTileInItsPlace() throws Exception {
        ChatEntry entry = new ChatEntry.Builder().userPrompt("a harbor at dawn").model("Nano Banana Pro")
                .size("3:2").build();
        ChatEntryCard card = onFxThread(() -> show(entry));
        List<GeneratedImage> images = onFxThread(() -> request(entry, 2));
        double generating = onFxThread(() -> height(card));

        onFxThread(() -> {
            images.get(0).setErrorMessage("Blocked by the safety filter");
            images.get(0).setDownloadStatus(GeneratedImage.DownloadStatus.FAILED);
            return null;
        });
        double[] failed = onFxThread(() -> new double[] { tiles(card), shown(card, ".entry-image-failed"),
                shown(card, ".progress-indicator"), height(card) });
        assertEquals(2, failed[0]);
        assertEquals(1, failed[1]);
        assertEquals(1, failed[2]);
        assertEquals(generating, failed[3]);
        assertEquals("Blocked by the safety filter", ImageDetails.failure(images.get(0)));
    }

    /**
     * An image deleted on another thread (the worker of the window, an MCP thread) while the card shows it: the width
     * of the tiles follows on the JavaFX thread only, never on the thread that changed the list.
     */
    @Test
    void anImageDeletedOnAnotherThreadResizesTheTilesOnlyOnTheFxThread() throws Exception {
        ChatEntry entry = new ChatEntry.Builder().userPrompt("a quay at night").model("Nano Banana Pro")
                .size("1:1").build();
        List<String> offFxThread = new CopyOnWriteArrayList<>();
        List<Double> widths = new CopyOnWriteArrayList<>();
        ChatEntryCard card = onFxThread(() -> {
            ChatEntryCard shown = new ChatEntryCard(entry);
            new Scene(new VBox(shown), 500, 2000);
            request(entry, 2);
            return shown;
        });
        double before = onFxThread(() -> {
            height(card);
            ImageView view = (ImageView) card.lookupAll(".image-view").iterator().next();
            view.fitWidthProperty().addListener((obs, oldWidth, width) -> {
                widths.add(width.doubleValue());
                if (!Platform.isFxApplicationThread())
                    offFxThread.add(Thread.currentThread().getName());
            });
            return view.getFitWidth();
        });

        Thread worker = new Thread(() -> ModelLock.change(() -> entry.getImages().remove(1)), "window-worker");
        worker.start();
        worker.join(10_000);
        onFxThread(() -> tiles(card));

        assertEquals(List.of(), offFxThread, "the threads that changed the scene graph besides the FX thread");
        assertEquals(1, onFxThread(() -> tiles(card)));
        assertFalse(widths.isEmpty(), "the tiles follow the number of images");
        assertTrue(widths.get(widths.size() - 1) > before, "one image takes the whole row");
    }

    @Test
    void anEntryOfAnAiAgentShowsTheBadgeMcpLastAndAnEntryOfTheUserDoesNot() throws Exception {
        ChatEntry agent = new ChatEntry.Builder().userPrompt("a lighthouse").model("Nano Banana Pro").size("3:2")
                .source(ChatEntry.Source.MCP).build();
        ChatEntry user = new ChatEntry.Builder().userPrompt("a lighthouse").model("Nano Banana Pro").size("3:2")
                .build();
        Locale before = I18nManager.getCurrentLocale();
        try {
            List<String> texts = onFxThread(() -> {
                I18nManager.setLocale(Locale.GERMAN);
                ChatEntryCard card = show(agent);
                List<Node> badges = List.copyOf(card.lookupAll(".entry-badge"));
                Label mcp = (Label) badges.get(badges.size() - 1);
                String german = mcp.getTooltip().getText();

                /*  A change of the language builds the badges anew  */
                I18nManager.setLocale(Locale.ENGLISH);
                Label changed = (Label) card.lookup(".entry-badge-mcp");
                int others = show(user).lookupAll(".entry-badge-mcp").size();
                return List.of(mcp.getText(), String.join(" ", mcp.getStyleClass()), german,
                        changed.getTooltip().getText(), String.valueOf(others));
            });
            assertEquals("MCP", texts.get(0));
            assertTrue(texts.get(1).contains("entry-badge-mcp"), "the last badge: " + texts.get(1));
            assertEquals("Von einem KI-Agenten über MCP erzeugt", texts.get(2));
            assertEquals("Created by an AI agent over MCP", texts.get(3));
            assertEquals("0", texts.get(4), "no badge for an entry of the send button");
        } finally {
            I18nManager.setLocale(before);
        }
    }

    @Test
    void aFailedImageWithoutMessageNamesTheFailedStatus() {
        GeneratedImage image = new GeneratedImage.Builder().downloadStatus(GeneratedImage.DownloadStatus.FAILED)
                .build();
        assertEquals(I18nManager.getText("entry.status.failed"), ImageDetails.failure(image));
        image.setFailureReason("Moderated");
        assertEquals("Moderated", ImageDetails.failure(image));
    }

    /**
     * "Copy seed" of the card and of the images view follows the provider of the model of an image, not its name: a
     * FLUX image and one of a former FLUX model offer it, a Gemini, Omni or OpenAI image never, nor one without seed.
     */
    @Test
    void onlyAnImageOfBflWithASeedOffersItsSeed() {
        assertTrue(EntryMenus.offersSeed(image(GenerationModel.FLUX2_PRO.getApiModelId(), 42)));
        assertTrue(EntryMenus.offersSeed(image(GenerationModel.FLUX3_IMAGE_1K.getApiModelId(), -7)));
        assertTrue(EntryMenus.offersSeed(image("flux-pro-1.1", 42)), "a former FLUX model");
        assertFalse(EntryMenus.offersSeed(image(GenerationModel.FLUX2_PRO.getApiModelId(), 0)), "no seed");
        assertFalse(EntryMenus.offersSeed(image(null, 42)), "no model");
        for (GenerationModel model : List.of(GenerationModel.GEMINI_PRO_2K, GenerationModel.NANO_BANANA2_1K,
                GenerationModel.OMNI_720P))
            assertFalse(EntryMenus.offersSeed(image(model.getApiModelId(), 42)), model.name());
        assertFalse(EntryMenus.offersSeed(image("gpt-image-1", 42)), "a model of the OpenAI Images API");
    }

    private static GeneratedImage image(String model, long seed) {
        return new GeneratedImage.Builder().model(model).seed(seed).build();
    }
}
