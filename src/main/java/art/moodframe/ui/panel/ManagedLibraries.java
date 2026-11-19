package art.moodframe.ui.panel;

import art.moodframe.model.Frame;
import art.moodframe.model.Mood;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.MoodService;
import art.moodframe.ui.dialog.FrameManageDialog;
import art.moodframe.ui.dialog.MoodManageDialog;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * The moods and frames of the user for their "Manage…" windows: the lists are read on the JavaFX thread, every
 * change, import and export runs on the worker of the window and completes on the JavaFX thread.
 */
final class ManagedLibraries {

    private ManagedLibraries() {
    }

    /**
     * Finds the moods with a block in the trash; each asks the trash for up to five blocks, so this runs on the worker.
     *
     * @param service the moods
     * @param moods   the moods of a list
     * @return the ids of those with a block in the trash
     */
    static Set<String> incomplete(MoodService service, List<Mood> moods) {
        return moods.stream().filter((mood) -> !service.trashedBlocks(mood).isEmpty()).map(Mood::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The moods of the user, changed on the worker.
     *
     * @param service the moods
     * @param writes  the worker of the window
     * @return the library of the window
     */
    static MoodManageDialog.Library moods(MoodService service, PanelWorker writes) {
        return new MoodManageDialog.Library() {
            @Override
            public List<Mood> list() {
                return service.getCustom();
            }

            @Override
            public boolean exists(String name) {
                return service.exists(name);
            }

            @Override
            public CompletableFuture<Set<String>> incomplete(List<Mood> moods) {
                List<Mood> listed = List.copyOf(moods);
                return writes.call(() -> ManagedLibraries.incomplete(service, listed));
            }

            @Override
            public CompletableFuture<Boolean> reorder(List<Mood> moods) {
                List<String> ids = moods.stream().map(Mood::getId).toList();
                return writes.call(() -> {
                    service.reorder(ids);
                    return true;
                });
            }

            @Override
            public CompletableFuture<Boolean> rename(Mood mood, String name) {
                mood.setName(name);
                return writes.call(() -> service.update(mood));
            }

            @Override
            public CompletableFuture<Boolean> delete(Mood mood) {
                String id = mood.getId();
                return writes.call(() -> service.moveToTrash(id));
            }

            @Override
            public CompletableFuture<Integer> importFrom(File file) {
                return writes.call(() -> service.importMoods(file));
            }

            @Override
            public CompletableFuture<Boolean> exportTo(File file) {
                return writes.call(() -> service.exportMoods(file));
            }
        };
    }

    /**
     * The frames of the user, changed on the worker.
     *
     * @param service the frames
     * @param writes  the worker of the window
     * @return the library of the window
     */
    static FrameManageDialog.Library frames(FrameService service, PanelWorker writes) {
        return new FrameManageDialog.Library() {
            @Override
            public List<Frame> list() {
                return service.getCustom();
            }

            @Override
            public boolean exists(String name) {
                return service.exists(name);
            }

            @Override
            public CompletableFuture<Boolean> reorder(List<Frame> frames) {
                List<String> ids = frames.stream().map(Frame::getId).toList();
                return writes.call(() -> {
                    service.reorder(ids);
                    return true;
                });
            }

            @Override
            public CompletableFuture<Boolean> rename(Frame frame, String name) {
                frame.setName(name);
                return writes.call(() -> service.update(frame));
            }

            @Override
            public CompletableFuture<Boolean> delete(Frame frame) {
                String id = frame.getId();
                return writes.call(() -> service.moveToTrash(id));
            }

            @Override
            public CompletableFuture<Integer> importFrom(File file) {
                return writes.call(() -> service.importFrames(file));
            }

            @Override
            public CompletableFuture<Boolean> exportTo(File file) {
                return writes.call(() -> service.exportFrames(file));
            }
        };
    }
}
