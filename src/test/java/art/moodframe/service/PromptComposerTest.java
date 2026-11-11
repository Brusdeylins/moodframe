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
    private static final ColorStyle OCEAN = new ColorStyle("Ocean Breeze", List.of("#48BDFF", "#0096C7"), true);

    private final PromptComposer composer = new PromptComposer(2000);

    @Test
    void putsTheMediumFirstAndOneLabeledLinePerBlockInTheFixedOrder() {
        String prompt = composer.compose("  a lighthouse on a cliff ", null,
                new PromptComposer.Selection(PHOTO, FOG, WINDOW, WIDE, OCEAN));

        assertEquals("""
                Image style: realistic photograph, sharp focus.
                a lighthouse on a cliff
                Atmosphere: quiet morning fog.
                Lighting: soft daylight from a large window.
                Camera: shot at eye level, wide shot, the full subject with its surroundings.
                Color palette: the image is composed predominantly of these colors: color 1 hex #48BDFF, \
                color 2 hex #0096C7. The palette takes precedence over the color of the light. The colors appear \
                only as colors of objects, surfaces and light; the image contains no written color codes, \
                color names or swatches.""", prompt);
    }

    @Test
    void leavesOutEmptyBlocks() {
        PromptBlock emptyLight = new PromptBlock("x", PromptBlock.Type.LIGHT, "Empty", "  ", null, true);

        assertEquals("a lighthouse", composer.compose("a lighthouse", null,
                new PromptComposer.Selection(null, null, emptyLight, PromptBlockService.camera("any", "Any",
                        CameraSettings.ANY), new ColorStyle("None", List.of(), false))));
        assertEquals("", composer.compose("   ", null, new PromptComposer.Selection(PHOTO, FOG, WINDOW, null, OCEAN)));
    }

    @Test
    void writesFramingInsteadOfCameraForAStyleThatIsNotPhotographic() {
        String prompt = composer.compose("a fox", null,
                new PromptComposer.Selection(WATERCOLOR, null, null, PORTRAIT, null));

        assertEquals("Image style: watercolor painting.\na fox\nFraming: close-up framing.", prompt);
    }

    @Test
    void givesLayoutStylesTheTextRuleOnceLeavesOutTheLightAndKeepsTheFraming() {
        String prompt = composer.compose("how a heat pump works", null,
                new PromptComposer.Selection(FLAT, FOG, WINDOW, PORTRAIT, OCEAN));

        assertTrue(prompt.startsWith("Image style: Flat vector infographic. " + PromptComposer.LAYOUT_TEXT_RULE + "\n"));
        assertEquals(1, prompt.split(PromptComposer.LAYOUT_TEXT_RULE, -1).length - 1);
        assertTrue(prompt.contains("\nAtmosphere: quiet morning fog.\n"));
        assertTrue(prompt.contains("\nColor palette: "));
        assertFalse(prompt.contains("Lighting:"));
        assertFalse(prompt.contains("Camera:"));
        assertTrue(prompt.contains("\nFraming: close-up framing.\n"));
    }

    @Test
    void placesTheVideoContentRightBehindTheSubject() {
        String prompt = composer.compose("a fox", "a fox runs through snow",
                new PromptComposer.Selection(null, FOG, null, null, null));

        assertEquals("a fox\nContent: a fox runs through snow\nAtmosphere: quiet morning fog.", prompt);
    }

    @Test
    void checksTheLengthOfTheWholePrompt() {
        PromptComposer small = new PromptComposer(60);
        String subjectOnly = small.compose("a lighthouse", null, new PromptComposer.Selection(null, null, null, null, null));
        String withBlocks = small.compose("a lighthouse", null, new PromptComposer.Selection(PHOTO, null, null, null, OCEAN));

        assertTrue(small.fits(subjectOnly));
        assertFalse(small.fits(withBlocks));
    }
}
