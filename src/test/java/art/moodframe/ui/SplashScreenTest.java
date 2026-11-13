package art.moodframe.ui;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The position of the splash and info window over the main window. */
class SplashScreenTest {

    /*  the visible bounds of a primary screen below a menu bar  */
    private static final Rectangle2D PRIMARY = new Rectangle2D(0, 25, 1920, 1055);

    @Test
    void centersOverTheMainWindow() {
        assertEquals(new Point2D(560, 190),
                SplashScreen.position(new Rectangle2D(100, 100, 1400, 900), PRIMARY, 480, 720));
    }

    @Test
    void shiftsIntoTheScreenAtTheEdges() {
        assertEquals(new Point2D(1440, 360),
                SplashScreen.position(new Rectangle2D(1700, 600, 800, 600), PRIMARY, 480, 720));
        assertEquals(new Point2D(0, 25),
                SplashScreen.position(new Rectangle2D(-300, 0, 400, 300), PRIMARY, 480, 720));
    }

    @Test
    void staysOnAScreenLeftOfThePrimaryOne() {
        Rectangle2D left = new Rectangle2D(-1920, 0, 1920, 1080);
        assertEquals(new Point2D(-1340, 140),
                SplashScreen.position(new Rectangle2D(-1700, 100, 1200, 800), left, 480, 720));
        assertEquals(new Point2D(-1920, 140),
                SplashScreen.position(new Rectangle2D(-2000, 100, 600, 800), left, 480, 720));
    }

    @Test
    void staysOnAScreenRightOfThePrimaryOne() {
        Rectangle2D right = new Rectangle2D(1920, 0, 2560, 1440);
        assertEquals(new Point2D(2560, 240),
                SplashScreen.position(new Rectangle2D(2200, 200, 1200, 800), right, 480, 720));
        assertEquals(new Point2D(4000, 720),
                SplashScreen.position(new Rectangle2D(4000, 1200, 800, 600), right, 480, 720));
    }
}
