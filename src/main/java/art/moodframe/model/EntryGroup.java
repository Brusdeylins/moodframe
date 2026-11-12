package art.moodframe.model;

/**
 * A group of the chat entries of the active chats for the left sidebar: by the mood ({@link MoodGroup}) or by the
 * frame ({@link FrameGroup}) stored at them. An open group shows its entries from all chats.
 *
 * @since 2.0.0
 */
public sealed interface EntryGroup permits MoodGroup, FrameGroup {

    /**
     * Checks if an entry belongs to the group by the copy stored at it.
     *
     * @param entry the entry
     * @return true if the entry belongs to the group
     */
    boolean holds(ChatEntry entry);

    /**
     * Checks if another group stands for the same entries, whatever its name, count or time.
     *
     * @param other the other group, may be null
     * @return true if both group the same way and match in kind and id
     */
    boolean sameAs(EntryGroup other);
}
