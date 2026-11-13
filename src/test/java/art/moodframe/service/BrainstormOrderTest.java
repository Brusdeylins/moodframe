package art.moodframe.service;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.BrainstormMessage;
import art.moodframe.model.BrainstormSession;
import art.moodframe.repository.BrainstormRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The order of the brainstorm sessions in the list: moved by the user, new sessions on top, kept over a restart. */
class BrainstormOrderTest {

    @TempDir
    Path tempDir;

    private Path databasePath;
    private BrainstormRepository repository;
    private BrainstormService service;
    private List<BrainstormSession> sessions;

    /*  A fresh database per test with the sessions A, B, C and D in this order, each new one created on top  */
    @BeforeEach
    void createSessions() {
        databasePath = tempDir.resolve("db").resolve("test.db");
        DatabaseConfig.initialize(databasePath.toString());
        repository = new BrainstormRepository();
        service = new BrainstormService(repository, null);
        for (String title : List.of("D", "C", "B", "A"))
            repository.createSession(title).orElseThrow();
        sessions = repository.findAllSessions();
    }

    @Test
    void putsANewSessionOnTop() throws Exception {
        assertEquals("ABCD", titles(sessions));
        service.moveSession(sessions, session("D"), session("A"));
        repository.createSession("E").orElseThrow();

        assertEquals("EDABC", titles(repository.findAllSessions()));
    }

    @Test
    void movesASessionToTheTop() throws Exception {
        service.moveSession(sessions, session("C"), session("A"));
        assertOrder("CABD");
    }

    @Test
    void movesASessionIntoTheMiddle() throws Exception {
        service.moveSession(sessions, session("A"), session("D"));
        assertOrder("BCAD");
    }

    @Test
    void movesASessionToTheBottom() throws Exception {
        assertEquals("ACDB", titles(service.moveSession(sessions, session("B"), null)), "the new order is returned");
        assertOrder("ACDB");
    }

    @Test
    void keepsItsPlaceWhenChanged() {
        repository.addMessage(session("C").getId(), new BrainstormMessage(BrainstormMessage.ROLE_USER, "a harbor"));
        repository.updateTitle(session("D").getId(), "D");

        assertEquals("ABCD", titles(repository.findAllSessions()), "a change does not move a session to the top");
    }

    @Test
    void loadsTheSessionsInTheStoredOrderAfterARestart() {
        service.moveSession(sessions, session("D"), session("B"));
        DatabaseConfig.initialize(databasePath.toString());

        assertEquals("ADBC", titles(new BrainstormRepository().findAllSessions()));
    }

    @Test
    void keepsTheOrderOfAnExistingLibraryOnItsFirstStart() throws Exception {
        /*  The library as 2.0.0 left it: no sort order, the migration not applied yet  */
        try (Connection connection = DatabaseConfig.getConnection();
                Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE brainstorm_sessions DROP COLUMN sort_order");
            stmt.executeUpdate("DELETE FROM schema_migrations WHERE version = '026'");
        }
        LocalDateTime now = LocalDateTime.now();
        int age = 0;
        try (Connection connection = DatabaseConfig.getConnection();
                PreparedStatement stmt = connection.prepareStatement(
                        "UPDATE brainstorm_sessions SET updated_at = ? WHERE id = ?")) {
            for (String title : List.of("C", "A", "D", "B")) {
                stmt.setTimestamp(1, Timestamp.valueOf(now.minusMinutes(age++)));
                stmt.setString(2, session(title).getId());
                stmt.executeUpdate();
            }
        }
        DatabaseConfig.initialize(databasePath.toString());

        assertOrder("CADB");
    }

    private BrainstormSession session(String title) {
        return sessions.stream().filter((session) -> session.getTitle().equals(title)).findFirst().orElseThrow();
    }

    /*  The list in the expected order, its sessions numbered from 0 on  */
    private void assertOrder(String expected) throws Exception {
        assertEquals(expected, titles(repository.findAllSessions()));
        List<Integer> numbers = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT sort_order FROM brainstorm_sessions ORDER BY sort_order")) {
            while (rs.next())
                numbers.add(rs.getInt(1));
        }
        assertEquals(List.of(0, 1, 2, 3), numbers, "numbered from 0 on");
    }

    private static String titles(List<BrainstormSession> sessions) {
        return String.join("", sessions.stream().map(BrainstormSession::getTitle).toList());
    }
}
