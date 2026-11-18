package art.moodframe.ui.component;

import art.moodframe.model.PromptBlock;
import art.moodframe.model.StylePresetItem;
import art.moodframe.ui.support.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The actions and the lock of a building block field and the actions of a group of the right panel, in a shown window
 * with the styles of the main window: shown while the pointer is over the field or the group and once focused by the
 * keyboard (Tab), but not after a click, also not once the dialog the click opened is closed again. A set lock stays
 * shown.
 */
class BlockActionFocusTest {

    private static String userAgentStylesheet;
    private static Stage stage;

    @BeforeAll
    static void startTheToolkit() throws Exception {
        CompletableFuture<Void> started = new CompletableFuture<>();
        try {
            Platform.startup(() -> started.complete(null));
        }
        catch (IllegalStateException alreadyStarted) {
            started.complete(null);
        }
        started.get(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
        userAgentStylesheet = Application.getUserAgentStylesheet();
    }

    @AfterAll
    static void restoreTheEnvironment() throws Exception {
        onFxThread(() -> {
            if (stage != null)
                stage.hide();
            Application.setUserAgentStylesheet(userAgentStylesheet);
            return null;
        });
    }

    /** Runs a step on the JavaFX thread and waits for it, then for a pulse with the styles of what it changed. */
    private static <T> T onFxThread(Callable<T> step) throws Exception {
        CompletableFuture<T> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                done.complete(step.call());
            }
            catch (Throwable failure) {
                done.completeExceptionally(failure);
            }
        });
        T result = done.get(10, TimeUnit.SECONDS);
        CompletableFuture<Void> styled = new CompletableFuture<>();
        Platform.runLater(() -> Platform.runLater(() -> {
            if (stage != null)
                stage.getScene().getRoot().applyCss();
            styled.complete(null);
        }));
        styled.get(10, TimeUnit.SECONDS);
        return result;
    }

    /*  a click of the primary mouse button on the middle of a button, as the pointer gives it  */
    private static void click(ButtonBase button) {
        Bounds bounds = button.getLayoutBounds();
        for (var type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
            boolean down = type == MouseEvent.MOUSE_PRESSED;
            Event.fireEvent(button, new MouseEvent(type, bounds.getCenterX(), bounds.getCenterY(), 0, 0,
                    MouseButton.PRIMARY, 1, false, false, false, false, down, false, false, true, false, true, null));
        }
    }

    /*  the Tab key on the node with the focus, forwards or with Shift backwards  */
    private static void tab(boolean backwards) {
        Event.fireEvent(stage.getScene().getFocusOwner(),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, backwards, false, false, false));
    }

    private static double opacity(Node node) throws Exception {
        return onFxThread(node::getOpacity);
    }

    @Test
    void theActionsAndTheLockShowOnHoverAndKeyboardFocusButNotAfterAClick() throws Exception {
        AtomicReference<Stage> dialog = new AtomicReference<>();
        AtomicReference<Button> edit = new AtomicReference<>();
        Button groupAction = new Button("Save");
        ToggleButton lock = new ToggleButton("Lock");
        GridPane field = new GridPane();
        VBox group = new VBox(groupAction);
        onFxThread(() -> {
            Application.setUserAgentStylesheet(ThemeManager.getAtlantaFXTheme("primer-dark").getUserAgentStylesheet());
            groupAction.getStyleClass().addAll("icon-button", "panel-group-action");
            group.getStyleClass().add("panel-group");
            lock.getStyleClass().addAll("icon-button", "block-lock");
            ComboBox<StylePresetItem> comboBox = new ComboBox<>();
            HBox row = new HBox(new Label("Style:"), lock);
            field.add(row, 0, 0);
            field.add(comboBox, 0, 1);
            field.getStyleClass().add("block-field");

            /*  "Edit" opens a dialog, as in the main window  */
            BlockChoice<StylePresetItem> choice = new BlockChoice<>(comboBox, lock,
                    (name) -> new StylePresetItem(name, "", false), (item) -> true, new BlockChoice.Actions<>() {
                        @Override
                        public void edit(StylePresetItem entry) {
                            Stage editor = new Stage();
                            editor.initOwner(stage);
                            editor.initModality(Modality.WINDOW_MODAL);
                            editor.setScene(new Scene(new Label("Edit"), 200, 100));
                            editor.show();
                            dialog.set(editor);
                        }

                        @Override
                        public void create() {
                        }

                        @Override
                        public void manage() {
                        }
                    });
            choice.setEntries(List.of(new StylePresetItem(new PromptBlock("id-photo", PromptBlock.Type.STYLE, "Photo",
                    "photo", PromptBlock.GROUP_PHOTO, true))), null, "Photo");
            edit.set((Button) row.getChildren().get(1));

            stage = new Stage();
            stage.setScene(new Scene(new VBox(group, field), 400, 300));
            stage.getScene().getStylesheets().addAll(
                    BlockActionFocusTest.class.getResource("/css/icon-button.css").toExternalForm(),
                    BlockActionFocusTest.class.getResource("/css/main-window.css").toExternalForm());
            stage.show();
            return null;
        });
        Button pencil = edit.get();
        assertTrue(stage.isFocused(), "the window has the focus");
        assertEquals(0, opacity(pencil), "hidden at rest");
        assertEquals(0, opacity(lock), "hidden at rest");
        assertEquals(0, opacity(groupAction), "hidden at rest");

        /*  A click on the pencil opens the dialog; once it is closed, the pencil has the focus but stays hidden  */
        onFxThread(() -> {
            click(pencil);
            return null;
        });
        assertTrue(dialog.get() != null && dialog.get().isShowing(), "the click opened the dialog");
        onFxThread(() -> {
            dialog.get().hide();
            return null;
        });
        assertTrue(stage.isFocused(), "the window has the focus again");
        assertTrue(pencil.isFocused(), "the pencil keeps the focus of the click");
        assertFalse(pencil.isFocusVisible(), "no focus of the keyboard");
        assertEquals(0, opacity(pencil), "hidden after the click");

        /*  A set lock stays shown; set free again by a click, it hides like the pencil  */
        onFxThread(() -> {
            click(lock);
            return null;
        });
        assertTrue(lock.isSelected() && lock.isFocused());
        assertEquals(1, opacity(lock), "a set lock stays shown");
        onFxThread(() -> {
            click(lock);
            return null;
        });
        assertFalse(lock.isSelected());
        assertTrue(lock.isFocused());
        assertEquals(0, opacity(lock), "hidden after the click");

        /*  The same for an action of a group  */
        onFxThread(() -> {
            click(groupAction);
            return null;
        });
        assertTrue(groupAction.isFocused());
        assertEquals(0, opacity(groupAction), "hidden after the click");

        /*  Tab from the action of the group on to the pencil shows the pencil, then on to the lock the lock  */
        onFxThread(() -> {
            tab(false);
            return null;
        });
        assertTrue(pencil.isFocusVisible(), "Tab gives the pencil the focus of the keyboard");
        assertEquals(1, opacity(pencil), "shown with the focus of the keyboard");
        onFxThread(() -> {
            tab(false);
            tab(false);
            tab(false);
            return null;
        });
        assertTrue(lock.isFocusVisible(), "Tab passes on New and Manage to the lock");
        assertEquals(0, opacity(pencil), "hidden again without the focus");
        assertEquals(1, opacity(lock), "shown with the focus of the keyboard");
        onFxThread(() -> {
            for (int i = 0; i < 4; i++)
                tab(true);
            return null;
        });
        assertTrue(groupAction.isFocusVisible(), "Shift+Tab goes back to the action of the group");
        assertEquals(1, opacity(groupAction), "shown with the focus of the keyboard");

        /*  The pointer over the field and the group shows all of them, as before  */
        onFxThread(() -> {
            groupAction.requestFocus();
            field.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
            group.pseudoClassStateChanged(PseudoClass.getPseudoClass("hover"), true);
            return null;
        });
        assertEquals(1, opacity(pencil), "shown while the pointer is over the field");
        assertEquals(1, opacity(lock), "shown while the pointer is over the field");
        assertEquals(1, opacity(groupAction), "shown while the pointer is over the group");
    }
}
