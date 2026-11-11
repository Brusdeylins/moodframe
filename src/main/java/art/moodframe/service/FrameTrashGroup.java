package art.moodframe.service;

import art.moodframe.model.Frame;

import java.util.List;

/**
 * The frames in the central trash, each with its model, resolution, aspect ratio and image count.
 *
 * @since 2.0.0
 */
public class FrameTrashGroup implements TrashGroup {

    private final FrameService frameService;

    /**
     * Creates the group.
     *
     * @param frameService where the frames are kept
     */
    public FrameTrashGroup(FrameService frameService) {
        this.frameService = frameService;
    }

    @Override
    public Kind kind() {
        return Kind.FRAMES;
    }

    @Override
    public List<TrashEntry> list() {
        return frameService.getDeleted().stream().map((Frame frame) -> new TrashEntry(frame.getId(), frame.getName(),
                frame.getDeletedAt(), FrameService.describe(frame))).toList();
    }

    @Override
    public boolean restore(String id) {
        return frameService.restore(id);
    }

    @Override
    public boolean deletePermanently(String id) {
        return frameService.deletePermanently(id);
    }
}
