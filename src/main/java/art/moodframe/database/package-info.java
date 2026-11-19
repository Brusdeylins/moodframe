/**
 * The SQLite database: opening plain JDBC connections, the WAL mode and the schema migrations from
 * {@code src/main/resources/db/migration}.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link DatabaseManager}: the one connection factory (WAL, busy timeout, foreign keys)</li>
 * <li>{@link MigrationRunner}: the ordered schema migrations and their SQL splitter</li>
 * <li>{@link FluxCostBackfill}: the Java migration V029 that fills in the cost of old FLUX entries</li>
 * <li>{@link DeletedCostCorrection}: the Java migration V030 that books the old deleted costs again</li>
 * </ul>
 */
package art.moodframe.database;
