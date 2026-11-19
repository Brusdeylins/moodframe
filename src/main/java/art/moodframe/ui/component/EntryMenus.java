package art.moodframe.ui.component;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.library.MoodService;
import art.moodframe.util.I18nManager;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

/**
 * The context menus of a chat entry: on its prompt and badges the mood, on its frame badge the frame, on an image the
 * mood of the image and its seed. An action that is null is not offered or disabled, as each menu says.
 */
final class EntryMenus {

    private EntryMenus() {
    }

    /**
     * An item of a menu of the entry, which keeps the key of its text for a change of the language.
     *
     * @param key    the i18n key of the text
     * @param action what the item does
     * @return the item
     */
    static MenuItem item(String key, Runnable action) {
        MenuItem item = new MenuItem(I18nManager.getText(key));
        item.setUserData(key);
        item.setOnAction((event) -> action.run());
        return item;
    }

    /**
     * Whether an entry carries building blocks or a former look style (1.x), which lives on as a standard mood.
     *
     * @param entry the entry
     * @return true if it has a mood to take over or save
     */
    static boolean hasMood(ChatEntry entry) {
        return entry.getMood() != null || MoodService.isLegacyLook(entry.getStyle());
    }

    /**
     * The menu with "Use mood" and "Save as mood...", both available for an entry with a mood.
     *
     * @param available whether the entry has a mood
     * @param use       takes over the mood
     * @param save      saves the mood under a new name
     * @return the menu
     */
    static ContextMenu moodMenu(boolean available, Runnable use, Runnable save) {
        MenuItem takeOver = item("entry.reuse.mood", use);
        takeOver.setDisable(!available);
        MenuItem saveAs = item("entry.mood.save", save);
        saveAs.setDisable(!available);
        return new ContextMenu(takeOver, saveAs);
    }

    /**
     * The menu of the frame badge with "Use frame" and "Save as frame...".
     *
     * @param use  takes over the frame
     * @param save saves the frame of the entry (null: disabled, for an entry without its frame)
     * @return the menu
     */
    static ContextMenu frameMenu(Runnable use, Runnable save) {
        MenuItem saveAs = item("entry.frame.save", save != null ? save : () -> { });
        saveAs.setDisable(save == null);
        return new ContextMenu(item("entry.reuse.frame", use), saveAs);
    }

    /**
     * The menu of an image with "Use mood" or "Mood from image..." and "Copy seed".
     *
     * @param useMood       takes over the mood saved at the entry (null: not offered)
     * @param moodFromImage analyzes the image into a mood, offered without "Use mood" (null: not offered)
     * @param copySeed      copies the seed of the image (null: not offered)
     * @param seedApplies   whether the seed takes effect, i.e. a FLUX model is chosen
     * @return the menu, null without any item
     */
    static ContextMenu imageMenu(Runnable useMood, Runnable moodFromImage, Runnable copySeed, boolean seedApplies) {
        ContextMenu menu = new ContextMenu();
        if (useMood != null)
            menu.getItems().add(item("entry.reuse.mood", useMood));
        else if (moodFromImage != null)
            menu.getItems().add(item("mood.image.menu", moodFromImage));
        if (copySeed != null) {
            MenuItem seed = item("tooltip.copy.seed", copySeed);
            seed.setDisable(!seedApplies);
            menu.getItems().add(seed);
        }
        return menu.getItems().isEmpty() ? null : menu;
    }

    /**
     * Whether an image offers "Copy seed": an image of BFL with a seed, as only BFL repeats an image by its seed. The
     * provider is that of the constant of its model; a model that is no constant any more is a former FLUX model,
     * unless it is one of the OpenAI Images API. FLUX seeds can be negative (XOR of random longs), so any seed but 0.
     *
     * @param image the image
     * @return true if its seed can be copied
     */
    static boolean offersSeed(GeneratedImage image) {
        String modelId = image.getModel();
        if (image.getSeed() == 0 || modelId == null)
            return false;
        GenerationModel model = GenerationModel.fromApiModelId(modelId);
        return model != null ? model.getProvider() == ApiProvider.BFL : !GenerationModel.isOpenAiImageModelId(modelId);
    }
}
