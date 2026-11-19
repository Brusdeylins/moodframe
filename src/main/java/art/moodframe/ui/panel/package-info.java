/**
 * The groups Mood and Frame of the right panel ({@code fxml/panel/MoodFramePanel.fxml}), self-contained, so other
 * windows can show the same fields; it lies below {@code ui.brainstorm} and the controllers, so the result of the
 * brainstorm shows them as well, with the checkboxes of its adoption.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link MoodFramePanelController}: the groups Mood and Frame, with their services as
 * {@link MoodFramePanelController.Services}</li>
 * <li>{@link BlockChoices}: the building block fields with their favorites, locks and dialogs</li>
 * <li>{@link FrameGroupPart}: frame, aspect ratio, resolution and the hints of the model</li>
 * <li>{@link AdoptionColumn}: the checkboxes right of the fields for a window that adopts them into another one,
 * as the brainstorm into the main window</li>
 * <li>{@link PanelGroups}: folds the groups and draws their lines, in the main window and the brainstorm</li>
 * <li>{@link PanelWorker}: runs the writes of the library on the worker of the window and shows their outcome on the
 * JavaFX thread</li>
 * <li>{@link ManagedLibraries}: the moods and frames of the user for their "Manage…" windows, changed on that worker</li>
 * </ul>
 */
package art.moodframe.ui.panel;
