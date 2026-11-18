/**
 * The application logic between the UI (and the MCP server) and the repositories: generation with the providers, the
 * library of chats, moods, frames and building blocks, the central trash and the files of the images. The provider
 * clients are interfaces with one implementation each, so tests can replace them.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link GenerationService}: carries out a {@link GenerationOrder} without any user interface</li>
 * <li>{@link BflApiService}: the BFL client, implemented by {@link BflApiServiceImpl} and driven by
 *     {@link BflGenerationFlow}</li>
 * <li>{@link GeminiApiService}: the Gemini client, implemented by {@link GeminiApiServiceImpl}</li>
 * <li>{@link OpenAiApiService}: the OpenAI client, implemented by {@link OpenAiApiServiceImpl}</li>
 * <li>{@link RequestResponseService}: the lifecycle of a BFL request, implemented by
 *     {@link RequestResponseManager}</li>
 * <li>{@link ChatLibrary}: the chats with their entries, shared by the main window and the MCP tools</li>
 * <li>{@link MoodService}: standard moods from code and the moods of the user; likewise {@link FrameService},
 *     {@link PromptBlockService} and {@link ColorStyleService}</li>
 * <li>{@link PromptComposer}: composes the final prompt from the subject and the building blocks</li>
 * <li>{@link TrashCenter}: the central trash with one {@link TrashGroup} per kind of item</li>
 * <li>{@link ThumbnailManager}: thumbnails of the generated images</li>
 * <li>{@link BrainstormService}: the guided brainstorm chat</li>
 * <li>{@link LibraryEvents}: reports changes of the library to the main window</li>
 * </ul>
 */
package art.moodframe.service;
