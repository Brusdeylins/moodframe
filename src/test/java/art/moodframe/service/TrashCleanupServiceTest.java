package art.moodframe.service;

import art.moodframe.model.Chat;
import art.moodframe.repository.ChatRepository;
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

        TrashCenter center = new TrashCenter();
        center.register(new ChatTrashGroup(repository));
        TrashCleanupService service = new TrashCleanupService(center, 60);
        List<String> reported = new ArrayList<>();
        center.addListener(new TrashCenter.Listener() {
            @Override
            public void restored(TrashGroup.Kind kind, String id) {
            }

            @Override
            public void deleted(TrashGroup.Kind kind, List<String> ids) {
                reported.addAll(ids);
            }
        });

        assertEquals(1, service.forceCleanup());
        assertEquals(List.of("old"), reported);
        service.shutdown();
    }

    @Test
    void deletesTheItemsOfEveryGroupAfterSixtyDaysAndKeepsTheYoungerOnes() {
        TrashCenter center = new TrashCenter();
        for (TrashGroup.Kind kind : TrashGroup.Kind.values()) {
            center.register(new MemoryTrashGroup(kind).put("expired", 61).put("kept", 59));
        }
        TrashCleanupService service = new TrashCleanupService(center);

        assertEquals(TrashGroup.Kind.values().length, service.performAutoCleanup());

        for (TrashGroup.Kind kind : TrashGroup.Kind.values()) {
            assertEquals(List.of("kept"), center.list(kind).stream().map(TrashEntry::id).toList());
        }
        assertEquals(0, service.performAutoCleanup());
        service.shutdown();
    }

    @Test
    void tellsTheDaysAnItemHasLeftInTheTrash() {
        TrashCleanupService service = new TrashCleanupService(new TrashCenter());

        assertEquals(60, service.getDaysUntilAutoDeletion(LocalDateTime.now()));
        assertEquals(50, service.getDaysUntilAutoDeletion(LocalDateTime.now().minusDays(10)));
        assertEquals(0, service.getDaysUntilAutoDeletion(LocalDateTime.now().minusDays(60)));
        assertEquals(0, service.getDaysUntilAutoDeletion(LocalDateTime.now().minusDays(90)));
        service.shutdown();
    }
}
