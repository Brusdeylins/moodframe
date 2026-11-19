/**
 * The guided brainstorm chat with the language model of a provider, and the analysis of an image into a mood.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link BrainstormService}: runs the brainstorm chat and stores its messages and drafts; a
 *     {@link BrainstormService.Factory} creates one for each brainstorm window</li>
 * <li>{@link BrainstormReply}: an answer of the assistant, parsed from the JSON the language model writes</li>
 * <li>{@link BrainstormPromptBuilder}: the system prompt of the assistant from a {@link BrainstormContext}</li>
 * <li>{@link MoodAnalysisService}: derives the building blocks of a mood from an image</li>
 * <li>{@link PaletteExtractor}: the main colors of an image by median cut, for the analysis</li>
 * <li>{@link YouTubeClip}: a YouTube video linked in a message, with its time range</li>
 * <li>{@link YouTubeInfoService}: title, length and preview images of a YouTube video the assistant suggests</li>
 * </ul>
 */
package art.moodframe.service.brainstorm;
