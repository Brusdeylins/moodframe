package art.moodframe.model;

import art.moodframe.model.CameraSettings.Aperture;
import art.moodframe.model.CameraSettings.Body;
import art.moodframe.model.CameraSettings.Iso;
import art.moodframe.model.CameraSettings.Lens;
import art.moodframe.model.CameraSettings.Perspective;
import art.moodframe.model.CameraSettings.Shot;
import art.moodframe.model.CameraSettings.Shutter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The settings a type fixes and the stages a chosen type or lens blocks, as their prompt texts contradict. */
class CameraSettingsTest {

    /*  every value of every setting  */
    private static final List<Enum<?>> STAGES = new ArrayList<>();

    static {
        Stream.of(Body.values(), Lens.values(), Aperture.values(), Shutter.values(), Iso.values(),
                Perspective.values(), Shot.values()).forEach((values) -> STAGES.addAll(List.of(values)));
    }

    @Test
    void fixesTheLensAndTheAutomaticOfATypeLikeTheRealCamera() {
        CameraSettings action = only(Body.ACTION_CAM);
        assertTrue(action.fixes(Lens.class));
        assertTrue(action.fixes(Aperture.class));
        assertFalse(action.fixes(Shutter.class));
        assertEquals(Lens.MM_14, action.fixed(Lens.class));
        assertEquals(Aperture.F_11, action.fixed(Aperture.class));

        CameraSettings instant = only(Body.INSTANT);
        assertEquals(Lens.MM_50, instant.fixed(Lens.class), "the normal lens of the instant camera");
        assertTrue(instant.fixes(Aperture.class));
        assertTrue(instant.fixes(Shutter.class));
        assertNull(instant.fixed(Aperture.class), "an automatic has no value");
        assertNull(instant.fixed(Shutter.class));

        assertEquals(Lens.MM_24, only(Body.DRONE).fixed(Lens.class), "a drone has a fixed wide-angle lens");
        for (Body body : List.of(Body.SMARTPHONE, Body.COMPACT_35MM, Body.FULL_FRAME, Body.MEDIUM_FORMAT))
            for (Class<? extends Enum<?>> setting : List.of(Lens.class, Aperture.class, Shutter.class, Iso.class))
                assertFalse(only(body).fixes(setting), body + " " + setting.getSimpleName());
        assertFalse(CameraSettings.ANY.fixes(Lens.class));
        assertFalse(only(Lens.MM_14).fixes(Lens.class), "a lens fixes nothing");
    }

    @Test
    void setsEverySettingTheTypeFixesAndDropsAChosenOne() {
        assertEquals(new CameraSettings(Body.ACTION_CAM, Lens.MM_14, Aperture.F_11, Shutter.FROZEN, null,
                Perspective.EYE_LEVEL, Shot.CLOSE_UP), new CameraSettings(Body.ACTION_CAM, Lens.MM_85, Aperture.F_1_4,
                Shutter.FROZEN, null, Perspective.EYE_LEVEL, Shot.CLOSE_UP).withoutConflicts());
        assertEquals(new CameraSettings(Body.INSTANT, Lens.MM_50, null, null, Iso.ISO_3200, null, null),
                new CameraSettings(Body.INSTANT, Lens.MM_400, Aperture.F_4, Shutter.FROZEN, Iso.ISO_3200, null, null)
                        .withoutConflicts(), "aperture and shutter are automatic, so no value of them stays");
        assertEquals(new CameraSettings(Body.DRONE, Lens.MM_24, null, null, null, null, null),
                only(Body.DRONE).withoutConflicts(), "\"Any\" does not exist for a fixed setting");
    }

    @Test
    void blocksEveryOtherValueOfASettingTheTypeFixes() {
        assertBlocks(Body.ACTION_CAM, Lens.MM_24, Lens.MM_35, Lens.MM_50, Lens.MM_85, Lens.MM_135, Lens.MM_400,
                Lens.MACRO, Lens.FISHEYE, Lens.TILT_SHIFT, Lens.SHIFT, Lens.ANAMORPHIC, Aperture.F_1_4, Aperture.F_4,
                Shot.EXTREME_WIDE, Shot.DETAIL);
        assertBlocks(Body.INSTANT, Lens.MM_14, Lens.MM_24, Lens.MM_35, Lens.MM_85, Lens.MM_135, Lens.MM_400,
                Lens.MACRO, Lens.FISHEYE, Lens.TILT_SHIFT, Lens.SHIFT, Lens.ANAMORPHIC, Aperture.F_1_4, Aperture.F_4,
                Aperture.F_11, Shutter.FROZEN, Shutter.NORMAL, Shutter.MOTION_BLUR, Shutter.LONG_EXPOSURE);
        assertBlocks(Body.DRONE, Lens.MM_14, Lens.MM_35, Lens.MM_50, Lens.MM_85, Lens.MM_135, Lens.MM_400,
                Lens.MACRO, Lens.FISHEYE, Lens.TILT_SHIFT, Lens.SHIFT, Lens.ANAMORPHIC, Perspective.EYE_LEVEL,
                Perspective.LOW_ANGLE, Shot.MEDIUM, Shot.CLOSE_UP, Shot.DETAIL);
    }

    @Test
    void blocksExactlyTheStagesOfEveryRowOfTheTable() {
        assertBlocks(Body.SMARTPHONE, Lens.MM_400, Lens.TILT_SHIFT, Lens.SHIFT, Aperture.F_1_4, Aperture.F_4);
        assertBlocks(Lens.MACRO, Shot.EXTREME_WIDE, Shot.WIDE, Shot.MEDIUM, Shot.CLOSE_UP);
        assertBlocks(Lens.TILT_SHIFT, Aperture.F_11, Perspective.LOW_ANGLE);
        assertBlocks(Lens.SHIFT, Perspective.LOW_ANGLE, Perspective.HIGH_ANGLE, Perspective.TOP_DOWN,
                Perspective.DUTCH_ANGLE);
        for (Body body : List.of(Body.COMPACT_35MM, Body.FULL_FRAME, Body.MEDIUM_FORMAT))
            assertBlocks(body);
        for (Lens lens : List.of(Lens.MM_14, Lens.MM_24, Lens.MM_35, Lens.MM_50, Lens.MM_85, Lens.MM_135,
                Lens.MM_400, Lens.FISHEYE, Lens.ANAMORPHIC))
            assertBlocks(lens);
    }

    @Test
    void freesWhatTheRealCameraCanDo() {
        for (Lens lens : List.of(Lens.MM_85, Lens.MM_135, Lens.MACRO))
            assertNull(only(Body.SMARTPHONE).blocker(lens), "tele camera and macro mode of a smartphone: " + lens);
        assertNull(only(Body.SMARTPHONE).blocker(Shutter.LONG_EXPOSURE), "night mode stacks frames");
        assertNull(only(Body.COMPACT_35MM).blocker(Iso.ISO_100), "film of ISO 100 exists");
        assertNull(only(Lens.MACRO).blocker(Aperture.F_11), "macro works at f/8 to f/16");
        assertNull(only(Lens.SHIFT).blocker(Aperture.F_11), "a shift lens keeps the focus");
    }

    @Test
    void blocksOnlyStagesAfterTheChosenOneSoNoChoiceBlocksItself() {
        for (Body body : Body.values())
            for (Lens lens : Lens.values()) {
                CameraSettings settings = new CameraSettings(body, lens, null, null, null, null, null);
                for (Body other : Body.values())
                    assertNull(settings.blocker(other), "a type is never blocked");
                for (Lens other : Lens.values())
                    assertEquals(only(body).blocker(other), settings.blocker(other), "a lens never blocks a lens");
            }
        assertNull(CameraSettings.ANY.blocker(null), "\"Any\" is never blocked");
    }

    @Test
    void dropsEveryBlockedStageAndLetsTheEarlierStageWin() {
        assertEquals(new CameraSettings(Body.DRONE, Lens.MM_24, Aperture.F_11, Shutter.FROZEN, Iso.ISO_100,
                Perspective.TOP_DOWN, Shot.EXTREME_WIDE), new CameraSettings(Body.DRONE, Lens.MACRO,
                Aperture.F_11, Shutter.FROZEN, Iso.ISO_100, Perspective.TOP_DOWN, Shot.EXTREME_WIDE)
                .withoutConflicts(), "the type fixes its lens, so the macro lens keeps no shot size from it");
        assertEquals(new CameraSettings(Body.SMARTPHONE, null, Aperture.F_11, null, null, null, null),
                new CameraSettings(Body.SMARTPHONE, Lens.TILT_SHIFT, Aperture.F_11, null, null, null, null)
                        .withoutConflicts(), "f/11 stays once the type dropped the tilt-shift lens");
        assertEquals(new CameraSettings(null, Lens.TILT_SHIFT, null, null, null, null, null),
                new CameraSettings(null, Lens.TILT_SHIFT, Aperture.F_11, null, null, Perspective.LOW_ANGLE, null)
                        .withoutConflicts());
        assertEquals(new CameraSettings(null, Lens.SHIFT, Aperture.F_11, null, null, Perspective.EYE_LEVEL, null),
                new CameraSettings(null, Lens.SHIFT, Aperture.F_11, null, null, Perspective.EYE_LEVEL, null)
                        .withoutConflicts(), "the shift lens keeps the camera level");
        assertEquals(new CameraSettings(null, Lens.MACRO, null, null, null, null, Shot.DETAIL),
                new CameraSettings(null, Lens.MACRO, null, null, null, null, Shot.DETAIL).withoutConflicts());
        assertEquals(new CameraSettings(null, Lens.MACRO, null, null, null, null, null),
                new CameraSettings(null, Lens.MACRO, null, null, null, null, Shot.CLOSE_UP).withoutConflicts(),
                "macro shows a small detail, not head and shoulders");
        CameraSettings free = new CameraSettings(Body.MEDIUM_FORMAT, Lens.TILT_SHIFT, Aperture.F_4,
                Shutter.LONG_EXPOSURE, Iso.ISO_100, Perspective.EYE_LEVEL, Shot.WIDE);
        assertEquals(free, free.withoutConflicts());
        CameraSettings film = new CameraSettings(Body.COMPACT_35MM, Lens.MM_35, null, null, Iso.ISO_100, null, null);
        assertEquals(film, film.withoutConflicts());
        assertEquals(CameraSettings.ANY, CameraSettings.ANY.withoutConflicts());
    }

    @Test
    void readsAStoredCameraModelAsTheTypeAlone() {
        assertEquals(new CameraSettings(Body.MEDIUM_FORMAT, Lens.MM_85, null, null, null, null, null),
                CameraSettings.fromJson("{\"body\": \"MEDIUM_FORMAT\", \"model\": \"Hasselblad X2D\", "
                        + "\"lens\": \"MM_85\"}"));
        assertFalse(new CameraSettings(Body.INSTANT, null, null, null, null, null, null).toJson().contains("model"));
    }

    /*  the chosen type or lens blocks the given stages and no other one  */
    private static void assertBlocks(Enum<?> chosen, Enum<?>... blocked) {
        CameraSettings settings = only(chosen);
        for (Enum<?> stage : STAGES)
            assertEquals(Set.of(blocked).contains(stage) ? chosen : null, settings.blocker(stage),
                    chosen + " and " + stage);
    }

    private static CameraSettings only(Enum<?> chosen) {
        return chosen instanceof Body body ? new CameraSettings(body, null, null, null, null, null, null)
                : new CameraSettings(null, (Lens) chosen, null, null, null, null, null);
    }
}
