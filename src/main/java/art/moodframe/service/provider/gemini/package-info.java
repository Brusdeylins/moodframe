/**
 * The client of Google Gemini: images, Omni videos and the language model of the brainstorm. The client is an
 * interface with one implementation, so tests can replace it.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link GeminiApiService}: the Gemini client, implemented by {@link GeminiApiServiceImpl}, a facade over the
 * package-private image, Omni and chat clients with their shared transport and error mapping</li>
 * <li>{@link GeminiImageClient}, {@link GeminiOmniClient}: the images and the videos</li>
 * <li>{@link GeminiChatClient}: the brainstorm chat, the research with Google Search, the checks of the language
 * model and the analysis of YouTube videos</li>
 * <li>{@link GeminiTransport}: the HTTP client, base URL, key header and executor the clients share</li>
 * <li>{@link GeminiErrors}: the mapping of an error answer to a {@link art.moodframe.service.provider.ApiHttpException}
 * per kind of call</li>
 * </ul>
 */
package art.moodframe.service.provider.gemini;
