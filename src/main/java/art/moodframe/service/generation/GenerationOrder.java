package art.moodframe.service.generation;

import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.Frame;
import art.moodframe.model.Mood;
import art.moodframe.service.prompt.PromptComposer;

import java.io.File;
import java.util.List;

/**
 * What to generate, independent of any user interface: the order the {@link GenerationService} carries out.
 *
 * @param chat the chat that takes the entry
 * @param prompt the prompt of the user, without the building blocks
 * @param blocks the selected building blocks, null for none
 * @param mood the chosen mood, null for none
 * @param moodChanged whether the building blocks differ from the chosen mood
 * @param target the frame and the target the model maps to its closest format and resolution
 * @param modelId the fixed id of the model, see {@link art.moodframe.model.GenerationModel#idOf(String)}
 * @param imageCount the number of images
 * @param parameters the parameters of the model
 * @param attachment the attached image file, null for none
 * @param referenceImages the Base64 data of the reference images, in their order
 * @param source who placed the order, kept in the entry: the send button or the MCP server
 * @param savedImages the IDs of the saved images among the reference images, in their order, kept in the entry
 * @since 2.1.0
 */
public record GenerationOrder(Chat chat, String prompt, PromptComposer.Selection blocks, Mood mood,
        boolean moodChanged, Target target, String modelId, int imageCount, Parameters parameters, File attachment,
        List<String> referenceImages, ChatEntry.Source source, List<String> savedImages) {

    /**
     * Creates an order without saved images.
     */
    public GenerationOrder(Chat chat, String prompt, PromptComposer.Selection blocks, Mood mood, boolean moodChanged,
            Target target, String modelId, int imageCount, Parameters parameters, File attachment,
            List<String> referenceImages, ChatEntry.Source source) {
        this(chat, prompt, blocks, mood, moodChanged, target, modelId, imageCount, parameters, attachment,
                referenceImages, source, List.of());
    }

    /**
     * Whether the order has at least one reference image, attached as file or as Base64 data.
     *
     * @return true if the model gets an image to work on
     */
    public boolean hasReference() {
        return attachment != null || (referenceImages != null && !referenceImages.isEmpty());
    }

    /**
     * The frame and the target aspect ratio and resolution.
     *
     * @param frame the chosen frame, null for none
     * @param changed whether the target differs from the chosen frame
     * @param aspectRatio the target aspect ratio, null for none (the model then takes a square)
     * @param resolution the target resolution, null for none
     */
    public record Target(Frame frame, boolean changed, String aspectRatio, String resolution) {
    }

    /**
     * The parameters of the model; each applies only to the models that know it, null leaves it out.
     *
     * @param seed the seed, null for a random one
     * @param safetyTolerance the safety tolerance of the models before FLUX.2, kept in the entry
     * @param flux2SafetyTolerance the safety tolerance of FLUX.2 and FLUX 3, null for the configured one
     * @param promptUpsampling whether BFL enhances the prompt
     * @param thinkingLevel the thinking level of Nano Banana 2 and Lite, null for the API default
     * @param googleSearch whether Gemini grounds the image in a Google Search
     * @param guidance the guidance of FLUX.2 [flex]
     * @param steps the steps of FLUX.2 [flex]
     * @param videoSeconds the video length, null for 8 seconds
     */
    public record Parameters(Long seed, int safetyTolerance, Integer flux2SafetyTolerance, boolean promptUpsampling,
            String thinkingLevel, Boolean googleSearch, Double guidance, Integer steps, Integer videoSeconds) {
    }
}
