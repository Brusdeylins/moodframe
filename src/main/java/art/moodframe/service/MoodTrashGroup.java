package art.moodframe.service;

import art.moodframe.model.Mood;

import java.util.List;

/**
 * The moods in the central trash, each with the names of its blocks.
 *
 * @since 2.0.0
 */
public class MoodTrashGroup implements TrashGroup {

    private final MoodService moodService;

    /**
     * Creates the group.
     *
     * @param moodService where the moods are kept
     */
    public MoodTrashGroup(MoodService moodService) {
        this.moodService = moodService;
    }

    @Override
    public Kind kind() {
        return Kind.MOODS;
    }

    @Override
    public List<TrashEntry> list() {
        return moodService.getDeleted().stream().map((Mood mood) -> new TrashEntry(mood.getId(), mood.getName(),
                mood.getDeletedAt(), moodService.describe(mood))).toList();
    }

    @Override
    public boolean restore(String id) {
        return moodService.restore(id);
    }

    @Override
    public boolean deletePermanently(String id) {
        return moodService.deletePermanently(id);
    }
}
