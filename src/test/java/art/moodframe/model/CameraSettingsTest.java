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
import static org.junit.jupiter.api.Assertions.assertNull;

/** The stages a chosen type or lens blocks, as their prompt texts contradict each other. */
class CameraSettingsTest {

    /*  every value of every setting  */
    private static final List<Enum<?>> STAGES = new ArrayList<>();

    static {
        Stream.of(Body.values(), Lens.values(), Aperture.values(), Shutter.values(), Iso.values(),
                Perspective.values(), Shot.values()).forEach((values) -> STAGES.addAll(List.of(values)));
    }

    @Test
    void blocksExactlyTheStagesOfEveryRowOfTheTable() {
        assertBlocks(Body.DRONE, Perspective.EYE_LEVEL, Perspective.LOW_ANGLE, Shot.CLOSE_UP, Shot.DETAIL,
                Lens.MACRO);
        assertBlocks(Body.SMARTPHONE, Aperture.F_1_4, Aperture.F_4, Lens.TILT_SHIFT);
        assertBlocks(Body.ACTION_CAM, Lens.MM_24, Lens.MM_35, Lens.MM_50, Lens.MM_85, Lens.MM_135, Lens.MM_400,
                Lens.MACRO, Lens.TILT_SHIFT, Lens.ANAMORPHIC);
        assertBlocks(Body.LARGE_FORMAT, Lens.FISHEYE, Perspective.DUTCH_ANGLE);
        assertBlocks(Lens.MACRO, Shot.EXTREME_WIDE, Shot.WIDE, Shot.MEDIUM);
        assertBlocks(Lens.TILT_SHIFT, Aperture.F_11);
        for (Body body : List.of(Body.COMPACT_35MM, Body.FULL_FRAME, Body.MEDIUM_FORMAT, Body.CINEMA, Body.INSTANT))
            assertBlocks(body);
        for (Lens lens : List.of(Lens.MM_14, Lens.MM_24, Lens.MM_35, Lens.MM_50, Lens.MM_85, Lens.MM_135,
                Lens.MM_400, Lens.FISHEYE, Lens.ANAMORPHIC))
            assertBlocks(lens);
    }

    @Test
    void blocksOnlyStagesAfterTheChosenOneSoNoChoiceBlocksItself() {
        for (Body body : Body.values())
            for (Lens lens : Lens.values()) {
                CameraSettings settings = new CameraSettings(body, null, lens, null, null, null, null, null);
                for (Body other : Body.values())
                    assertNull(settings.blocker(other), "a type is never blocked");
                for (Lens other : Lens.values())
                    assertEquals(new CameraSettings(body, null, null, null, null, null, null, null).blocker(other),
                            settings.blocker(other), "a lens never blocks a lens");
            }
        assertNull(CameraSettings.ANY.blocker(null), "\"Any\" is never blocked");
    }

    @Test
    void dropsEveryBlockedStageAndLetsTheEarlierStageWin() {
        assertEquals(new CameraSettings(Body.DRONE, "DJI Mavic 3", null, Aperture.F_11, Shutter.FROZEN, Iso.ISO_100,
                Perspective.TOP_DOWN, Shot.EXTREME_WIDE), new CameraSettings(Body.DRONE, "DJI Mavic 3", Lens.MACRO,
                Aperture.F_11, Shutter.FROZEN, Iso.ISO_100, Perspective.TOP_DOWN, Shot.EXTREME_WIDE)
                .withoutConflicts(), "the type drops the macro lens, which then keeps no shot size from it");
        assertEquals(new CameraSettings(Body.SMARTPHONE, null, null, Aperture.F_11, null, null, null, null),
                new CameraSettings(Body.SMARTPHONE, null, Lens.TILT_SHIFT, Aperture.F_11, null, null, null, null)
                        .withoutConflicts(), "f/11 stays once the type dropped the tilt-shift lens");
        assertEquals(new CameraSettings(null, null, Lens.TILT_SHIFT, null, null, null, null, null),
                new CameraSettings(null, null, Lens.TILT_SHIFT, Aperture.F_11, null, null, null, null)
                        .withoutConflicts());
        assertEquals(new CameraSettings(Body.ACTION_CAM, null, null, null, null, null, Perspective.EYE_LEVEL,
                Shot.CLOSE_UP), new CameraSettings(Body.ACTION_CAM, null, Lens.MM_85, null, null, null,
                Perspective.EYE_LEVEL, Shot.CLOSE_UP).withoutConflicts());
        CameraSettings free = new CameraSettings(Body.LARGE_FORMAT, "Linhof Technika", Lens.TILT_SHIFT, Aperture.F_4,
                Shutter.LONG_EXPOSURE, Iso.ISO_100, Perspective.EYE_LEVEL, Shot.WIDE);
        assertEquals(free, free.withoutConflicts());
        assertEquals(CameraSettings.ANY, CameraSettings.ANY.withoutConflicts());
    }

    /*  the chosen type or lens blocks the given stages and no other one  */
    private static void assertBlocks(Enum<?> chosen, Enum<?>... blocked) {
        CameraSettings settings = chosen instanceof Body body
                ? new CameraSettings(body, null, null, null, null, null, null, null)
                : new CameraSettings(null, null, (Lens) chosen, null, null, null, null, null);
        for (Enum<?> stage : STAGES)
            assertEquals(Set.of(blocked).contains(stage) ? chosen : null, settings.blocker(stage),
                    chosen + " and " + stage);
    }
}
