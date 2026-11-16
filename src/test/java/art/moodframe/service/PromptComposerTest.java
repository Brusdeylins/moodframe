package art.moodframe.service;

import art.moodframe.model.CameraSettings;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.PromptBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptComposerTest {

    private static final PromptBlock PHOTO = new PromptBlock("std:style:photography", PromptBlock.Type.STYLE,
            "Photography", "realistic photograph, sharp focus", PromptBlock.GROUP_PHOTO, true);
    private static final PromptBlock WATERCOLOR = new PromptBlock("std:style:watercolor", PromptBlock.Type.STYLE,
            "Watercolor", "watercolor painting", PromptBlock.GROUP_ART, false);
    private static final PromptBlock FLAT = new PromptBlock("std:style:infographic-flat", PromptBlock.Type.STYLE,
            "Infographic Flat", "Flat vector infographic.", PromptBlock.GROUP_LAYOUT, false);
    private static final PromptBlock FOG = new PromptBlock("fog", PromptBlock.Type.ATMOSPHERE, "Fog",
            "quiet morning fog", null, true);
    private static final PromptBlock WINDOW = new PromptBlock("window", PromptBlock.Type.LIGHT, "Window Light",
            "soft daylight from a large window", null, true);
    private static final PromptBlock WIDE = PromptBlockService.camera("wide", "Wide", new CameraSettings(null, null,
            null, null, null, null, CameraSettings.Perspective.EYE_LEVEL, CameraSettings.Shot.WIDE));
    private static final PromptBlock PORTRAIT = PromptBlockService.camera("portrait", "Portrait", new CameraSettings(
            CameraSettings.Body.FULL_FRAME, null, CameraSettings.Lens.MM_85, null, null, null, null,
            CameraSettings.Shot.CLOSE_UP));
    private static final String CLOSE_UP = "a close-up: the main subject fills the frame, such as a face with head and "
            + "shoulders";
    private static final ColorStyle OCEAN = new ColorStyle("Ocean Breeze", List.of("#48BDFF", "#0096C7"), true);

    private final PromptComposer composer = new PromptComposer(2000);

    @Test
    void putsTheMediumFirstAndOneLabeledLinePerBlockInTheFixedOrder() {
        String prompt = composer.compose("  a lighthouse on a cliff ",
                new PromptComposer.Selection(PHOTO, FOG, WINDOW, WIDE, OCEAN));

        assertEquals("""
                Image style: realistic photograph, sharp focus.
                a lighthouse on a cliff
                Atmosphere: quiet morning fog.
                Lighting: soft daylight from a large window.
                Camera: eye level: the camera at the height of the main subject, looking straight at it, vertical \
                lines upright; a wide shot: the whole main subject in the frame with space around it, such as a \
                person from head to toe.
                Color palette: the image is composed predominantly of these colors: color 1 hex #48BDFF, \
                color 2 hex #0096C7. The palette takes precedence over the color of the light. The colors appear \
                only as colors of objects, surfaces and light; the image contains no written color codes, \
                color names or swatches.""", prompt);
    }

    @Test
    void leavesOutEmptyBlocks() {
        PromptBlock emptyLight = new PromptBlock("x", PromptBlock.Type.LIGHT, "Empty", "  ", null, true);

        assertEquals("a lighthouse", composer.compose("a lighthouse",
                new PromptComposer.Selection(null, null, emptyLight, PromptBlockService.camera("any", "Any",
                        CameraSettings.ANY), new ColorStyle("None", List.of(), false))));
        assertEquals("", composer.compose("   ", new PromptComposer.Selection(PHOTO, FOG, WINDOW, null, OCEAN)));
    }

    @Test
    void writesFramingInsteadOfCameraForAStyleThatIsNotPhotographic() {
        String prompt = composer.compose("a fox",
                new PromptComposer.Selection(WATERCOLOR, null, null, PORTRAIT, null));

        assertEquals("Image style: watercolor painting.\na fox\nFraming: " + CLOSE_UP + ".", prompt);
    }

    @Test
    void givesLayoutStylesTheTextRuleOnceLeavesOutTheLightAndKeepsTheFraming() {
        String prompt = composer.compose("how a heat pump works",
                new PromptComposer.Selection(FLAT, FOG, WINDOW, PORTRAIT, OCEAN));

        assertTrue(prompt.startsWith("Image style: Flat vector infographic. " + PromptComposer.LAYOUT_TEXT_RULE + "\n"));
        assertEquals(1, prompt.split(PromptComposer.LAYOUT_TEXT_RULE, -1).length - 1);
        assertTrue(prompt.contains("\nAtmosphere: quiet morning fog.\n"));
        assertTrue(prompt.contains("\nColor palette: "));
        assertFalse(prompt.contains("Lighting:"));
        assertFalse(prompt.contains("Camera:"));
        assertTrue(prompt.contains("\nFraming: " + CLOSE_UP + ".\n"));
    }

    @Test
    void putsTheEditIntroBeforeThePhotographicCameraOnlyWithAReferenceImage() {
        PromptComposer.Selection photo = new PromptComposer.Selection(PHOTO, FOG, null, PORTRAIT, null);
        String camera = "Camera: " + CameraPromptBuilder.build(CameraSettings.fromJson(PORTRAIT.getParams()), true)
                + ".";

        assertEquals("Image style: realistic photograph, sharp focus.\na woman on a ledge\nAtmosphere: quiet morning "
                + "fog.\n" + PromptComposer.EDIT_CAMERA_INTRO + " " + camera,
                composer.compose("a woman on a ledge", photo, true));
        assertTrue(composer.compose("a woman on a ledge", photo, false).endsWith("\n" + camera));
        assertEquals(composer.compose("a woman on a ledge", photo), composer.compose("a woman on a ledge", photo,
                false), "without reference image by default");
        assertEquals("Image style: watercolor painting.\na fox\nFraming: " + CLOSE_UP + ".", composer.compose("a fox",
                new PromptComposer.Selection(WATERCOLOR, null, null, PORTRAIT, null), true), "no intro for framing");
        assertFalse(composer.compose("a fox", new PromptComposer.Selection(PHOTO, FOG, null, null, null), true)
                .contains(PromptComposer.EDIT_CAMERA_INTRO), "no intro without camera");
    }

    @Test
    void checksTheLengthWithTheEditIntroAndTheLongestCamera() {
        PromptBlock longest = PromptBlockService.camera("longest", "Longest", new CameraSettings(
                CameraSettings.Body.SMARTPHONE, "Google Pixel 10 Pro", CameraSettings.Lens.MM_14,
                CameraSettings.Aperture.F_11, CameraSettings.Shutter.LONG_EXPOSURE, CameraSettings.Iso.ISO_3200,
                CameraSettings.Perspective.DUTCH_ANGLE, CameraSettings.Shot.EXTREME_WIDE));
        PromptComposer.Selection all = new PromptComposer.Selection(PHOTO, FOG, WINDOW, longest, OCEAN);
        String plain = composer.compose("a woman on a ledge", all, false);
        String edit = composer.compose("a woman on a ledge", all, true);

        assertEquals(plain.length() + PromptComposer.EDIT_CAMERA_INTRO.length() + 1, edit.length());
        assertTrue(composer.fits(edit), "every block leaves room for the subject in 2000 characters: " + edit.length());
        assertFalse(new PromptComposer(plain.length()).fits(edit), "the intro counts");
    }

    @Test
    void checksTheLengthOfTheWholePrompt() {
        PromptComposer small = new PromptComposer(60);
        String subjectOnly = small.compose("a lighthouse", new PromptComposer.Selection(null, null, null, null, null));
        String withBlocks = small.compose("a lighthouse", new PromptComposer.Selection(PHOTO, null, null, null, OCEAN));

        assertTrue(small.fits(subjectOnly));
        assertFalse(small.fits(withBlocks));
    }
}
