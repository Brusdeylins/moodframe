package art.moodframe.ui.dialog;

import javafx.beans.binding.Bindings;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.transform.Shear;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The light Markdown of the AI answers in the brainstorm chat: {@code **bold**}, {@code *italic*} and
 * {@code _italic_} lose their markers, lines starting with "- " or "* " become bullets with "•", heading markers
 * ({@code #}) and backticks vanish, line breaks stay. Unpaired markers stay as they are. {@link #parse} needs no UI.
 *
 * @since 2.0.0
 */
final class BrainstormMarkdown {

    /** A piece of text in one style; line breaks are part of the text. */
    record Span(String text, boolean bold, boolean italic) {
    }

    private static final Pattern HEADING = Pattern.compile("^\\s*#{1,6}\\s+");
    private static final Pattern BULLET  = Pattern.compile("^(\\s*)[-*]\\s+");
    /** The slant of italic words, about 11 degrees. */
    private static final double SLANT = 0.2;

    private BrainstormMarkdown() {
    }

    /**
     * Builds the text of a bubble: bold spans get the AtlantaFX text style, italic spans are slanted, the fill is the
     * one of the theme.
     *
     * @param text the text of the AI
     * @return the flow of the text pieces
     */
    static TextFlow toFlow(String text) {
        TextFlow flow = new TextFlow();
        for (Span span : parse(text)) {
            /*  JavaFX draws the system font of macOS upright even in italic, so italic words are slanted instead: one
                Text per word, as the slant pivots on the middle of its line  */
            for (String word : span.italic() ? span.text().split("(?=\\s)|(?<=\\s)") : new String[] { span.text() }) {
                Text piece = new Text(word);
                if (span.bold())
                    piece.getStyleClass().add("text-bold");
                if (span.italic()) {
                    Shear slant = new Shear(-SLANT, 0.0);
                    slant.pivotYProperty().bind(Bindings.createDoubleBinding(
                            () -> piece.getBoundsInLocal().getCenterY(), piece.boundsInLocalProperty()));
                    piece.getTransforms().add(slant);
                }
                flow.getChildren().add(piece);
            }
        }
        return flow;
    }

    /**
     * Splits a text into spans, adjacent spans of the same style merged.
     *
     * @param text the text of the AI
     * @return the spans in the order of the text
     */
    static List<Span> parse(String text) {
        List<Span> spans = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0)
                add(spans, "\n", false, false);
            String line = HEADING.matcher(lines[i].replace("`", "")).replaceFirst("");
            inline(BULLET.matcher(line).replaceFirst("$1• "), false, false, spans);
        }
        return spans;
    }

    /** Adds the spans of a line; an emphasis inside an emphasis keeps the outer style. */
    private static void inline(String text, boolean bold, boolean italic, List<Span> spans) {
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int run = runLength(text, i);
            int end = isMarker(text, i, run) && opens(text, i, run) ? closing(text, i, run) : -1;
            if (end < 0) {
                int length = Math.max(run, 1);
                plain.append(text, i, i + length);
                i += length;
                continue;
            }
            add(spans, plain.toString(), bold, italic);
            plain.setLength(0);
            inline(text.substring(i + run, end), bold || run == 2, italic || run == 1, spans);
            i = end + run;
        }
        add(spans, plain.toString(), bold, italic);
    }

    /** The length of the run of "*" or "_" at a position, 0 for any other character. */
    private static int runLength(String text, int i) {
        char c = text.charAt(i);
        if (c != '*' && c != '_')
            return 0;
        int end = i;
        while (end < text.length() && text.charAt(end) == c)
            end++;
        return end - i;
    }

    /** Only "**", "*" and "_" are markers, longer runs stay as text. */
    private static boolean isMarker(String text, int i, int run) {
        return run == 1 || run == 2 && text.charAt(i) == '*';
    }

    /** A marker opens before a non-blank character and not inside a word, so "5 * 3" and "file_name" stay. */
    private static boolean opens(String text, int i, int run) {
        int next = i + run;
        return next < text.length() && !Character.isWhitespace(text.charAt(next))
                && (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)));
    }

    /** The position of the same marker that closes the one at a position, -1 if it stays unpaired. */
    private static int closing(String text, int start, int run) {
        char c = text.charAt(start);
        int j = start + run + 1;
        while (j < text.length()) {
            int length = runLength(text, j);
            if (length == 0) {
                j++;
                continue;
            }
            int after = j + length;
            if (text.charAt(j) == c && length == run && !Character.isWhitespace(text.charAt(j - 1))
                    && (after == text.length() || !Character.isLetterOrDigit(text.charAt(after))))
                return j;
            j = after;
        }
        return -1;
    }

    /** Appends a piece of text, merged into the last span if that has the same style. */
    private static void add(List<Span> spans, String text, boolean bold, boolean italic) {
        if (text.isEmpty())
            return;
        Span last = spans.isEmpty() ? null : spans.getLast();
        if (last != null && last.bold() == bold && last.italic() == italic)
            spans.set(spans.size() - 1, new Span(last.text() + text, bold, italic));
        else
            spans.add(new Span(text, bold, italic));
    }
}
