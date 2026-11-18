package art.moodframe.ui.support;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A burst of library changes queries the groups once or twice, and the results come in the order of the queries. */
class CoalescedLoadTest {

    /*  the worker and the FX thread as queues this test works off step by step  */
    private final Queue<Runnable> worker = new ArrayDeque<>();
    private final Queue<Runnable> fxThread = new ArrayDeque<>();
    private final AtomicInteger queries = new AtomicInteger();
    private final List<Integer> shown = new ArrayList<>();

    private CoalescedLoad load(boolean failing) {
        return new CoalescedLoad("numbers", worker::add, fxThread::add, () -> {
            Callable<Runnable> query = () -> {
                int number = queries.incrementAndGet();
                if (failing && number == 1)
                    throw new IllegalStateException("database locked");
                return () -> shown.add(number);
            };
            return query;
        });
    }

    /*  runs the queued queries and their results until nothing is left  */
    private void settle() {
        while (!worker.isEmpty() || !fxThread.isEmpty()) {
            while (!worker.isEmpty())
                worker.poll().run();
            while (!fxThread.isEmpty())
                fxThread.poll().run();
        }
    }

    @Test
    void aBurstOfRequestsQueriesOnceMoreAfterTheRunningQuery() {
        CoalescedLoad load = load(false);

        for (int i = 0; i < 10; i++)
            load.request();
        assertEquals(1, worker.size());
        settle();

        assertEquals(2, queries.get());
        assertEquals(List.of(1, 2), shown);
    }

    @Test
    void aSingleRequestQueriesOnce() {
        CoalescedLoad load = load(false);

        load.request();
        settle();
        load.request();
        settle();

        assertEquals(List.of(1, 2), shown);
    }

    @Test
    void aRequestWhileTheResultWaitsForTheFxThreadQueriesAgain() {
        CoalescedLoad load = load(false);

        load.request();
        worker.poll().run();
        load.request();
        assertTrue(worker.isEmpty());
        settle();

        assertEquals(List.of(1, 2), shown);
    }

    @Test
    void aFailedQueryDoesNotBlockTheNext() {
        CoalescedLoad load = load(true);

        load.request();
        settle();
        load.request();
        settle();

        assertEquals(List.of(2), shown);
    }

    @Test
    void nothingToLoadQueriesNothing() {
        CoalescedLoad load = new CoalescedLoad("nothing", worker::add, fxThread::add, () -> null);

        load.request();
        assertTrue(worker.isEmpty());
    }
}
