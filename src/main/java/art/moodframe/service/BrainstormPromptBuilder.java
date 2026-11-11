package art.moodframe.service;

import art.moodframe.model.BrainstormDraft;
import art.moodframe.model.ColorStyle;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.model.AspectRatios;
import art.moodframe.model.ModelFamilies;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Builds the system prompt of the brainstorm assistant from the template and the app data.
 *
 * @since 1.7.0
 */
public final class BrainstormPromptBuilder {

    private static final Pattern PRICE = Pattern.compile("\\([^()]*\\$[^()]*\\)\\s*$");

    private BrainstormPromptBuilder() {
    }

    /**
     * Fills all placeholders of the system prompt template.
     *
     * @param template the template with {@code {{...}}} placeholders
     * @param uiLanguage language name the chat is held in, for example "German"
     * @param context the selectable building blocks, moods and models and the current main window state
     * @param currentDraft the current draft as JSON text, or empty if there is none yet
     * @return the system prompt
     */
    public static String build(String template, String uiLanguage, BrainstormContext context, String currentDraft) {
        return template
                .replace("{{UI_LANGUAGE}}", uiLanguage)
                .replace("{{STYLES}}", styles(context))
                .replace("{{ATMOSPHERES}}", blocks(context.atmospheres()))
                .replace("{{LIGHTS}}", blocks(context.lights()))
                .replace("{{COLOR_STYLES}}", colorStyles(context))
                .replace("{{MOODS}}", moods(context))
                .replace("{{ASPECT_RATIOS}}", aspectRatios())
                .replace("{{MODELS}}", models(context))
                .replace("{{CURRENT_STATE}}", context.currentState())
                .replace("{{CURRENT_DRAFT}}", currentDraft.isEmpty() ? "none yet" : currentDraft);
    }

    /** One line per style with its group, which tells the layout styles (no light) apart. */
    private static String styles(BrainstormContext context) {
        return context.styles().stream()
                .map((PromptBlock style) -> "- " + style.getName()
                        + (style.getGroup() != null ? " (" + style.getGroup() + ")" : "") + ": " + style.getText())
                .collect(Collectors.joining("\n"));
    }

    private static String blocks(List<PromptBlock> blocks) {
        return blocks.stream()
                .map((PromptBlock block) -> "- " + block.getName() + ": " + block.getText())
                .collect(Collectors.joining("\n"));
    }

    /** One line per mood with the names of its blocks in use; a mood without any is left out. */
    private static String moods(BrainstormContext context) {
        List<String> lines = new ArrayList<>();
        for (Mood mood : context.moods()) {
            List<String> parts = new ArrayList<>();
            for (Mood.Field field : BrainstormDraft.FIELDS) {
                String name = context.nameOf(field, mood.getReference(field));
                if (name != null)
                    parts.add(field.name().toLowerCase(Locale.ROOT) + " " + name);
            }
            if (!parts.isEmpty())
                lines.add("- " + mood.getName() + ": " + String.join(", ", parts));
        }
        return String.join("\n", lines);
    }

    private static String colorStyles(BrainstormContext context) {
        return context.colorStyles().stream()
                .map((ColorStyle style) -> "- " + style.getName() + ": " + String.join(", ", style.getColors()))
                .collect(Collectors.joining("\n"));
    }

    private static String aspectRatios() {
        return "- FLUX 3 Image and FLUX 3 Image Klein: " + String.join(", ", AspectRatios.FLUX_3) + "\n"
                + "- FLUX.2 models: " + String.join(", ", AspectRatios.BFL) + "\n"
                + "- Nano Banana (Gemini image) models: " + String.join(", ", AspectRatios.GEMINI) + "\n"
                + "- Nano Banana 2 and Nano Banana 2 Lite models: " + String.join(", ", AspectRatios.NANO_BANANA_2) + "\n"
                + "- OpenAI image models (gpt-image, dall-e): " + String.join(", ", AspectRatios.OPENAI) + "\n"
                + "- Other OpenAI models (gateway, e.g. Qwen-Image): " + String.join(", ", AspectRatios.OPENAI_COMPATIBLE) + "\n"
                + "- Video models (Gemini Omni): " + String.join(", ", AspectRatios.VIDEO);
    }

    /** One line per model family with its resolutions and their prices, as the model dropdown offers them. */
    private static String models(BrainstormContext context) {
        Map<String, List<Integer>> families = new LinkedHashMap<>();
        IntStream.range(0, context.models().size()).forEach((int i) -> families
                .computeIfAbsent(ModelFamilies.familyOf(context.modelNames().get(i)), (String family) -> new ArrayList<>())
                .add(i));

        return families.entrySet().stream().map((Map.Entry<String, List<Integer>> family) -> {
            List<String> names = family.getValue().stream().map(context.modelNames()::get).toList();
            GenerationModel first = context.models().get(family.getValue().get(0));
            List<String> resolutions = ModelFamilies.resolutionsOf(context.modelNames(), names.get(0)).stream()
                    .map((String resolution) -> {
                        String price = names.stream().filter((String name) -> resolution.equals(ModelFamilies.resolutionOf(name)))
                                .map(BrainstormPromptBuilder::price).filter(Objects::nonNull).findFirst().orElse(null);
                        return price == null ? resolution : resolution + " " + price;
                    }).toList();
            return "- " + family.getKey() + " | " + (first.isVideoModel() ? "video" : "image") + " | "
                    + (resolutions.isEmpty() ? "" : "resolutions: " + String.join(", ", resolutions) + " | ")
                    + first.getDescription();
        }).collect(Collectors.joining("\n"));
    }

    /** The price in the display name, for example {@code ($0.067)}, or null. */
    private static String price(String name) {
        Matcher matcher = PRICE.matcher(name);
        return matcher.find() ? matcher.group() : null;
    }
}
