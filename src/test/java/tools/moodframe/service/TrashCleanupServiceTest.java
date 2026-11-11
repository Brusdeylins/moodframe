package tools.moodframe.service;

import tools.moodframe.model.Chat;
import tools.moodframe.repository.ChatRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrashCleanupServiceTest {

    @Test
    void reportsTheIdsOfTheDeletedChatsSoTheListCanDropThemWithoutAReload() throws Exception {
        Chat expired = new Chat.Builder().id("old").deletedAt(LocalDateTime.now().minusDays(90)).build();
        Chat recent = new Chat.Builder().id("new").deletedAt(LocalDateTime.now().minusDays(5)).build();
        Chat failing = new Chat.Builder().id("stuck").deletedAt(LocalDateTime.now().minusDays(90)).build();
        ChatRepository repository = mock(ChatRepository.class);
        when(repository.findDeletedChats()).thenReturn(List.of(expired, recent, failing));
        when(repository.deleteChatPermanently("old")).thenReturn(true);
        when(repository.deleteChatPermanently("stuck")).thenReturn(false);

        TrashCleanupService service = new TrashCleanupService(repository, 60);
        List<String> reported = new ArrayList<>();
        service.setCleanupCallback(reported::addAll);

        assertEquals(1, service.forceCleanup());
        assertEquals(List.of("old"), reported);
        service.shutdown();
    }
}
