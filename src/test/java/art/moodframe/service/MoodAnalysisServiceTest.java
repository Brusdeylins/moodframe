package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.BrainstormTurn;
import art.moodframe.model.CameraSettings;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.util.I18nManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Checks the analysis of an image: the request to the language model, its answer and the mapping to the lists. */
class MoodAnalysisServiceTest {

    private static final List<String> COLORS = List.of("#112233", "#445566");

    private final BrainstormContext context = new BrainstormContext(
            List.of(block("std:style:watercolor", PromptBlock.Type.STYLE, "Watercolor", "watercolor painting")),
            List.of(block("std:atmosphere:foggy", PromptBlock.Type.ATMOSPHERE, "Foggy", "dense fog, muted space"),
                    block("u1", PromptBlock.Type.ATMOSPHERE, "Harbor Mist", "mist over the harbor")),
            List.of(block("std:light:golden-hour", PromptBlock.Type.LIGHT, "Golden Hour", "low warm sun")),
            List.of(PromptBlockService.camera("std:camera:classic-portrait", "Classic Portrait", new CameraSettings(
                    CameraSettings.Body.FULL_FRAME, null, CameraSettings.Lens.MM_85, null, null, null, null, null))),
            List.of(palette("std:color:ocean", "Ocean")), List.of(), List.of(), "", List.of(), Set.of());

    private GeminiApiService gemini;
    private OpenAiApiService openAi;
    private ApplicationConfig config;
    private MoodAnalysisService service;

    private static PromptBlock block(String id, PromptBlock.Type type, String name, String text) {
        return new PromptBlock(id, type, name, text, null, false);
    }

    private static ColorStyle palette(String id, String name) {
        ColorStyle palette = new ColorStyle(name, List.of("#000000"));
        palette.setId(id);
        return palette;
    }

    /** A two-color image: two thirds dark blue, one third orange. */
    private static BufferedImage image() {
        BufferedImage image = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 30; x++)
                image.setRGB(x, y, y < 20 ? 0x102040 : 0xFF8800);
        }
        return image;
    }

    private static CompletableFuture<BrainstormTurn> answer(String text) {
        return CompletableFuture.completedFuture(new BrainstormTurn(text, new JSONArray(), List.of()));
    }

    @BeforeEach
    void setUp() {
        gemini = mock(GeminiApiService.class);
        openAi = mock(OpenAiApiService.class);
        config = mock(ApplicationConfig.class);
        when(config.getBrainstormEngine()).thenReturn("gemini");
        when(config.getOpenAiChatModel()).thenReturn("gpt-test");
        service = new MoodAnalysisService(gemini, openAi, config);
    }

    @Test
    void entriesAreReferredToByIdAndNewBlocksKeepTheirText() {
        String raw = "```json\n{\"atmosphere\": {\"name\": \"Harbor Mist\", \"is_new\": false},"
                + " \"style\": {\"name\": \"Ink Wash\", \"text\": \"loose ink wash on rough paper\", \"is_new\": true},"
                + " \"light\": null, \"color_name\": \"Night Port\"}\n```";

        Map<Mood.Field, BrainstormDraft.Block> blocks = MoodAnalysisService.parseReply(raw, context, COLORS);

        assertEquals(new BrainstormDraft.Block("u1", "Harbor Mist", null, List.of(), false),
                blocks.get(Mood.Field.ATMOSPHERE));
        assertEquals(new BrainstormDraft.Block(null, "Ink Wash", "loose ink wash on rough paper", List.of(), true),
                blocks.get(Mood.Field.STYLE));
        assertFalse(blocks.containsKey(Mood.Field.LIGHT));
        assertEquals(new BrainstormDraft.Block(null, "Night Port", null, COLORS, true), blocks.get(Mood.Field.COLOR));
    }

    @Test
    void aCameraIsAnEntryOrANewCameraWithTheSettingsFromTheImage() {
        Map<Mood.Field, BrainstormDraft.Block> blocks = MoodAnalysisService.parseReply("{\"camera\": {\"name\": "
                + "\"Classic Portrait\", \"is_new\": true, \"settings\": {\"body\": \"DRONE\", \"lens\": \"MM_24\", "
                + "\"aperture\": \"F_11\", \"perspective\": \"TOP_DOWN\", \"shot\": \"EXTREME_WIDE\"}}}",
                context, List.of());

        assertEquals(new BrainstormDraft.Block(null, "Classic Portrait (1)", null, List.of(), new CameraSettings(
                CameraSettings.Body.DRONE, null, CameraSettings.Lens.MM_24, CameraSettings.Aperture.F_11, null, null,
                CameraSettings.Perspective.TOP_DOWN, CameraSettings.Shot.EXTREME_WIDE), true),
                blocks.get(Mood.Field.CAMERA));
        assertEquals("std:camera:classic-portrait", MoodAnalysisService.parseReply(
                "{\"camera\": {\"name\": \"Classic Portrait\", \"is_new\": false}}", context, List.of())
                .get(Mood.Field.CAMERA).id());
        assertFalse(MoodAnalysisService.parseReply("{\"camera\": {\"name\": \"Wide\", \"text\": \"24mm\", "
                + "\"is_new\": true}}", context, List.of()).containsKey(Mood.Field.CAMERA), "a camera needs settings");
    }

    @Test
    void anEntryTheListDoesNotHaveIsLeftOut() {
        Map<Mood.Field, BrainstormDraft.Block> blocks = MoodAnalysisService.parseReply(
                "{\"light\": {\"name\": \"Neon Glow\", \"is_new\": false}, \"style\": \"Watercolor\"}", context, List.of());

        assertFalse(blocks.containsKey(Mood.Field.LIGHT));
        assertEquals("std:style:watercolor", blocks.get(Mood.Field.STYLE).id());
        assertFalse(blocks.containsKey(Mood.Field.COLOR));
    }

    @Test
    void aNewBlockNamedLikeAnEntryGetsTheNextFreeName() {
        Map<Mood.Field, BrainstormDraft.Block> blocks = MoodAnalysisService.parseReply(
                "{\"atmosphere\": {\"name\": \"Foggy\", \"text\": \"thin morning fog\", \"is_new\": true},"
                        + " \"color_name\": \"Ocean\"}", context, COLORS);

        assertEquals("Foggy (1)", blocks.get(Mood.Field.ATMOSPHERE).name());
        assertTrue(blocks.get(Mood.Field.ATMOSPHERE).isNew());
        assertEquals("Ocean (1)", blocks.get(Mood.Field.COLOR).name());
        assertEquals("Golden Hour", MoodAnalysisService.freeName(context, Mood.Field.ATMOSPHERE, "Golden Hour"));
    }

    @Test
    void anAnswerWithoutJsonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MoodAnalysisService.parseReply("no idea", context, COLORS));
    }

    @Test
    void thePromptCarriesTheRulesAndTheLists() throws Exception {
        String prompt = MoodAnalysisService.prompt(
                "{{UI_LANGUAGE}}|{{ATMOSPHERES}}|{{STYLES}}|{{LIGHTS}}|{{CAMERAS}}|{{CAMERA_SETTINGS}}", context);
        String template = new String(getClass().getResourceAsStream("/brainstorm/mood-from-image.txt").readAllBytes());

        assertFalse(prompt.contains("{{"));
        assertFalse(MoodAnalysisService.prompt(template, context).contains("{{"), "the shipped template is filled");
        assertTrue(prompt.contains("- Classic Portrait: {"), prompt);
        assertTrue(prompt.contains("- perspective: EYE_LEVEL, LOW_ANGLE, HIGH_ANGLE, TOP_DOWN, DUTCH_ANGLE"), prompt);
        assertTrue(prompt.contains("- Harbor Mist: mist over the harbor"));
        assertTrue(prompt.contains("- Watercolor: watercolor painting"));
        assertTrue(prompt.contains("- Golden Hour: low warm sun"));
    }

    @Test
    void withoutLanguageModelOnlyTheColorsAreFound() {
        MoodAnalysisService.Analysis analysis = service.analyze(image(), context).join();

        assertFalse(service.hasLanguageModel());
        assertEquals(Set.of(Mood.Field.COLOR), analysis.blocks().keySet());
        assertEquals(List.of("#102040", "#FF8800"), analysis.blocks().get(Mood.Field.COLOR).colors());
        assertEquals(I18nManager.getText("mood.image.palette"), analysis.blocks().get(Mood.Field.COLOR).name());
        assertEquals(I18nManager.getText("mood.image.no.model"), analysis.notice());
        verify(gemini, never()).chatTurn(anyString(), any(JSONArray.class), anyBoolean(), anyBoolean());
    }

    @Test
    void geminiGetsTheImageAsInlineData() {
        when(config.hasGeminiApiKey()).thenReturn(true);
        when(config.isGeminiProviderEnabled()).thenReturn(true);
        when(gemini.chatTurn(anyString(), any(JSONArray.class), eq(false), eq(false)))
                .thenReturn(answer("{\"light\": {\"name\": \"Golden Hour\", \"is_new\": false}}"));

        MoodAnalysisService.Analysis analysis = service.analyze(image(), context).join();

        ArgumentCaptor<JSONArray> contents = ArgumentCaptor.forClass(JSONArray.class);
        verify(gemini).chatTurn(anyString(), contents.capture(), eq(false), eq(false));
        JSONObject image = contents.getValue().getJSONObject(0).getJSONArray("parts").getJSONObject(0)
                .getJSONObject("inlineData");
        assertEquals("image/jpeg", image.getString("mimeType"));
        assertFalse(image.getString("data").isEmpty());
        assertNull(analysis.notice());
        assertEquals("std:light:golden-hour", analysis.blocks().get(Mood.Field.LIGHT).id());
        assertTrue(analysis.blocks().containsKey(Mood.Field.COLOR));
    }

    @Test
    void openAiAnswersWhenItIsTheOnlyProvider() {
        when(config.hasGeminiApiKey()).thenReturn(true);
        when(config.isGeminiProviderEnabled()).thenReturn(true);
        when(config.getBrainstormEngine()).thenReturn("openai");
        when(config.isOpenAiBrainstormAvailable()).thenReturn(true);
        when(config.getDefaultModel()).thenReturn(GenerationModel.OPENAI_PREFIX + "gpt-image-1");
        when(openAi.chatTurn(eq("gpt-test"), anyString(), any(JSONArray.class), eq(false), eq(false), eq(false)))
                .thenReturn(answer("{\"style\": {\"name\": \"Watercolor\", \"is_new\": false}}"));

        MoodAnalysisService.Analysis analysis = service.analyze(image(), context).join();

        assertTrue(ProviderPolicy.isOpenAiOnly(config));
        assertEquals("std:style:watercolor", analysis.blocks().get(Mood.Field.STYLE).id());
        verify(gemini, never()).chatTurn(anyString(), any(JSONArray.class), anyBoolean(), anyBoolean());
    }

    @Test
    void aFailedRequestStillGivesTheColors() {
        when(config.hasGeminiApiKey()).thenReturn(true);
        when(config.isGeminiProviderEnabled()).thenReturn(true);
        when(gemini.chatTurn(anyString(), any(JSONArray.class), anyBoolean(), anyBoolean()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("HTTP 500")));

        MoodAnalysisService.Analysis analysis = service.analyze(image(), context).join();

        assertEquals(Set.of(Mood.Field.COLOR), analysis.blocks().keySet());
        assertEquals(I18nManager.getText("mood.image.error", "HTTP 500"), analysis.notice());
    }
}
