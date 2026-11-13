package art.moodframe.service;

import art.moodframe.model.CameraSettings;
import art.moodframe.model.CameraSettings.Aperture;
import art.moodframe.model.CameraSettings.Body;
import art.moodframe.model.CameraSettings.Iso;
import art.moodframe.model.CameraSettings.Lens;
import art.moodframe.model.CameraSettings.Perspective;
import art.moodframe.model.CameraSettings.Shot;
import art.moodframe.model.CameraSettings.Shutter;
import art.moodframe.model.PromptBlock;
import art.moodframe.repository.PromptBlockRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * <p>Rules for the standard atmosphere texts: they describe mood, weather and sense of space in concrete
 * words; no persons or facial expressions except as a condition ("people, where present, ..."), no technique,
 * no light, no colors. "Open Business" keeps the wording of the former style "Business Photography".</p>
 *
 * <p>Rules for the standard light texts (BFL "Lighting"): source, quality, direction and what the light
 * does; a color temperature only where it belongs to the situation (golden and blue hour), otherwise no
 * colors, no medium and no mood.</p>
 *
 * <p>No standard text suggests a place, a setting or scene content: a block describes its own task only,
 * the place comes from the motif.</p>
 *
 * <p>The standard cameras carry settings only ({@link CameraSettings}); their fragment comes from
 * {@link CameraPromptBuilder}. Exposure stays with the light, film stock with the style.</p>
 *
 * @since 2.0.0
 */
public class PromptBlockService {

    private static final Logger logger = LoggerFactory.getLogger(PromptBlockService.class);

    private static final ObjectMapper JSON = new ObjectMapper();

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
    private final List<PromptBlock> defaultAtmospheres;
    private final List<PromptBlock> defaultLights;
    private final List<PromptBlock> defaultCameras;

    /**
     * Creates the service.
     */
    public PromptBlockService() {
        this.repository = new PromptBlockRepository();
        this.defaultStyles = defaultStyles();
        this.defaultAtmospheres = defaultAtmospheres();
        this.defaultLights = defaultLights();
        this.defaultCameras = defaultCameras();
        renameCustomBlocksNamedLikeDefaults();
    }

    /**
     * A block of the user named like a standard block (which may be new) keeps its text and gets the
     * next free name with a counter (see {@link #freeName}), so every name stays selectable.
     */
    private void renameCustomBlocksNamedLikeDefaults() {
        for (PromptBlock.Type type : PromptBlock.Type.values()) {
            for (PromptBlock custom : repository.findAll(type)) {
                if (!isDefault(type, custom.getName()))
                    continue;
                String original = custom.getName();
                custom.setName(freeName(type, original));
                if (repository.update(custom))
                    logger.info("Renamed {} '{}' of the user to '{}', a standard block has its name",
                            type.name().toLowerCase(Locale.ROOT), original, custom.getName());
            }
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
                "candid street photograph, an unposed fleeting moment, documentary style"));
        styles.add(style("Documentary", photo, true,
                "documentary photograph, photojournalistic style, raw unposed moment, environmental context, "
                + "storytelling composition"));
        styles.add(style("Editorial Fashion", photo, true,
                "high-fashion editorial photograph, elegant pose, magazine-quality styling"));
        styles.add(style("Product Photography", photo, true,
                "product photograph, the product crisp and detailed on a clean uncluttered backdrop, "
                + "accurate reflections, catalog style"));
        styles.add(style("Styled Set", photo, true,
                "product photograph on an art-directed tabletop set with a few matching props, styled like a "
                + "magazine still life, the product the clear hero"));
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
                "detailed fantasy illustration, painterly concept art, richly rendered textures"));
        styles.add(style("Pop Art", art, false,
                "pop art silkscreen print, flat color blocks, Ben-Day halftone dots, bold graphic outlines, "
                + "1960s commercial art"));
        styles.add(style("Surrealism", art, false,
                "surrealist painting, dreamlike impossible scene rendered with photorealistic precision, "
                + "bizarre juxtapositions, distorted objects"));
        styles.add(style("Synthwave", art, false,
                "1980s retro-futuristic synthwave illustration, airbrushed gradients, VHS scan lines, CRT bloom, "
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

    private static List<PromptBlock> defaultAtmospheres() {
        List<PromptBlock> atmospheres = new ArrayList<>();
        atmospheres.add(atmosphere("Melancholic",
                "quiet, melancholic mood, a sense of stillness and gentle sadness, subdued and unhurried"));
        atmospheres.add(atmosphere("Euphoric",
                "elated, ecstatic mood, overflowing happiness, buoyant and carefree"));
        atmospheres.add(atmosphere("Longing",
                "wistful mood of longing, a quiet feeling of waiting and missing something, much empty space around "
                + "the subject"));
        atmospheres.add(atmosphere("Menacing",
                "tense, suspenseful mood, a quiet unease, guarded and watchful"));
        atmospheres.add(atmosphere("Calm",
                "calm, serene mood, peaceful and balanced, plenty of breathing room"));
        atmospheres.add(atmosphere("Playful",
                "playful, lighthearted mood, cheerful and a little cheeky"));
        atmospheres.add(atmosphere("Epic",
                "awe-inspiring, uplifting mood, powerful and dramatic, a sense of grandeur"));
        atmospheres.add(atmosphere("Intimate",
                "intimate, tender mood, close and private, quiet warmth"));
        atmospheres.add(atmosphere("Nostalgic",
                "nostalgic mood, like a fond memory from decades ago, gentle and sentimental"));
        atmospheres.add(atmosphere("Clean and Factual",
                "clear, matter-of-fact mood, tidy and precise, neutral and uncluttered"));
        atmospheres.add(atmosphere("Dreamlike",
                "dreamlike, ethereal mood, soft and slightly unreal, floating between reality and imagination"));
        atmospheres.add(atmosphere("Raw",
                "raw, gritty mood, unpolished and honest, rough textures and imperfection"));
        atmospheres.add(atmosphere("Mysterious",
                "mysterious mood, secretive and quiet, much left hidden"));
        atmospheres.add(atmosphere("Glamorous",
                "glamorous, elegant mood, confident and luxurious"));
        atmospheres.add(atmosphere("Futuristic",
                "futuristic mood, sleek, clean and precise, a cool artificial perfection"));

        /*  The person and mood parts of the former style "Business Photography", word for word  */
        atmospheres.add(atmosphere("Open Business",
                "open, future-oriented, modern atmosphere, authentic business situation in a modern and dynamic "
                + "setting, people are authentic and natural-looking with friendly and approachable expressions, "
                + "wearing business casual attire, diverse ethnicities and body types"));

        /*  Weather  */
        atmospheres.add(atmosphere("Fog",
                "dense fog drifting through the scene, distant shapes fading into haze, muffled and quiet"));
        atmospheres.add(atmosphere("Rain",
                "steady rain, wet glistening surfaces, puddles and reflections"));

        for (int i = 0; i < atmospheres.size(); i++)
            atmospheres.get(i).setSortOrder(i);
        return atmospheres;
    }

    private static PromptBlock atmosphere(String name, String text) {
        return new PromptBlock(standardId(PromptBlock.Type.ATMOSPHERE, name), PromptBlock.Type.ATMOSPHERE, name, text,
                null, false);
    }

    private static List<PromptBlock> defaultLights() {
        List<PromptBlock> lights = new ArrayList<>();
        lights.add(light("Soft Natural",
                "soft natural lighting, gentle and even"));
        lights.add(light("Golden Hour",
                "low sun shortly before sunset, warm directional light raking across the scene, long soft shadows, "
                + "a gentle glow on edges"));
        lights.add(light("Blue Hour",
                "twilight just after sunset, soft cool ambient skylight without direct sun, "
                + "low contrast and faint shadows"));
        lights.add(light("Overcast",
                "overcast sky acting as a giant diffuser, flat even light, soft shadow-free illumination"));
        lights.add(light("Hard Noon Sun",
                "harsh direct midday sun from high above, crisp short shadows, strong contrast"));
        lights.add(light("Window Light",
                "soft daylight from a large window at the side, gentle falloff into shadow"));
        lights.add(light("Studio Softbox",
                "large diffused key light at 45 degrees with subtle fill, controlled even lighting, "
                + "soft-edged shadows"));
        lights.add(light("Rembrandt",
                "Rembrandt lighting, key light at 45 degrees from above, a small triangle of light on the shadow side "
                + "of the face"));
        lights.add(light("Rim Light",
                "strong backlight outlining the subject with a bright rim, the front in gentle shadow, "
                + "clear separation from the background"));
        lights.add(light("Backlit Silhouette",
                "light source directly behind the subject, the subject a dark silhouette against a bright background"));
        lights.add(light("Chiaroscuro",
                "hard low-key light from one side, deep black shadows and bright highlights, strong chiaroscuro "
                + "contrast"));
        lights.add(light("Neon Practicals",
                "night, lit only by saturated neon light from sources outside the frame, the glow reflecting on "
                + "nearby surfaces, dark surroundings"));
        lights.add(light("Candlelight",
                "lit only by candle flames, small flickering pools of light, rapid falloff into darkness"));
        lights.add(light("Moonlight",
                "dim moonlight from above, low overall brightness, soft-edged shadows, a night scene"));
        lights.add(light("Light Rays",
                "beams of light cutting through haze, clearly visible shafts of light, glowing dust in the air"));
        lights.add(light("Direct Flash",
                "harsh direct on-camera flash, flat frontal light, a sharp shadow right behind the subject, "
                + "the background falling off into darkness"));
        lights.add(light("High-Key",
                "bright high-key lighting, a very light background, minimal shadows, airy exposure"));

        for (int i = 0; i < lights.size(); i++)
            lights.get(i).setSortOrder(i);
        return lights;
    }

    private static PromptBlock light(String name, String text) {
        return new PromptBlock(standardId(PromptBlock.Type.LIGHT, name), PromptBlock.Type.LIGHT, name, text, null,
                false);
    }

    private static List<PromptBlock> defaultCameras() {
        List<PromptBlock> cameras = new ArrayList<>();
        cameras.add(camera("Street 35mm", new CameraSettings(Body.COMPACT_35MM, "Leica M6", Lens.MM_35, Aperture.F_11,
                null, null, Perspective.EYE_LEVEL, null)));
        cameras.add(camera("Classic Portrait", new CameraSettings(Body.FULL_FRAME, null, Lens.MM_85, Aperture.F_1_4,
                null, null, Perspective.EYE_LEVEL, Shot.CLOSE_UP)));
        cameras.add(camera("Epic Wide Low Angle", new CameraSettings(Body.FULL_FRAME, null, Lens.MM_14, Aperture.F_11,
                null, null, Perspective.LOW_ANGLE, Shot.WIDE)));
        cameras.add(camera("Telephoto Compression", new CameraSettings(Body.FULL_FRAME, null, Lens.MM_135,
                Aperture.F_4, null, null, null, null)));
        cameras.add(camera("Macro Detail", new CameraSettings(Body.FULL_FRAME, null, Lens.MACRO, Aperture.F_4,
                null, null, null, Shot.DETAIL)));
        cameras.add(camera("Drone Overview", new CameraSettings(Body.DRONE, null, Lens.MM_24, Aperture.F_11,
                null, null, Perspective.TOP_DOWN, Shot.EXTREME_WIDE)));
        cameras.add(camera("Cinematic Anamorphic", new CameraSettings(Body.CINEMA, "ARRI Alexa 35", Lens.ANAMORPHIC,
                Aperture.F_1_4, null, null, null, Shot.MEDIUM)));
        cameras.add(camera("Product Tabletop", new CameraSettings(Body.MEDIUM_FORMAT, "Hasselblad X2D", Lens.MM_85,
                Aperture.F_11, null, Iso.ISO_100, Perspective.HIGH_ANGLE, null)));
        cameras.add(camera("Product Bokeh", new CameraSettings(Body.FULL_FRAME, null, Lens.MM_85, Aperture.F_1_4,
                null, null, Perspective.EYE_LEVEL, Shot.CLOSE_UP)));
        cameras.add(camera("Long Exposure Landscape", new CameraSettings(Body.FULL_FRAME, null, Lens.MM_24,
                Aperture.F_11, Shutter.LONG_EXPOSURE, Iso.ISO_100, null, Shot.WIDE)));
        cameras.add(camera("Phone Snapshot", new CameraSettings(Body.SMARTPHONE, null, null, null, null, null,
                Perspective.EYE_LEVEL, null)));

        for (int i = 0; i < cameras.size(); i++)
            cameras.get(i).setSortOrder(i);
        return cameras;
    }

    /**
     * A camera block from its settings; its text is the fragment for a photographic style, for display and export.
     * The prompt itself is built from the settings, see {@link CameraPromptBuilder}.
     *
     * @param id       the id, null to let the repository assign one
     * @param name     the name
     * @param settings the settings
     * @return the block
     */
    public static PromptBlock camera(String id, String name, CameraSettings settings) {
        PromptBlock block = new PromptBlock(id, PromptBlock.Type.CAMERA, name,
                CameraPromptBuilder.build(settings, true), null, false);
        block.setParams(settings.toJson());
        return block;
    }

    private static PromptBlock camera(String name, CameraSettings settings) {
        return camera(standardId(PromptBlock.Type.CAMERA, name), name, settings);
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
        return switch (type) {
            case STYLE -> new ArrayList<>(defaultStyles);
            case ATMOSPHERE -> new ArrayList<>(defaultAtmospheres);
            case LIGHT -> new ArrayList<>(defaultLights);
            case CAMERA -> new ArrayList<>(defaultCameras);
        };
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
     * Finds a block of the user in the trash.
     *
     * @param id the block id
     * @return the block, null if it is not in the trash
     */
    public PromptBlock findInTrash(String id) {
        return id == null || id.startsWith(PromptBlock.STANDARD_PREFIX) ? null : repository.findInTrash(id);
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
     * Stores a block as a new block of the user, e.g. a copy of a standard block; a taken name gets the next free
     * counter (see {@link #freeName}).
     *
     * @param block the block; its id is replaced by a new one
     * @return true if it was stored
     */
    public boolean addCopy(PromptBlock block) {
        block.setId(null);
        block.setName(freeName(block.getType(), block.getName()));
        return addCustom(block);
    }

    /**
     * Puts the blocks of the user of a type into the given order.
     *
     * @param type the block type
     * @param ids the ids of the blocks of the user in their new order
     * @return true if all of them were updated
     */
    public boolean reorderCustom(PromptBlock.Type type, List<String> ids) {
        boolean updated = true;
        for (PromptBlock block : repository.findAll(type)) {
            int index = ids.indexOf(block.getId());
            if (index < 0 || block.getSortOrder() == 1000 + index)
                continue;
            block.setSortOrder(1000 + index);
            updated &= repository.update(block);
        }
        return updated;
    }

    /**
     * Moves a block of the user to the trash; standard blocks cannot be deleted.
     *
     * @param id the block id
     * @return true if it was moved to the trash
     */
    public boolean deleteCustom(String id) {
        return id != null && !id.startsWith(PromptBlock.STANDARD_PREFIX) && repository.findById(id) != null
                && repository.setDeletedAt(id, LocalDateTime.now());
    }

    /**
     * Returns the blocks of the user in the trash, all types, the latest deletion first.
     *
     * @return the blocks
     */
    public List<PromptBlock> getDeleted() {
        return repository.findDeleted();
    }

    /**
     * Takes a block out of the trash. As the name of a block in the trash is free, a block that took it meanwhile
     * keeps it and the restored one gets the next free counter.
     *
     * @param id the block id
     * @return true if it is back in use
     */
    public boolean restore(String id) {
        PromptBlock block = repository.findDeletedById(id);
        if (block == null)
            return false;

        String name = freeName(block.getType(), block.getName());
        if (!name.equals(block.getName())) {
            block.setName(name);
            repository.update(block);
        }
        return repository.setDeletedAt(id, null);
    }

    /**
     * Removes a block of the user for good.
     *
     * @param id the block id
     * @return true if it is gone
     */
    public boolean deletePermanently(String id) {
        return id != null && !id.startsWith(PromptBlock.STANDARD_PREFIX) && repository.delete(id);
    }

    /**
     * Returns the blocks of the user of a type as plain data for a JSON file: id, type, name, text, group,
     * photographic and parameters.
     *
     * @param type the block type
     * @return one map per block, in their order
     */
    public List<Map<String, Object>> exportCustom(PromptBlock.Type type) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (PromptBlock block : repository.findAll(type)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", block.getId());
            entry.put("type", block.getType().name());
            entry.put("name", block.getName());
            entry.put("text", block.getText());
            entry.put("group", block.getGroup());
            entry.put("photographic", block.isPhotographic());
            entry.put("params", block.getParams());
            entries.add(entry);
        }
        return entries;
    }

    /**
     * Adds the blocks of a type from plain data (see {@link #exportCustom(PromptBlock.Type)}) behind the existing
     * ones. An entry of another type is skipped, an id that is taken gets a new one, a taken name the next free
     * counter.
     *
     * @param type the block type
     * @param entries the blocks as maps; {@code "promptText"} stands for the text in older exports
     * @return the number of added blocks
     */
    public int importCustom(PromptBlock.Type type, List<Map<String, Object>> entries) {
        int imported = 0;
        for (Map<String, Object> entry : entries) {
            Object entryType = entry.get("type");
            Object entryText = entry.containsKey("text") ? entry.get("text") : entry.get("promptText");
            if ((entryType != null && !type.name().equals(entryType)) || !(entry.get("name") instanceof String name)
                    || !(entryText instanceof String text))
                continue;

            /*  Older exports have no flag: styles of the user count as photographic  */
            PromptBlock block = new PromptBlock(null, type, freeName(type, name), text, (String) entry.get("group"),
                    !Boolean.FALSE.equals(entry.get("photographic")));
            block.setParams((String) entry.get("params"));
            if (entry.get("id") instanceof String id && !id.startsWith(PromptBlock.STANDARD_PREFIX)
                    && !repository.existsById(id))
                block.setId(id);
            if (addCustom(block))
                imported++;
        }
        return imported;
    }

    /**
     * Writes the blocks of the user of a type into a JSON file.
     *
     * @param type the block type
     * @param file the target file
     * @throws IOException if the file cannot be written
     */
    public void exportCustom(PromptBlock.Type type, File file) throws IOException {
        JSON.writerWithDefaultPrettyPrinter().writeValue(file, exportCustom(type));
    }

    /**
     * Adds the blocks of a type from a JSON file written by {@link #exportCustom(PromptBlock.Type, File)}.
     *
     * @param type the block type
     * @param file the source file
     * @return the number of added blocks
     * @throws IOException if the file cannot be read or is no list of blocks
     */
    public int importCustom(PromptBlock.Type type, File file) throws IOException {
        return importCustom(type, JSON.readValue(file, new TypeReference<List<Map<String, Object>>>() { }));
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
