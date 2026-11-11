package tools.moodframe.repository;

import tools.moodframe.database.DatabaseConfig;
import tools.moodframe.model.BrainstormDraft;
import tools.moodframe.model.BrainstormMessage;
import tools.moodframe.model.BrainstormSession;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a fresh SQLite database, so migration V014 is applied together with all earlier ones. */
class BrainstormRepositoryTest {

    @TempDir
    static Path tempDir;

    private final BrainstormRepository repository = new BrainstormRepository();

    @BeforeAll
    static void createDatabase() {
        DatabaseConfig.initialize(tempDir.resolve("db").resolve("test.db").toString());
    }

    @Test
    void storesSessionsMessagesAndDraftVersions() {
        BrainstormSession session = repository.createSession("Neue Sitzung").orElseThrow();

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
        assertEquals("Pixel Art", repository.findLatestDraft(session.getId()).orElseThrow().getStyle());
    }

    @Test
    void remembersTheLanguageModelOfEachSession() {
        BrainstormSession openAi = repository.createSession("OpenAI", "openai").orElseThrow();
        BrainstormSession older = repository.createSession("Ohne Wahl").orElseThrow();
        repository.updateEngine(older.getId(), "gemini");

        List<BrainstormSession> stored = repository.findAllSessions();
        assertEquals("openai", stored.stream().filter(s -> s.getId().equals(openAi.getId())).findFirst()
                .orElseThrow().getEngine());
        assertEquals("gemini", stored.stream().filter(s -> s.getId().equals(older.getId())).findFirst()
                .orElseThrow().getEngine());
    }

    @Test
    void migrationV015ShiftsTheStepsOfStoredAssistantRepliesToTheEightStepProcess() throws Exception {
        BrainstormSession session = repository.createSession("Alt").orElseThrow();
        for (int step = 1; step <= 5; step++) {
            repository.addMessage(session.getId(), new BrainstormMessage(BrainstormMessage.ROLE_ASSISTANT,
                    "{\"step\": " + step + ", \"message\": \"m" + step + "\"}"));
        }
        repository.addMessage(session.getId(), new BrainstormMessage(BrainstormMessage.ROLE_ASSISTANT, "not json"));
        repository.addMessage(session.getId(), new BrainstormMessage(BrainstormMessage.ROLE_USER, "{\"step\": 3}"));

        String sql = new String(getClass().getResourceAsStream("/db/migration/V015__Shift_brainstorm_steps.sql")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        try (java.sql.Connection conn = DatabaseConfig.getConnection(); java.sql.Statement stmt = conn.createStatement()) {
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
    void deletesSessionWithMessagesAndDrafts() {
        BrainstormSession session = repository.createSession("Weg").orElseThrow();
        repository.addMessage(session.getId(), new BrainstormMessage(BrainstormMessage.ROLE_USER, "x"));
        repository.addDraft(session.getId(), new BrainstormDraft());

        assertTrue(repository.deleteSession(session.getId()));

        assertTrue(repository.findMessages(session.getId()).isEmpty());
        assertTrue(repository.findLatestDraft(session.getId()).isEmpty());
        assertFalse(repository.findAllSessions().stream().anyMatch(s -> s.getId().equals(session.getId())));
    }
}
