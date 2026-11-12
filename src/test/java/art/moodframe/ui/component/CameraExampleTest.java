package art.moodframe.ui.component;

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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The example pictures of the camera dialog. */
class CameraExampleTest {

    @Test
    void hasAPictureForEveryStageOfEverySettingAndForNoCamera() {
        List<Enum<?>> stages = new ArrayList<>();
        stages.add(null);
        Stream.of(Body.values(), Lens.values(), Aperture.values(), Shutter.values(), Iso.values(),
                Perspective.values(), Shot.values()).forEach((values) -> stages.addAll(List.of(values)));
        for (Enum<?> stage : stages)
            assertNotNull(CameraExample.class.getResource(CameraExample.resource(stage)),
                    CameraExample.resource(stage));
        assertEquals("/images/camera/lens-MM_85.jpg", CameraExample.resource(Lens.MM_85));
    }
}
