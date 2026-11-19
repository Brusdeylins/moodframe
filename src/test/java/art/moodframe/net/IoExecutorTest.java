package art.moodframe.net;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks that the blocking network work runs on named daemon threads of its own pool. */
class IoExecutorTest {

    @Test
    void runsOnNamedDaemonThreadsOutsideTheCommonPool() {
        Thread thread = CompletableFuture.supplyAsync(Thread::currentThread, IoExecutor.get()).join();

        assertTrue(thread.isDaemon());
        assertTrue(thread.getName().startsWith("moodframe-io-"), thread.getName());
        assertFalse(thread.getName().contains("ForkJoinPool"));
    }

    @Test
    void isOnePoolUntilItIsShutDown() {
        ExecutorService first = IoExecutor.get();
        assertSame(first, IoExecutor.get());

        IoExecutor.shutdown();
        assertTrue(first.isShutdown());
        assertNotSame(first, IoExecutor.get());
    }
}
