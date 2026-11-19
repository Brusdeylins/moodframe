package art.moodframe.ui.controller.settings;

import javafx.concurrent.Task;
import javafx.scene.Node;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Runs the blocking work of a section of the settings on a thread of its own, off the JavaFX thread: a connection
 * test, the backup, its import and the reset, which write the library. The nodes that started it are disabled
 * meanwhile; the result, or what the work threw, comes back on the JavaFX thread.
 */
final class SectionTask {

    private SectionTask() {
    }

    /**
     * Runs a work on a daemon thread of its own.
     *
     * @param busy     the nodes disabled while the work runs
     * @param work     the work, run off the JavaFX thread
     * @param onResult takes the result on the JavaFX thread
     * @param onError  takes what the work threw on the JavaFX thread
     * @param <T>      the type of the result
     */
    static <T> void run(List<? extends Node> busy, Callable<T> work, Consumer<T> onResult,
            Consumer<Throwable> onError) {
        busy.forEach((node) -> node.setDisable(true));
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded((event) -> {
            busy.forEach((node) -> node.setDisable(false));
            onResult.accept(task.getValue());
        });
        task.setOnFailed((event) -> {
            busy.forEach((node) -> node.setDisable(false));
            onError.accept(task.getException());
        });

        Thread thread = new Thread(task, "MoodFrame-settings");
        thread.setDaemon(true);
        thread.start();
    }
}
