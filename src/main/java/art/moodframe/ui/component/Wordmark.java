package art.moodframe.ui.component;

import javafx.beans.NamedArg;
import javafx.scene.Group;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The MoodFrame wordmark, drawn from the paths of {@code /images/wordmark.svg}: "Mood" as a color gradient, cut in
 * its lower third, "FRAME" and two corners below in the text color of the theme. Its baseline is the baseline of
 * "FRAME", so a label next to it in a baseline-aligned row (the version number) sits on the same line. Its left edge
 * is the stroke of the "M", without the empty margin of the drawing, so it lines up with text above or below it.
 */
public class Wordmark extends Region {

    /*  geometry of the drawing: view box, its empty left margin, where "Mood" is cut, the baseline of "FRAME"  */
    private static final double BOX_WIDTH = 1600.0;
    private static final double BOX_HEIGHT = 560.0;
    private static final double MARGIN = 96.0;
    private static final double CUT = 339.0;
    private static final double BASELINE = 492.0;

    private final double scale;

    public Wordmark(@NamedArg(value = "markHeight", defaultValue = "46") double markHeight) {
        this.scale = markHeight / BOX_HEIGHT;
        List<String> paths = readPaths();

        SVGPath mood = new SVGPath();
        mood.setContent(paths.get(0));
        mood.setFill(new LinearGradient(96.0, 0.0, 1504.0, 0.0, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#ff6b4a")), new Stop(0.35, Color.web("#f5b83d")),
                new Stop(0.7, Color.web("#2fb8a6")), new Stop(1.0, Color.web("#5b6ee1"))));
        mood.setClip(new Rectangle(0.0, 0.0, BOX_WIDTH, CUT));

        SVGPath frame = new SVGPath();
        frame.setContent(paths.get(1));
        SVGPath corners = new SVGPath();
        corners.setContent(paths.get(2));
        frame.setStyle("-fx-fill: -color-fg-default;");
        corners.setStyle("-fx-fill: -color-fg-default;");

        Group drawing = new Group(mood, frame, corners);
        drawing.getTransforms().add(new Scale(scale, scale, 0.0, 0.0));
        drawing.setLayoutX(-MARGIN * scale);
        drawing.setManaged(false);
        getChildren().add(drawing);
        setAccessibleText("MoodFrame");

        double width = Math.ceil((BOX_WIDTH - MARGIN) * scale);
        double height = Math.ceil(BOX_HEIGHT * scale);
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(width, height);
    }

    @Override
    public double getBaselineOffset() {
        return BASELINE * scale;
    }

    /*  the "d" attributes of the three paths, in the order Mood, FRAME, corners  */
    private static List<String> readPaths() {
        try (InputStream in = Wordmark.class.getResourceAsStream("/images/wordmark.svg")) {
            String svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<String> paths = new ArrayList<>();
            Matcher matcher = Pattern.compile(" d=\"([^\"]+)\"").matcher(svg);
            while (matcher.find())
                paths.add(matcher.group(1));
            if (paths.size() != 3)
                throw new IllegalStateException("wordmark.svg must hold exactly three paths");
            return paths;
        }
        catch (IOException e) {
            throw new IllegalStateException("wordmark.svg cannot be read", e);
        }
    }
}
