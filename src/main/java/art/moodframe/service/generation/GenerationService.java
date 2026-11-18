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
import art.moodframe.service.library.MoodService;
import art.moodframe.service.prompt.PromptComposer;
import art.moodframe.service.provider.BflApiService;
import art.moodframe.service.provider.GeminiApiService;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.service.provider.RequestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Carries out a generation order without any user interface: composes and checks the prompt, checks the key of the
 * provider, maps the target to the format and resolution of the model, estimates the cost, stores the chat entry
 * and submits the request. The main window and later callers share it; models are named by their fixed id (see
 * {@link GenerationModel#idOf(String)}).
 *
 * <p>{@link #submit(GenerationOrder)} adds the entry to its chat through the {@link ChatLibrary}, so it shows in the
 * main window, whoever placed the order.</p>
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
     * @param entry the stored chat entry
     * @param submitted completes once the request is handed to the provider, exceptionally if that failed
     * @param finished completes with the state the entry ends in: COMPLETED, FAILED or CANCELLED
     */
    public record Submission(ChatEntry entry, CompletableFuture<Void> submitted,
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

    /**
     * Creates the service.
     *
     * @param config      the application configuration (keys, OpenAI models, FLUX.2 safety tolerance)
     * @param chats       adds the entries to their chats and stores them
     * @param moodService copies the mood into the entry, null for none
     * @param requests    sends the requests and reports the progress of the entries
     * @param providers   the providers, which know the prompt limits and the parameters of their models
     */
    public GenerationService(ApplicationConfig config, ChatLibrary chats, MoodService moodService,
            RequestResponseService requests, ProviderRegistry providers) {
        this.config = config;
        this.chats = chats;
        this.moodService = moodService;
        this.requests = requests;
        this.providers = providers;
    }

    /**
     * Gets the prompt limit of a model in characters, as its provider tells it.
     *
     * @param modelId the fixed id of the model (may be null)
     * @return the limit, that of BFL without model
     */
    public int promptLimit(String modelId) {
        GenerationModel model = GenerationModel.fromId(modelId);
        if (model == null)
            return providers.get(ApiProvider.BFL).maxPromptLength(null);
        return providers.get(model.getProvider()).maxPromptLength(apiModelId(model, modelId));
    }

    /**
     * Gets the prompt limit of a model without the providers at hand, for the main window: the same limits as
     * {@link #promptLimit(String)} gets from the providers.
     *
     * @param modelId the fixed id of the model (may be null)
     * @return the limit, that of BFL without model
     */
    public static int maxPromptLength(String modelId) {
        GenerationModel model = GenerationModel.fromId(modelId);
        if (model == GenerationModel.OPENAI_IMAGE)
            return GenerationModel.openAiMaxPromptLength(modelId);
        return model != null && model.getProvider() == ApiProvider.GOOGLE_GEMINI ? GeminiApiService.MAX_PROMPT_LENGTH
                : BflApiService.MAX_PROMPT_LENGTH;
    }

    /*  the API id of a model: that of its constant, the id itself for a model of an OpenAI-compatible gateway  */
    private static String apiModelId(GenerationModel model, String modelId) {
        return model == GenerationModel.OPENAI_IMAGE ? modelId : model.getApiModelId();
    }

    /**
     * Gets the dropdown items of the models to choose from: those of the enabled providers with a key.
     *
     * @param config the application configuration
     * @return the items, see {@link ModelLabels#availableItems(boolean, boolean, List)}
     */
    public static List<String> availableItems(ApplicationConfig config) {
        return ModelLabels.availableItems(config.accounts().isUsable(ApiProvider.BFL),
                config.accounts().isUsable(ApiProvider.GOOGLE_GEMINI),
                config.accounts().isUsable(ApiProvider.OPENAI) ? config.getOpenAiModels() : List.of());
    }

    /**
     * The items of the models to choose from that take this many attached images, in the order of the dropdown.
     *
     * @param config     the configuration with the accounts
     * @param imageCount the number of attached images, 0 for all models to choose from
     * @return the items of {@link #availableItems(ApplicationConfig)} whose model takes at least that many images
     */
    public static List<String> availableItems(ApplicationConfig config, int imageCount) {
        if (imageCount <= 0)
            return availableItems(config);
        Set<String> taking = GenerationModel.selectable().stream()
                .filter((GenerationModel model) -> model.getMaxInputImages() >= imageCount)
                .map(ModelLabels::displayName).collect(java.util.stream.Collectors.toSet());
        boolean openAi = GenerationModel.OPENAI_IMAGE.getMaxInputImages() >= imageCount;
        return availableItems(config).stream()
                .filter((String item) -> GenerationModel.openAiModelId(item) != null ? openAi : taking.contains(item))
                .toList();
    }

    /**
     * Picks the model of a family for a target resolution among the models to choose from, as the main window does.
     *
     * @param family     the family name, for example {@code Nano Banana 2}
     * @param resolution the target resolution, for example {@code 2K} (may be null)
     * @return the fixed id of the model, null if no model of the family can be chosen
     */
    public String modelFor(String family, String resolution) {
        return GenerationModel.idOf(ModelFamilies.variantForTarget(availableItems(config), family, resolution));
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
     * @return completes with true if it was pending or processing
     */
    public CompletableFuture<Boolean> cancel(ChatEntry entry) {
        return requests.cancelEntry(entry);
    }

    /**
     * Carries out an order: stores the chat entry and submits the request.
     *
     * @param order the order
     * @return the entry with the progress of its request
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

        /*  A constant of the enum, else a model of the gateway  */
        String modelId = order.modelId();
        GenerationModel model = GenerationModel.fromId(modelId);
        if (model == null || (model == GenerationModel.OPENAI_IMAGE && !config.getOpenAiModels().contains(modelId)))
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

        /*  The entry keeps the prompt of the user (for the copy button), the English display name of the model and
            the item of an OpenAI model; the request gets the prompt with the building blocks  */
        ChatEntry entry = new ChatEntry.Builder()
                .chatId(chat.getId())
                .userPrompt(userPrompt)
                .enhancedPrompt(parameters.promptUpsampling()
                        ? "Enhanced: " + prompt + ", professional quality, highly detailed"
                        : prompt)
                .status(ChatEntry.Status.PENDING)
                .model(model == GenerationModel.OPENAI_IMAGE ? item : model.getDisplayName())
                .size(Objects.requireNonNullElse(aspectRatio, "16:9"))
                .style(blocks.style() != null ? blocks.style().getName() : "None")
                .mood(moodService != null ? moodService.snapshot(order.mood(), order.moodChanged(), blocks) : null)
                .frame(EntryFrame.of(target.frame(), target.changed(), target.aspectRatio(), target.resolution()))
                .source(order.source())
                .safetyTolerance(parameters.safetyTolerance())
                .seed(parameters.seed() != null ? parameters.seed() : 0)
                .cost(totalCost)
                .build();

        /*  Stored with its chat; without database, or if storing fails, kept in memory only  */
        chats.addEntry(chat, entry);

        ImageGenerationRequest request = createRequest(prompt, model, item, aspectRatio, order);

        /*  The entry is finished once the provider reports it in a final state  */
        CompletableFuture<ChatEntry.Status> finished = new CompletableFuture<>();
        Consumer<ChatEntry> watch = (ChatEntry updated) -> {
            if (updated.getId().equals(entry.getId()) && FINAL.contains(updated.getStatus()))
                finished.complete(updated.getStatus());
        };
        requests.addEntryListener(watch);
        finished.whenComplete((ChatEntry.Status status, Throwable throwable) -> requests.removeEntryListener(watch));

        CompletableFuture<Void> submitted = requests.submitImageGeneration(request, entry, order.attachment())
                .whenComplete((Void done, Throwable throwable) -> {
                    if (throwable == null) {
                        logger.debug("Real API request submitted to {} for prompt: {}",
                                provider.getDisplayName(), prompt);
                        return;
                    }
                    logger.error("Failed to submit API request", throwable);
                    FxModel.change(() -> {
                        entry.setStatus(ChatEntry.Status.FAILED);
                        entry.setErrorMessage(throwable.getMessage());
                        finished.complete(ChatEntry.Status.FAILED);
                    });
                });
        return new Submission(entry, submitted, finished);
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
        String apiModelId = apiModelId(model, order.modelId());
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
