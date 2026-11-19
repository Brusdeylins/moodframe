package art.moodframe.service.generation;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ApiProvider;
import art.moodframe.model.AspectRatios;
import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.GenerationCost;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageDimensions;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ModelFamilies;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.chat.ModelLock;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.prompt.PromptComposer;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.service.provider.RequestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Carries out a generation order without any user interface: composes and checks the prompt, checks the key of the
 * provider, maps the target to the format and resolution of the model, estimates the cost, stores the chat entry
 * and submits the request. The main window and later callers share it; models are named by their fixed id (see
 * {@link GenerationModel#idOf(String)}).
 *
 * <p>{@link #submit(GenerationOrder)} adds the entry to its chat through the {@link ChatLibrary}, so it shows in the
 * main window, whoever placed the order. It may be called on the JavaFX thread: it checks the order in memory and
 * leaves the writes to the store executor; only the copy of the mood looks up a palette without id by its name, a
 * short read like those that feed what the window shows at once.</p>
 *
 * @since 2.1.0
 */
public class GenerationService {

    private static final Logger logger = LoggerFactory.getLogger(GenerationService.class);

    /*  the states an entry ends in  */
    private static final Set<ChatEntry.Status> FINAL = Set.of(ChatEntry.Status.COMPLETED, ChatEntry.Status.FAILED,
            ChatEntry.Status.CANCELLED);

    private static final PromptComposer.Selection NO_BLOCKS = new PromptComposer.Selection(null, null, null, null,
            null);

    /** Why an order is refused before anything is stored or sent. */
    public enum Failure {
        /** The prompt of the user is empty. */
        EMPTY_PROMPT,
        /** The prompt with the building blocks exceeds the limit of the model. */
        PROMPT_TOO_LONG,
        /** The order names no chat. */
        NO_CHAT,
        /** The id names neither a model nor one of the OpenAI-compatible gateway. */
        UNKNOWN_MODEL,
        /** The provider of the model has no API key. */
        MISSING_KEY
    }

    /** An order refused before anything is stored or sent. */
    public static class GenerationException extends Exception {

        private final Failure failure;
        private final int promptLength;
        private final int maxPromptLength;

        GenerationException(Failure failure, String message) {
            this(failure, message, 0, 0);
        }

        GenerationException(Failure failure, String message, int promptLength, int maxPromptLength) {
            super(message);
            this.failure = failure;
            this.promptLength = promptLength;
            this.maxPromptLength = maxPromptLength;
        }

        public Failure getFailure() {
            return failure;
        }

        /** The length of the prompt with the building blocks, set for {@link Failure#PROMPT_TOO_LONG}. */
        public int getPromptLength() {
            return promptLength;
        }

        /** The prompt limit of the model, set for {@link Failure#PROMPT_TOO_LONG}. */
        public int getMaxPromptLength() {
            return maxPromptLength;
        }
    }

    /**
     * An accepted order.
     *
     * @param entry the chat entry, to be shown at once; it is in its chat and stored once {@code stored} completes
     * @param stored completes once the entry is in its chat and stored (kept in memory only if the database fails),
     *               exceptionally if that could not run (the entry is marked failed then)
     * @param submitted completes once the requests are handed to the flows of the provider, after {@code stored},
     *                  exceptionally if that failed (the entry is marked failed then); what the provider answers, a
     *                  refusal included, arrives later and ends in {@code finished}
     * @param finished completes with the state the entry ends in: COMPLETED, FAILED or CANCELLED
     */
    public record Submission(ChatEntry entry, CompletableFuture<Void> stored, CompletableFuture<Void> submitted,
            CompletableFuture<ChatEntry.Status> finished) {
    }

    /**
     * What an order would make, without storing or sending anything, as the main window shows it.
     *
     * @param prompt the final prompt with the building blocks
     * @param maxPromptLength the prompt limit of the model in characters
     * @param aspectRatio the aspect ratio the model makes of the target, null without target (then a square)
     * @param resolution the resolution the model makes, null for a model with a fixed size
     * @param costPerImage the estimated cost of one image (video: of the video) in USD with Google Search, 0 if
     *                     unknown, see {@link GenerationCost#quote}
     * @param cost the estimated cost of all images in USD, 0 if unknown
     */
    public record Preview(String prompt, int maxPromptLength, String aspectRatio, String resolution,
            double costPerImage, double cost) {

        /**
         * Tells whether the model takes the prompt.
         *
         * @return true if the prompt is within the limit
         */
        public boolean fits() {
            return prompt.length() <= maxPromptLength;
        }
    }

    private final ApplicationConfig config;
    private final ChatLibrary chats;
    private final MoodService moodService;
    private final RequestResponseService requests;
    private final ProviderRegistry providers;
    private final Executor store;

    /**
     * Creates the service.
     *
     * @param config      the application configuration (keys, FLUX.2 safety tolerance)
     * @param chats       adds the entries to their chats and stores them
     * @param moodService copies the mood into the entry, null for none
     * @param requests    sends the requests and reports the progress of the entries
     * @param providers   the providers, which know the prompt limits and the parameters of their models
     * @param store       stores the new entries, off the thread that places the order: the store thread of the
     *                    generation ({@link GenerationWorkers#store()}), so the writes of an entry keep their order
     */
    public GenerationService(ApplicationConfig config, ChatLibrary chats, MoodService moodService,
            RequestResponseService requests, ProviderRegistry providers, Executor store) {
        this.config = config;
        this.chats = chats;
        this.moodService = moodService;
        this.requests = requests;
        this.providers = providers;
        this.store = store;
    }

    /**
     * Gets the prompt limit of a model in characters, as its provider tells it, see
     * {@link ProviderRegistry#promptLimit(String)}.
     *
     * @param modelId the fixed id of the model (may be null)
     * @return the limit
     */
    public int promptLimit(String modelId) {
        return providers.promptLimit(modelId);
    }

    /**
     * Gets the providers this service orders from.
     *
     * @return the registry
     */
    public ProviderRegistry providers() {
        return providers;
    }

    /**
     * The dropdown items of the models to choose from that take this many attached images, with the providers of this
     * service, see {@link #availableItems(ApplicationConfig, ProviderRegistry, int)}.
     *
     * @param config     the configuration with the accounts
     * @param imageCount the number of attached images, 0 for all models to choose from
     * @return the items in the order of the dropdown
     */
    public List<String> availableItems(ApplicationConfig config, int imageCount) {
        return availableItems(config, providers, imageCount);
    }

    /**
     * The dropdown items of the models to choose from that take this many attached images: the models of the
     * registered providers the user enabled with a key, those a provider lists beyond the constants among them (see
     * {@link ModelLabels#availableItems}), in the order of the dropdown. The window and the MCP tools take them from
     * here, the listed models from the same source as {@link ProviderRegistry#isOrderable}.
     *
     * @param config     the configuration with the accounts
     * @param providers  the registered providers
     * @param imageCount the number of attached images, 0 for all models to choose from
     * @return the items whose model takes at least that many images
     */
    public static List<String> availableItems(ApplicationConfig config, ProviderRegistry providers,
            int imageCount) {
        List<String> items = ModelLabels.availableItems(config.accounts()::isUsable, providers);
        if (imageCount <= 0)
            return items;
        return items.stream().filter((String item) -> {
            GenerationModel model = GenerationModel.fromLocalizedDisplayName(item);
            return model != null && model.getMaxInputImages() >= imageCount;
        }).toList();
    }

    /**
     * Picks the model of a family for a target resolution among the models to choose from, as the main window does.
     *
     * @param family     the family name, for example {@code Nano Banana 2}
     * @param resolution the target resolution, for example {@code 2K} (may be null)
     * @return the fixed id of the model, null if no model of the family can be chosen
     */
    public String modelFor(String family, String resolution) {
        return GenerationModel.idOf(ModelFamilies.variantForTarget(availableItems(config, 0), family, resolution));
    }

    /**
     * Tells what an order would make: the final prompt, the format the model makes of the target and the estimated
     * cost, as {@link #submit(GenerationOrder)} takes them; nothing is stored or sent.
     *
     * @param order the order with a known model
     * @return the preview
     */
    public Preview preview(GenerationOrder order) {
        String item = ModelLabels.itemOf(order.modelId());
        int maxLength = promptLimit(order.modelId());
        String prompt = new PromptComposer(maxLength).compose(order.prompt(),
                order.blocks() != null ? order.blocks() : NO_BLOCKS, order.hasReference());

        /*  The format as the hints of the main window name it: the closest aspect ratio and resolution of the model  */
        GenerationOrder.Target target = order.target();
        String aspectRatio = target.aspectRatio() != null
                ? AspectRatios.nearestForItem(target.aspectRatio(), item) : null;
        List<String> free = ModelFamilies.freeResolutions(item);
        String resolution = free != null ? ModelFamilies.nearest(target.resolution(), free)
                : ModelFamilies.resolutionOf(item);
        GenerationCost.Quote quote = quote(GenerationModel.fromId(order.modelId()), order);
        return new Preview(prompt, maxLength, aspectRatio, resolution, quote.perImage(), quote.total());
    }

    /*  the estimated price of an order, as the preview and the first cost of its entry show it  */
    private static GenerationCost.Quote quote(GenerationModel model, GenerationOrder order) {
        return GenerationCost.quote(model, order.parameters().videoSeconds(), order.parameters().googleSearch(),
                order.imageCount());
    }

    /**
     * Cancels a running entry, see {@link RequestResponseService#cancelEntry(ChatEntry)}.
     *
     * @param entry the entry
     * @return completes with true once the cancelled entry is stored, with false if it was not pending or processing
     */
    public CompletableFuture<Boolean> cancel(ChatEntry entry) {
        return requests.cancelEntry(entry);
    }

    /**
     * Settles the generation of an entry before it goes to the trash, as {@link ChatLibrary.Generations} asks: a
     * running entry is cancelled ({@link #cancel}), and the future completes once the writes of the generation asked
     * for so far, those of the cancellation included, are done on the store thread, so the trash books the cost they
     * settled. Only a running entry waits for the JavaFX thread.
     *
     * @param entry the entry
     * @return completes once its writes are done, exceptionally if the store thread is closed
     */
    public CompletableFuture<Void> settle(ChatEntry entry) {
        boolean running = ModelLock.read(() -> entry.getStatus() == ChatEntry.Status.PENDING
                || entry.getStatus() == ChatEntry.Status.PROCESSING);
        CompletableFuture<Boolean> cancelled = running ? cancel(entry) : CompletableFuture.completedFuture(false);
        return cancelled.handle((Boolean done, Throwable failure) -> null)
                .thenRunAsync(() -> logger.debug("The writes of entry {} are done", entry.getId()), store);
    }

    /**
     * Carries out an order: stores the chat entry and submits the request.
     *
     * <p><strong>Threading:</strong> the checks that refuse an order (empty prompt, prompt too long, no chat, unknown
     * model, missing key) and the building of the entry and the request run on the calling thread, in memory but for
     * one short read: the copy of the mood looks up a palette without id by its name ({@link MoodService#snapshot}).
     * The entry goes into its chat and the database on the store executor, and only then is the request dispatched,
     * so its images find the row of their entry. The caller, e.g. the JavaFX thread, never waits for a write: it
     * shows the entry at once; a caller that needs the entry in the library waits for {@link Submission#stored()}.</p>
     *
     * @param order the order
     * @return the entry with the progress of its storing and its request
     * @throws GenerationException if the order is refused; nothing is stored or sent then
     */
    public Submission submit(GenerationOrder order) throws GenerationException {
        String userPrompt = order.prompt() != null ? order.prompt().trim() : "";
        if (userPrompt.isEmpty())
            throw new GenerationException(Failure.EMPTY_PROMPT, "The prompt is empty");

        /*  The final prompt includes the building blocks  */
        int maxLength = promptLimit(order.modelId());
        PromptComposer composer = new PromptComposer(maxLength);
        PromptComposer.Selection blocks = order.blocks() != null ? order.blocks() : NO_BLOCKS;
        String prompt = composer.compose(userPrompt, blocks, order.hasReference());
        if (!composer.fits(prompt))
            throw new GenerationException(Failure.PROMPT_TOO_LONG, "The prompt has " + prompt.length()
                    + " characters, the model takes " + maxLength, prompt.length(), maxLength);

        Chat chat = order.chat();
        if (chat == null) {
            logger.warn("No chat selected for sending message");
            throw new GenerationException(Failure.NO_CHAT, "No chat selected for sending message");
        }

        /*  A constant of the enum, else a model its provider lists  */
        String modelId = order.modelId();
        GenerationModel model = GenerationModel.fromId(modelId);
        if (model == null || !providers.isOrderable(modelId))
            throw new GenerationException(Failure.UNKNOWN_MODEL, "Unknown model: " + modelId);
        String item = ModelLabels.itemOf(modelId);

        ApiProvider provider = model.getProvider();
        if (!config.accounts().hasApiKey(provider)) {
            logger.warn("Send request blocked - no {} API key configured", provider.getDisplayName());
            throw new GenerationException(Failure.MISSING_KEY, "No API key for " + provider.getDisplayName());
        }

        /*  The aspect ratio the model makes of the target and the price of all images  */
        GenerationOrder.Target target = order.target();
        GenerationOrder.Parameters parameters = order.parameters();
        String aspectRatio = target.aspectRatio() != null
                ? AspectRatios.nearestForItem(target.aspectRatio(), item) : null;
        double totalCost = quote(model, order).total();

        /*  The entry keeps the prompt of the user (for the copy button) and the fixed id of the model, which depends on
            no language or price; the request gets the prompt with the building blocks  */
        ChatEntry entry = new ChatEntry.Builder()
                .chatId(chat.getId())
                .userPrompt(userPrompt)
                .enhancedPrompt(parameters.promptUpsampling()
                        ? "Enhanced: " + prompt + ", professional quality, highly detailed"
                        : prompt)
                .status(ChatEntry.Status.PENDING)
                .model(GenerationModel.entryModelOf(modelId))
                .size(Objects.requireNonNullElse(aspectRatio, "16:9"))
                .style(blocks.style() != null ? blocks.style().getName() : "None")
                .mood(moodService != null ? moodService.snapshot(order.mood(), order.moodChanged(), blocks) : null)
                .frame(EntryFrame.of(target.frame(), target.changed(), target.aspectRatio(), target.resolution()))
                .source(order.source())
                .safetyTolerance(parameters.safetyTolerance())
                .seed(parameters.seed() != null ? parameters.seed() : 0)
                .cost(totalCost)
                .build();

        ImageGenerationRequest request = createRequest(prompt, model, item, aspectRatio, order);

        /*  The entry is finished once the provider reports it in a final state  */
        CompletableFuture<ChatEntry.Status> finished = new CompletableFuture<>();
        Consumer<ChatEntry> watch = (ChatEntry updated) -> {
            if (updated.getId().equals(entry.getId()) && FINAL.contains(updated.getStatus()))
                finished.complete(updated.getStatus());
        };
        requests.addEntryListener(watch);
        finished.whenComplete((ChatEntry.Status status, Throwable throwable) -> requests.removeEntryListener(watch));

        /*  Stored with its chat off the calling thread (without database, or if storing fails, kept in memory only),
            then dispatched; a closed store fails the future instead of throwing  */
        CompletableFuture<Void> stored = CompletableFuture.completedFuture((Void) null)
                .thenRunAsync(() -> chats.addEntry(chat, entry), store);
        stored.exceptionally((Throwable failure) -> {
            logger.error("Failed to add the entry {} to its chat", entry.getId(), failure);
            requests.markFailed(entry, failure);
            return null;
        });

        /*  A failed submission is stored as failed with the reason by the requests, whose listeners finish the entry  */
        CompletableFuture<Void> submitted = stored
                .thenCompose((Void done) -> requests.submitImageGeneration(request, entry, order.attachment()))
                .whenComplete((Void done, Throwable throwable) -> {
                    if (throwable == null)
                        logger.debug("Request submitted to {} for prompt: {}", provider.getDisplayName(), prompt);
                    else
                        logger.error("Failed to submit API request", throwable);
                });
        return new Submission(entry, stored, submitted, finished);
    }

    /**
     * Creates the request: the dimensions of the aspect ratio, the general parameters, what the provider of the model
     * takes besides (see {@link art.moodframe.service.provider.ImageProvider#prepare}) and the price per image once the
     * size is known.
     */
    private ImageGenerationRequest createRequest(String prompt, GenerationModel model, String item,
            String aspectRatio, GenerationOrder order) {
        GenerationOrder.Parameters parameters = order.parameters();
        List<String> referenceImages = order.referenceImages() != null ? order.referenceImages() : List.of();
        String apiModelId = ProviderRegistry.apiModelId(order.modelId());
        logger.debug("Creating request with model id: '{}' -> API ID: '{}'", order.modelId(), apiModelId);

        ImageGenerationRequest request = new ImageGenerationRequest(prompt, apiModelId);
        request.setImageCount(order.imageCount());

        /*  Prompt upsampling applies to BFL models only; without a seed of the user the generation is random  */
        request.setPromptUpsampling(parameters.promptUpsampling());
        request.setSeed(parameters.seed());

        /*  The standard safety tolerance; FLUX.2 and FLUX 3 take their own (0-5) in the preparation of BFL  */
        request.setSafetyTolerance(parameters.safetyTolerance());

        /*  The style preset if one is applied  */
        if (order.blocks() != null && order.blocks().style() != null)
            request.setStylePreset(order.blocks().style().getName());

        /*  Width and height of the aspect ratio within the limits of the model, else the default square  */
        if (aspectRatio != null) {
            setDimensionsFromAspectRatio(request, aspectRatio, model);
            request.setAspectRatio(aspectRatio);
        } else {
            int defaultSize = roundToMultipleOf32(Math.min(model.getMaxResolution(),
                    model.getMaxMegapixels() == 4 ? 2048 : 1024));
            request.setWidth(defaultSize);
            request.setHeight(defaultSize);
            request.setAspectRatio("1:1");
        }

        /*  What the model of the provider takes: resolution tier, references, search, thinking, FLUX, video  */
        int fluxSafetyTolerance = parameters.flux2SafetyTolerance() != null ? parameters.flux2SafetyTolerance()
                : config.getFlux2SafetyTolerance();
        providers.get(model.getProvider()).prepare(request, model, new RequestOptions(item,
                order.target().resolution(), fluxSafetyTolerance, parameters.googleSearch(),
                parameters.thinkingLevel(), parameters.guidance(), parameters.steps(), parameters.videoSeconds(),
                referenceImages));
        if (logger.isDebugEnabled())
            logger.debug("Request of {}: imageSize={}, search={}, thinking={}, safety={}, images={}, video={}s",
                    apiModelId, request.getImageSize(), request.getEnableGoogleSearch(), request.getThinkingMode(),
                    request.getSafetyTolerance(), request.getInputImageCount(), request.getVideoDuration());

        /*  The price per image, now that all dimensions are known  */
        double costPerImage = GenerationCost.perImage(model, request.getWidth(), request.getHeight(),
                request.getVideoDuration());
        request.setBaseCostPerImage(costPerImage);
        if (logger.isDebugEnabled())
            logger.debug("Cost calculation: ${} per image for model {} ({}x{})", String.format("%.4f", costPerImage),
                    model.getApiModelId(), request.getWidth(), request.getHeight());

        return request;
    }

    /*  Width and height of an aspect ratio within the resolution limit of the model, multiples of 16 (FLUX.2) or 32  */
    private void setDimensionsFromAspectRatio(ImageGenerationRequest request, String aspectRatio,
            GenerationModel model) {
        int maxResolution = model.getMaxResolution();
        int dimensionMultiple = ImageDimensions.multipleFor(model);
        logger.debug("Setting dimensions for aspect ratio: {} with maxResolution: {} (multiple of {}) for model: {}",
                aspectRatio, maxResolution, dimensionMultiple, model.getApiModelId());

        int[] dimensions = ImageDimensions.forAspectRatio(aspectRatio, maxResolution, dimensionMultiple);
        logger.debug("Final dimensions: {}x{} for aspect ratio: {}", dimensions[0], dimensions[1], aspectRatio);

        request.setWidth(dimensions[0]);
        request.setHeight(dimensions[1]);
    }

    /*  Rounds to the nearest multiple of 32 (FLUX.1 API requirement)  */
    private static int roundToMultipleOf32(int value) {
        return ((value + 16) / 32) * 32;
    }
}
