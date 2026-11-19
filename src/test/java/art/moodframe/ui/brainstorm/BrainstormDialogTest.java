package art.moodframe.ui.brainstorm;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormSession;
import art.moodframe.model.CameraSettings;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.BrainstormRepository;
import art.moodframe.service.brainstorm.BrainstormContext;
import art.moodframe.service.brainstorm.BrainstormService;
import art.moodframe.service.generation.ModelLabels;
import art.moodframe.service.prompt.CameraPromptBuilder;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.util.I18nManager;
import art.moodframe.ui.support.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Builds the popup on the JavaFX thread without showing it, to catch wiring errors in the layout code. */
class BrainstormDialogTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);
    }

    @Test
    void buildsWithHistoryAndVideoModelsWithoutErrors() throws Exception {
        BrainstormRepository repository = mock(BrainstormRepository.class);
        when(repository.findAllSessions()).thenReturn(List.of(new BrainstormSession("s1", "Serverausfall",
                LocalDateTime.now(), LocalDateTime.now())));
        BrainstormService service = new BrainstormService(repository, mock(GeminiApiService.class));
        BrainstormContext context = new BrainstormContext(
                List.of(new PromptBlock("std:style:pixel-art", PromptBlock.Type.STYLE, "Pixel Art", "retro pixel art",
                        PromptBlock.GROUP_ART, false)),
                List.of(new ColorStyle("Ocean Breeze", List.of("#48BDFF"), true)),
                List.of(GenerationModel.FLUX2_PRO, GenerationModel.OMNI_720P),
                "Target model: x");

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                new BrainstormDialog(null, null, service, context,
                        ModelLabels.displayName(GenerationModel.OMNI_720P), result -> { });
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(20, TimeUnit.SECONDS), "the dialog was not built in time");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        assertNull(failure.get());
    }

    @Test
    void hasGripDividersAndTheHeadingsOfTheThreeColumnsOnOneBaseline() throws Exception {
        BrainstormRepository repository = mock(BrainstormRepository.class);
        when(repository.findAllSessions()).thenReturn(List.of(new BrainstormSession("s1", "Leuchtturm",
                LocalDateTime.now(), LocalDateTime.now())));
        BrainstormService service = new BrainstormService(repository, mock(GeminiApiService.class));
        BrainstormContext context = new BrainstormContext(List.of(), List.of(), List.of(GenerationModel.FLUX2_PRO),
                "Target model: x");

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<String>> grips = new AtomicReference<>();
        AtomicReference<List<Double>> baselines = new AtomicReference<>();
        AtomicReference<List<String>> stylesheets = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                BrainstormDialog dialog = new BrainstormDialog(null, null, service, context,
                        ModelLabels.displayName(GenerationModel.FLUX2_PRO), result -> { });
                dialog.getScene().getRoot().applyCss();
                dialog.getScene().getRoot().layout();
                grips.set(dialog.getScene().getRoot().lookupAll(".split-pane").stream()
                        .filter((node) -> node.getStyleClass().contains("grip-split-pane"))
                        .map((node) -> ((SplitPane) node).getOrientation().name()).toList());
                baselines.set(dialog.getScene().getRoot().lookupAll(".title-4").stream().map((node) -> (Label) node)
                        .map((label) -> label.localToScene(0, label.getBaselineOffset()).getY()).toList());
                stylesheets.set(List.copyOf(dialog.getScene().getStylesheets()));
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(20, TimeUnit.SECONDS), "the dialog was not built in time");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        assertEquals(List.of("HORIZONTAL", "VERTICAL"), grips.get(), "both dividers are grips as in the main window");
        assertTrue(stylesheets.get().stream().anyMatch((sheet) -> sheet.endsWith("/css/split-pane-grips.css")));
        assertEquals(3, baselines.get().size(), "history, chat and result have a heading each");
        assertEquals(1, baselines.get().stream().distinct().count(), "the headings share one baseline");
    }

    @Test
    void paintsTheBackgroundOfTheThemeBehindTheDividersInEveryTheme() throws Exception {
        BrainstormRepository repository = mock(BrainstormRepository.class);
        when(repository.findAllSessions()).thenReturn(List.of());
        BrainstormService service = new BrainstormService(repository, mock(GeminiApiService.class));
        BrainstormContext context = new BrainstormContext(List.of(), List.of(), List.of(GenerationModel.FLUX2_PRO),
                "Target model: x");

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<String>> mismatches = new AtomicReference<>(List.of());
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            String previous = Application.getUserAgentStylesheet();
            try {
                List<String> found = new ArrayList<>();
                for (String theme : ThemeManager.getAvailableThemes()) {
                    Application.setUserAgentStylesheet(ThemeManager.getAtlantaFXTheme(theme).getUserAgentStylesheet());
                    BrainstormDialog dialog = new BrainstormDialog(null, null, service, context,
                            ModelLabels.displayName(GenerationModel.FLUX2_PRO), result -> { });
                    Region root = (Region) dialog.getScene().getRoot();
                    root.applyCss();
                    Region probe = new Region();
                    probe.setStyle("-fx-background-color: -color-bg-inset;");
                    new Scene(probe);
                    probe.applyCss();
                    Paint expected = probe.getBackground().getFills().get(0).getFill();
                    Paint painted = root.getBackground() != null ? root.getBackground().getFills().get(0).getFill()
                            : null;
                    if (!expected.equals(painted))
                        found.add(theme + ": " + painted + " instead of " + expected);
                }
                mismatches.set(found);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                Application.setUserAgentStylesheet(previous);
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "the dialogs were not built in time");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        assertEquals(List.of(), mismatches.get(), "the gaps of the dividers show the window background, not white");
    }

    @Test
    void showsTheMoodTheNewBlocksAndTheLockedFieldOfAStoredDraftAndAdoptsTheNewCamera() throws Exception {
        PromptBlock calm = new PromptBlock("std:atmosphere:calm", PromptBlock.Type.ATMOSPHERE, "Calm", "calm", null,
                false);
        PromptBlock blueHour = new PromptBlock("std:light:blue-hour", PromptBlock.Type.LIGHT, "Blue Hour", "blue hour",
                null, false);
        Mood morning = new Mood("std:mood:quiet-morning", "Quiet Morning");
        morning.setReference(Mood.Field.ATMOSPHERE, calm.getId());
        morning.setReference(Mood.Field.LIGHT, blueHour.getId());
        BrainstormContext context = new BrainstormContext(List.of(), List.of(calm), List.of(blueHour), List.of(),
                List.of(new ColorStyle("Ocean Breeze", List.of("#48BDFF"), true)), List.of(morning),
                List.of(GenerationModel.FLUX2_PRO), "Target model: x",
                List.of(ModelLabels.displayName(GenerationModel.FLUX2_PRO)), Set.of(Mood.Field.LIGHT));

        BrainstormRepository repository = mock(BrainstormRepository.class);
        when(repository.findAllSessions()).thenReturn(List.of(new BrainstormSession("s1", "Leuchtturm",
                LocalDateTime.now(), LocalDateTime.now())));
        when(repository.findLatestDraft("s1")).thenReturn(Optional.of("""
            {"prompt": "A lighthouse", "mood": "Quiet Morning",
             "style": {"name": "Ink Wash", "text": "ink wash on rice paper", "is_new": true},
             "color": {"name": "Sea", "colors": ["#112233"], "is_new": true},
             "camera": {"name": "Harbor Tele", "settings": {"body": "FULL_FRAME", "lens": "MM_135"}, "is_new": true}}
            """));
        CameraSettings tele = new CameraSettings(CameraSettings.Body.FULL_FRAME, CameraSettings.Lens.MM_135,
                null, null, null, null, null);
        BrainstormService service = new BrainstormService(repository, mock(GeminiApiService.class));

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<CheckBox>> checks = new AtomicReference<>();
        AtomicReference<List<String>> labels = new AtomicReference<>();
        AtomicReference<BrainstormDialog.Result> adopted = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                /*  The worker runs at once, so the stored session is open when the dialog is built  */
                BrainstormDialog dialog = new BrainstormDialog(null, null, service, context,
                        ModelLabels.displayName(GenerationModel.FLUX2_PRO), adopted::set,
                        new BrainstormWorker(Runnable::run, Runnable::run));
                dialog.getScene().getRoot().applyCss();
                checks.set(dialog.getScene().getRoot().lookupAll(".check-box").stream()
                        .map((node) -> (CheckBox) node).toList());
                labels.set(dialog.getScene().getRoot().lookupAll(".label").stream()
                        .map((node) -> ((Label) node).getText()).toList());
                dialog.getScene().getRoot().lookupAll(".button").stream().map((node) -> (Button) node)
                        .filter((button) -> I18nManager.getText("brainstorm.adopt").equals(button.getText()))
                        .findFirst().orElseThrow().fire();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(20, TimeUnit.SECONDS), "the dialog was not built in time");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        String myList = I18nManager.getText("brainstorm.block.mylist");
        assertEquals(3, checks.get().stream().filter((check) -> myList.equals(check.getText()) && check.isSelected())
                .count(), "the new style, the new palette and the new camera go into the list of the user");
        assertTrue(labels.get().contains(CameraPromptBuilder.build(tele, true)), "a new camera shows its fragment");
        CheckBox locked = checks.get().stream().filter(CheckBox::isDisabled)
                .filter((check) -> check.getTooltip() != null).findFirst().orElseThrow();
        assertFalse(locked.isSelected(), "the locked light is not adopted");
        BrainstormDraft.Block camera = adopted.get().blocks().get(Mood.Field.CAMERA);
        assertEquals("Harbor Tele", camera.name());
        assertEquals(tele, camera.settings(), "the new camera is adopted with its settings");
        assertFalse(adopted.get().blocks().containsKey(Mood.Field.LIGHT));
    }

    @Test
    void readsTheStoredSessionsOffTheJavaFxThreadAndOpensTheLastOne() throws Exception {
        BrainstormRepository repository = mock(BrainstormRepository.class);
        List<Boolean> onFxThread = new java.util.concurrent.CopyOnWriteArrayList<>();
        when(repository.findAllSessions()).thenAnswer((invocation) -> {
            onFxThread.add(Platform.isFxApplicationThread());
            return List.of(new BrainstormSession("s1", "Leuchtturm", LocalDateTime.now(), LocalDateTime.now()));
        });
        when(repository.findMessages("s1")).thenAnswer((invocation) -> {
            onFxThread.add(Platform.isFxApplicationThread());
            return List.of(new art.moodframe.model.BrainstormMessage(art.moodframe.model.BrainstormMessage.ROLE_USER,
                    "Ein Leuchtturm"));
        });
        when(repository.findLatestDraft("s1")).thenAnswer((invocation) -> {
            onFxThread.add(Platform.isFxApplicationThread());
            return Optional.of("{\"prompt\": \"A lighthouse at dusk\"}");
        });
        BrainstormService service = new BrainstormService(repository, mock(GeminiApiService.class));
        BrainstormContext context = new BrainstormContext(List.of(), List.of(), List.of(GenerationModel.FLUX2_PRO),
                "Target model: x");

        AtomicReference<BrainstormDialog> dialog = new AtomicReference<>();
        Platform.runLater(() -> dialog.set(new BrainstormDialog(null, null, service, context,
                ModelLabels.displayName(GenerationModel.FLUX2_PRO), result -> { })));

        /*  The stored session opens once the worker has read it, its draft fills the prompt  */
        long until = System.currentTimeMillis() + 10_000;
        String prompt = "";
        while (!"A lighthouse at dusk".equals(prompt) && System.currentTimeMillis() < until) {
            Thread.sleep(50);
            AtomicReference<String> shown = new AtomicReference<>("");
            CountDownLatch read = new CountDownLatch(1);
            Platform.runLater(() -> {
                if (dialog.get() != null) {
                    dialog.get().getScene().getRoot().applyCss();
                    dialog.get().getScene().getRoot().lookupAll(".text-area").stream()
                            .map((node) -> ((javafx.scene.control.TextArea) node).getText())
                            .filter((text) -> text.startsWith("A lighthouse")).findFirst().ifPresent(shown::set);
                }
                read.countDown();
            });
            read.await(5, TimeUnit.SECONDS);
            prompt = shown.get();
        }
        assertEquals("A lighthouse at dusk", prompt);
        assertEquals(List.of(false, false, false), onFxThread, "the window reads the sessions on its worker");
    }

    @Test
    void offersOnlyTheAccountsWithLanguageModelAndWebSearch() throws Exception {
        art.moodframe.config.ApplicationConfig config = art.moodframe.config.ConfigMocks.config();
        when(config.accounts().hasApiKey(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        when(config.accounts().isEnabled(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        when(config.accounts().isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI)).thenReturn(false);
        when(config.accounts().isBrainstormAvailable(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().getOpenAiChatModel()).thenReturn("gpt-x");
        BrainstormRepository repository = mock(BrainstormRepository.class);
        BrainstormService service = new BrainstormService(repository, mock(GeminiApiService.class));
        BrainstormContext context = new BrainstormContext(List.of(), List.of(), List.of(GenerationModel.FLUX2_PRO),
                "Target model: x");

        /*  Gemini has a key but no web search: only OpenAI is offered, so there is nothing to choose  */
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<String>> items = new AtomicReference<>();
        AtomicReference<Boolean> disabled = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                BrainstormDialog dialog = new BrainstormDialog(null, config, service, context,
                        ModelLabels.displayName(GenerationModel.FLUX2_PRO), result -> { });
                dialog.getScene().getRoot().applyCss();
                ComboBox<?> box = (ComboBox<?>) dialog.getScene().getRoot().lookup(".combo-box");
                items.set(box.getItems().stream().map(String::valueOf).toList());
                disabled.set(box.isDisabled());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "the dialog was not built in time");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        assertEquals(List.of(I18nManager.getText("brainstorm.engine.openai", "gpt-x")), items.get());
        assertTrue(disabled.get(), "a single account leaves nothing to choose");
    }

    @Test
    void aReopenedWindowWaitsForTheTurnOfTheClosedOneAndShowsItsAnswer() throws Exception {
        BrainstormSession stored = new BrainstormSession("s1", "Leuchtturm", LocalDateTime.now(), LocalDateTime.now());
        BrainstormRepository repository = mock(BrainstormRepository.class);
        List<art.moodframe.model.BrainstormMessage> messages = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<String> drafts = new java.util.concurrent.CopyOnWriteArrayList<>();
        when(repository.findAllSessions()).thenReturn(List.of(stored));
        when(repository.findMessages("s1")).thenAnswer((invocation) -> new ArrayList<>(messages));
        when(repository.findLatestDraft("s1")).thenAnswer((invocation) ->
                drafts.isEmpty() ? Optional.empty() : Optional.of(drafts.getLast()));
        org.mockito.Mockito.doAnswer((invocation) -> messages.add(invocation.getArgument(1)))
                .when(repository).addMessage(org.mockito.ArgumentMatchers.eq("s1"), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doAnswer((invocation) -> drafts.add(invocation.getArgument(1)))
                .when(repository).addDraft(org.mockito.ArgumentMatchers.eq("s1"), org.mockito.ArgumentMatchers.any());
        GeminiApiService gemini = mock(GeminiApiService.class);
        java.util.concurrent.CompletableFuture<art.moodframe.service.provider.ChatTurn.Model> gate =
                new java.util.concurrent.CompletableFuture<>();
        when(gemini.chatTurn(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(gate);

        /*  Both windows work on the one store of the sessions, as the factory of the composition root hands it out  */
        art.moodframe.service.brainstorm.BrainstormSessions sessions =
                new art.moodframe.service.brainstorm.BrainstormSessions(repository);
        BrainstormService closed = new BrainstormService(sessions,
                new art.moodframe.service.brainstorm.BrainstormModels(gemini, null, null),
                new art.moodframe.service.brainstorm.BrainstormTools(gemini, null));
        BrainstormService reopened = new BrainstormService(sessions,
                new art.moodframe.service.brainstorm.BrainstormModels(gemini, null, null),
                new art.moodframe.service.brainstorm.BrainstormTools(gemini, null));
        BrainstormContext context = new BrainstormContext(List.of(), List.of(), List.of(GenerationModel.FLUX2_PRO),
                "Target model: x");
        closed.sendUserMessage(stored, "Ein Leuchtturm", context);
        org.mockito.Mockito.verify(gemini, org.mockito.Mockito.timeout(5000)).chatTurn(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyBoolean());

        AtomicReference<BrainstormDialog> dialog = new AtomicReference<>();
        Platform.runLater(() -> dialog.set(new BrainstormDialog(null, null, reopened, context,
                ModelLabels.displayName(GenerationModel.FLUX2_PRO), result -> { },
                new BrainstormWorker(Runnable::run, Platform::runLater))));
        assertTrue(waitFor(dialog, (texts) -> texts.contains("Ein Leuchtturm")), "the stored question is shown");
        assertFalse(texts(dialog).contains("Ein Licht am Meer?"));

        gate.complete(new art.moodframe.service.provider.ChatTurn.Model("{\"step\": 1, "
                + "\"message\": \"Ein Licht am Meer?\", \"draft\": {\"prompt\": \"A lighthouse at dusk\"}}"));
        assertTrue(waitFor(dialog, (texts) -> texts.contains("Ein Licht am Meer?")
                && texts.contains("A lighthouse at dusk")), "the answer of the closed window arrives here");
    }

    /*  Polls the texts of the labels, text flows and text areas of the window until they match  */
    private static boolean waitFor(AtomicReference<BrainstormDialog> dialog,
            java.util.function.Predicate<List<String>> match) throws Exception {
        long until = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < until) {
            if (match.test(texts(dialog)))
                return true;
            Thread.sleep(50);
        }
        return false;
    }

    private static List<String> texts(AtomicReference<BrainstormDialog> dialog) throws Exception {
        List<String> texts = new java.util.concurrent.CopyOnWriteArrayList<>();
        CountDownLatch read = new CountDownLatch(1);
        Platform.runLater(() -> {
            if (dialog.get() != null) {
                javafx.scene.Parent root = dialog.get().getScene().getRoot();
                root.applyCss();
                collect(root, texts);
            }
            read.countDown();
        });
        read.await(5, TimeUnit.SECONDS);
        return texts;
    }

    private static void collect(javafx.scene.Node node, List<String> texts) {
        if (node instanceof Label label)
            texts.add(label.getText());
        else if (node instanceof javafx.scene.text.Text text)
            texts.add(text.getText());
        else if (node instanceof javafx.scene.control.TextInputControl input)
            texts.add(input.getText());
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable())
                collect(child, texts);
        }
        if (node instanceof javafx.scene.control.ScrollPane pane && pane.getContent() != null)
            collect(pane.getContent(), texts);
    }
}
