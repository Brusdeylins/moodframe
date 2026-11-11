package art.moodframe.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrashCenterTest {

    private final TrashCenter center = new TrashCenter();
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void listen() {
        center.addListener(new TrashCenter.Listener() {
            @Override
            public void restored(TrashGroup.Kind kind, String id) {
                events.add("restored " + kind + " " + id);
            }

            @Override
            public void deleted(TrashGroup.Kind kind, List<String> ids) {
                events.add("deleted " + kind + " " + ids);
            }
        });
    }

    @ParameterizedTest
    @EnumSource(TrashGroup.Kind.class)
    void restoresAnItemOfEveryKindAndTellsTheListeners(TrashGroup.Kind kind) {
        center.register(new MemoryTrashGroup(kind).put("a", 1).put("b", 2));

        assertTrue(center.restore(kind, "a"));

        assertEquals(List.of("b"), center.list(kind).stream().map(TrashEntry::id).toList());
        assertEquals(List.of("restored " + kind + " a"), events);
    }

    @ParameterizedTest
    @EnumSource(TrashGroup.Kind.class)
    void deletesItemsOfEveryKindForGoodAndReportsOnlyWhatIsGone(TrashGroup.Kind kind) {
        center.register(new MemoryTrashGroup(kind).put("a", 1).put("b", 2).put("c", 3).stuck("b"));

        assertEquals(List.of("a", "c"), center.deletePermanently(kind, List.of("a", "b", "c")));

        assertEquals(List.of("b"), center.list(kind).stream().map(TrashEntry::id).toList());
        assertEquals(List.of("deleted " + kind + " [a, c]"), events);
    }

    @Test
    void aFailedRestoreIsNotReported() {
        center.register(new MemoryTrashGroup(TrashGroup.Kind.CHATS).put("a", 1).stuck("a"));

        assertFalse(center.restore(TrashGroup.Kind.CHATS, "a"));
        assertFalse(center.restore(TrashGroup.Kind.CHATS, "unknown"));

        assertEquals(1, center.list(TrashGroup.Kind.CHATS).size());
        assertTrue(events.isEmpty());
    }

    @Test
    void aKindWithoutADockedGroupIsEmptyAndRefusesEverything() {
        center.register(new MemoryTrashGroup(TrashGroup.Kind.CHATS).put("a", 1));

        assertTrue(center.list(TrashGroup.Kind.MOODS).isEmpty());
        assertFalse(center.restore(TrashGroup.Kind.MOODS, "a"));
        assertTrue(center.deletePermanently(TrashGroup.Kind.MOODS, List.of("a")).isEmpty());
        assertTrue(events.isEmpty());
    }
}
