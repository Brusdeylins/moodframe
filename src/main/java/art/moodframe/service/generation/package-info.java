/**
 * Carries out a generation: from the order of the UI or the MCP server to the chat entry, the request at the
 * provider, the polling of its job and the image files.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link GenerationService}: carries out a {@link GenerationOrder} without any user interface</li>
 * <li>{@link RequestResponseService}: the lifecycle of a request, implemented by {@link RequestResponseManager}</li>
 * <li>{@link BflGenerationFlow}: submits a BFL job per image, polls it and downloads the image</li>
 * <li>{@link ChatEntryTracker}: keeps a chat entry in step with its images while a request runs</li>
 * <li>{@link ImageFileStore}: writes the generated images to the images directory</li>
 * </ul>
 */
package art.moodframe.service.generation;
