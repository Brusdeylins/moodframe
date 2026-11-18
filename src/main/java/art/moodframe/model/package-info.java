/**
 * The domain model: data classes, records and enums (some with JavaFX properties for binding), without persistence or
 * service logic, shared by all layers.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link Chat}: a chat with its entries</li>
 * <li>{@link ChatEntry}: one prompt of a chat with its generated images</li>
 * <li>{@link GeneratedImage}: one generated image or video with its metadata</li>
 * <li>{@link GenerationModel}: all generation models of FLUX, Gemini, Gemini Omni and OpenAI</li>
 * <li>{@link ApiProvider}: the providers BFL, Gemini and OpenAI</li>
 * <li>{@link Mood}: a named combination of building blocks</li>
 * <li>{@link Frame}: a named target aspect ratio and resolution</li>
 * <li>{@link PromptBlock}: one building block of the prompt</li>
 * <li>{@link ImageGenerationRequest}: the parameters of a generation request</li>
 * <li>{@link BrainstormSession}: a saved brainstorm session, with {@link BrainstormMessage} and
 *     {@link BrainstormDraft}</li>
 * </ul>
 */
package art.moodframe.model;
