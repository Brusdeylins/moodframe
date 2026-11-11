package art.moodframe.service;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.Frame;
import art.moodframe.model.GenerationModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Standard frames, the frames of the user in a fresh SQLite database, and frames whose model is not available. */
class FrameServiceTest {

    private static final String OPENAI = GenerationModel.OPENAI_PREFIX + "gpt-image-1";

    @TempDir
    static Path tempDir;

    private static FrameService frames;

    /*  The model dropdown with all providers set up, and with only one of them  */
    private static List<String> all;
    private static List<String> bfl;
    private static List<String> gemini;

    @BeforeAll
    static void createDatabase() {
        DatabaseConfig.initialize(tempDir.resolve("db").resolve("test.db").toString());
        frames = new FrameService();
        bfl = items(ApiProvider.BFL);
        gemini = items(ApiProvider.GOOGLE_GEMINI);
        all = new ArrayList<>(bfl);
        all.addAll(gemini);
        all.add(OPENAI);
    }

    private static List<String> items(ApiProvider provider) {
        return GenerationModel.selectable().stream().filter((GenerationModel model) -> model.getProvider() == provider)
                .map(GenerationModel::getLocalizedDisplayName).toList();
    }

    private static String item(GenerationModel model) {
        return model.getLocalizedDisplayName();
    }

    private static Frame standard(String name) {
        return frames.getDefaults().stream().filter((Frame frame) -> frame.getName().equals(name)).findFirst()
                .orElseThrow();
    }

    @Test
    void offersSevenLockedStandardFramesThatTakeTheirPreferredModel() {
        assertEquals(List.of("Social Post", "Story / Reel", "Präsentation", "Web-Banner", "Poster / Druck",
                "Text im Bild", "Schnelle Skizzen"), frames.getDefaults().stream().map(Frame::getName).toList());
        for (Frame frame : frames.getDefaults()) {
            assertTrue(frame.isDefault(), frame.getName());
            assertTrue(frame.getId().startsWith("std:frame:"), frame.getName());
            assertNotNull(frames.resolve(frame, all), frame.getName() + " has no model");
            assertFalse(frames.moveToTrash(frame.getId()), frame.getName() + " went to the trash");
            assertFalse(frames.update(frame), frame.getName() + " was changed");
        }

        assertEquals(item(GenerationModel.NANO_BANANA2_2K), frames.resolve(standard("Social Post"), all));
        assertEquals(item(GenerationModel.NANO_BANANA2_2K), frames.resolve(standard("Story / Reel"), all));
        assertEquals(item(GenerationModel.FLUX3_IMAGE_2K), frames.resolve(standard("Präsentation"), all));
        assertEquals(item(GenerationModel.FLUX3_IMAGE_2K), frames.resolve(standard("Web-Banner"), all));
        assertEquals(item(GenerationModel.FLUX3_IMAGE_4K), frames.resolve(standard("Poster / Druck"), all));
        assertEquals(item(GenerationModel.GEMINI_PRO_2K), frames.resolve(standard("Text im Bild"), all));
        assertEquals(item(GenerationModel.FLUX3_KLEIN_1K), frames.resolve(standard("Schnelle Skizzen"), all));

        FrameService.Fields social = frames.apply(standard("Social Post"), all, item(GenerationModel.FLUX3_IMAGE_1K));
        assertTrue(social.available());
        assertEquals("4:5", social.aspectRatio());
        assertEquals(4, social.imageCount());
    }

    @Test
    void aStandardFrameTakesTheFirstModelWhoseProviderHasAnApiKey() {
        assertEquals(item(GenerationModel.NANO_BANANA2_2K), frames.resolve(standard("Web-Banner"), gemini));
        assertEquals(item(GenerationModel.GEMINI_PRO_4K), frames.resolve(standard("Poster / Druck"), gemini));
        assertEquals(item(GenerationModel.NANO_BANANA2_LITE), frames.resolve(standard("Schnelle Skizzen"), gemini));
        assertEquals(item(GenerationModel.FLUX2_FLEX), frames.resolve(standard("Text im Bild"), bfl));

        /*  With OpenAI alone, the format falls back to the closest one OpenAI can do  */
        FrameService.Fields social = frames.apply(standard("Social Post"), List.of(OPENAI), OPENAI);
        assertTrue(social.available());
        assertEquals(OPENAI, social.item());
        assertEquals("2:3", social.aspectRatio());
        assertNull(frames.resolve(standard("Web-Banner"), List.of(OPENAI)), "Web-Banner names no OpenAI model");
    }

    @Test
    void aFrameWhoseModelIsNotAvailableStaysAndSetsTheOtherFields() {
        Frame frame = new Frame(null, "Instagram Story");
        frame.setModel(GenerationModel.NANO_BANANA2_2K.getApiModelId());
        frame.setResolution("2K");
        frame.setAspectRatio("9:16");
        frame.setImageCount(4);
        assertTrue(frames.add(frame));

        /*  Provider without API key: the model stays, the other fields are set  */
        String current = item(GenerationModel.FLUX3_IMAGE_1K);
        FrameService.Fields fields = frames.apply(frame, bfl, current);
        assertFalse(fields.available());
        assertEquals(current, fields.item());
        assertNull(fields.resolution());
        assertEquals("9:16", fields.aspectRatio());
        assertEquals(4, fields.imageCount());

        /*  The current model cannot do the format: the closest one it can do  */
        assertEquals("2:3", frames.apply(frame, List.of(OPENAI), OPENAI).aspectRatio());

        /*  OpenAI model no longer listed, model shut down (Nano Banana 1K), model unknown  */
        frame.setModel("openai:dall-e-3");
        assertNull(frames.resolve(frame, all));
        frame.setModel(GenerationModel.GEMINI_FLASH_1K.getApiModelId());
        assertNull(frames.resolve(frame, all));
        frame.setModel("flux.9-ultra");
        assertNull(frames.resolve(frame, all));

        /*  The frame is kept as it is  */
        Frame stored = frames.find(frame.getId());
        assertNotNull(stored);
        assertEquals(GenerationModel.NANO_BANANA2_2K.getApiModelId(), stored.getModel());
        assertEquals(item(GenerationModel.NANO_BANANA2_2K), frames.resolve(stored, all));
    }

    @Test
    void capturesTheFieldsAndKeepsGuidanceAndStepsOnlyForFlex() {
        Frame frame = new Frame(null, "Druck A3");
        FrameService.capture(frame, item(GenerationModel.FLUX3_IMAGE_4K), "4K", "2:3", 1, 5.0, 50);
        assertEquals("flux.3-image", frame.getModel());
        assertEquals("4K", frame.getResolution());
        assertEquals("2:3", frame.getAspectRatio());
        assertEquals(1, frame.getImageCount());
        assertNull(frame.getGuidance());
        assertNull(frame.getSteps());
        assertEquals(item(GenerationModel.FLUX3_IMAGE_4K), frames.resolve(frame, all));

        FrameService.capture(frame, item(GenerationModel.FLUX2_FLEX), null, "16:9", 2, 3.5, 28);
        assertEquals("flux.2-flex", frame.getModel());
        assertNull(frame.getResolution());
        assertEquals(3.5, frame.getGuidance());
        assertEquals(28, frame.getSteps());
        FrameService.Fields fields = frames.apply(frame, all, OPENAI);
        assertEquals(3.5, fields.guidance());
        assertEquals(28, fields.steps());

        /*  A model of a gateway keeps its free resolution  */
        String qwen = GenerationModel.OPENAI_PREFIX + "qwen-image-2.1";
        FrameService.capture(frame, qwen, "4K", "16:9", 1, 5.0, 50);
        assertEquals("openai:qwen-image-2.1", frame.getModel());
        assertEquals("4K", frame.getResolution());
        fields = frames.apply(frame, List.of(OPENAI, qwen), OPENAI);
        assertEquals(qwen, fields.item());
        assertEquals("4K", fields.resolution());
    }

    @Test
    void aVideoModelMakesOneVideo() {
        Frame frame = new Frame(null, "Clip");
        frame.setModel(GenerationModel.OMNI_720P.getApiModelId());
        frame.setResolution("720p");
        frame.setAspectRatio("4:5");
        frame.setImageCount(4);

        FrameService.Fields fields = frames.apply(frame, gemini, item(GenerationModel.NANO_BANANA2_1K));
        assertEquals(item(GenerationModel.OMNI_720P), fields.item());
        assertEquals("9:16", fields.aspectRatio());
        assertEquals(1, fields.imageCount());
    }

    @Test
    void restoresAFrameFromTheTrashUnderAFreeName() {
        Frame frame = new Frame(null, "Trash Me");
        frame.setModel("flux.3-image");
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
        frame.setModel("flux.2-flex");
        frame.setAspectRatio("3:2");
        frame.setImageCount(2);
        frame.setGuidance(4.5);
        frame.setSteps(30);
        assertTrue(frames.add(frame));

        File file = tempDir.resolve("frames.json").toFile();
        assertTrue(frames.exportFrames(file));
        assertTrue(frames.importFrames(file) > 0);

        Frame copy = frames.getCustom().stream().filter((Frame custom) -> custom.getName().equals("Export Me (1)"))
                .findFirst().orElseThrow();
        assertEquals("flux.2-flex", copy.getModel());
        assertNull(copy.getResolution());
        assertEquals("3:2", copy.getAspectRatio());
        assertEquals(2, copy.getImageCount());
        assertEquals(4.5, copy.getGuidance());
        assertEquals(30, copy.getSteps());
        assertEquals(-1, frames.importFrames(tempDir.resolve("missing.json").toFile()));
    }

    private static Frame copy(Frame frame, String name) {
        Frame copy = new Frame(null, name);
        copy.setModels(frame.getModels());
        copy.setResolution(frame.getResolution());
        copy.setAspectRatio(frame.getAspectRatio());
        copy.setImageCount(frame.getImageCount());
        return copy;
    }
}
