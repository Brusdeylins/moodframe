package art.moodframe.service.prompt;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The camera fragment from its settings, and the settings as JSON. */
class CameraPromptBuilderTest {

    private static final CameraSettings FULL = new CameraSettings(Body.FULL_FRAME, Lens.MM_85, Aperture.F_4,
            Shutter.FROZEN, Iso.ISO_3200, Perspective.LOW_ANGLE, Shot.CLOSE_UP);

    private static final String SMARTPHONE = "shot on a smartphone: everything sharp from front to back, bright HDR "
            + "with lifted shadows, oversharpened edges, punchy colors, a small hard sunstar with green lens ghosts, "
            + "if the sun is present";
    private static final String COMPACT_35MM = "shot on a 35mm film compact camera: a candid analog snapshot, coarse, "
            + "clearly visible film grain, faded film colors, darker corners, a warm halation glow around the sun and "
            + "bright lights, if present";
    private static final String MEDIUM_FORMAT = "shot on a medium format camera: exceptionally fine detail, very "
            + "smooth tonal transitions, rich lifelike midtones, the main subject sharp, the background falling off "
            + "softly into gentle blur";
    private static final String ACTION_CAM = "action camera: the camera very close to the main subject, which looms "
            + "large, the background small and far away, extreme barrel distortion filling the rectangular frame, the "
            + "horizon curved into an arc";
    private static final String F_11 = "f/11: a deep depth of field, everything sharp from the nearest foreground to "
            + "the far background; the sun or bright point lights, if present, as crisp starbursts with long rays";
    private static final String ISO_100 = "ISO 100: the finest grain or noise, barely visible, a clean, smooth image "
            + "with the most detail, rich colors and the widest tonal range";
    private static final String LOW_ANGLE = "a low angle: the camera low, looking up at the main subject, which looms "
            + "large, the sky or ceiling behind it, vertical lines, if present, converging upward";
    private static final String CLOSE_UP = "a close-up: the main subject fills the frame, such as a face with head and "
            + "shoulders";
    private static final String CLOSE = "the camera very close for this framing, the background wide and far away, "
            + "stretched near the edges";
    private static final String FAR = "the camera far away for this framing, the background enlarged, pulled close "
            + "and flattened behind the main subject";

    @Test
    void writesEverySettingInTheFixedOrder() {
        assertEquals("shot on a full-frame digital camera: crisp fine detail, clean tones, a wide dynamic range with "
                + "detail in the brightest and darkest areas, natural colors; "
                + "85mm lens, 29° view: " + FAR + "; "
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
        assertEquals("", CameraPromptBuilder.build(new CameraSettings(null, null, null, Shutter.NORMAL, null,
                null, null), true), "\"Normal\" has no text");
        assertEquals(F_11 + "; a top-down view from high above, looking straight down: the main subject seen from "
                + "above like everything around it, no larger than people or things beside it, only ground and tops "
                + "visible, no horizon", CameraPromptBuilder.build(new CameraSettings(null, null, Aperture.F_11,
                Shutter.NORMAL, null, Perspective.TOP_DOWN, null), true));
    }

    @Test
    void leavesOutAStageThatContradictsAnEarlierOne() {
        assertEquals("aerial drone photo from far above, about 50 meters high, looking steeply down: the main subject "
                + "tiny, the terrain around it spread out below like a map; an extreme wide shot: the main subject "
                + "tiny within a vast view of its surroundings",
                CameraPromptBuilder.build(new CameraSettings(Body.DRONE, Lens.MACRO, null, null, null,
                        Perspective.EYE_LEVEL, Shot.EXTREME_WIDE), true), "the type drops lens and perspective");
        assertEquals(SMARTPHONE.replace("everything sharp from front to back, ", "") + "; " + F_11,
                CameraPromptBuilder.build(new CameraSettings(Body.SMARTPHONE, Lens.TILT_SHIFT, Aperture.F_11,
                        null, null, null, null), true), "f/11 stays once the type dropped the tilt-shift lens");
        assertEquals(SMARTPHONE, CameraPromptBuilder.build(new CameraSettings(Body.SMARTPHONE, Lens.MM_400, null,
                null, null, null, null), true), "a smartphone has no super-telephoto lens");
        assertEquals("macro lens at 1:1: a tiny detail of the main subject, such as the eye of a person, filling the "
                + "frame, nothing between the lens and the detail, finest textures visible, a paper-thin plane in "
                + "focus", CameraPromptBuilder.build(new CameraSettings(null, Lens.MACRO, null, null, null, null,
                Shot.CLOSE_UP), true), "macro shows a small detail, not head and shoulders");
        assertEquals("", CameraPromptBuilder.build(new CameraSettings(Body.DRONE, null, null, null, null,
                Perspective.EYE_LEVEL, null), false), "the camera is the same for every style");
    }

    @Test
    void addsNoTextForASettingTheTypeFixesSoNothingIsSaidTwice() {
        assertEquals(ACTION_CAM, CameraPromptBuilder.build(new CameraSettings(Body.ACTION_CAM, Lens.MM_85,
                Aperture.F_1_4, null, null, null, null), true), "lens and aperture are the ones of the action cam");
        assertEquals("scan of an instant film photo: a square picture in a cream-white frame with a much wider "
                + "bottom border, soft detail, low contrast, pastel colors; " + ISO_100, CameraPromptBuilder.build(
                new CameraSettings(Body.INSTANT, Lens.MM_135, Aperture.F_11, Shutter.FROZEN, Iso.ISO_100, null,
                        null), true), "only the ISO refines the instant camera");
        String drone = CameraPromptBuilder.build(new CameraSettings(Body.DRONE, Lens.MM_24, Aperture.F_11, null,
                null, null, null), true);
        assertFalse(drone.contains("24mm"), drone);
        for (Body body : List.of(Body.ACTION_CAM, Body.INSTANT, Body.DRONE)) {
            String text = CameraPromptBuilder.build(new CameraSettings(body, null, null, null, null, null, null),
                    true);
            assertFalse(text.contains("mm lens") || text.contains("f/"), text);
        }
    }

    @Test
    void leavesOutTheDepthOfFieldOrGrainOfATypeForAChosenApertureOrIso() {
        assertEquals(MEDIUM_FORMAT, CameraPromptBuilder.build(new CameraSettings(Body.MEDIUM_FORMAT, null, null, null,
                null, null, null), true));
        assertEquals("shot on a medium format camera: exceptionally fine detail, very smooth tonal transitions, rich "
                + "lifelike midtones; " + F_11, CameraPromptBuilder.build(new CameraSettings(Body.MEDIUM_FORMAT, null,
                Aperture.F_11, null, null, null, null), true), "the aperture wins over the soft background");
        assertEquals("shot on a smartphone: bright HDR with lifted shadows, oversharpened edges, punchy colors, a small "
                + "hard sunstar with green lens ghosts, if the sun is present; " + F_11, CameraPromptBuilder.build(
                new CameraSettings(Body.SMARTPHONE, null, Aperture.F_11, null, null, null, null), true));
        assertEquals("shot on a 35mm film compact camera: a candid analog snapshot, faded film colors, darker corners, "
                + "a warm halation glow around the sun and bright lights, if present; " + ISO_100,
                CameraPromptBuilder.build(new CameraSettings(Body.COMPACT_35MM, null, null, null, Iso.ISO_100, null,
                        null), true), "the ISO wins over the coarse grain of the film");
        assertEquals(COMPACT_35MM, CameraPromptBuilder.build(new CameraSettings(Body.COMPACT_35MM, null, null, null,
                null, null, null), true));
    }

    @Test
    void namesOnlyTheDistanceOfAFocalLengthWithAShotSize() {
        assertEquals("14mm ultra-wide lens, 114° view: " + CLOSE + "; " + CLOSE_UP, CameraPromptBuilder.build(
                new CameraSettings(null, Lens.MM_14, null, null, null, null, Shot.CLOSE_UP), true),
                "no \"everything much smaller\" against the close-up");
        assertEquals("135mm telephoto lens, 18° view: " + FAR + "; an extreme wide shot: the main subject tiny within "
                + "a vast view of its surroundings", CameraPromptBuilder.build(new CameraSettings(null, Lens.MM_135,
                null, null, null, null, Shot.EXTREME_WIDE), true), "no \"everything much larger\" against the "
                + "extreme wide shot");
        assertEquals("50mm lens, 47° view; " + CLOSE_UP, CameraPromptBuilder.build(new CameraSettings(null,
                Lens.MM_50, null, null, null, null, Shot.CLOSE_UP), true), "a normal lens adds nothing");
        for (Lens lens : List.of(Lens.MM_24, Lens.MM_85, Lens.MM_400)) {
            String text = CameraPromptBuilder.build(new CameraSettings(null, lens, null, null, null, null,
                    Shot.WIDE), true);
            assertTrue(text.startsWith(lens.millimeters() + "mm ") && text.contains(lens.millimeters() < 50 ? CLOSE
                    : FAR), text);
        }
        assertTrue(CameraPromptBuilder.build(new CameraSettings(null, Lens.FISHEYE, null, null, null, null,
                Shot.WIDE), true).startsWith(build(Lens.FISHEYE) + "; "), "a special lens keeps its text");
    }

    @Test
    void givesEveryValueOfEverySettingItsOwnText() {
        Set<String> texts = new HashSet<>();
        int values = 0;
        for (Body body : Body.values())
            values += add(texts, new CameraSettings(body, null, null, null, null, null, null));
        for (Lens lens : Lens.values()) {
            values += add(texts, new CameraSettings(null, lens, null, null, null, null, null));
            if (!lens.isSpecial())
                assertTrue(build(lens).startsWith(lens.millimeters() + "mm "), build(lens));
        }
        for (Aperture aperture : Aperture.values()) {
            values += add(texts, new CameraSettings(null, null, aperture, null, null, null, null));
            assertTrue(build(aperture).startsWith(aperture.label() + ": "), build(aperture));
        }
        for (Shutter shutter : List.of(Shutter.FROZEN, Shutter.MOTION_BLUR, Shutter.LONG_EXPOSURE))
            values += add(texts, new CameraSettings(null, null, null, shutter, null, null, null));
        for (Iso iso : Iso.values()) {
            values += add(texts, new CameraSettings(null, null, null, null, iso, null, null));
            assertTrue(build(iso).startsWith(iso.label() + ": "), build(iso));
        }
        for (Perspective perspective : Perspective.values())
            values += add(texts, new CameraSettings(null, null, null, null, null, perspective, null));
        for (Shot shot : Shot.values())
            values += add(texts, new CameraSettings(null, null, null, null, null, null, shot));

        assertEquals(7 + 12 + 3 + 3 + 2 + 5 + 5, values);
        assertEquals(values, texts.size(), "two values share a text");
    }

    @Test
    void keepsPeopleAndObjectsOnlyInTheEditIntroSoLensAndShotSayItNotTwice() {
        assertTrue(PromptComposer.EDIT_CAMERA_INTRO.contains("all people and objects"));
        for (Lens lens : Lens.values())
            for (Shot shot : Shot.values()) {
                CameraSettings settings = new CameraSettings(null, lens, null, null, null, null, shot);
                String text = CameraPromptBuilder.build(settings, true);
                assertFalse(text.contains("kept") || text.contains("all people and objects"), text);
                if (settings.withoutConflicts().shot() != null)
                    assertTrue(text.endsWith("; " + CameraPromptBuilder.build(new CameraSettings(null, null, null,
                            null, null, null, shot), true)), text);
            }
    }

    @Test
    void keepsOnlyPerspectiveAndShotSizeForAStyleThatIsNotPhotographic() {
        assertEquals(LOW_ANGLE + "; " + CLOSE_UP, CameraPromptBuilder.build(FULL, false));
        assertEquals("", CameraPromptBuilder.build(new CameraSettings(Body.DRONE, Lens.MM_24,
                Aperture.F_11, Shutter.LONG_EXPOSURE, Iso.ISO_100, null, null), false));
    }

    @Test
    void namesTheStagesAnEditHardlyShows() {
        assertEquals(List.of(Body.ACTION_CAM, Perspective.DUTCH_ANGLE), CameraPromptBuilder.weakInEdits(
                new CameraSettings(Body.ACTION_CAM, null, null, null, null, Perspective.DUTCH_ANGLE, null), true));
        for (Lens lens : List.of(Lens.FISHEYE, Lens.TILT_SHIFT, Lens.SHIFT, Lens.MM_135, Lens.MM_400))
            assertEquals(List.of(lens), CameraPromptBuilder.weakInEdits(new CameraSettings(Body.FULL_FRAME, lens,
                    null, null, null, null, null), true), lens.name());
        assertEquals(List.of(Perspective.DUTCH_ANGLE), CameraPromptBuilder.weakInEdits(new CameraSettings(
                Body.ACTION_CAM, Lens.FISHEYE, null, null, null, Perspective.DUTCH_ANGLE, null), false),
                "a style that is not photographic keeps only the framing");
        assertEquals(List.of(), CameraPromptBuilder.weakInEdits(new CameraSettings(Body.SMARTPHONE, Lens.MM_400,
                Aperture.F_11, Shutter.LONG_EXPOSURE, Iso.ISO_3200, Perspective.LOW_ANGLE, Shot.WIDE), true),
                "the smartphone has no 400 mm lens");
        assertEquals(List.of(), CameraPromptBuilder.weakInEdits(CameraSettings.ANY, true));
    }

    @Test
    void readsTheSettingsBackFromJson() {
        CameraSettings settings = new CameraSettings(Body.FULL_FRAME, Lens.ANAMORPHIC, Aperture.F_1_4,
                null, null, null, Shot.MEDIUM);

        assertEquals(settings, CameraSettings.fromJson(settings.toJson()));
        assertEquals(CameraSettings.ANY, CameraSettings.fromJson(CameraSettings.ANY.toJson()));
        assertEquals(new CameraSettings(null, Lens.MM_50, null, null, null, null, null),
                CameraSettings.fromJson("{\"lens\": \"MM_50\", \"aperture\": \"F_0_95\", \"body\": 3}"),
                "an unknown value counts as \"any\"");
        assertEquals(CameraSettings.ANY, CameraSettings.fromJson("no json"));
        assertEquals(CameraSettings.ANY, CameraSettings.fromJson(null));
    }

    @Test
    void readsARemovedValueAsTheValueReplacingIt() {
        assertEquals(new CameraSettings(Body.FULL_FRAME, Lens.MM_135, Aperture.F_1_4, null,
                Iso.ISO_100, null, null), CameraSettings.fromJson("{\"body\": \"APS_C\", "
                + "\"model\": \"Fujifilm X-T5\", \"lens\": \"MM_200\", \"aperture\": \"F_2\", "
                + "\"iso\": \"ISO_800\"}"));
        assertEquals(new CameraSettings(Body.FULL_FRAME, Lens.FISHEYE, null, null, null, null, null),
                CameraSettings.fromJson("{\"body\": \"LARGE_FORMAT\", \"model\": \"Linhof Technika\", "
                        + "\"lens\": \"FISHEYE\"}"), "large format and its model read as full frame");
        for (String model : List.of("ARRI Alexa 35", "RED V-Raptor", "IMAX film camera"))
            assertEquals(new CameraSettings(Body.FULL_FRAME, Lens.ANAMORPHIC, null, null, null, null, null),
                    CameraSettings.fromJson("{\"body\": \"CINEMA\", \"model\": \"" + model + "\", "
                            + "\"lens\": \"ANAMORPHIC\"}"), model);
        assertEquals(new CameraSettings(Body.INSTANT, null, null, null, null, null, null), CameraSettings.fromJson(
                "{\"body\": \"INSTANT\", \"model\": \"Polaroid SX-70\"}"), "a model reads as its type alone");
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
        return CameraPromptBuilder.build(new CameraSettings(null, lens, null, null, null, null, null), true);
    }

    private static String build(Aperture aperture) {
        return CameraPromptBuilder.build(new CameraSettings(null, null, aperture, null, null, null, null), true);
    }

    private static String build(Iso iso) {
        return CameraPromptBuilder.build(new CameraSettings(null, null, null, null, iso, null, null), true);
    }
}
