/**
 * The service provider interface of the image and video providers: what generation, account checks and the MCP server
 * need of every provider, and the registry that finds a provider by its id or by a model id. The client interfaces in
 * {@code art.moodframe.service} (BFL, Gemini, OpenAI) extend it with their own extras.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link ImageProvider}: one provider, with the threading contract of its futures</li>
 * <li>{@link ProviderRegistry}: the providers by {@link art.moodframe.model.ApiProvider}, built once at the start</li>
 * </ul>
 */
package art.moodframe.service.provider;
