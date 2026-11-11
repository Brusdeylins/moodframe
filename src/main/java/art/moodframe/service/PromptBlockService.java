package art.moodframe.service;

import art.moodframe.model.PromptBlock;
import art.moodframe.repository.PromptBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service for the prompt building blocks: the standard blocks from code with fixed ids,
 * the blocks of the user from SQLite.
 *
 * <p>Rules for the standard style texts: they describe the medium and its rendering only,
 * by visible features; no quality phrases ("8K", "professional"), no negative lists
 * ("no text"), no names of living artists, studios, magazines or brands, no camera brands.</p>
 *
 * @since 2.0.0
 */
public class PromptBlockService {

    private static final Logger logger = LoggerFactory.getLogger(PromptBlockService.class);

    /** Former infographic styles that were merged into the remaining ones, by name. */
    private static final Map<String, String> MERGED_STYLES = Map.of(
            "Infographic Modern", "Infographic Flat",
            "Infographic Minimalist", "Infographic Minimal",
            "Infographic Educational", "Infographic Sketch",
            "Infographic Timeline", "Infographic Flat",
            "Infographic Flowchart", "Infographic Flat",
            "Infographic Mind Map", "Infographic Flat",
            "Infographic Comparison", "Infographic Flat",
            "Infographic Data", "Infographic Flat");

    private final PromptBlockRepository repository;
    private final List<PromptBlock> defaultStyles;

    /**
     * Creates the service.
     */
    public PromptBlockService() {
        this.repository = new PromptBlockRepository();
        this.defaultStyles = defaultStyles();
        renameCustomBlocksNamedLikeDefaults();
    }

    /**
     * A block of the user named like a standard block (which may be new) keeps its text and gets the
     * next free name with a counter (see {@link #freeName}), so every name stays selectable.
     */
    private void renameCustomBlocksNamedLikeDefaults() {
        for (PromptBlock custom : repository.findAll(PromptBlock.Type.STYLE)) {
            if (!isDefault(PromptBlock.Type.STYLE, custom.getName()))
                continue;
            String original = custom.getName();
            custom.setName(freeName(PromptBlock.Type.STYLE, original));
            if (repository.update(custom))
                logger.info("Renamed style '{}' of the user to '{}', a standard style has its name", original,
                        custom.getName());
        }
    }

    private static List<PromptBlock> defaultStyles() {
        List<PromptBlock> styles = new ArrayList<>();

        /*  Photographic styles  */
        String photo = PromptBlock.GROUP_PHOTO;
        styles.add(style("Photography", photo, true,
                "realistic photograph, sharp focus, natural detail and texture"));
        styles.add(style("Business Photo", photo, true,
                "realistic, detailed photography, clean composition"));
        styles.add(style("Portrait Photography", photo, true,
                "portrait photograph, natural skin texture, catchlights in the eyes"));
        styles.add(style("Street Photography", photo, true,
                "candid street photograph, an unposed moment in an urban environment, documentary style"));
        styles.add(style("Documentary", photo, true,
                "documentary photograph, photojournalistic style, raw unposed moment, environmental context, "
                + "storytelling composition"));
        styles.add(style("Editorial Fashion", photo, true,
                "high-fashion editorial photograph, elegant pose, magazine-quality styling"));
        styles.add(style("Product Photography", photo, true,
                "product photograph, the product crisp and detailed on a clean uncluttered backdrop, "
                + "accurate reflections, catalog style"));
        styles.add(style("Analog Film", photo, true,
                "analog photograph on Kodak Portra 400 35mm film, natural film grain, organic tones, "
                + "slightly soft focus"));
        styles.add(style("Black-and-White Film", photo, true,
                "black and white photograph on 35mm film, rich tonal range from deep black to bright white, "
                + "natural film grain"));
        styles.add(style("Cinematic Film Still", photo, true,
                "still frame from a feature film, widescreen cinematic composition, motion-picture look"));
        styles.add(style("2000s Digicam", photo, true,
                "early digital camera photo, slight noise, candid snapshot, 2000s digicam style"));

        /*  Painted, drawn and digital styles  */
        String art = PromptBlock.GROUP_ART;
        styles.add(style("Oil Painting", art, false,
                "oil painting on canvas in the manner of the old masters, visible thick impasto brushstrokes, "
                + "layered paint, rich texture"));
        styles.add(style("Watercolor", art, false,
                "delicate watercolor painting on textured paper, soft transparent washes, "
                + "paint bleeding naturally at the edges"));
        styles.add(style("Pencil Sketch", art, false,
                "hand-drawn graphite pencil sketch on white paper, fine precise linework, cross-hatched shading"));
        styles.add(style("Digital Art", art, false,
                "digital illustration, concept art, clean precise lineart, detailed rendering"));
        styles.add(style("Illustration", art, false,
                "symbolic, conceptual stock illustration using common metaphors, icons and symbols to illustrate "
                + "the mentioned concepts, clean vector style, simple clear shapes, balanced composition"));
        styles.add(style("Pixel Art", art, false,
                "retro 16-bit pixel art, crisp square pixels without anti-aliasing, clean sprite-style shapes"));
        styles.add(style("Anime", art, false,
                "anime illustration, clean sharp lineart, cel shading, detailed expressive eyes, "
                + "Japanese animation style"));
        styles.add(style("Fantasy Art", art, false,
                "detailed fantasy illustration, painterly concept art, mythical elements"));
        styles.add(style("Pop Art", art, false,
                "pop art silkscreen print, flat color blocks, Ben-Day halftone dots, bold graphic outlines, "
                + "1960s commercial art"));
        styles.add(style("Surrealism", art, false,
                "surrealist painting, dreamlike impossible scene rendered with photorealistic precision, "
                + "bizarre juxtapositions, distorted objects"));
        styles.add(style("Synthwave", art, false,
                "1980s retro-futuristic synthwave illustration, perspective grid floor, VHS scan lines, CRT bloom, "
                + "chrome surfaces"));
        styles.add(style("Graphic Novel", art, false,
                "graphic novel illustration, heavy black inks, detailed crosshatching, mature cinematic panels"));
        styles.add(style("European Comic", art, false,
                "Franco-Belgian comic album style, ligne claire, clean precise outlines, flat colors, "
                + "detailed backgrounds"));

        /*  Layout styles: the look of infographics, logos and comics, without fixed colors  */
        String layout = PromptBlock.GROUP_LAYOUT;
        styles.add(style("Infographic Flat", layout, false,
                "Flat vector infographic with a clean, modern look. Simple solid shapes and uniform line icons, "
                + "generous whitespace, a clear visual hierarchy and sans-serif labels."));
        styles.add(style("Infographic Minimal", layout, false,
                "Ultra-minimalist infographic in the Swiss design tradition. Very few elements, simple geometric "
                + "icons, a strict grid, large margins and small neutral sans-serif labels."));
        styles.add(style("Infographic Sketch", layout, false,
                "Hand-drawn infographic that looks sketched with pen and marker on paper. Slightly uneven outlines, "
                + "crosshatched shading, doodle icons and hand-lettered labels."));
        styles.add(style("Infographic Kids", layout, false,
                "Child-friendly infographic with rounded shapes and friendly cartoon characters. Large simple icons, "
                + "very little text, big easily readable labels."));
        styles.add(style("Infographic Corporate", layout, false,
                "Business dashboard infographic with a clean card-based layout. KPI tiles, compact charts, "
                + "thin dividers, restrained icons and precise small labels."));
        styles.add(style("Infographic Isometric", layout, false,
                "Isometric 3D infographic drawn at consistent 30-degree angles. Clean geometric objects forming "
                + "a small miniature world, with short labels connected by thin lines."));
        styles.add(style("Infographic Paper Cutout", layout, false,
                "Paper cutout infographic made of layered construction paper. Scissor-cut edges, visible paper "
                + "texture, soft drop shadows between the layers, cut-out lettering."));
        styles.add(style("Infographic Dark Mode", layout, false,
                "Dark interface infographic with glowing elements on a dark background. Thin luminous lines, "
                + "monospaced labels, high contrast for readability."));
        styles.add(style("Infographic Neon", layout, false,
                "Neon sign infographic on a dark brick wall. Icons and lettering formed from glowing glass tubes "
                + "with a soft halo."));
        styles.add(style("Infographic Magazine", layout, false,
                "Editorial magazine infographic with massive bold headlines, an asymmetrical grid, large color "
                + "blocks and a subtle print grain."));
        styles.add(style("Infographic Technical", layout, false,
                "Technical diagram infographic with rounded glass-like component cards on a deep charcoal "
                + "background. Thin glowing connector lines with arrowheads, a small clean icon on every component, "
                + "short monospaced labels, generous spacing and precise alignment."));
        styles.add(style("Logo Design", layout, false,
                "clean, flat, vector-style logo, minimalist flat vector art, clean geometric shapes, a simple "
                + "memorable iconic symbol, clever use of negative space, balanced symmetrical composition, "
                + "works in a single color and at any size"));
        styles.add(style("Comic Strip", layout, false,
                "4-panel comic strip, sequential storytelling, classic newspaper strip look, clean lineart, "
                + "speech bubbles, expressive characters, white gutters between panels, humorous tone"));
        styles.add(style("Manga Panel", layout, false,
                "manga page layout, black and white inks with screentone shading, dynamic speed lines, "
                + "dramatic angles, expressive emotions, shonen action manga style"));

        for (int i = 0; i < styles.size(); i++)
            styles.get(i).setSortOrder(i);
        return styles;
    }

    private static PromptBlock style(String name, String group, boolean photographic, String text) {
        return new PromptBlock(standardId(PromptBlock.Type.STYLE, name), PromptBlock.Type.STYLE, name, text, group,
                photographic);
    }

    /**
     * The fixed id of a standard block, for example {@code std:style:watercolor}.
     *
     * @param type the block type
     * @param name the block name
     * @return the id
     */
    static String standardId(PromptBlock.Type type, String name) {
        return PromptBlock.STANDARD_PREFIX + type.name().toLowerCase(Locale.ROOT) + ":" + slug(name);
    }

    /**
     * The name in lower case, every run of other characters than letters and digits replaced by one dash.
     *
     * @param name the name
     * @return the slug, for example {@code ocean-breeze}
     */
    public static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    /**
     * Returns the standard blocks of a type, followed by those of the user.
     *
     * @param type the block type
     * @return the blocks
     */
    public List<PromptBlock> getAll(PromptBlock.Type type) {
        List<PromptBlock> blocks = getDefaults(type);
        blocks.addAll(repository.findAll(type));
        return blocks;
    }

    /**
     * Returns the standard blocks of a type.
     *
     * @param type the block type
     * @return the blocks
     */
    public List<PromptBlock> getDefaults(PromptBlock.Type type) {
        return type == PromptBlock.Type.STYLE ? new ArrayList<>(defaultStyles) : new ArrayList<>();
    }

    /**
     * Returns the blocks of a type the user created.
     *
     * @param type the block type
     * @return the blocks
     */
    public List<PromptBlock> getCustom(PromptBlock.Type type) {
        return repository.findAll(type);
    }

    /**
     * Finds a block by id or name. A former infographic style that was merged into another one
     * resolves to that one.
     *
     * @param type the block type
     * @param reference the id or the name of the block
     * @return the block, null if there is none
     */
    public PromptBlock find(PromptBlock.Type type, String reference) {
        if (reference == null || reference.isEmpty())
            return null;

        for (PromptBlock block : getDefaults(type)) {
            if (block.getId().equals(reference) || block.getName().equals(reference))
                return block;
        }
        PromptBlock custom = repository.findById(reference);
        if (custom == null || custom.getType() != type)
            custom = repository.findByName(type, reference);
        if (custom == null && type == PromptBlock.Type.STYLE && MERGED_STYLES.containsKey(reference))
            return find(type, MERGED_STYLES.get(reference));
        return custom;
    }

    /**
     * Returns the name unchanged if no block of the type has it yet, else the name with the next free counter,
     * e.g. {@code "Analog Film (1)"}.
     *
     * @param type the block type
     * @param name the wanted name
     * @return a name no block of the type has
     */
    public String freeName(PromptBlock.Type type, String name) {
        if (!exists(type, name))
            return name;
        int counter = 1;
        while (exists(type, name + " (" + counter + ")"))
            counter++;
        return name + " (" + counter + ")";
    }

    /**
     * Checks if a standard block or a block of the user has this name.
     *
     * @param type the block type
     * @param name the name
     * @return true if the name is taken
     */
    public boolean exists(PromptBlock.Type type, String name) {
        return isDefault(type, name) || repository.findByName(type, name) != null;
    }

    /**
     * Checks if a standard block has this name.
     *
     * @param type the block type
     * @param name the name
     * @return true if a standard block has the name
     */
    public boolean isDefault(PromptBlock.Type type, String name) {
        return getDefaults(type).stream().anyMatch((PromptBlock block) -> block.getName().equals(name));
    }

    /**
     * Stores a new block of the user behind the existing ones.
     *
     * @param block the block, without id
     * @return true if it was stored, false if the name is taken
     */
    public boolean addCustom(PromptBlock block) {
        if (exists(block.getType(), block.getName())) {
            logger.warn("Cannot add prompt block '{}' - name already exists", block.getName());
            return false;
        }

        /*  Blocks of the user follow the standard blocks, from 1000 on  */
        block.setSortOrder(Math.max(1000, repository.findAll(block.getType()).stream()
                .mapToInt((PromptBlock existing) -> existing.getSortOrder() + 1).max().orElse(1000)));
        return repository.save(block);
    }

    /**
     * Updates a block of the user; standard blocks cannot be changed.
     *
     * @param block the changed block with its id
     * @return true if it was updated, false for a standard block, an unknown id or a taken name
     */
    public boolean updateCustom(PromptBlock block) {
        PromptBlock existing = block.isDefault() ? null : repository.findById(block.getId());
        if (existing == null) {
            logger.warn("Cannot update prompt block '{}' - no block of the user", block.getName());
            return false;
        }
        if (!existing.getName().equals(block.getName()) && exists(block.getType(), block.getName())) {
            logger.warn("Cannot rename prompt block to '{}' - name already exists", block.getName());
            return false;
        }
        return repository.update(block);
    }

    /**
     * Deletes a block of the user; standard blocks cannot be deleted.
     *
     * @param id the block id
     * @return true if it was deleted
     */
    public boolean deleteCustom(String id) {
        return id != null && !id.startsWith(PromptBlock.STANDARD_PREFIX) && repository.delete(id);
    }

    /**
     * Deletes all blocks of a type the user created.
     *
     * @param type the block type
     * @return the number of deleted blocks
     */
    public int resetToDefaults(PromptBlock.Type type) {
        return repository.deleteAll(type);
    }
}
