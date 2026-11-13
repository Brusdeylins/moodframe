package art.moodframe.ui.component;

import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The badges of a chat entry: a saved mood or frame by name, a combination or setting of its own as mixture. */
class EntryBadgeTest {

    private static final Map<Mood.Field, EntryMood.Block> BLOCKS = Map.of(Mood.Field.LIGHT,
            new EntryMood.Block("std:light:golden-hour", "Golden Hour", "warm low sun", null, false, List.of(), null));

    private final String mixture = I18nManager.getText("entry.badge.mood.mixture");
    private final String customFrame = I18nManager.getText("entry.badge.frame.custom");

    @Test
    void aSavedMoodShowsItsName() {
        assertEquals(new EntryBadge("Film Noir", false),
                EntryBadge.mood(new EntryMood("std:mood:film-noir", "Film Noir", BLOCKS), "None"));
    }

    @Test
    void aMixtureShowsTheMoodItStartedFromIfKnown() {
        assertEquals(new EntryBadge(mixture + " · Golden Coast", true), EntryBadge.mood(
                new EntryMood(null, null, BLOCKS, "std:mood:golden-coast", "Golden Coast", Set.of(Mood.Field.LIGHT)),
                "None"));
        assertEquals(new EntryBadge(mixture, true), EntryBadge.mood(new EntryMood(null, null, BLOCKS), "None"));
    }

    @Test
    void anEntryWithoutMoodHasNoMoodBadge() {
        assertNull(EntryBadge.mood(null, "None"));
        assertNull(EntryBadge.mood(null, null));
    }

    @Test
    void anEntryFromBefore20ShowsItsLookAsMoodAndAnyOtherStyleAsMixture() {
        assertEquals(new EntryBadge("Film Noir", false), EntryBadge.mood(null, "Film Noir"));
        assertEquals(new EntryBadge(mixture, true), EntryBadge.mood(null, "Watercolor"));
    }

    @Test
    void aFrameShowsNameAspectRatioAndResolution() {
        assertEquals(new EntryBadge("Landscape print · 3:2 · 2K", false),
                EntryBadge.frame(new EntryFrame("std:frame:landscape-print", "Landscape print", "3:2", "2K"), "3:2"));
        assertEquals(new EntryBadge(customFrame + " · 16:9 · 4K · Landscape print", true), EntryBadge.frame(
                new EntryFrame(null, null, "16:9", "4K", "std:frame:landscape-print", "Landscape print"), "16:9"));
        assertEquals(new EntryBadge(customFrame + " · 16:9 · 4K", true),
                EntryBadge.frame(new EntryFrame(null, null, "16:9", "4K"), "16:9"));
    }

    @Test
    void anEntryFromBeforeTheFramesShowsTheAspectRatioOfItsModel() {
        assertEquals(new EntryBadge(customFrame + " · 16:9", true), EntryBadge.frame(null, "16:9"));
        assertNull(EntryBadge.frame(null, null));
    }

    @Test
    void theDotLineShowsTheMoodByNameAndAMixtureByTheMoodItStartedFrom() {
        assertEquals(new EntryBadge("Film Noir", false),
                EntryBadge.moodName(new EntryMood("std:mood:film-noir", "Film Noir", BLOCKS), "None"));
        assertEquals(new EntryBadge("Golden Coast", true), EntryBadge.moodName(
                new EntryMood(null, null, BLOCKS, "std:mood:golden-coast", "Golden Coast", Set.of(Mood.Field.LIGHT)),
                "None"));
        assertEquals(new EntryBadge(mixture, true), EntryBadge.moodName(new EntryMood(null, null, BLOCKS), "None"));
        assertNull(EntryBadge.moodName(null, null));
    }

    @Test
    void theDotLineShowsTheFrameByNameAndASettingOfItsOwnByItsAspectRatio() {
        assertEquals(new EntryBadge("Landscape print", false), EntryBadge.frameName(
                new EntryFrame("std:frame:landscape-print", "Landscape print", "3:2", "2K"), "3:2"));
        assertEquals(new EntryBadge("16:9", true), EntryBadge.frameName(
                new EntryFrame(null, null, "16:9", "4K", "std:frame:landscape-print", "Landscape print"), "16:9"));
        assertEquals(new EntryBadge("16:9", true), EntryBadge.frameName(null, "16:9"));
        assertEquals(new EntryBadge(customFrame, true), EntryBadge.frameName(new EntryFrame(null, null, null, "2K"),
                null));
        assertNull(EntryBadge.frameName(null, null));
    }

    @Test
    void theModelShowsUnderItsLocalizedName() {
        GenerationModel model = GenerationModel.getDefault();
        assertEquals(new EntryBadge(model.getLocalizedDisplayName(), false), EntryBadge.model(model.getDisplayName()));
        assertEquals(new EntryBadge("gpt-image-1", false), EntryBadge.model("gpt-image-1"));
        assertNull(EntryBadge.model(null));
    }
}
