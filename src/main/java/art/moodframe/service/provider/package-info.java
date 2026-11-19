/**
 * The image and video providers: the service provider interface, the registry that finds a provider by its id or by a
 * model id, and the types the providers share. The clients of BFL, Gemini and OpenAI live in the subpackages
 * {@code bfl}, {@code gemini} and {@code openai}, which use this package and none of each other; the check of their
 * accounts lives in {@code validation}, above them.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link ImageProvider}: one provider, with the threading contract of its futures</li>
 * <li>{@link ProviderRegistry}: the providers by {@link art.moodframe.model.ApiProvider}, built once at the start</li>
 * <li>{@link RequestOptions}: what an order asks of a request beyond the general parameters</li>
 * <li>{@link ChatTurn} and {@link ChatTool}: a conversation with a language model and its tools in the format of no
 * provider, which each chat client translates</li>
 * <li>{@link ProviderPolicy}: which providers may be contacted</li>
 * <li>{@link ProviderException}: a failure of a provider with a localized message, also to be had in English</li>
 * <li>{@link ApiHttpException}: an error answer of a provider with its HTTP status</li>
 * <li>{@link GenerationJobInfo}, {@link GenerationJobResult}: the answer of a submission and the state of a polled
 * job, read from the JSON of BFL; the other providers fill them from their answers</li>
 * </ul>
 */
package art.moodframe.service.provider;
