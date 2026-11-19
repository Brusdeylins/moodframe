package art.moodframe.repository;

import art.moodframe.database.TestDatabase;
import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormMessage;
import art.moodframe.model.BrainstormSession;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a fresh SQLite database, so migration V014 is applied together with all earlier ones. */
class BrainstormRepositoryTest {

    @TempDir
    static Path tempDir;

    private final BrainstormRepository repository = new BrainstormRepository(TestDatabase.get());

    @BeforeAll
    static void createDatabase() {
        TestDatabase.open(tempDir.resolve("db").resolve("test.db").toString());
    }

    @Test
    void storesSessionsMessagesAndDraftVersions() throws Exception {
        BrainstormSession session = repository.createSession("Neue Sitzung");

        repository.addMessage(session.getId(), new BrainstormMessage(BrainstormMessage.ROLE_USER, "Hallo"));
        repository.addMessage(session.getId(), new BrainstormMessage(BrainstormMessage.ROLE_ASSISTANT, "{}"));
        repository.updateTitle(session.getId(), "Hallo");

        List<BrainstormMessage> messages = repository.findMessages(session.getId());
        assertEquals(List.of("user", "assistant"), messages.stream().map(BrainstormMessage::getRole).toList());
        assertEquals("Hallo", repository.findAllSessions().stream()
                .filter(s -> s.getId().equals(session.getId())).findFirst().orElseThrow().getTitle());

        BrainstormDraft first = BrainstormDraft.fromJson(new JSONObject("{\"prompt\": \"v1\"}"));
        BrainstormDraft second = BrainstormDraft.fromJson(new JSONObject("{\"prompt\": \"v2\", \"style\": \"Pixel Art\"}"));
        assertEquals(1, repository.addDraft(session.getId(), first));
        assertEquals(2, repository.addDraft(session.getId(), second));
        assertEquals("v2", repository.findLatestDraft(session.getId()).orElseThrow().getPrompt());
        assertEquals("Pixel Art", repository.findLatestDraft(session.getId()).orElseThrow()
                .getBlock(art.moodframe.model.Mood.Field.STYLE).name());
    }

    @Test
    void replacesATitleOnlyWhileItIsTheExpectedOne() throws Exception {
        BrainstormSession session = repository.createSession("Erster Satz");

        assertTrue(repository.replaceTitle(session.getId(), "Erster Satz", "Kurz"));
        assertFalse(repository.replaceTitle(session.getId(), "Erster Satz", "Anders"), "the title changed");
        assertEquals("Kurz", repository.findAllSessions().stream()
                .filter((BrainstormSession s) -> s.getId().equals(session.getId())).findFirst().orElseThrow()
                .getTitle());
    }

    @Test
    void remembersTheLanguageModelOfEachSession() throws Exception {
        BrainstormSession openAi = repository.createSession("OpenAI", "openai");
        BrainstormSession older = repository.createSession("Ohne Wahl");
        repository.updateEngine(older.getId(), "gemini");

        List<BrainstormSession> stored = repository.findAllSessions();
        assertEquals("openai", stored.stream().filter(s -> s.getId().equals(openAi.getId())).findFirst()
                .orElseThrow().getEngine());
        assertEquals("gemini", stored.stream().filter(s -> s.getId().equals(older.getId())).findFirst()
                .orElseThrow().getEngine());
    }

    @Test
    void migrationV015ShiftsTheStepsOfStoredAssistantRepliesToTheEightStepProcess() throws Exception {
        BrainstormSession session = repository.createSession("Alt");
        for (int step = 1; step <= 5; step++) {
            repository.addMessage(session.getId(), new BrainstormMessage(BrainstormMessage.ROLE_ASSISTANT,
                    "{\"step\": " + step + ", \"message\": \"m" + step + "\"}"));
        }
        repository.addMessage(session.getId(), new BrainstormMessage(BrainstormMessage.ROLE_ASSISTANT, "not json"));
        repository.addMessage(session.getId(), new BrainstormMessage(BrainstormMessage.ROLE_USER, "{\"step\": 3}"));

        String sql = new String(getClass().getResourceAsStream("/db/migration/V015__Shift_brainstorm_steps.sql")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        try (java.sql.Connection conn = TestDatabase.get().getConnection();
                java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }

        List<BrainstormMessage> messages = repository.findMessages(session.getId());
        List<Integer> steps = messages.subList(0, 5).stream()
                .map(message -> new JSONObject(message.getContent()).getInt("step")).toList();
        assertEquals(List.of(1, 6, 7, 7, 8), steps);
        assertEquals("not json", messages.get(5).getContent(), "text that is no JSON stays");
        assertEquals("{\"step\": 3}", messages.get(6).getContent(), "only assistant replies are changed");
    }

    @Test
    void deletesSessionWithMessagesAndDrafts() throws Exception {
        BrainstormSession session = repository.createSession("Weg");
        repository.addMessage(session.getId(), new BrainstormMessage(BrainstormMessage.ROLE_USER, "x"));
        repository.addDraft(session.getId(), new BrainstormDraft());

        assertTrue(repository.deleteSession(session.getId()));

        assertTrue(repository.findMessages(session.getId()).isEmpty());
        assertTrue(repository.findLatestDraft(session.getId()).isEmpty());
        assertFalse(repository.findAllSessions().stream().anyMatch(s -> s.getId().equals(session.getId())));
    }

    @Test
    void storesAMessageOnlyTogetherWithTheTimeOfTheSession() throws Exception {
        BrainstormSession session = repository.createSession("Kaputt");
        try (Connection conn = TestDatabase.get().getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TRIGGER refuse_touch BEFORE UPDATE OF updated_at ON brainstorm_sessions "
                    + "WHEN NEW.title = 'Kaputt' BEGIN SELECT RAISE(ABORT, 'refused'); END");
        }

        assertThrows(SQLException.class, () -> repository.addMessage(session.getId(),
                new BrainstormMessage(BrainstormMessage.ROLE_USER, "lost")));
        assertTrue(repository.findMessages(session.getId()).isEmpty(), "the message goes back with the session");
    }
}
