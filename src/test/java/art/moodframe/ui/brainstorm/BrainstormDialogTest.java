package art.moodframe.ui.brainstorm;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormSession;
import art.moodframe.model.CameraSettings;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.database.TestDatabase;
import art.moodframe.repository.BrainstormRepository;
import art.moodframe.service.brainstorm.BrainstormContext;
import art.moodframe.service.brainstorm.BrainstormService;
import art.moodframe.service.brainstorm.TestBrainstorm;
import art.moodframe.service.generation.ModelLabels;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.library.TestLibraryServices;
import art.moodframe.service.prompt.CameraPromptBuilder;
import art.moodframe.service.provider.gemini.GeminiApiService;
import art.moodframe.util.I18nManager;
import art.moodframe.ui.panel.MoodFramePanelController;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

        /*  The toolkit stays when a shown dialog, the last window, closes  */
        Platform.setImplicitExit(false);
        TestDatabase.open(home.resolve("brainstorm.db").toString());
    }

    @TempDir
    static Path home;

    /*  The dialogs a test built: an unshown window never hides, so it is released after the test, as a closed one  */
    private static final List<BrainstormDialog> built = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static BrainstormDialog track(BrainstormDialog dialog) {
        built.add(dialog);
        return dialog;
    }

    @AfterEach
    void releaseTheDialogs() throws Exception {
        CountDownLatch released = new CountDownLatch(1);
        Platform.runLater(() -> {
            for (BrainstormDialog dialog : built)
                if (!dialog.isShowing())
                    dialog.fireEvent(new javafx.stage.WindowEvent(dialog, javafx.stage.WindowEvent.WINDOW_HIDDEN));
            built.clear();
            released.countDown();
        });
        assertTrue(released.await(10, TimeUnit.SECONDS));
    }

    /*  The groups Mood and Frame on the standard library, as the main window has them: no mood, no frame  */
    private static BrainstormDialog.Groups groups(Set<Mood.Field> locked) {
        PromptBlockService blocks = TestLibraryServices.blocks();
        ColorStyleService colors = TestLibraryServices.palettes();
        return new BrainstormDialog.Groups(new MoodFramePanelController.Services(null, blocks, colors,
                TestLibraryServices.moods(blocks, colors), TestLibraryServices.frames(),
                TestLibraryServices.favorites(), null),
                new MoodFramePanelController.Selection(null, false, Map.of(), locked, null, false, "16:9", "1K"));
    }

    @Test
    void buildsWithHistoryAndVideoModelsWithoutErrors() throws Exception {
        BrainstormRepository repository = mock(BrainstormRepository.class);
        when(repository.findAllSessions()).thenReturn(List.of(new BrainstormSession("s1", "Serverausfall",
                LocalDateTime.now(), LocalDateTime.now())));
        BrainstormService service = TestBrainstorm.service(repository, mock(GeminiApiService.class));
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
                track(new BrainstormDialog(null, null, service, context,
                        ModelLabels.displayName(GenerationModel.OMNI_720P), groups(Set.of()), result -> { }));
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
        BrainstormService service = TestBrainstorm.service(repository, mock(GeminiApiService.class));
        BrainstormContext context = new BrainstormContext(List.of(), List.of(), List.of(GenerationModel.FLUX2_PRO),
                "Target model: x");

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<String>> grips = new AtomicReference<>();
        AtomicReference<List<Double>> baselines = new AtomicReference<>();
        AtomicReference<List<String>> stylesheets = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                BrainstormDialog dialog = track(new BrainstormDialog(null, null, service, context,
                        ModelLabels.displayName(GenerationModel.FLUX2_PRO), groups(Set.of()), result -> { }));
                dialog.getScene().getRoot().applyCss();
                dialog.getScene().getRoot().layout();
                grips.set(dialog.getScene().getRoot().lookupAll(".split-pane").stream()
                        .filter((node) -> node.getStyleClass().contains("grip-split-pane"))
                        .map((node) -> ((SplitPane) node).getOrientation().name()).toList());
                baselines.set(dialog.getScene().getRoot().lookupAll(".title-4").stream().map((node) -> (Label) node)
                        .filter((label) -> !label.getParent().getStyleClass().contains("panel-group-header"))
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
        BrainstormService service = TestBrainstorm.service(repository, mock(GeminiApiService.class));
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
                    BrainstormDialog dialog = track(new BrainstormDialog(null, null, service, context,
                            ModelLabels.displayName(GenerationModel.FLUX2_PRO), groups(Set.of()), result -> { }));
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
                List.of(ModelLabels.displayName(GenerationModel.FLUX2_PRO)), Set.of(Mood.Field.LIGHT), List.of(),
                List.of());

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
        BrainstormService service = TestBrainstorm.service(repository, mock(GeminiApiService.class));

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<CheckBox>> checks = new AtomicReference<>();
        AtomicReference<List<String>> labels = new AtomicReference<>();
        AtomicReference<BrainstormDialog.Result> adopted = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                /*  The worker runs at once, so the stored session is open when the dialog is built  */
                BrainstormDialog dialog = track(new BrainstormDialog(null, null, service, context,
                        ModelLabels.displayName(GenerationModel.FLUX2_PRO), groups(Set.of(Mood.Field.LIGHT)),
                        adopted::set,
                        new BrainstormWorker(Runnable::run, Runnable::run)));
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
        assertEquals(3, checks.get().stream().filter((check) -> check.getTooltip() != null
                && myList.equals(check.getTooltip().getText()) && check.isSelected()).count(),
                "the new style, the new palette and the new camera go into the list of the user");
        assertTrue(labels.get().contains(CameraPromptBuilder.build(tele, true)), "a new camera shows its fragment");
        String lockedText = I18nManager.getText("brainstorm.block.locked");
        CheckBox locked = checks.get().stream().filter(CheckBox::isDisabled).filter((check) -> check.getTooltip() != null
                && lockedText.equals(check.getTooltip().getText())).findFirst().orElseThrow();
        assertFalse(locked.isSelected(), "the locked light is not adopted");
        assertEquals("std:mood:quiet-morning", adopted.get().moodId(), "the mood of the draft is adopted");
        assertEquals("std:atmosphere:calm", adopted.get().blocks().get(Mood.Field.ATMOSPHERE).id(),
                "the mood ticks the blocks it sets");
        BrainstormDraft.Block camera = adopted.get().blocks().get(Mood.Field.CAMERA);
        assertEquals("Harbor Tele", camera.name());
        assertEquals(tele, camera.settings(), "the new camera is adopted with its settings");
        assertFalse(adopted.get().blocks().containsKey(Mood.Field.LIGHT));
    }

    /*  the result column alone, recording what it adopts  */
    private static BrainstormResultForm form(BrainstormContext context, GenerationModel model,
            AtomicReference<BrainstormDialog.Result> adopted) {
        return new BrainstormResultForm(context, ModelLabels.displayName(model), groups(Set.of()), Runnable::run,
                new BrainstormResultForm.Listener() {
                    @Override
                    public void changed(org.json.JSONObject event) {
                    }

                    @Override
                    public void promptEdited(String prompt) {
                    }

                    @Override
                    public void adopted(BrainstormDialog.Result result) {
                        adopted.set(result);
                    }
                });
    }

    private static void adopt(BrainstormResultForm form) {
        form.lookupAll(".button").stream().map((node) -> (Button) node)
                .filter((button) -> I18nManager.getText("brainstorm.adopt").equals(button.getText()))
                .findFirst().orElseThrow().fire();
    }

    /*  a checkbox of the form; the scroll pane holds its content only with a skin, which needs a scene  */
    private static CheckBox check(BrainstormResultForm form, String text) {
        if (form.getScene() == null)
            new Scene(form);
        form.applyCss();
        return form.lookupAll(".check-box").stream().map((node) -> (CheckBox) node)
                .filter((check) -> text.equals(check.getText())).findFirst().orElseThrow();
    }

    @Test
    void aDraftArrivingWhileTheChatWaitsTicksAndAdoptsItsMood() throws Exception {
        PromptBlock calm = new PromptBlock("std:atmosphere:calm", PromptBlock.Type.ATMOSPHERE, "Calm", "calm", null,
                false);
        Mood morning = new Mood("std:mood:quiet-morning", "Quiet Morning");
        morning.setReference(Mood.Field.ATMOSPHERE, calm.getId());
        BrainstormContext context = new BrainstormContext(List.of(), List.of(calm), List.of(), List.of(), List.of(),
                List.of(morning), List.of(GenerationModel.FLUX2_PRO), "Target model: x",
                List.of(ModelLabels.displayName(GenerationModel.FLUX2_PRO)), Set.of(), List.of(), List.of());
        BrainstormDraft draft = new BrainstormDraft();
        draft.setPrompt("A lighthouse");
        draft.setMood(new BrainstormDraft.Block("std:mood:quiet-morning", "Quiet Morning", null, List.of(), false));
        AtomicReference<BrainstormDialog.Result> adopted = new AtomicReference<>();

        onFx(() -> {

            /*  As after a live answer: the window shows the draft before it ends the busy state  */
            BrainstormResultForm form = form(context, GenerationModel.FLUX2_PRO, adopted);
            form.setBusy(true);
            form.show(draft);
            form.setBusy(false);
            adopt(form);
            return null;
        });

        assertEquals("std:mood:quiet-morning", adopted.get().moodId(), "the mood of a live answer is adopted");
        assertEquals("std:atmosphere:calm", adopted.get().blocks().get(Mood.Field.ATMOSPHERE).id(),
                "the blocks of the mood are ticked");
    }

    @Test
    void ticksTheProposedSavedImagesOnlyAsFarAsTheTargetModelTakesThem() throws Exception {
        List<art.moodframe.model.SavedImage> images = List.of(
                new art.moodframe.model.SavedImage("img-1", "Logo", "logo.png", 200, 100, "a", "the logo", false,
                        LocalDateTime.now(), null),
                new art.moodframe.model.SavedImage("img-2", "Product", "product.png", 200, 100, "b", "the product",
                        false, LocalDateTime.now(), null));
        List<GenerationModel> models = List.of(GenerationModel.FLUX2_PRO, GenerationModel.OMNI_720P);
        BrainstormContext context = new BrainstormContext(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), models, "Target model: x", models.stream().map(ModelLabels::displayName).toList(),
                Set.of(), List.of(), images);
        BrainstormDraft draft = new BrainstormDraft();
        draft.setPrompt("A poster");
        draft.getSavedImages().add(new BrainstormDraft.Block("img-1", "Logo", null, List.of(), false));
        draft.getSavedImages().add(new BrainstormDraft.Block("img-2", "Product", null, List.of(), false));
        AtomicReference<BrainstormDialog.Result> image = new AtomicReference<>();
        AtomicReference<BrainstormDialog.Result> video = new AtomicReference<>();

        List<Boolean> videoState = onFx(() -> {
            BrainstormResultForm form = form(context, GenerationModel.FLUX2_PRO, image);
            form.show(draft);
            adopt(form);

            /*  Omni takes one image, its start frame: none ticked, and one ticked disables the other  */
            BrainstormResultForm omni = form(context, GenerationModel.OMNI_720P, video);
            omni.show(draft);
            boolean noneTicked = !check(omni, "Logo").isSelected() && !check(omni, "Product").isSelected();
            check(omni, "Logo").setSelected(true);
            boolean otherDisabled = check(omni, "Product").isDisable();
            adopt(omni);
            return List.of(noneTicked, otherDisabled);
        });

        assertEquals(List.of("img-1", "img-2"), image.get().savedImageIds(), "an image model takes both");
        assertTrue(videoState.get(0), "a video model ticks no proposed image");
        assertTrue(videoState.get(1), "at the limit of the model the other image cannot be ticked");
        assertEquals(List.of("img-1"), video.get().savedImageIds());
    }

    @Test
    void showsAndAdoptsTheVariantOfTheVideoResolutionOfTheDraftAndTheImageResolutionAsBefore() throws Exception {
        List<GenerationModel> models = List.of(GenerationModel.FLUX2_PRO, GenerationModel.NANO_BANANA2_1K,
                GenerationModel.NANO_BANANA2_2K, GenerationModel.OMNI_720P, GenerationModel.OMNI_1080P);
        BrainstormContext context = new BrainstormContext(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), models, "Target model: x", models.stream().map(ModelLabels::displayName).toList(),
                Set.of(), List.of(), List.of());
        AtomicReference<BrainstormDialog.Result> video = new AtomicReference<>();
        AtomicReference<BrainstormDialog.Result> closest = new AtomicReference<>();
        AtomicReference<BrainstormDialog.Result> image = new AtomicReference<>();

        String hint = onFx(() -> {
            BrainstormResultForm omni = form(context, GenerationModel.FLUX2_PRO, video);
            omni.show(draft("Gemini Omni", "1080p"));
            new Scene(omni);
            omni.applyCss();
            String shown = omni.lookupAll(".label").stream().map((node) -> (Label) node)
                    .filter((label) -> label.isVisible() && label.getText() != null
                            && label.getText().startsWith("Gemini Omni → "))
                    .map(Label::getText).findFirst().orElse(null);
            adopt(omni);

            BrainstormResultForm unknown = form(context, GenerationModel.FLUX2_PRO, closest);
            unknown.show(draft("Gemini Omni", "1440p"));
            adopt(unknown);

            BrainstormResultForm banana = form(context, GenerationModel.FLUX2_PRO, image);
            banana.show(draft("Nano Banana 2", "2K"));
            adopt(banana);
            return shown;
        });

        assertEquals("Gemini Omni → 1080p", hint, "the group Frame names the variant of the draft");
        assertEquals(ModelLabels.displayName(GenerationModel.OMNI_1080P), video.get().targetModel());
        assertEquals("2K", video.get().resolution(), "the target that selects 1080p in the main window as well");
        assertEquals(ModelLabels.displayName(GenerationModel.OMNI_1080P), closest.get().targetModel(),
                "a resolution the model does not have takes the closest variant");
        assertEquals(ModelLabels.displayName(GenerationModel.NANO_BANANA2_2K), image.get().targetModel());
        assertEquals("2K", image.get().resolution(), "an image resolution goes into the target as before");
    }

    /*  a draft that names a target model and a resolution  */
    private static BrainstormDraft draft(String model, String resolution) {
        BrainstormDraft draft = new BrainstormDraft();
        draft.setPrompt("A lighthouse");
        draft.setTargetModel(model);
        draft.setResolution(resolution);
        return draft;
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
        BrainstormService service = TestBrainstorm.service(repository, mock(GeminiApiService.class));
        BrainstormContext context = new BrainstormContext(List.of(), List.of(), List.of(GenerationModel.FLUX2_PRO),
                "Target model: x");

        AtomicReference<BrainstormDialog> dialog = new AtomicReference<>();
        Platform.runLater(() -> dialog.set(track(new BrainstormDialog(null, null, service, context,
                ModelLabels.displayName(GenerationModel.FLUX2_PRO), groups(Set.of()), result -> { }))));

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
        BrainstormService service = TestBrainstorm.service(repository, mock(GeminiApiService.class));
        BrainstormContext context = new BrainstormContext(List.of(), List.of(), List.of(GenerationModel.FLUX2_PRO),
                "Target model: x");

        /*  Gemini has a key but no web search: only OpenAI is offered, so there is nothing to choose  */
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<String>> items = new AtomicReference<>();
        AtomicReference<Boolean> disabled = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                BrainstormDialog dialog = track(new BrainstormDialog(null, config, service, context,
                        ModelLabels.displayName(GenerationModel.FLUX2_PRO), groups(Set.of()), result -> { }));
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
    void followsTheLanguageWhileOpenAndKeepsTheChoices() throws Exception {
        art.moodframe.config.ApplicationConfig config = art.moodframe.config.ConfigMocks.config();
        when(config.accounts().isBrainstormAvailable(ApiProvider.GOOGLE_GEMINI)).thenReturn(true);
        when(config.accounts().isBrainstormAvailable(ApiProvider.OPENAI)).thenReturn(true);
        when(config.accounts().getOpenAiChatModel()).thenReturn("gpt-x");
        when(config.accounts().getBrainstormEngine()).thenReturn("openai");
        BrainstormRepository repository = mock(BrainstormRepository.class);
        BrainstormService service = TestBrainstorm.service(repository, mock(GeminiApiService.class));
        BrainstormContext context = new BrainstormContext(List.of(), List.of(), List.of(GenerationModel.FLUX2_PRO),
                "Target model: x");
        java.util.Locale locale = I18nManager.getCurrentLocale();
        AtomicReference<BrainstormDialog> dialog = new AtomicReference<>();
        try {
            int listeners = onFx(() -> {
                I18nManager.setLocale(java.util.Locale.ENGLISH);
                int before = I18nManager.getChangeListenerCount();
                dialog.set(track(new BrainstormDialog(null, config, service, context,
                        ModelLabels.displayName(GenerationModel.FLUX2_PRO), groups(Set.of()), result -> { },
                        new BrainstormWorker(Runnable::run, Runnable::run))));
                dialog.get().show();
                return before;
            });
            assertTrue(texts(dialog).contains(I18nManager.getText("brainstorm.result.title")));

            onFx(() -> {
                I18nManager.setLocale(java.util.Locale.GERMAN);
                return null;
            });
            List<String> texts = texts(dialog);
            for (String key : List.of("brainstorm.history.title", "brainstorm.result.title", "brainstorm.result.model",
                    "params.mood.title", "params.style", "brainstorm.session.new"))
                assertTrue(texts.contains(I18nManager.getText(key)), key + " in German: " + texts);
            assertEquals(I18nManager.getText("brainstorm.title"), onFx(() -> dialog.get().getTitle()));
            javafx.scene.Parent root = onFx(() -> dialog.get().getScene().getRoot());
            assertTrue(onFx(() -> root.lookupAll(".button").stream().map((node) -> ((Button) node).getText())
                    .toList()).containsAll(List.of(I18nManager.getText("brainstorm.send"),
                    I18nManager.getText("brainstorm.adopt"), I18nManager.getText("brainstorm.quick.image"))));
            ComboBox<?> engines = onFx(() -> (ComboBox<?>) root.lookup(".combo-box"));
            assertEquals(I18nManager.getText("brainstorm.engine.openai", "gpt-x"), onFx(() -> engines.getValue()),
                    "the chosen language model stays chosen");
            assertTrue(texts.contains(I18nManager.getText("params.mood.none")), "No mood in German: " + texts);
            assertFalse(texts.contains(I18nManager.getTextIn(java.util.Locale.ENGLISH, "params.mood.none")));

            onFx(() -> {
                dialog.get().close();
                return null;
            });
            assertEquals(listeners, onFx(I18nManager::getChangeListenerCount), "a closed window no longer listens");
        }
        finally {
            onFx(() -> {
                I18nManager.setLocale(locale);
                return null;
            });
        }
    }

    /**
     * A new chat whose session could not be stored keeps its message and the failure when the language changes; only
     * an untouched new chat greets anew.
     */
    @Test
    void aNewChatThatCouldNotBeStoredKeepsItsMessagesOnAChangeOfTheLanguage() throws Exception {
        BrainstormRepository repository = mock(BrainstormRepository.class);
        when(repository.createSession(anyString(), any())).thenThrow(new java.sql.SQLException("disk full"));
        BrainstormService service = TestBrainstorm.service(repository, mock(GeminiApiService.class));
        BrainstormContext context = new BrainstormContext(List.of(), List.of(), List.of(GenerationModel.FLUX2_PRO),
                "Target model: x");
        java.util.Locale locale = I18nManager.getCurrentLocale();
        AtomicReference<BrainstormDialog> dialog = new AtomicReference<>();
        try {
            onFx(() -> {
                I18nManager.setLocale(java.util.Locale.ENGLISH);
                dialog.set(track(new BrainstormDialog(null, null, service, context,
                        ModelLabels.displayName(GenerationModel.FLUX2_PRO), groups(Set.of()), result -> { },
                        new BrainstormWorker(Runnable::run, Runnable::run))));
                dialog.get().show();
                javafx.scene.Parent root = dialog.get().getScene().getRoot();
                ((javafx.scene.control.TextArea) root.lookup(".text-area")).setText("A lighthouse at dusk");
                root.lookupAll(".button").stream().map((node) -> (Button) node)
                        .filter((button) -> I18nManager.getText("brainstorm.send").equals(button.getText()))
                        .findFirst().orElseThrow().fire();
                return null;
            });
            String failure = I18nManager.getText("brainstorm.error.session");
            assertTrue(texts(dialog).containsAll(List.of("A lighthouse at dusk", failure)), "sent and failed");

            onFx(() -> {
                I18nManager.setLocale(java.util.Locale.GERMAN);
                return null;
            });
            List<String> texts = texts(dialog);
            assertTrue(texts.containsAll(List.of("A lighthouse at dusk", failure)), "the messages stay: " + texts);
            assertFalse(texts.contains(I18nManager.getText("brainstorm.greeting")), "no greeting in their place");
            onFx(() -> {
                dialog.get().close();
                return null;
            });
        }
        finally {
            onFx(() -> {
                I18nManager.setLocale(locale);
                return null;
            });
        }
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
                new art.moodframe.service.brainstorm.BrainstormTools(gemini, null, null));
        BrainstormService reopened = new BrainstormService(sessions,
                new art.moodframe.service.brainstorm.BrainstormModels(gemini, null, null),
                new art.moodframe.service.brainstorm.BrainstormTools(gemini, null, null));
        BrainstormContext context = new BrainstormContext(List.of(), List.of(), List.of(GenerationModel.FLUX2_PRO),
                "Target model: x");
        closed.sendUserMessage(stored, "Ein Leuchtturm", context);
        org.mockito.Mockito.verify(gemini, org.mockito.Mockito.timeout(5000)).chatTurn(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyBoolean());

        AtomicReference<BrainstormDialog> dialog = new AtomicReference<>();
        Platform.runLater(() -> dialog.set(track(new BrainstormDialog(null, null, reopened, context,
                ModelLabels.displayName(GenerationModel.FLUX2_PRO), groups(Set.of()), result -> { },
                new BrainstormWorker(Runnable::run, Platform::runLater)))));
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

    /*  runs a step on the JavaFX thread and returns its result  */
    private static <T> T onFx(java.util.concurrent.Callable<T> step) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(step.call());
            }
            catch (Throwable t) {
                failure.set(t);
            }
            finally {
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "the step ran in time");
        if (failure.get() != null)
            throw new AssertionError(failure.get());
        return result.get();
    }
}
