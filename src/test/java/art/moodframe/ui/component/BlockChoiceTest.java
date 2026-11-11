package art.moodframe.ui.component;

import art.moodframe.model.PromptBlock;
import art.moodframe.model.StylePresetItem;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives a building block list on the JavaFX thread without showing it. */
class BlockChoiceTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        started.await(10, TimeUnit.SECONDS);
    }

    /** Runs a step on the JavaFX thread and waits for it and for what it left for later. */
    private static void onFxThread(Runnable step) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(step);

        /*  posted from the queue, so it runs after what the step itself left for later  */
        Platform.runLater(() -> Platform.runLater(done::countDown));
        assertTrue(done.await(10, TimeUnit.SECONDS));
    }

    private static StylePresetItem entry(String name, String group) {
        return new StylePresetItem(new PromptBlock("id-" + name, PromptBlock.Type.STYLE, name, name + " fragment", group,
                PromptBlock.GROUP_PHOTO.equals(group)));
    }

    @Test
    void anActionKeepsTheSelectedEntryAndRuns() throws Exception {
        ComboBox<StylePresetItem> comboBox = new ComboBox<>();
        /*  the actions run on the FX thread, the test reads them on its own  */
        List<String> calls = new java.util.concurrent.CopyOnWriteArrayList<>();
        BlockChoice<StylePresetItem> choice = new BlockChoice<>(comboBox, (name) -> new StylePresetItem(name, "", false),
                (item) -> item.getBlock() != null, new BlockChoice.Actions<>() {
                    @Override
                    public void edit(StylePresetItem entry) {
                        calls.add("edit " + entry.getName());
                    }

                    @Override
                    public void create() {
                        calls.add("create");
                    }

                    @Override
                    public void manage() {
                        calls.add("manage");
                    }
                });
        StylePresetItem none = new StylePresetItem("None", "", false);
        StylePresetItem photo = entry("Photo Style", PromptBlock.GROUP_PHOTO);
        StylePresetItem art = entry("Art Style", PromptBlock.GROUP_ART);

        onFxThread(() -> choice.setEntries(List.of(none, photo, art),
                (item) -> item.getBlock() == null ? null : "block.group." + item.getBlock().getGroup().toLowerCase(),
                "Photo Style"));
        assertEquals(photo, comboBox.getValue());

        /*  Edit, New, Manage, the line, None, header, Photo Style, header, Art Style  */
        List<StylePresetItem> items = List.copyOf(comboBox.getItems());
        assertEquals(9, items.size());
        assertEquals(List.of(false, false, false, false, true, false, true, false, true),
                items.stream().map(choice::isEntry).toList());

        onFxThread(() -> comboBox.setValue(items.get(0)));
        onFxThread(() -> comboBox.setValue(items.get(1)));
        onFxThread(() -> comboBox.setValue(items.get(2)));

        assertEquals(List.of("edit Photo Style", "create", "manage"), calls);
        assertEquals(photo, comboBox.getValue());

        /*  The arrow keys pass a header on to the next entry, but stop at the line  */
        onFxThread(() -> comboBox.setValue(items.get(7)));
        assertEquals(art, comboBox.getValue());
        onFxThread(() -> comboBox.setValue(items.get(7)));
        assertEquals(photo, comboBox.getValue());
        onFxThread(() -> comboBox.setValue(items.get(5)));
        assertEquals(none, comboBox.getValue());
        onFxThread(() -> comboBox.setValue(items.get(3)));
        assertEquals(none, comboBox.getValue());
        assertEquals(3, calls.size());

        /*  "Edit…" cannot edit "None": its cell is disabled and choosing it does nothing  */
        ListCell<StylePresetItem> cell = new ListCell<>();
        List<Boolean> drawn = new ArrayList<>();
        onFxThread(() -> comboBox.setValue(none));
        onFxThread(() -> {
            drawn.add(choice.render(cell, items.get(0)));
            drawn.add(cell.isDisable());
            drawn.add(choice.render(cell, art));
            drawn.add(cell.isDisable());
            comboBox.setValue(items.get(0));
        });
        assertEquals(List.of(true, true, false, false), drawn);
        assertEquals(none, comboBox.getValue());
        assertEquals(3, calls.size());

        /*  A name that is gone selects the first entry  */
        onFxThread(() -> choice.setEntries(List.of(none, art), null, "Photo Style"));
        assertEquals(none, comboBox.getValue());
        assertEquals(6, comboBox.getItems().size());
        onFxThread(() -> choice.setEntries(List.of(), null, null));
        assertNull(comboBox.getValue());
    }
}
