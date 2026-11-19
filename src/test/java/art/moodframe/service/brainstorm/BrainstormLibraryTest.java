package art.moodframe.service.brainstorm;

import art.moodframe.database.TestDatabase;
import art.moodframe.model.Frame;
import art.moodframe.model.Mood;
import art.moodframe.model.SavedImage;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.FavoriteService;
import art.moodframe.service.library.FrameService;
import art.moodframe.service.library.LibraryEvents;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.library.SavedImageService;
import art.moodframe.service.library.TestLibraryServices;
import art.moodframe.service.provider.ChatTool;
import art.moodframe.service.provider.ChatTurn;
import art.moodframe.service.provider.gemini.GeminiApiService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The tools of the brainstorm on the library, in a fresh SQLite database: they list and look up the standard moods
 * and frames, those of the user and the favorites first, and the saved images with their descriptions; an unknown id
 * is an error for the model, no failure of the turn.
 */
class BrainstormLibraryTest {

    @TempDir
    static Path home;

    private static final GeminiApiService gemini = mock(GeminiApiService.class);
    private static BrainstormTools tools;
    private static Mood ownMood;
    private static Frame ownFrame;
    private static SavedImage logo;

    @BeforeAll
    static void createLibrary() throws IOException {
        TestDatabase.open(home.resolve("db").resolve("library.db").toString());
        LibraryEvents events = new LibraryEvents();
        PromptBlockService blocks = TestLibraryServices.blocks(events);
        ColorStyleService colors = TestLibraryServices.palettes(events);
        MoodService moods = TestLibraryServices.moods(blocks, colors, events);
        FrameService frames = TestLibraryServices.frames(events);
        FavoriteService favorites = TestLibraryServices.favorites(events);
        SavedImageService savedImages = TestLibraryServices.savedImages(home.resolve("library-images"), events);

        ownMood = new Mood(null, "Harbor Dawn");
        ownMood.setReference(Mood.Field.ATMOSPHERE, "std:atmosphere:calm");
        assertTrue(moods.add(ownMood));
        ownFrame = new Frame(null, "Shop Banner");
        ownFrame.setAspectRatio("21:9");
        ownFrame.setResolution("2K");
        assertTrue(frames.add(ownFrame));
        favorites.toggle(FavoriteService.Kind.MOOD, ownMood.getId());
        favorites.toggle(FavoriteService.Kind.FRAME, ownFrame.getId());

        BufferedImage pixels = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Path file = home.resolve("logo.png");
        ImageIO.write(pixels, "png", file.toFile());
        logo = savedImages.save(file, "ACME Logo", "the company logo, for every advertisement").image();

        tools = new BrainstormTools(gemini, null, new BrainstormLibrary(moods, frames, favorites, savedImages),
                Runnable::run);
    }

    /*  runs a call of the model and gives its answer, with the status the window shows  */
    private static JSONObject call(String name, JSONObject args, List<String> status) {
        BrainstormTools.Run run = tools.run(new BrainstormTools.Request("s1", ChatModel.gemini(gemini), status::add,
                () -> { }), new ChatTurn.ToolCall("call_0", name, args));
        assertNull(run.note(), "a lookup of the library is no research");
        return run.result().response();
    }

    private static List<String> names(JSONArray entries) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++)
            names.add(entries.getJSONObject(i).getString("name"));
        return names;
    }

    @Test
    void offersTheToolsOfTheLibraryToEveryModel() {
        List<String> offered = tools.offeredTo(ChatModel.gemini(gemini)).stream().map(ChatTool::name).toList();

        assertTrue(offered.containsAll(List.of("list_moods", "get_mood", "list_frames", "get_frame",
                "list_saved_images")), offered.toString());
        assertTrue(tools.looksUp(new ChatTurn.ToolCall("c", "get_mood", new JSONObject())));
        assertFalse(tools.looksUp(new ChatTurn.ToolCall("c", "research", new JSONObject())));
    }

    @Test
    void listsTheStandardMoodsAndThoseOfTheUserWithTheFavoritesFirst() {
        List<String> status = new ArrayList<>();
        JSONObject answer = call("list_moods", new JSONObject(), status);

        List<String> listed = new ArrayList<>(names(answer.getJSONArray("moods")));
        for (int offset = answer.optInt("next_offset", -1); offset > 0; offset = answer.optInt("next_offset", -1)) {
            answer = call("list_moods", new JSONObject().put("offset", offset), status);
            listed.addAll(names(answer.getJSONArray("moods")));
        }
        assertEquals("Harbor Dawn", listed.get(0), "the favorite comes first");
        assertTrue(listed.containsAll(List.of("Quiet Morning", "Clean Product")), "the standard moods are listed");
        assertEquals(answer.getInt("total"), listed.size(), "the offsets lead through the whole list");
        assertTrue(status.contains("the moods") || status.contains("die Moods"), status.toString());

        JSONObject first = call("list_moods", new JSONObject(), status).getJSONArray("moods").getJSONObject(0);
        assertEquals(ownMood.getId(), first.getString("id"));
        assertTrue(first.getBoolean("favorite"));
        assertEquals("atmosphere Calm", first.getString("blocks"));
        assertTrue(call("list_moods", new JSONObject(), status).toString().length()
                <= BrainstormTools.TOOL_RESULT_MAX_LENGTH, "the answer is cut to the length of a tool result");
    }

    @Test
    void getsAMoodWithTheTextsOfItsBlocks() {
        JSONObject mood = call("get_mood", new JSONObject().put("id", "std:mood:quiet-morning"), new ArrayList<>());

        assertEquals("Quiet Morning", mood.getString("name"));
        assertTrue(mood.getBoolean("standard"));
        JSONObject blocks = mood.getJSONObject("blocks");
        assertEquals("Calm", blocks.getJSONObject("atmosphere").getString("name"));
        assertFalse(blocks.getJSONObject("atmosphere").getString("text").isBlank());
        assertTrue(blocks.getJSONObject("color").getJSONArray("colors").length() > 0, "a palette with its colors");
    }

    @Test
    void listsAndGetsTheFramesWithTheirFormat() {
        JSONObject answer = call("list_frames", new JSONObject(), new ArrayList<>());

        List<String> listed = names(answer.getJSONArray("frames"));
        assertEquals("Shop Banner", listed.get(0), "the favorite comes first");
        assertTrue(listed.contains("Social Post"), "the standard frames are listed");
        assertEquals("21:9 · 2K", answer.getJSONArray("frames").getJSONObject(0).getString("format"));

        JSONObject frame = call("get_frame", new JSONObject().put("id", ownFrame.getId()), new ArrayList<>());
        assertEquals("21:9", frame.getString("aspect_ratio"));
        assertEquals("2K", frame.getString("resolution"));
        assertFalse(frame.getBoolean("standard"));
    }

    @Test
    void listsTheSavedImagesWithNameAndDescription() {
        JSONObject image = call("list_saved_images", new JSONObject(), new ArrayList<>())
                .getJSONArray("saved_images").getJSONObject(0);

        assertEquals(logo.id(), image.getString("id"));
        assertEquals("ACME Logo", image.getString("name"));
        assertEquals("the company logo, for every advertisement", image.getString("description"));
    }

    @Test
    void anUnknownIdIsAnErrorForTheModel() {
        JSONObject mood = call("get_mood", new JSONObject().put("id", "gone"), new ArrayList<>());
        JSONObject frame = call("get_frame", new JSONObject(), new ArrayList<>());

        assertTrue(mood.getString("error").contains("No mood with the id 'gone'"), mood.toString());
        assertTrue(frame.getString("error").contains("No frame with the id ''"), frame.toString());
        verifyNoInteractions(gemini);
    }
}
