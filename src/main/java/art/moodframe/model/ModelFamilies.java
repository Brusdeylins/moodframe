package art.moodframe.model;

import art.moodframe.model.GenerationModel;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Groups the model dropdown items (one per variant) into families with a resolution choice, for the main
 * window and the brainstorm popup, and maps a target resolution to the resolutions of a model.
 *
 * @since 1.9.0
 */
public final class ModelFamilies {

    /** Every image resolution of every model once, from small to large: the targets a frame can choose from. */
    public static final List<String> RESOLUTIONS = Stream.of(GenerationModel.values())
            .filter((GenerationModel model) -> !model.isVideoModel()).map(GenerationModel::getResolutionLabel)
            .filter(Objects::nonNull).distinct().sorted(Comparator.comparingDouble(ModelFamilies::pixels)).toList();

    private ModelFamilies() {
    }

    /**
     * Gets the family of a dropdown item: an OpenAI model is a family of its own, the others share it by resolution.
     *
     * @param item the dropdown item (may be null)
     * @return the family name
     */
    public static String familyOf(String item) {
        if (item == null) {
            return null;
        }
        if (GenerationModel.openAiModelId(item) != null) {
            return item;
        }
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(item);
        return model == null ? item : model.getFamilyName();
    }

    /**
     * Gets the resolutions of a model of an OpenAI-compatible gateway, whose size is free text.
     *
     * @param item the dropdown item
     * @return 1K, 2K and 4K (only 2K and 4K for Qwen-Image, which is too weak at 1K), or null for any other item
     */
    public static List<String> freeResolutions(String item) {
        String id = GenerationModel.openAiModelId(item);
        if (id == null || GenerationModel.isOpenAiImageModelId(id)) {
            return null;
        }
        return id.toLowerCase().contains("qwen") ? List.of("2K", "4K") : List.of("1K", "2K", "4K");
    }

    /**
     * Gets the config key under which the resolution of a family is remembered.
     *
     * @param family the family name
     * @return the key
     */
    public static String resolutionKey(String family) {
        return "ui.resolution." + family.replaceAll("[^A-Za-z0-9]+", "_");
    }

    /**
     * Gets the items of a family.
     *
     * @param items all dropdown items
     * @param family the family name
     * @return the items of the family, in the order of the dropdown
     */
    public static List<String> variantsOf(List<String> items, String family) {
        return items.stream().filter((String item) -> family.equals(familyOf(item))).toList();
    }

    /**
     * Gets the resolutions to choose from for an item: the free ones, or those of the variants of its family.
     *
     * @param items all dropdown items
     * @param item the dropdown item
     * @return the resolution labels, empty for a model without resolution variants
     */
    public static List<String> resolutionsOf(List<String> items, String item) {
        List<String> free = freeResolutions(item);
        if (free != null) {
            return free;
        }
        return variantsOf(items, familyOf(item)).stream()
                .map(GenerationModel::fromLocalizedDisplayName).filter(Objects::nonNull)
                .map(GenerationModel::getResolutionLabel).filter(Objects::nonNull).distinct().toList();
    }

    /**
     * Gets the resolution of a variant.
     *
     * @param item the dropdown item
     * @return the resolution label, null if the variant has none
     */
    public static String resolutionOf(String item) {
        GenerationModel model = GenerationModel.fromLocalizedDisplayName(item);
        return model == null ? null : model.getResolutionLabel();
    }

    /**
     * Picks the variant of a family: the wanted resolution, else the fallback, else 1K, else the first.
     *
     * @param items all dropdown items
     * @param family the family name
     * @param wanted the wanted resolution (may be null)
     * @param fallback the resolution to take if the wanted one does not exist (may be null)
     * @return the item, or null if the family has no item
     */
    public static String variantFor(List<String> items, String family, String wanted, String fallback) {
        List<String> variants = variantsOf(items, family);
        if (variants.isEmpty()) {
            return null;
        }
        for (String resolution : new String[] {wanted, fallback, "1K"}) {
            for (String variant : variants) {
                if (resolution != null && resolution.equals(resolutionOf(variant))) {
                    return variant;
                }
            }
        }
        return variants.get(0);
    }

    /**
     * Picks the resolution closest to a target, comparing the pixels of the long side on a logarithmic scale, that is
     * by the factor between both; on a tie the smaller one.
     *
     * @param target the target resolution, for example {@code 4K}; one that cannot be read counts as {@code 1K}
     * @param resolutions the resolutions to choose from
     * @return the target itself if it is offered, else the closest one; null without resolutions
     */
    public static String nearest(String target, List<String> resolutions) {
        double wanted = pixels(target);
        String nearest = null;
        double best = Double.MAX_VALUE;
        for (String candidate : resolutions) {
            double pixels = pixels(candidate);
            double factor = Math.max(pixels, wanted) / Math.min(pixels, wanted);
            if (factor < best || factor == best && pixels < pixels(nearest)) {
                nearest = candidate;
                best = factor;
            }
        }
        return nearest;
    }

    /**
     * Picks the resolution a model makes of a target resolution, for example of a frame: the target itself if the
     * model offers it, else its closest one (see {@link #nearest(String, List)}).
     *
     * @param target the target resolution
     * @param items all dropdown items
     * @param item the dropdown item of the model (may be null)
     * @return the resolution for the model, null for a model without resolution, which keeps its fixed size
     */
    public static String nearestForItem(String target, List<String> items, String item) {
        return item == null ? null : nearest(target, resolutionsOf(items, item));
    }

    /** Pixels of the long side of a resolution: {@code 2K} 2048, {@code 768} 768, {@code 1080p} 1920; else 1024. */
    private static double pixels(String resolution) {
        if (resolution == null) {
            return 1024;
        }
        double unit = resolution.endsWith("K") ? 1024 : resolution.endsWith("p") ? 16.0 / 9 : 1;
        try {
            double pixels = Double.parseDouble(resolution.replaceFirst("[Kp]$", "")) * unit;
            return pixels > 0 && Double.isFinite(pixels) ? pixels : 1024;
        } catch (NumberFormatException e) {
            return 1024;
        }
    }
}
