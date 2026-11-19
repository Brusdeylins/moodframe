package art.moodframe.service.chat;

import art.moodframe.database.DatabaseManager;
import art.moodframe.repository.ChatFiles;
import art.moodframe.repository.ChatGroupQueries;
import art.moodframe.repository.ChatRepository;
import art.moodframe.repository.ChatTrashRepository;
import art.moodframe.service.trash.TrashManager;

/**
 * The chat history for tests, wired on one database as {@code MoodframeApplication} does: the chats, their trash and
 * the groups of their entries.
 */
public final class TestChatServices {

    private TestChatServices() {
    }

    /**
     * @param repository the chats, on the database
     * @param database   the database of the chats
     * @return the history without files and without trash folder
     */
    public static ChatHistoryService history(ChatRepository repository, DatabaseManager database) {
        return history(repository, database, ChatFiles.NONE, null);
    }

    /**
     * @param database the database of the chats
     * @return the history without files and without trash folder
     */
    public static ChatHistoryService history(DatabaseManager database) {
        return history(new ChatRepository(database), database);
    }

    /**
     * @param repository   the chats, on the database
     * @param database     the database of the chats
     * @param files        moves the files of the deletions
     * @param trashManager locates the files in the trash (may be null)
     * @return the history
     */
    public static ChatHistoryService history(ChatRepository repository, DatabaseManager database, ChatFiles files,
            TrashManager trashManager) {
        return new ChatHistoryService(repository, new ChatTrashRepository(database, files),
                new ChatGroupQueries(database), trashManager);
    }
}
