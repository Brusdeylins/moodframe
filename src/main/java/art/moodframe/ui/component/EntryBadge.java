package art.moodframe.ui.component;

import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.MoodService;
import art.moodframe.util.I18nManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The text of a badge below the prompt of a chat entry: its mood, its frame or its model. A saved mood or frame shows
 * its name; a combination or setting of its own shows as mixture, with the mood or frame it started from if known.
 *
 * @param text the text of the badge
 * @param mixture whether the badge stands for a combination or setting of its own, drawn dashed and in italics
 * @since 2.0.0
 */
public record EntryBadge(String text, boolean mixture) {

    private static final String SEPARATOR = " · ";

    /**
     * The badge of the mood of an entry. An entry from before 2.0 shows a former look style as the standard mood of the
     * same name, any other style as mixture.
     *
     * @param mood the copy of the mood at the entry, null for none
     * @param style the style name of the entry, "None" or null for none
     * @return the badge, null for an entry without mood
     */
    public static EntryBadge mood(EntryMood mood, String style) {
        if (mood != null && mood.moodId() != null)
            return new EntryBadge(mood.moodName(), false);
        if (mood != null)
            return new EntryBadge(join(I18nManager.getText("entry.badge.mood.mixture"), mood.baseName()), true);
        if (MoodService.isLegacyLook(style))
            return new EntryBadge(style, false);
        if (style != null && !style.isBlank() && !"None".equals(style))
            return new EntryBadge(I18nManager.getText("entry.badge.mood.mixture"), true);
        return null;
    }

    /**
     * The badge of the frame of an entry: name, target aspect ratio and target resolution. An entry from before the
     * frames were copied shows the aspect ratio of the model as setting of its own.
     *
     * @param frame the copy of the frame at the entry, null for none
     * @param size the aspect ratio the model generated with, null for none
     * @return the badge, null for an entry without either
     */
    public static EntryBadge frame(EntryFrame frame, String size) {
        if (frame != null && frame.frameId() != null)
            return new EntryBadge(join(frame.frameName(), frame.aspectRatio(), frame.resolution()), false);
        if (frame != null)
            return new EntryBadge(join(I18nManager.getText("entry.badge.frame.custom"), frame.aspectRatio(),
                    frame.resolution(), frame.baseName()), true);
        if (size != null)
            return new EntryBadge(join(I18nManager.getText("entry.badge.frame.custom"), size), true);
        return null;
    }

    /**
     * The badge of the model of an entry, under its localized name.
     *
     * @param model the model name stored at the entry (its display name or an OpenAI model id), null for none
     * @return the badge, null for an entry without model
     */
    public static EntryBadge model(String model) {
        if (model == null)
            return null;
        GenerationModel known = GenerationModel.fromDisplayName(model);
        return new EntryBadge(known != null ? known.getLocalizedDisplayName() : model, false);
    }

    /** Joins the parts that are given with a middle dot. */
    static String join(String... parts) {
        List<String> given = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank())
                given.add(part);
        }
        return String.join(SEPARATOR, given);
    }
}
