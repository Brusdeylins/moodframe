/**
 * The image and video providers: the service provider interface, the registry that finds a provider by its id or by a
 * model id, and the clients of BFL, Gemini and OpenAI with the checks of their accounts. The clients are interfaces
 * with one implementation each, so tests can replace them.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link ImageProvider}: one provider, with the threading contract of its futures</li>
 * <li>{@link ProviderRegistry}: the providers by {@link art.moodframe.model.ApiProvider}, built once at the start</li>
 * <li>{@link BflApiService}: the BFL client, implemented by {@link BflApiServiceImpl}</li>
 * <li>{@link GeminiApiService}: the Gemini client, implemented by {@link GeminiApiServiceImpl}, a facade over the
 * package-private image, Omni and chat clients with their shared transport and error mapping</li>
 * <li>{@link OpenAiApiService}: the OpenAI client, implemented by {@link OpenAiApiServiceImpl}</li>
 * <li>{@link ApiProviderValidator}: checks the keys of all providers at the start</li>
 * <li>{@link ProviderPolicy}: which providers may be contacted</li>
 * <li>{@link ProviderException}: a failure of a provider with a localized message, also to be had in English</li>
 * <li>{@link ApiHttpException}: an error answer of a provider with its HTTP status; {@link BflApiException} with
 * the details of BFL</li>
 * </ul>
 */
package art.moodframe.service.provider;
