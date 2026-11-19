package art.moodframe.ui.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pool of the disk work of the views: bounded, named daemon threads. */
class DiskWorkerTest {

    @Test
    void atMostFourNamedDaemonThreadsWorkAtOnce() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger most = new AtomicInteger();
        Set<String> names = ConcurrentHashMap.newKeySet();
        Set<Boolean> daemons = ConcurrentHashMap.newKeySet();
        List<CompletableFuture<Void>> tasks = IntStream.range(0, DiskWorker.MAX_THREADS + 3)
                .mapToObj((int index) -> CompletableFuture.runAsync(() -> {
                    most.accumulateAndGet(running.incrementAndGet(), Math::max);
                    names.add(Thread.currentThread().getName());
                    daemons.add(Thread.currentThread().isDaemon());
                    try {
                        assertTrue(release.await(10, TimeUnit.SECONDS));
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    running.decrementAndGet();
                }, DiskWorker.get())).toList();

        long deadline = System.currentTimeMillis() + 5000;
        while (running.get() < DiskWorker.MAX_THREADS && System.currentTimeMillis() < deadline)
            Thread.sleep(10);
        Thread.sleep(100);
        assertEquals(DiskWorker.MAX_THREADS, running.get(), "the further tasks wait in the queue");

        release.countDown();
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        assertEquals(DiskWorker.MAX_THREADS, most.get());
        assertTrue(names.stream().allMatch((String name) -> name.startsWith("moodframe-disk-")), names.toString());
        assertEquals(Set.of(true), daemons);
    }
}
