package art.moodframe;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The website in website/: the tiles of the chapter More fill every row of their grid, at every column count the
 * stylesheet gives the grid, so no row stays half empty.
 */
class WebsiteTest {

    private static final Pattern FEATURES_LIST = Pattern.compile("<ul class=\"features\">(.*?)</ul>", Pattern.DOTALL);
    private static final Pattern FEATURES_COLS = Pattern.compile("\\.features \\{[^}]*grid-template-columns: ([^;]+);");
    private static final Pattern REPEAT        = Pattern.compile("^repeat\\((\\d+),");

    @Test
    void moreTilesFillEveryRow() throws Exception {
        String html = Files.readString(Path.of("website/index.html"));
        String css  = Files.readString(Path.of("website/assets/css/site.css"));

        Matcher list = FEATURES_LIST.matcher(html);
        assertTrue(list.find(), "no <ul class=\"features\"> in index.html");
        int tiles = list.group(1).split("<li", -1).length - 1;

        Matcher cols = FEATURES_COLS.matcher(css);
        boolean found = false;
        while (cols.find()) {
            found = true;

            /*  repeat(n, ...) gives n columns, a single track like minmax(0, 1fr) one  */
            Matcher repeat = REPEAT.matcher(cols.group(1).trim());
            int columns = repeat.find() ? Integer.parseInt(repeat.group(1)) : 1;
            assertEquals(0, tiles % columns, tiles + " tiles in " + columns + " columns leave a row half empty");
        }
        assertTrue(found, "no grid-template-columns for .features in site.css");
        assertTrue(tiles > 0, "no tiles in the chapter More");
    }
}
