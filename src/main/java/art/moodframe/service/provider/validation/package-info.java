/**
 * The check of the accounts of all providers: the connection of every registered provider and, for Gemini and OpenAI,
 * the language model of the brainstorm and its web search. It uses the clients of the providers and so lies above
 * them.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link ApiProviderValidator}: checks the keys of all providers at the start and stores the outcomes in the
 * configuration</li>
 * </ul>
 */
package art.moodframe.service.provider.validation;
