package art.moodframe;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.mcp.McpService;
import art.moodframe.service.chat.ThumbnailManager;
import art.moodframe.service.generation.RequestResponseManager;
import art.moodframe.service.trash.TrashCleanupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The shutdown stops only what was created, creates nothing, and a failing or missing service keeps no later step
 * from running, the writing of the settings included.
 */
class ShutdownTest {

    private final ServiceFactory services = ServiceFactory.getInstance();

    @AfterEach
    void clear() {
        services.clearServices();
    }

    @Test
    void aMissingOrFailingServiceKeepsNoLaterStepFromRunning() {
        services.clearServices();

        /*  No trash cleanup, its creation failed; the request manager was never created  */
        AtomicBoolean created = new AtomicBoolean();
        services.registerProvider(RequestResponseManager.class, () -> {
            created.set(true);
            return null;
        });
        ThumbnailManager thumbnails = mock(ThumbnailManager.class);
        doThrow(new IllegalStateException("the thumbnail pool is broken")).when(thumbnails).shutdown();
        services.registerService(ThumbnailManager.class, thumbnails);
        McpService mcp = mock(McpService.class);
        services.registerService(McpService.class, mcp);
        ApplicationConfig config = mock(ApplicationConfig.class);
        services.registerService(ApplicationConfig.class, config);

        MoodframeApplication.shutDown(services);

        assertFalse(created.get(), "the shutdown creates no service");
        verify(mcp).stop();
        verify(config).flushConfiguration();
        assertNull(services.getCreatedService(ApplicationConfig.class), "the services are cleared at the end");
    }

    @Test
    void theTrashCleanupCreatedAtTheStartIsStopped() {
        services.clearServices();
        TrashCleanupService cleanup = mock(TrashCleanupService.class);
        services.registerService(TrashCleanupService.class, cleanup);

        MoodframeApplication.shutDown(services);

        verify(cleanup).shutdown();
    }
}
