package art.moodframe.ui.dialog;

import art.moodframe.ui.dialog.BrainstormMarkdown.Span;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The light Markdown of the AI bubbles: markers vanish, unpaired ones stay. */
class BrainstormMarkdownTest {

    private static Span plain(String text) {
        return new Span(text, false, false);
    }

    private static Span bold(String text) {
        return new Span(text, true, false);
    }

    private static Span italic(String text) {
        return new Span(text, false, true);
    }

    @Test
    void showsBoldWithoutTheAsterisks() {
        assertEquals(List.of(plain("Take "), bold("Nano Banana Pro at 2K ($0.134)"), plain(".")),
                BrainstormMarkdown.parse("Take **Nano Banana Pro at 2K ($0.134)**."));
    }

    @Test
    void showsItalicWithAsteriskAndUnderscore() {
        assertEquals(List.of(plain("a "), italic("soft"), plain(" and "), italic("warm"), plain(" light")),
                BrainstormMarkdown.parse("a *soft* and _warm_ light"));
    }

    @Test
    void turnsDashAndAsteriskLinesIntoBulletsAndKeepsTheLineBreaks() {
        assertEquals(List.of(plain("Options:\n• first\n  • nested\n• "), italic("third")),
                BrainstormMarkdown.parse("Options:\n- first\n  * nested\n* *third*"));
    }

    @Test
    void mixesBoldItalicAndPlainInOneLine() {
        assertEquals(List.of(plain("• "), bold("Prompt:"), plain(" a lighthouse at "), italic("dusk"),
                        plain(", "), bold("very "), new Span("calm", true, true), plain(" sea")),
                BrainstormMarkdown.parse("- **Prompt:** a lighthouse at *dusk*, **very _calm_** sea"));
    }

    @Test
    void keepsUnpairedAsterisksAsTheyAre() {
        assertEquals(List.of(plain("**Nano Banana costs 5 * 3 *cents")),
                BrainstormMarkdown.parse("**Nano Banana costs 5 * 3 *cents"));
        assertEquals(List.of(plain("a ** b and *** c")), BrainstormMarkdown.parse("a ** b and *** c"));
    }

    @Test
    void keepsMarkersInsideWords() {
        assertEquals(List.of(plain("file_name_here and 2*3*4")), BrainstormMarkdown.parse("file_name_here and 2*3*4"));
    }

    @Test
    void dropsHeadingMarkersAndBackticks() {
        assertEquals(List.of(plain("Proposal\nUse the flux-2-pro model, #1 choice")),
                BrainstormMarkdown.parse("## Proposal\nUse the `flux-2-pro` model, #1 choice"));
    }

    @Test
    void readsWindowsLineBreaks() {
        assertEquals(List.of(bold("a"), plain("\nb")), BrainstormMarkdown.parse("**a**\r\nb"));
    }
}
