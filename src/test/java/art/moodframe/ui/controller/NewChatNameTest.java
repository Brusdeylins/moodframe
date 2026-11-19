package art.moodframe.ui.controller;

import art.moodframe.model.Chat;
import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The name of a new chat: localized, with the first number that no chat outside the trash has. */
class NewChatNameTest {

    private final String base = I18nManager.getText("chat.new.default") + " ";

    private static Chat chat(String name, boolean trashed) {
        return new Chat.Builder().name(name).deletedAt(trashed ? LocalDateTime.now() : null).build();
    }

    @Test
    void startsAtOne() {
        assertEquals(base + "1", LibrarySidebarController.newChatName(List.of()));
    }

    @Test
    void takesTheFirstFreeNumberAndIgnoresTheTrashAndOtherNames() {
        List<Chat> chats = List.of(chat(base + "1", false), chat(base + "2", true), chat(base + "3", false),
                chat("Harbor", false), chat("Lighthouse", false));

        assertEquals(base + "2", LibrarySidebarController.newChatName(chats));
    }
}
