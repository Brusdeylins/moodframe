package tools.moodframe.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ServiceFactoryTest {

    private static final class Once {
    }

    private static final class Failing {
    }

    @Test
    void createsAServiceOnceEvenIfSeveralThreadsAskAtTheSameTime() throws Exception {
        AtomicInteger created = new AtomicInteger();
        ServiceFactory.getInstance().registerProvider(Once.class, () -> {
            created.incrementAndGet();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new Once();
        });

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Once>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++)
            results.add(pool.submit(() -> ServiceFactory.getInstance().getService(Once.class)));
        Once first = results.get(0).get();
        for (Future<Once> result : results)
            assertSame(first, result.get());
        pool.shutdown();

        assertEquals(1, created.get());
    }

    @Test
    void doesNotCacheAProviderThatReturnsNull() {
        AtomicInteger calls = new AtomicInteger();
        ServiceFactory.getInstance().registerProvider(Failing.class, () -> calls.incrementAndGet() == 1 ? null : new Failing());

        assertNull(ServiceFactory.getInstance().getService(Failing.class));
        assertNotNull(ServiceFactory.getInstance().getService(Failing.class));
        assertEquals(2, calls.get());
    }
}
