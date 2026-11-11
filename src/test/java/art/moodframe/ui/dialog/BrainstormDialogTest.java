package art.moodframe.ui.dialog;

import art.moodframe.model.BrainstormSession;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.BrainstormRepository;
import art.moodframe.service.BrainstormContext;
import art.moodframe.service.BrainstormService;
import art.moodframe.service.GeminiApiService;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
}
