/**
 * The domain model: data classes, records and enums, without persistence or service logic, shared by all layers;
 * {@link ChatEntry} and {@link GeneratedImage} carry the few JavaFX properties the views observe.
 *
 * <p>Decision: a value type that is stored as JSON carries that JSON form itself, as the same form is read by
 * several layers: {@link EntryMood} and {@link EntryFrame} (the {@code mood_json} and {@code frame_json} of a chat
 * entry, which the repositories store and the MCP server shows) and {@link CameraSettings} (the {@code params} of a
 * camera block, which the repository stores, the composer, the views and the MCP server read). {@link ColorStyle}
 * carries the Jackson names of the palette file (export, import, backup). No class of the model reads or writes
 * what a language model exchanges: the draft and the research events of the brainstorm are
 * {@code service.brainstorm.BrainstormJson}. {@code ArchitectureTest} names the classes that may use JSON.</p>
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
