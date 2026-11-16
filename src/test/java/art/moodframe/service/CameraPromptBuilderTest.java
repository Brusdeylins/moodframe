package art.moodframe.service;

import art.moodframe.model.CameraSettings;
import art.moodframe.model.CameraSettings.Aperture;
import art.moodframe.model.CameraSettings.Body;
import art.moodframe.model.CameraSettings.Iso;
import art.moodframe.model.CameraSettings.Lens;
import art.moodframe.model.CameraSettings.Perspective;
import art.moodframe.model.CameraSettings.Shot;
import art.moodframe.model.CameraSettings.Shutter;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The camera fragment from its settings, and the settings as JSON. */
class CameraPromptBuilderTest {

    private static final CameraSettings FULL = new CameraSettings(Body.FULL_FRAME, null, Lens.MM_85, Aperture.F_4,
            Shutter.FROZEN, Iso.ISO_3200, Perspective.LOW_ANGLE, Shot.CLOSE_UP);

    private static final String SMARTPHONE = "shot on a smartphone: everything sharp from front to back, bright HDR "
            + "with lifted shadows, oversharpened edges, punchy colors, a small hard sunstar with green lens ghosts, "
            + "if the sun is present";
    private static final String COMPACT_35MM = "a 35mm film compact camera: a candid analog snapshot, coarse, clearly "
            + "visible film grain, faded film colors, darker corners, a warm halation glow around the sun and bright "
            + "lights, if present";
    private static final String MEDIUM_FORMAT = "camera: exceptionally fine detail, very smooth tonal transitions, "
            + "rich lifelike midtones, the main subject sharp, the background falling off softly into gentle blur";
    private static final String F_11 = "f/11: a deep depth of field, everything sharp from the nearest foreground to "
            + "the far background; the sun or bright point lights, if present, as crisp starbursts with long rays";
    private static final String LOW_ANGLE = "a low angle: the camera low, looking up at the main subject, which looms "
            + "large, the sky or ceiling behind it, vertical lines, if present, converging upward";
    private static final String CLOSE_UP = "a close-up: the main subject fills the frame, such as a face with head and "
            + "shoulders";

    @Test
    void writesEverySettingInTheFixedOrder() {
        assertEquals("shot on a full-frame digital camera: crisp fine detail, clean tones, a wide dynamic range with "
                + "detail in the brightest and darkest areas, natural colors; "
                + "85mm lens, 29° view: a narrower view, everything in the frame larger, less of the surroundings, the "
                + "background enlarged and pulled closer, softly out of focus; "
                + "f/4: a moderate depth of field, the main subject sharp, things well in front of or behind it gently "
                + "blurred but recognizable; the sun, if present, with short soft rays; "
                + "a very fast shutter of 1/4000 s: everything that moves, such as hair, fabric, water drops, people "
                + "or vehicles, if present, frozen crisply in mid-motion, no blur; "
                + "ISO 3200: strong, clearly visible grain and color noise across the whole image, strongest in the "
                + "shadows, slightly softer detail and muted colors; "
                + LOW_ANGLE + "; " + CLOSE_UP,
                CameraPromptBuilder.build(FULL, true));
    }

    @Test
    void leavesOutEverySettingThatIsAnyAndTheNormalShutter() {
        assertEquals("", CameraPromptBuilder.build(CameraSettings.ANY, true));
        assertEquals("", CameraPromptBuilder.build(new CameraSettings(null, null, null, null, Shutter.NORMAL, null,
                null, null), true), "\"Normal\" has no text");
        assertEquals(F_11 + "; a top-down view from high above, looking straight down: the main subject seen from "
                + "above like everything around it, no larger than people or things beside it, only ground and tops "
                + "visible, no horizon", CameraPromptBuilder.build(new CameraSettings(null, null, null, Aperture.F_11,
                Shutter.NORMAL, null, Perspective.TOP_DOWN, null), true));
    }

    @Test
    void leavesOutAStageThatContradictsAnEarlierOne() {
        assertEquals("aerial drone photo from far above, about 50 meters high, looking steeply down: the main subject "
                + "tiny, the terrain around it spread out below like a map; an extreme wide shot: the main subject "
                + "tiny within a vast view of its surroundings",
                CameraPromptBuilder.build(new CameraSettings(Body.DRONE, null, Lens.MACRO, null, null, null,
                        Perspective.EYE_LEVEL, Shot.EXTREME_WIDE), true), "the type drops lens and perspective");
        assertEquals(SMARTPHONE + "; " + F_11,
                CameraPromptBuilder.build(new CameraSettings(Body.SMARTPHONE, null, Lens.TILT_SHIFT, Aperture.F_11,
                        null, null, null, null), true), "f/11 stays once the type dropped the tilt-shift lens");
        assertEquals(SMARTPHONE, CameraPromptBuilder.build(new CameraSettings(Body.SMARTPHONE, null, Lens.MM_135, null,
                null, null, null, null), true), "a wide view and a narrow one exclude each other");
        assertEquals("macro lens at 1:1: a tiny detail of the main subject, such as the eye of a person, filling the "
                + "frame, nothing between the lens and the detail, finest textures visible, a paper-thin plane in "
                + "focus", CameraPromptBuilder.build(new CameraSettings(null, null, Lens.MACRO, Aperture.F_11, null,
                null, null, null), true), "a paper-thin plane in focus excludes front and back in focus");
        assertEquals("", CameraPromptBuilder.build(new CameraSettings(Body.DRONE, null, null, null, null, null,
                Perspective.EYE_LEVEL, null), false), "the camera is the same for every style");
    }

    @Test
    void givesEveryValueOfEverySettingItsOwnText() {
        Set<String> texts = new HashSet<>();
        int values = 0;
        for (Body body : Body.values())
            values += add(texts, new CameraSettings(body, null, null, null, null, null, null, null));
        for (Lens lens : Lens.values()) {
            values += add(texts, new CameraSettings(null, null, lens, null, null, null, null, null));
            if (!lens.isSpecial())
                assertTrue(build(lens).startsWith(lens.millimeters() + "mm "), build(lens));
        }
        for (Aperture aperture : Aperture.values()) {
            values += add(texts, new CameraSettings(null, null, null, aperture, null, null, null, null));
            assertTrue(build(aperture).startsWith(aperture.label() + ": "), build(aperture));
        }
        for (Shutter shutter : List.of(Shutter.FROZEN, Shutter.MOTION_BLUR, Shutter.LONG_EXPOSURE))
            values += add(texts, new CameraSettings(null, null, null, null, shutter, null, null, null));
        for (Iso iso : Iso.values()) {
            values += add(texts, new CameraSettings(null, null, null, null, null, iso, null, null));
            assertTrue(build(iso).startsWith(iso.label() + ": "), build(iso));
        }
        for (Perspective perspective : Perspective.values())
            values += add(texts, new CameraSettings(null, null, null, null, null, null, perspective, null));
        for (Shot shot : Shot.values())
            values += add(texts, new CameraSettings(null, null, null, null, null, null, null, shot));

        assertEquals(7 + 11 + 3 + 3 + 2 + 5 + 5, values);
        assertEquals(values, texts.size(), "two values share a text");
    }

    @Test
    void keepsPeopleAndObjectsOnlyInTheEditIntroSoLensAndShotSayItNotTwice() {
        assertTrue(PromptComposer.EDIT_CAMERA_INTRO.contains("all people and objects"));
        for (Lens lens : Lens.values())
            for (Shot shot : Shot.values()) {
                CameraSettings settings = new CameraSettings(null, null, lens, null, null, null, null, shot);
                String text = CameraPromptBuilder.build(settings, true);
                assertFalse(text.contains("kept") || text.contains("all people and objects"), text);
                if (settings.withoutConflicts().shot() != null)
                    assertEquals(build(lens) + "; " + CameraPromptBuilder.build(new CameraSettings(null, null, null,
                            null, null, null, null, shot), true), text);
            }
    }

    @Test
    void namesAModelFirstAndKeepsItsDeviceClassAndEffect() {
        assertEquals("shot on " + COMPACT_35MM, CameraPromptBuilder.build(new CameraSettings(Body.COMPACT_35MM, null,
                null, null, null, null, null, null), true), "\"Generic\" names the device class only");
        assertEquals("shot on Leica M6, " + COMPACT_35MM, CameraPromptBuilder.build(new CameraSettings(
                Body.COMPACT_35MM, "Leica M6", null, null, null, null, null, null), true));
        assertEquals("shot on Polaroid SX-70, scan of an instant film photo: a square picture in a cream-white frame "
                + "with a much wider bottom border, soft detail, low contrast, pastel colors",
                CameraPromptBuilder.build(new CameraSettings(Body.INSTANT, "Polaroid SX-70", null, null, null, null,
                        null, null), true));
        assertEquals("shot on Pentax 67, a medium format film " + MEDIUM_FORMAT, CameraPromptBuilder.build(
                new CameraSettings(Body.MEDIUM_FORMAT, "Pentax 67", null, null, null, null, null, null), true),
                "an analog model of a digital type names a film camera");
        assertEquals("shot on Hasselblad X2D, a medium format " + MEDIUM_FORMAT, CameraPromptBuilder.build(
                new CameraSettings(Body.MEDIUM_FORMAT, "Hasselblad X2D", null, null, null, null, null, null), true));
    }

    @Test
    void keepsOnlyPerspectiveAndShotSizeForAStyleThatIsNotPhotographic() {
        assertEquals(LOW_ANGLE + "; " + CLOSE_UP, CameraPromptBuilder.build(FULL, false));
        assertEquals("", CameraPromptBuilder.build(new CameraSettings(Body.DRONE, "DJI Mavic 3", Lens.MM_24,
                Aperture.F_11, Shutter.LONG_EXPOSURE, Iso.ISO_100, null, null), false));
    }

    @Test
    void offersOnlyTheModelsOfTheTypeAndNoneForDroneAndActionCam() {
        for (Body body : Body.values()) {
            List<CameraPromptBuilder.Model> models = CameraPromptBuilder.models(body);
            assertEquals(body == Body.DRONE || body == Body.ACTION_CAM, models.isEmpty(), body.name());
            assertTrue(models.stream().allMatch((CameraPromptBuilder.Model model) -> model.body() == body));
        }
        assertEquals(List.of("Polaroid SX-70"), CameraPromptBuilder.models(Body.INSTANT).stream()
                .map(CameraPromptBuilder.Model::name).toList());
        assertEquals(List.of("Hasselblad X2D", "Fujifilm GFX 100S", "Hasselblad 500C/M (analog)", "Pentax 67 (analog)"),
                CameraPromptBuilder.models(Body.MEDIUM_FORMAT).stream().map(CameraPromptBuilder.Model::label).toList());
    }

    @Test
    void readsTheSettingsBackFromJson() {
        CameraSettings settings = new CameraSettings(Body.FULL_FRAME, "Sony A7 IV", Lens.ANAMORPHIC, Aperture.F_1_4,
                null, null, null, Shot.MEDIUM);

        assertEquals(settings, CameraSettings.fromJson(settings.toJson()));
        assertEquals(CameraSettings.ANY, CameraSettings.fromJson(CameraSettings.ANY.toJson()));
        assertEquals(new CameraSettings(null, null, Lens.MM_50, null, null, null, null, null),
                CameraSettings.fromJson("{\"lens\": \"MM_50\", \"aperture\": \"F_0_95\", \"body\": 3}"),
                "an unknown value counts as \"any\"");
        assertEquals(CameraSettings.ANY, CameraSettings.fromJson("no json"));
        assertEquals(CameraSettings.ANY, CameraSettings.fromJson(null));
    }

    @Test
    void readsARemovedValueAsTheValueReplacingIt() {
        assertEquals(new CameraSettings(Body.FULL_FRAME, null, Lens.MM_135, Aperture.F_1_4, null,
                Iso.ISO_100, null, null), CameraSettings.fromJson("{\"body\": \"APS_C\", "
                + "\"model\": \"Fujifilm X-T5\", \"lens\": \"MM_200\", \"aperture\": \"F_2\", "
                + "\"iso\": \"ISO_800\"}"));
        assertEquals(new CameraSettings(Body.FULL_FRAME, null, Lens.FISHEYE, null, null, null, null, null),
                CameraSettings.fromJson("{\"body\": \"LARGE_FORMAT\", \"model\": \"Linhof Technika\", "
                        + "\"lens\": \"FISHEYE\"}"), "large format and its model read as \"Generic\" full frame");
        for (String model : List.of("ARRI Alexa 35", "RED V-Raptor", "IMAX film camera"))
            assertEquals(new CameraSettings(Body.FULL_FRAME, null, Lens.ANAMORPHIC, null, null, null, null, null),
                    CameraSettings.fromJson("{\"body\": \"CINEMA\", \"model\": \"" + model + "\", "
                            + "\"lens\": \"ANAMORPHIC\"}"), model);
        for (String model : List.of("Contax T2", "Olympus mju-II", "Fujifilm Instax", "DJI Mavic 3", "GoPro Hero"))
            assertNull(CameraSettings.fromJson("{\"body\": \"INSTANT\", \"model\": \"" + model
                    + "\"}").model(), model);
        assertEquals("Polaroid SX-70", CameraSettings.fromJson("{\"body\": \"INSTANT\", "
                + "\"model\": \"Polaroid SX-70\"}").model(), "a model that stays keeps loading");
        assertEquals(Aperture.F_11, CameraSettings.fromJson("{\"aperture\": \"F_8\"}").aperture());
        assertEquals(Aperture.F_11, CameraSettings.fromJson("{\"aperture\": \"F_16\"}").aperture());
    }

    /*  a text of 200 characters at most, without "kept", as the edit intro keeps people and objects  */
    private static int add(Set<String> texts, CameraSettings settings) {
        String text = CameraPromptBuilder.build(settings, true);
        assertFalse(text.isEmpty(), settings.toString());
        assertTrue(text.length() <= 200, text.length() + " characters: " + text);
        assertFalse(text.contains("kept"), text);
        texts.add(text);
        return 1;
    }

    private static String build(Lens lens) {
        return CameraPromptBuilder.build(new CameraSettings(null, null, lens, null, null, null, null, null), true);
    }

    private static String build(Aperture aperture) {
        return CameraPromptBuilder.build(new CameraSettings(null, null, null, aperture, null, null, null, null), true);
    }

    private static String build(Iso iso) {
        return CameraPromptBuilder.build(new CameraSettings(null, null, null, null, null, iso, null, null), true);
    }
}
