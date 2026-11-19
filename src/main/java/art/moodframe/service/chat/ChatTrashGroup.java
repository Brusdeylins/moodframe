package art.moodframe.service.chat;

import art.moodframe.repository.ChatTrashRepository;
import art.moodframe.service.trash.TrashEntry;
import art.moodframe.service.trash.TrashGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The chats in the central trash. It builds on the soft delete of the {@link ChatTrashRepository}: a deleted chat
 * keeps its entries, and its files wait in the trash directory until it is restored or removed for good.
 *
 * @since 2.0.0
 */
public class ChatTrashGroup implements TrashGroup {

    private static final Logger logger = LoggerFactory.getLogger(ChatTrashGroup.class);

    private final ChatTrashRepository trash;

    /**
     * Creates the group.
     *
     * @param trash the trash of the chats, which moves their files
     */
    public ChatTrashGroup(ChatTrashRepository trash) {
        this.trash = trash;
    }

    @Override
    public Kind kind() {
        return Kind.CHATS;
    }

    /**
     * Lists the deleted chats with the number of their images, those that came ({@link
     * art.moodframe.model.GeneratedImage#isReal()}), and their cost, in one query.
     *
     * @param locale the language of the context, null for that of the user interface
     * @return the chats in the trash, the latest deletion first
     */
    @Override
    public List<TrashEntry> list(Locale locale) {
        List<TrashEntry> entries = new ArrayList<>();
        try {
            for (ChatTrashRepository.DeletedChat deleted : trash.findDeletedChats()) {

                /*  The total of the chat header, as ChatCost#total sums it: all it cost, deleted entries and
                    images included  */
                double cost = deleted.entriesCost() + deleted.chat().getDeletedCost();
                entries.add(new TrashEntry(deleted.chat().getId(), deleted.chat().getName(),
                        deleted.chat().getDeletedAt(), TrashGroup.text(locale, deleted.images() == 1
                                ? "trash.context.chat.singular" : "trash.context.chat.plural",
                                deleted.images(), TrashGroup.cost(locale, cost))));
            }
        } catch (SQLException e) {
            logger.error("Failed to list the deleted chats", e);
        }
        return entries;
    }

    @Override
    public boolean restore(String id) {
        try {
            return trash.restoreChat(id);
        } catch (SQLException e) {
            logger.error("Failed to restore chat: {}", id, e);
            return false;
        }
    }

    @Override
    public boolean deletePermanently(String id) {
        try {
            return trash.deleteChatPermanently(id);
        } catch (SQLException e) {
            logger.error("Failed to permanently delete chat: {}", id, e);
            return false;
        }
    }
}
