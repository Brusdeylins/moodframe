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

    @Test
    void writesEverySettingInTheFixedOrder() {
        assertEquals("shot on a full-frame digital camera, clean sharp detail, high dynamic range, "
                + "85mm lens, a tight view on the subject, the background visibly enlarged and softly compressed "
                + "behind it, "
                + "f/4, moderate depth of field, the background gently soft, "
                + "fast shutter speed, motion frozen crisply, "
                + "ISO 3200, pronounced grain and high-ISO noise, "
                + "low-angle shot from below, looking up, "
                + "close-up framing", CameraPromptBuilder.build(FULL, true));
    }

    @Test
    void leavesOutEverySettingThatIsAnyAndTheNormalShutter() {
        assertEquals("", CameraPromptBuilder.build(CameraSettings.ANY, true));
        assertEquals("", CameraPromptBuilder.build(new CameraSettings(null, null, null, null, Shutter.NORMAL, null,
                null, null), true), "\"Normal\" has no text");
        assertEquals("f/11, deep depth of field, front and back in sharp focus, top-down view from directly above",
                CameraPromptBuilder.build(new CameraSettings(null, null, null, Aperture.F_11, Shutter.NORMAL, null,
                        Perspective.TOP_DOWN, null), true));
    }

    @Test
    void leavesOutAStageThatContradictsAnEarlierOne() {
        assertEquals("aerial photograph from high above, extreme wide shot, the subject small in the frame",
                CameraPromptBuilder.build(new CameraSettings(Body.DRONE, null, Lens.MACRO, null, null, null,
                        Perspective.EYE_LEVEL, Shot.EXTREME_WIDE), true), "the type drops lens and perspective");
        assertEquals("shot on a smartphone, a wide view with everything sharp from front to back, bright HDR look "
                + "with lifted shadows, f/11, deep depth of field, front and back in sharp focus",
                CameraPromptBuilder.build(new CameraSettings(Body.SMARTPHONE, null, Lens.TILT_SHIFT, Aperture.F_11,
                        null, null, null, null), true), "f/11 stays once the type dropped the tilt-shift lens");
        assertEquals("shot on a smartphone, a wide view with everything sharp from front to back, bright HDR look "
                + "with lifted shadows", CameraPromptBuilder.build(new CameraSettings(Body.SMARTPHONE, null,
                Lens.MM_135, null, null, null, null, null), true), "a wide view and a narrow one exclude each other");
        assertEquals("macro lens, extreme magnification, tiny structures and textures filling the whole frame, "
                + "razor-thin focus", CameraPromptBuilder.build(new CameraSettings(null, null, Lens.MACRO,
                Aperture.F_11, null, null, null, null), true), "razor-thin focus excludes front and back in focus");
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
            assertTrue(build(aperture).startsWith(aperture.label() + ", "), build(aperture));
        }
        for (Shutter shutter : List.of(Shutter.FROZEN, Shutter.MOTION_BLUR, Shutter.LONG_EXPOSURE))
            values += add(texts, new CameraSettings(null, null, null, null, shutter, null, null, null));
        for (Iso iso : Iso.values()) {
            values += add(texts, new CameraSettings(null, null, null, null, null, iso, null, null));
            assertTrue(build(iso).startsWith(iso.label() + ", "), build(iso));
        }
        for (Perspective perspective : Perspective.values())
            values += add(texts, new CameraSettings(null, null, null, null, null, null, perspective, null));
        for (Shot shot : Shot.values())
            values += add(texts, new CameraSettings(null, null, null, null, null, null, null, shot));

        assertEquals(7 + 11 + 3 + 3 + 2 + 5 + 5, values);
        assertEquals(values, texts.size(), "two values share a text");
    }

    @Test
    void namesAModelFirstAndKeepsItsDeviceClassAndEffect() {
        assertEquals("shot on a 35mm film camera, a candid analog snapshot with visible film grain and soft, "
                + "slightly faded colors", CameraPromptBuilder.build(new CameraSettings(Body.COMPACT_35MM, null, null,
                null, null, null, null, null), true), "\"Generic\" names the device class only");
        assertEquals("shot on Leica M6, a 35mm film camera, a candid analog snapshot with visible film grain and "
                + "soft, slightly faded colors", CameraPromptBuilder.build(new CameraSettings(Body.COMPACT_35MM,
                "Leica M6", null, null, null, null, null, null), true));
        assertEquals("shot on Polaroid SX-70, scan of an instant film photo with its plain white border, soft detail, "
                + "slightly imperfect exposure, gentle faded colors", CameraPromptBuilder.build(new CameraSettings(
                Body.INSTANT, "Polaroid SX-70", null, null, null, null, null, null), true));
        assertEquals("shot on Pentax 67, a medium format film camera, exceptional detail, smooth tonal transitions",
                CameraPromptBuilder.build(new CameraSettings(Body.MEDIUM_FORMAT, "Pentax 67", null, null, null, null,
                        null, null), true), "an analog model of a digital type names a film camera");
        assertEquals("shot on Hasselblad X2D, a medium format camera, exceptional detail, smooth tonal transitions",
                CameraPromptBuilder.build(new CameraSettings(Body.MEDIUM_FORMAT, "Hasselblad X2D", null, null, null,
                        null, null, null), true));
    }

    @Test
    void keepsOnlyPerspectiveAndShotSizeForAStyleThatIsNotPhotographic() {
        assertEquals("low-angle shot from below, looking up, close-up framing", CameraPromptBuilder.build(FULL, false));
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

    private static int add(Set<String> texts, CameraSettings settings) {
        String text = CameraPromptBuilder.build(settings, true);
        assertFalse(text.isEmpty(), settings.toString());
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
