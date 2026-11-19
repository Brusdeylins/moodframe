/**
 * The client of BFL (FLUX), which submits every image as a job and polls it. The client is an interface with one
 * implementation, so tests can replace it.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link BflApiService}: the BFL client, implemented by {@link BflApiServiceImpl}</li>
 * <li>{@link BflApiException}: an error answer of BFL with its details, e.g. the safety tolerance the account is
 * approved for</li>
 * <li>{@link BflRequestBody}: the wire format of a generation request of FLUX 3 and FLUX.2</li>
 * </ul>
 */
package art.moodframe.service.provider.bfl;
