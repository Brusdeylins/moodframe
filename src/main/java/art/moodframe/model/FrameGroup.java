package art.moodframe.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * The chat entries of the active chats that share the frame stored at them (see {@link EntryFrame}), for the left
 * sidebar by frame: those of one frame, the settings of their own (no frame chosen or the chosen one changed), or
 * those without copy, like the entries from before the frames were copied.
 *
 * @param kind the kind of the group
 * @param frameId the id of the frame, null for the other kinds
 * @param name the current name of a frame of the user in use, else the name stored at the latest entry; null for the
 *             other kinds
 * @param deleted whether the frame of the user lies in the trash or is deleted for good
 * @param aspectRatio the target aspect ratio at the latest entry of a frame, null for none and the other kinds
 * @param resolution the target resolution at the latest entry of a frame, null for none and the other kinds
 * @param imageCount the number of generated images that are not deleted
 * @param lastUsed the creation time of the latest entry, null for a group without entries
 * @since 2.0.0
 */
public record FrameGroup(Kind kind, String frameId, String name, boolean deleted, String aspectRatio,
        String resolution, int imageCount, LocalDateTime lastUsed) implements EntryGroup {

    /** The kinds of groups, in the order of the list. */
    public enum Kind {
        FRAME, CUSTOM, NONE
    }

    @Override
    public boolean holds(ChatEntry entry) {
        EntryFrame frame = entry.getFrame();
        return switch (kind) {
            case FRAME -> frame != null && frameId.equals(frame.frameId());
            case CUSTOM -> frame != null && frame.frameId() == null;
            case NONE -> frame == null;
        };
    }

    @Override
    public boolean sameAs(EntryGroup other) {
        return other instanceof FrameGroup group && kind == group.kind && Objects.equals(frameId, group.frameId);
    }
}
