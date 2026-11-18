/**
 * The SQLite database: opening plain JDBC connections, the WAL mode and the schema migrations from
 * {@code src/main/resources/db/migration}.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link DatabaseConfig}: the database file, connections and the schema</li>
 * <li>{@link DatabaseManager}: initialization, connection settings and the migrations</li>
 * <li>{@link FluxCostBackfill}: the Java migration V029 that fills in the cost of old FLUX entries</li>
 * </ul>
 */
package art.moodframe.database;
