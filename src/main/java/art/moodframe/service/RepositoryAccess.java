package art.moodframe.service;

import org.slf4j.Logger;

import java.sql.SQLException;

/**
 * Runs the calls of a service to its repository. A repository reports a database error as {@link SQLException}; here
 * the error is logged with the logger of the service and the service goes on with the fallback it answers instead,
 * such as false, null or an empty list.
 *
 * @since 2.1.3
 */
final class RepositoryAccess {

    /** A read or write of a repository with a result. */
    @FunctionalInterface
    interface Call<T> {
        T run() throws SQLException;
    }

    /** A write of a repository without a result. */
    @FunctionalInterface
    interface Write {
        void run() throws SQLException;
    }

    private final Logger logger;

    /**
     * Creates the access for a service.
     *
     * @param logger the logger of the service, which logs the database errors
     */
    RepositoryAccess(Logger logger) {
        this.logger = logger;
    }

    /**
     * Runs a call; a database error is logged and gives the fallback.
     *
     * @param call     the call of the repository
     * @param fallback the result on a database error
     * @param failure  the message logged on a database error
     * @return the result of the call, or the fallback
     */
    <T> T call(Call<T> call, T fallback, String failure) {
        try {
            return call.run();
        } catch (SQLException e) {
            logger.error(failure, e);
            return fallback;
        }
    }

    /**
     * Runs a write without a result; a database error is logged.
     *
     * @param write   the write of the repository
     * @param failure the message logged on a database error
     */
    void run(Write write, String failure) {
        try {
            write.run();
        } catch (SQLException e) {
            logger.error(failure, e);
        }
    }
}
