package art.moodframe.ui.dialog;

import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormSession;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.BrainstormRepository;
import art.moodframe.service.BrainstormContext;
import art.moodframe.service.BrainstormService;
import art.moodframe.service.GeminiApiService;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
                new BrainstormDialog(null, service, context,
                        GenerationModel.OMNI_720P.getLocalizedDisplayName(), result -> { });
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
    void showsTheMoodTheNewBlocksAndTheLockedFieldOfAStoredDraft() throws Exception {
        PromptBlock calm = new PromptBlock("std:atmosphere:calm", PromptBlock.Type.ATMOSPHERE, "Calm", "calm", null,
                false);
        PromptBlock blueHour = new PromptBlock("std:light:blue-hour", PromptBlock.Type.LIGHT, "Blue Hour", "blue hour",
                null, false);
        Mood morning = new Mood("std:mood:quiet-morning", "Quiet Morning");
        morning.setReference(Mood.Field.ATMOSPHERE, calm.getId());
        morning.setReference(Mood.Field.LIGHT, blueHour.getId());
        BrainstormContext context = new BrainstormContext(List.of(), List.of(calm), List.of(blueHour),
                List.of(new ColorStyle("Ocean Breeze", List.of("#48BDFF"), true)), List.of(morning),
                List.of(GenerationModel.FLUX2_PRO), "Target model: x",
                List.of(GenerationModel.FLUX2_PRO.getLocalizedDisplayName()), Set.of(Mood.Field.LIGHT));

        BrainstormRepository repository = mock(BrainstormRepository.class);
        when(repository.findAllSessions()).thenReturn(List.of(new BrainstormSession("s1", "Leuchtturm",
                LocalDateTime.now(), LocalDateTime.now())));
        when(repository.findLatestDraft("s1")).thenReturn(Optional.of(BrainstormDraft.fromJson(new JSONObject("""
            {"prompt": "A lighthouse", "mood": "Quiet Morning",
             "style": {"name": "Ink Wash", "text": "ink wash on rice paper", "is_new": true},
             "color": {"name": "Sea", "colors": ["#112233"], "is_new": true}}
            """))));
        BrainstormService service = new BrainstormService(repository, mock(GeminiApiService.class));

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<CheckBox>> checks = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                BrainstormDialog dialog = new BrainstormDialog(null, service, context,
                        GenerationModel.FLUX2_PRO.getLocalizedDisplayName(), result -> { });
                dialog.getScene().getRoot().applyCss();
                checks.set(dialog.getScene().getRoot().lookupAll(".check-box").stream()
                        .map((node) -> (CheckBox) node).toList());
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
        assertEquals(2, checks.get().stream().filter((check) -> myList.equals(check.getText()) && check.isSelected())
                .count(), "the new style and the new palette go into the list of the user");
        CheckBox locked = checks.get().stream().filter(CheckBox::isDisabled)
                .filter((check) -> check.getTooltip() != null).findFirst().orElseThrow();
        assertFalse(locked.isSelected(), "the locked light is not adopted");
    }
}
