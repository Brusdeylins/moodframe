package art.moodframe.config;

import java.nio.file.Path;

/**
 * The folders and files of the library: the one place that knows where they are.
 *
 * <p>{@code MoodframeApplication} creates the paths once from {@code user.home} and hands them to the classes that
 * need a folder; tests use the paths of a temporary home. The library lives in {@code <home>/.moodframe}, the images
 * where the setting {@code path.images} says (by default {@code <home>/.moodframe/images}, see
 * {@link ApplicationConfig#paths()}). The log files are set in {@code logback.xml}, which reads {@code user.home}
 * itself before any code runs.</p>
 *
 * @param home   the home directory of the user
 * @param root   the library folder, {@code <home>/.moodframe}
 * @param images the folder of the images and videos
 */
public record LibraryPaths(Path home, Path root, Path images) {

    /**
     * Gets the paths of the user of this process, from the system property {@code user.home}.
     *
     * @return the paths with the default images folder
     */
    public static LibraryPaths fromUserHome() {
        return under(Path.of(System.getProperty("user.home")));
    }

    /**
     * Gets the paths of a library in the given home directory.
     *
     * @param home the home directory
     * @return the paths with the default images folder
     */
    public static LibraryPaths under(Path home) {
        Path root = home.resolve(".moodframe");
        return new LibraryPaths(home, root, root.resolve("images"));
    }

    /**
     * Gets the same paths with another images folder.
     *
     * @param images the images folder
     * @return the paths
     */
    public LibraryPaths withImages(Path images) {
        return new LibraryPaths(home, root, images);
    }

    /** @return the settings file {@code config.properties} */
    public Path configFile() {
        return root.resolve("config.properties");
    }

    /** @return the key that encrypts the stored API keys and tokens */
    public Path secretKey() {
        return root.resolve("secret.key");
    }

    /** @return the folder of the database */
    public Path databaseDirectory() {
        return root.resolve("db");
    }

    /** @return the default database file */
    public Path database() {
        return databaseDirectory().resolve("moodframe.db");
    }

    /** @return the folder of the log files */
    public Path logs() {
        return root.resolve("logs");
    }

    /** @return the folder of the thumbnails */
    public Path thumbnails() {
        return root.resolve("thumbnails");
    }

    /** @return the trash folder of image and thumbnail files */
    public Path trash() {
        return root.resolve("trash");
    }

    /** @return the folder of the corporate root certificates */
    public Path certificates() {
        return root.resolve("certificates");
    }

    /** @return the Downloads folder of the user, the target of the download button */
    public Path downloads() {
        return home.resolve("Downloads");
    }

    /** @return the Documents folder of the user, where the export and import dialogs start */
    public Path documents() {
        return home.resolve("Documents");
    }
}
