package art.moodframe.service;

import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.repository.ChatRepository;
import art.moodframe.util.I18nManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The chats in the central trash. It builds on the soft delete of the {@link ChatRepository}: a deleted chat keeps
 * its entries, and its files wait in the trash directory until it is restored or removed for good.
 *
 * @since 2.0.0
 */
public class ChatTrashGroup implements TrashGroup {

    private static final Logger logger = LoggerFactory.getLogger(ChatTrashGroup.class);

    private final ChatRepository chatRepository;

    /**
     * Creates the group.
     *
     * @param chatRepository where chats are stored
     */
    public ChatTrashGroup(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    @Override
    public Kind kind() {
        return Kind.CHATS;
    }

    /**
     * Lists the deleted chats with the number of their images and their cost.
     *
     * @return the chats in the trash, the latest deletion first
     */
    @Override
    public List<TrashEntry> list() {
        List<TrashEntry> entries = new ArrayList<>();
        try {
            for (Chat chat : chatRepository.findDeletedChats()) {
                List<ChatEntry> chatEntries = chatRepository.findChatEntriesByChatId(chat.getId());
                int images = chatRepository.findImagesByEntryIds(chatEntries.stream().map(ChatEntry::getId).toList())
                        .values().stream().mapToInt(List::size).sum();

                /*  The total of the chat header: all entries plus what the deleted entries had cost  */
                double cost = chatEntries.stream().mapToDouble(ChatEntry::getCost).sum() + chat.getDeletedCost();

                entries.add(new TrashEntry(chat.getId(), chat.getName(), chat.getDeletedAt(), I18nManager.getText(
                        images == 1 ? "trash.context.chat.singular" : "trash.context.chat.plural",
                        images, String.format("%.2f", cost))));
            }
        } catch (SQLException e) {
            logger.error("Failed to list the deleted chats", e);
        }
        return entries;
    }

    @Override
    public boolean restore(String id) {
        try {
            return chatRepository.restoreChat(id);
        } catch (SQLException e) {
            logger.error("Failed to restore chat: {}", id, e);
            return false;
        }
    }

    @Override
    public boolean deletePermanently(String id) {
        try {
            return chatRepository.deleteChatPermanently(id);
        } catch (SQLException e) {
            logger.error("Failed to permanently delete chat: {}", id, e);
            return false;
        }
    }
}
