package art.moodframe.service;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.AspectRatios;
import art.moodframe.model.Frame;
import art.moodframe.model.GenerationModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Standard frames, the frames of the user in a fresh SQLite database, and a frame applied to different models. */
class FrameServiceTest {

    private static final String OPENAI = GenerationModel.OPENAI_PREFIX + "gpt-image-1";

    @TempDir
    static Path tempDir;

    private static FrameService frames;

    @BeforeAll
    static void createDatabase() {
        DatabaseConfig.initialize(tempDir.resolve("db").resolve("test.db").toString());
        frames = new FrameService();
    }

    private static String item(GenerationModel model) {
        return model.getLocalizedDisplayName();
    }

    private static Frame standard(String name) {
        return frames.getDefaults().stream().filter((Frame frame) -> frame.getName().equals(name)).findFirst()
                .orElseThrow();
    }

    @Test
    void offersSevenLockedStandardFramesWithATargetAspectRatio() {
        assertEquals(List.of("Social Post", "Story / Reel", "Präsentation", "Web-Banner", "Poster / Druck",
                "Text im Bild", "Schnelle Skizzen"), frames.getDefaults().stream().map(Frame::getName).toList());
        assertEquals(List.of("4:5", "9:16", "16:9", "21:9", "2:3", "16:9", "1:1"),
                frames.getDefaults().stream().map(Frame::getAspectRatio).toList());
        for (Frame frame : frames.getDefaults()) {
            assertTrue(frame.isDefault(), frame.getName());
            assertTrue(frame.getId().startsWith("std:frame:"), frame.getName());
            assertTrue(List.of(AspectRatios.ALL).contains(frame.getAspectRatio()), frame.getName());
            assertFalse(frames.moveToTrash(frame.getId()), frame.getName() + " went to the trash");
            assertFalse(frames.update(frame), frame.getName() + " was changed");
        }
    }

    @Test
    void aFrameAppliedToDifferentModelsTakesTheClosestAspectRatioOfEach() {
        String socialPost = standard("Social Post").getAspectRatio();
        assertEquals("4:5", AspectRatios.nearestForItem(socialPost, item(GenerationModel.FLUX2_PRO)));
        assertEquals("4:5", AspectRatios.nearestForItem(socialPost, item(GenerationModel.NANO_BANANA2_2K)));
        assertEquals("2:3", AspectRatios.nearestForItem(socialPost, OPENAI));
        assertEquals("9:16", AspectRatios.nearestForItem(socialPost, item(GenerationModel.OMNI_720P)));

        String webBanner = standard("Web-Banner").getAspectRatio();
        assertEquals("21:9", AspectRatios.nearestForItem(webBanner, item(GenerationModel.FLUX3_IMAGE_2K)));
        assertEquals("3:2", AspectRatios.nearestForItem(webBanner, OPENAI));
        assertEquals("16:9", AspectRatios.nearestForItem(webBanner, item(GenerationModel.OMNI_1080P)));

        /*  A target only Nano Banana 2 can do: the others take their widest format  */
        Frame panorama = new Frame(null, "Panorama");
        panorama.setAspectRatio("8:1");
        assertTrue(frames.add(panorama));
        String target = frames.find(panorama.getId()).getAspectRatio();
        assertEquals("8:1", AspectRatios.nearestForItem(target, item(GenerationModel.NANO_BANANA2_1K)));
        assertEquals("21:9", AspectRatios.nearestForItem(target, item(GenerationModel.FLUX2_PRO)));
        assertEquals("21:9", AspectRatios.nearestForItem(target, item(GenerationModel.GEMINI_PRO_2K)));
        assertEquals("3:2", AspectRatios.nearestForItem(target, OPENAI));
    }

    @Test
    void storesAFrameOnlyWithNameAndAspectRatio() {
        Frame frame = new Frame(null, "No Ratio");
        assertFalse(frames.add(frame), "the aspect ratio is missing");
        frame.setAspectRatio("5:4");
        assertTrue(frames.add(frame));
        assertEquals("5:4", FrameService.describe(frames.find(frame.getId())));

        frame.setAspectRatio("4:3");
        assertTrue(frames.update(frame));
        assertEquals("4:3", frames.find(frame.getId()).getAspectRatio());
        frame.setAspectRatio(null);
        assertFalse(frames.update(frame), "the aspect ratio is missing");
    }

    @Test
    void restoresAFrameFromTheTrashUnderAFreeName() {
        Frame frame = new Frame(null, "Trash Me");
        frame.setAspectRatio("1:1");
        assertTrue(frames.add(frame));
        assertFalse(frames.add(copy(frame, "Trash Me")), "the name is taken");
        assertFalse(frames.add(copy(frame, "Social Post")), "a standard frame has the name");

        assertTrue(frames.moveToTrash(frame.getId()));
        assertNull(frames.find(frame.getId()));
        assertTrue(frames.getDeleted().stream().anyMatch((Frame deleted) -> deleted.getId().equals(frame.getId())));
        assertTrue(frames.add(copy(frame, "Trash Me")));

        assertTrue(frames.restore(frame.getId()));
        assertEquals("Trash Me (1)", frames.find(frame.getId()).getName());
        assertTrue(frames.deletePermanently(frame.getId()));
        assertNull(frames.find(frame.getId()));
    }

    @Test
    void exportsAndImportsTheFramesOfTheUserAsJson() {
        Frame frame = new Frame(null, "Export Me");
        frame.setAspectRatio("3:2");
        assertTrue(frames.add(frame));

        File file = tempDir.resolve("frames.json").toFile();
        assertTrue(frames.exportFrames(file));
        assertTrue(frames.importFrames(file) > 0);

        Frame copy = frames.getCustom().stream().filter((Frame custom) -> custom.getName().equals("Export Me (1)"))
                .findFirst().orElseThrow();
        assertEquals("3:2", copy.getAspectRatio());
        assertEquals(List.of("id", "name", "aspectRatio"), List.copyOf(frames.exportCustom().get(0).keySet()));
        assertEquals(-1, frames.importFrames(tempDir.resolve("missing.json").toFile()));
    }

    @Test
    void importsAnOlderExportWithModelAndKeepsItsAspectRatio() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", "older-export");
        entry.put("name", "Older Export");
        entry.put("model", "flux.2-flex");
        entry.put("resolution", "2K");
        entry.put("aspectRatio", "16:9");
        entry.put("imageCount", 4);
        entry.put("guidance", 3.5);
        entry.put("steps", 28);

        assertEquals(1, frames.importCustom(List.of(entry)));
        Frame imported = frames.find("older-export");
        assertEquals("Older Export", imported.getName());
        assertEquals("16:9", imported.getAspectRatio());
    }

    private static Frame copy(Frame frame, String name) {
        Frame copy = new Frame(null, name);
        copy.setAspectRatio(frame.getAspectRatio());
        return copy;
    }
}
