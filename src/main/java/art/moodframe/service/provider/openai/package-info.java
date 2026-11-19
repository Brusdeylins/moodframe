/**
 * The client of OpenAI or an OpenAI-compatible gateway: images, the models of the account and the language model of
 * the brainstorm. The client is an interface with one implementation, so tests can replace it.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link OpenAiApiService}: the OpenAI client, implemented by {@link OpenAiApiServiceImpl}, a facade over the
 * package-private parts below</li>
 * <li>{@link OpenAiImageClient}: the Images API, its bodies and the sizes of the models</li>
 * <li>{@link OpenAiChatClient}: the brainstorm chat, the research with the built-in web search and the checks of a
 * language model</li>
 * <li>{@link OpenAiAccounts}: the connection test, which lists the models of the API and the image models to offer</li>
 * <li>{@link OpenAiTransport}: the HTTP clients, token and base URL the parts share, with the one mapping of an error
 * answer</li>
 * </ul>
 */
package art.moodframe.service.provider.openai;
