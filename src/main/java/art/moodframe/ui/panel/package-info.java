/**
 * The groups Mood and Frame of the right panel ({@code fxml/panel/MoodFramePanel.fxml}), self-contained, so other
 * windows can show the same fields; it lies below {@code ui.brainstorm} and the controllers, which allows the
 * brainstorm dialog to reuse it (planned with ticket 136, not done yet).
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link MoodFramePanelController}: the groups Mood and Frame, with their services as
 * {@link MoodFramePanelController.Services}</li>
 * <li>{@link BlockChoices}: the building block fields with their favorites, locks and dialogs</li>
 * <li>{@link FrameGroupPart}: frame, aspect ratio, resolution and the hints of the model</li>
 * <li>{@link PanelWorker}: runs the writes of the library on the worker of the window and shows their outcome on the
 * JavaFX thread</li>
 * </ul>
 */
package art.moodframe.ui.panel;
