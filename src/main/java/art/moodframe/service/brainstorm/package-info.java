/**
 * The guided brainstorm chat with the language model of a provider, and the analysis of an image into a mood.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link BrainstormService}: runs the brainstorm chat of a window, one request of a session after the other,
 *     and stores its messages and drafts; a {@link BrainstormService.Factory} creates one for each brainstorm
 *     window</li>
 * <li>{@link BrainstormSessions}: the stored sessions with their messages and drafts</li>
 * <li>{@link BrainstormConversation}: one answer of the assistant, with the tool loop of the language model</li>
 * <li>{@link BrainstormModels}: the language model of a session, Gemini or OpenAI ({@link ChatModel})</li>
 * <li>{@link BrainstormTools}: the tools of the assistant (research, watching a YouTube video, the library)</li>
 * <li>{@link BrainstormLibrary}: the tools on the moods, frames and saved images, which the system prompt does not
 *     list</li>
 * <li>{@link BrainstormFactCheck}: the facts of a draft against the research of the session</li>
 * <li>{@link BrainstormReply}: an answer of the assistant, parsed from the JSON the language model writes</li>
 * <li>{@link BrainstormJson}: the JSON of a draft (written by the language model, stored by the session) and of a
 *     research event</li>
 * <li>{@link BrainstormPromptBuilder}: the system prompt of the assistant from a {@link BrainstormContext}</li>
 * <li>{@link MoodAnalysisService}: derives the building blocks of a mood from an image</li>
 * <li>{@link PaletteExtractor}: the main colors of an image by median cut, for the analysis</li>
 * <li>{@link YouTubeClip}: a YouTube video linked in a message, with its time range</li>
 * <li>{@link YouTubeInfoService}: title, length and preview images of a YouTube video the assistant suggests</li>
 * </ul>
 */
package art.moodframe.service.brainstorm;
