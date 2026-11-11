package tools.moodframe.service;

import tools.moodframe.config.ApplicationConfig;
import tools.moodframe.config.ServiceFactory;
import tools.moodframe.model.StylePreset;
import tools.moodframe.repository.StylePresetRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing style presets with SQLite persistence.
 * 
 * <p>
 * This service replaces the old JSON-based StylePresetManager with a proper
 * SQLite-backed implementation using the Repository pattern. It provides
 * migration
 * from the old JSON format and offers improved performance and data integrity.
 * </p>
 * 
 * <p>
 * <strong>Migration Strategy:</strong>
 * </p>
 * <ul>
 * <li>Automatically migrates existing JSON data to SQLite on first startup</li>
 * <li>Maintains backward compatibility with export/import features</li>
 * <li>Provides batch operations for performance</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class StylePresetService implements StylePresets {

    private static final Logger logger = LoggerFactory.getLogger(StylePresetService.class);
    private static final String CUSTOM_STYLES_KEY = "style.presets.custom";
    private static final String MIGRATION_COMPLETED_KEY = "style.presets.migrated";

    private final StylePresetRepository repository;
    private final ApplicationConfig config;
    private final ObjectMapper objectMapper;
    private final List<StylePreset> defaultStyles;

    /**
     * Creates a new StylePresetService with automatic migration.
     */
    public StylePresetService() {
        this.repository = new StylePresetRepository();
        this.config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        this.objectMapper = new ObjectMapper();
        this.defaultStyles = initializeDefaultStyles();

        // Perform migration if not yet completed
        if (!isMigrationCompleted()) {
            performMigration();
        } else {
            // Clean up legacy entries if migration was already done
            cleanupLegacyConfigEntries();
        }

        // Clean up old default styles from database (they now come from code only)
        cleanupDefaultStylesFromDatabase();

        // Clean up custom styles that have same name as default styles (conflict
        // resolution)
        cleanupConflictingCustomStyles();
    }

    /**
     * Initializes the default system style presets optimized for FLUX.
     *
     * <p>
     * Categories (54 Styles Total):
     * </p>
     * <ul>
     * <li>Photography & Portrait (0-6) - 7 styles</li>
     * <li>Cinematic & Film (7-11) - 5 styles</li>
     * <li>Artistic Styles (12-21) - 10 styles (incl. Pencil Sketch, Pop Art,
     * Surrealism)</li>
     * <li>Documentary & Retro (22-23) - 2 styles</li>
     * <li>Design & Branding (24) - 1 style (Logo Design)</li>
     * <li>Infographic Styles (25-41) - 17 styles</li>
     * <li>Comic & Sequential Art (42-45) - 4 styles (Comic Strip, Graphic Novel,
     * Manga, European)</li>
     * <li>Commercial & Product (46) - 1 style</li>
     * </ul>
     */
    private List<StylePreset> initializeDefaultStyles() {
        List<StylePreset> defaults = new ArrayList<>();

        // ============================================
        // CATEGORY 1: Photography & Portrait (7 Styles)
        // ============================================
        defaults.add(new StylePreset("Portrait",
                "professional portrait photography, shallow depth of field, soft diffused lighting, bokeh background, shot on Canon EOS R5 with 85mm f/1.4 lens, natural skin texture, catchlights in eyes, cinematic color grading",
                true));

        defaults.add(new StylePreset("Photography",
                "realistic photograph, professional photography, DSLR, sharp focus, high dynamic range, natural lighting, depth of field, shot on Sony A7IV, clean sharp, 8K resolution",
                true));

        defaults.add(new StylePreset("Business Photography",
                "realistic, detailed photography, people are authentic and natural-looking with friendly and approachable expressions, wearing business casual attire, diverse ethnicities and body types, authentic business situation in a modern and dynamic setting, clean composition, soft natural lighting, open, future-oriented, modern atmosphere",
                true));

        defaults.add(new StylePreset("Editorial Fashion",
                "high-fashion editorial photography, Vogue magazine style, dramatic studio lighting, elegant pose, professional color grading, glamorous atmosphere, shot on Hasselblad X2D, no text, no logos, no watermarks",
                true));

        defaults.add(new StylePreset("Street Photography",
                "candid street photography, urban environment, natural moment captured, documentary style, available light, shot on Leica Q2, authentic atmosphere",
                true));

        defaults.add(new StylePreset("Studio Portrait",
                "professional studio portrait, three-point lighting setup, clean white backdrop, commercial photography, beauty dish lighting, perfect skin retouching, 8K ultra-detailed, no visible equipment, no light stands, no softboxes",
                true));

        defaults.add(new StylePreset("Vintage Film",
                "vintage photography, shot on Kodak Portra 400, natural film grain, organic warm colors, analog camera aesthetic, soft focus, nostalgic atmosphere, 35mm film",
                true));

        // ============================================
        // CATEGORY 2: Cinematic & Film (5 Styles)
        // ============================================
        defaults.add(new StylePreset("Cinematic",
                "cinematic lighting, film grain texture, dramatic composition, movie scene aesthetic, color graded, shot on ARRI Alexa, 2.39:1 aspect ratio, blockbuster movie still",
                true));

        defaults.add(new StylePreset("Film Noir",
                "film noir cinematography, high contrast black and white, dramatic shadows, venetian blind lighting, 1940s detective movie aesthetic, moody atmosphere, chiaroscuro lighting",
                true));

        defaults.add(new StylePreset("Blockbuster",
                "Hollywood blockbuster movie still, epic scale, dynamic action composition, volumetric lighting, IMAX quality, Michael Bay style explosions, dramatic sky, 8K cinematic",
                true));

        defaults.add(new StylePreset("Arthouse",
                "arthouse cinema aesthetic, thoughtful composition, muted color palette, European film style, natural lighting, contemplative mood, A24 films visual style",
                true));

        defaults.add(new StylePreset("Sci-Fi Cinema",
                "science fiction movie still, futuristic environment, neon lighting, cyberpunk aesthetic, Blade Runner inspired, atmospheric fog, high-tech details, cinematic wide shot",
                true));

        // ============================================
        // CATEGORY 3: Artistic Styles (10 Styles)
        // ============================================
        defaults.add(new StylePreset("Oil Painting",
                "hand-drawn oil painting by an old master, visible brush strokes, thick paint structure, masterful oil painting on canvas, thick impasto brushstrokes, rich textures, classical painting technique, layered paint, museum quality, reminiscent of Dutch Masters",
                true));

        defaults.add(new StylePreset("Watercolor",
                "delicate watercolor painting, soft color washes, paint bleeding naturally, textured watercolor paper, transparent layers, artistic brushstrokes, luminous quality",
                true));

        defaults.add(new StylePreset("Pencil Sketch",
                "hand-drawn pencil sketch, detailed pencil drawing on white paper, graphite sketch, hand-drawn illustration, fine precise linework, realistic shading, cross-hatching technique, clean white background, traditional art, masterful graphite rendering",
                true));

        defaults.add(new StylePreset("Digital Art",
                "digital art, professional digital illustration, concept art quality, clean precise lineart, vibrant saturated colors, ArtStation trending, detailed rendering",
                true));

        defaults.add(new StylePreset("Illustration",
                "symbolic, conceptual stock illustration using common metaphors, icons and symbols to illustrate the mentioned concepts, clean vector style, simple clear shapes, balanced composition",
                true));

        defaults.add(new StylePreset("Pixel Art",
                "retro pixel art, limited color palette, crisp square pixels, 16-bit game aesthetic, no anti-aliasing, clean sprite-style shapes",
                true));

        defaults.add(new StylePreset("Anime",
                "anime art style, clean sharp lineart, cell shading, vibrant colors, manga inspired, detailed expressive eyes, Studio Ghibli quality, Japanese animation aesthetic",
                true));

        defaults.add(new StylePreset("Fantasy Art",
                "epic fantasy art, magical atmosphere, ethereal lighting, mythical elements, detailed fantasy illustration, concept art for AAA game, dramatic composition",
                true));

        defaults.add(new StylePreset("Pop Art",
                "pop art style, Andy Warhol silkscreen aesthetic, bold primary colors red yellow blue, Ben-Day dots halftone pattern, high contrast graphic design, four-panel grid repetition, factory mass production feel, 1960s commercial art, Roy Lichtenstein comic book style, flat color blocks, celebrity portrait iconography",
                true));

        defaults.add(new StylePreset("Surrealism",
                "surrealist art, Salvador Dalí and René Magritte inspired, dreamlike impossible scenes, melting clocks and distorted objects, impossible physics defying gravity, photorealistic rendering of unreal concepts, paranoid-critical method aesthetic, symbolic subconscious imagery, bizarre fantastical juxtapositions, desert landscapes with elongated shadows, hyperdetailed impossible architecture",
                true));

        // ============================================
        // CATEGORY 4: Cinematic & Documentary (2 Styles)
        // ============================================
        defaults.add(new StylePreset("Documentary",
                "documentary photography, photojournalistic style, raw authentic unposed moments, available natural light only, powerful storytelling composition, National Geographic and Magnum Photos quality, candid human emotion, gritty realistic atmosphere, decisive moment captured, environmental context, social documentary aesthetic, Pulitzer Prize winning photography style",
                true));

        defaults.add(new StylePreset("Retro 80s",
                "1980s retro aesthetic, synthwave and outrun style, neon pink magenta and cyan colors, VHS scan lines and CRT bloom effects, chrome metallic text, sunset gradient horizon pink orange purple, perspective grid floor extending to infinity, retro-futuristic Miami Vice aesthetic, vaporwave inspired, palm tree silhouettes, nostalgic 80s arcade vibes, Tron inspired glow effects",
                true));

        // ============================================
        // CATEGORY 5: Design & Branding (1 Style)
        // ============================================
        defaults.add(new StylePreset("Logo Design",
                "clean, flat, vector-style logo, professional logo design, minimalist flat vector art, clean geometric shapes, infinitely scalable graphics, single color versatility, corporate branding aesthetic, simple memorable iconic symbol, clever negative space usage, transparent background ready, balanced symmetrical composition, timeless modern design, works at any size from favicon to billboard",
                true));

        // ============================================
        // CATEGORY 6: Infographic Styles (17 Styles) - Sort 25-41
        // ============================================
        defaults.add(new StylePreset("Infographic Modern",
                "modern flat vector style infographic, clean minimalist design, professional data visualization, clear hierarchy, sans-serif typography, soft neutral background, ample whitespace, corporate aesthetic",
                true));

        defaults.add(new StylePreset("Infographic Timeline",
                "horizontal timeline infographic, linear progression from left to right, connected milestone nodes, flat design 2.0 style, flowing ribbon path connecting events, clear date markers, professional layout",
                true));

        defaults.add(new StylePreset("Infographic Educational",
                "educational tutorial infographic, hand-drawn illustration aesthetic with thick outline strokes, step-by-step numbered process, friendly approachable style, clear icons for each step, vertical flow layout",
                true));

        defaults.add(new StylePreset("Infographic Kids",
                "child-friendly educational infographic, bright cheerful colors, rounded friendly shapes, cartoon illustrations, large readable text, fun engaging design, playful aesthetic, friendly characters",
                true));

        defaults.add(new StylePreset("Infographic Minimalist",
                "ultra-minimalist infographic design, maximum whitespace, monochromatic color scheme, simple geometric icons, Helvetica typography, clean data visualization, no clutter, Swiss design principles",
                true));

        defaults.add(new StylePreset("Infographic Sketch",
                "hand-drawn sketch style infographic, pencil and pen illustration, organic flowing lines, notebook paper texture, authentic hand-lettered text, crosshatching shading, whiteboard presentation feel, can be monochromatic or with color accents, casual professional aesthetic",
                true));

        defaults.add(new StylePreset("Infographic Corporate",
                "professional corporate dashboard infographic, business intelligence visualization, metallic textures, glass screen elements, KPI metrics display, clean card-based layout, executive presentation quality",
                true));

        defaults.add(new StylePreset("Infographic Data",
                "data-driven visualization infographic, clean charts and graphs, pie charts with clear segments, bar graphs, line trends, color-coded legend, professional statistical presentation, National Geographic style",
                true));

        defaults.add(new StylePreset("Infographic Flowchart",
                "professional flowchart diagram, decision tree layout, connected process boxes with arrows, clean geometric shapes, color-coded pathways, clear start and end nodes, technical documentation style",
                true));

        defaults.add(new StylePreset("Infographic Mind Map",
                "radial mind map visualization, central concept with branching ideas, organic neuron-like curved connections, color-coded category branches, hierarchical structure, brainstorming aesthetic",
                true));

        defaults.add(new StylePreset("Infographic Comparison",
                "split-screen comparison infographic, side-by-side layout, symmetrical visual divider, contrasting color schemes for each side, clear pros and cons icons, balanced composition",
                true));

        defaults.add(new StylePreset("Infographic Neon",
                "neon sign style infographic, dark brick wall background, glowing glass tube typography, cyberpunk aesthetic, Las Vegas signage style, vibrant neon colors against black, retro-futuristic",
                true));

        defaults.add(new StylePreset("Infographic Paper Cutout",
                "paper cutout craft style infographic, layered construction paper aesthetic, textured paper materials, scissors-cut edges, collage technique, visible paper layers with shadows, colorful crafted elements, handmade tactile feel",
                true));

        defaults.add(new StylePreset("Infographic Isometric",
                "isometric 3D infographic, technical axonometric projection, clean geometric buildings and objects, consistent 30-degree angles, detailed miniature world, architectural precision, colorful isometric icons, gaming aesthetic",
                true));

        defaults.add(new StylePreset("Infographic Dark Mode",
                "dark mode UI infographic, futuristic interface design, glowing elements on dark background, tech dashboard aesthetic, neon accent colors, matrix-style data streams, developer focused, high contrast readability",
                true));

        defaults.add(new StylePreset("Infographic Technical",
                "modern technical architecture infographic, layered system diagram with rounded component cards, 45-degree isometric perspective, floating platform blocks for each tier, thin glowing connector lines with arrowheads showing data flow, color-coded arrows for requests, events and data, small clean icon on every component, clear short labels, glassmorphism cards on a deep charcoal background, cyan and magenta accent colors, monospaced typography for labels, generous spacing, precise alignment, professional software architecture documentation quality",
                true));

        defaults.add(new StylePreset("Infographic Magazine",
                "magazine spread infographic, Wired or Vox editorial style, massive bold typography, asymmetrical grid layout, high-impact journalism aesthetic, vibrant color blocks, two-page comparison format, grainy texture overlay, eye-catching headlines",
                true));

        // ============================================
        // CATEGORY 7: Comic & Sequential Art (4 Styles)
        // ============================================
        defaults.add(new StylePreset("Comic Strip",
                "4-panel comic strip layout, sequential storytelling, newspaper comic style, clean lineart, speech bubbles, expressive characters, Calvin and Hobbes aesthetic, white gutters between panels, humorous tone",
                true));

        defaults.add(new StylePreset("Graphic Novel",
                "graphic novel art style, mature cinematic illustration, high contrast black and white inks, Frank Miller Sin City aesthetic, dramatic shadows, noir atmosphere, detailed crosshatching, Watchmen inspired storytelling",
                true));

        defaults.add(new StylePreset("Manga Panel",
                "manga panel layout, Japanese comic aesthetic, dynamic action lines, screentone shading, expressive emotions, dramatic angles, black and white high contrast, shounen manga quality, speed lines",
                true));

        defaults.add(new StylePreset("European Comic",
                "Franco-Belgian bande dessinée style, ligne claire technique, clean precise outlines, flat vibrant colors, Tintin and Asterix inspired, detailed backgrounds, European comic album quality",
                true));

        // ============================================
        // CATEGORY 10: Commercial & Product (1 Style)
        // ============================================
        defaults.add(new StylePreset("Product Photography",
                "professional product photography, clean white or gradient background, soft studio lighting, perfect reflections, commercial advertising quality, sharp focus on product details, minimalist composition, e-commerce ready, high-end catalog style, 8K ultra-detailed",
                true));

        // Set sort order for defaults
        for (int i = 0; i < defaults.size(); i++) {
            defaults.get(i).setSortOrder(i);
            defaults.get(i).setId(UUID.randomUUID().toString());
        }

        return defaults;
    }

    /**
     * Checks if migration from JSON to SQLite has been completed.
     */
    private boolean isMigrationCompleted() {
        return Boolean.parseBoolean(config.getProperty(MIGRATION_COMPLETED_KEY, "false"));
    }

    /**
     * Performs migration from JSON-based storage to SQLite.
     */
    private void performMigration() {

        try {
            // Load existing JSON data if present
            String json = config.getProperty(CUSTOM_STYLES_KEY, null);
            if (json != null && !json.isEmpty() && !"[]".equals(json)) {
                List<StylePreset> customStyles = objectMapper.readValue(json,
                        new TypeReference<List<StylePreset>>() {
                        });

                // Migrate custom styles to SQLite
                if (!customStyles.isEmpty()) {
                    int migrated = repository.batchInsert(customStyles);
                    if (config.getLoggingEnabled()) {
                        logger.info("Migrated {} custom styles from JSON to SQLite", migrated);
                    }
                }
            }

            // Mark migration as completed
            config.setProperty(MIGRATION_COMPLETED_KEY, "true");

            // Clean up legacy entries immediately after migration
            cleanupLegacyConfigEntries();

        } catch (Exception e) {
            logger.error("Migration failed, will retry on next startup", e);
        }
    }

    /**
     * Removes all default styles from the database.
     * Default styles now come directly from code, only custom styles are stored in
     * DB.
     */
    private void cleanupDefaultStylesFromDatabase() {
        List<StylePreset> existingDefaults = repository.findAllDefault();
        if (!existingDefaults.isEmpty()) {
            int deleted = repository.deleteAllDefault();
            if (config.getLoggingEnabled()) {
                logger.info("Cleaned up {} default styles from database (now served from code)", deleted);
            }
        }
    }

    /**
     * Removes custom styles from the database that have the same name as default
     * styles.
     * This resolves conflicts when a default style is added with the same name as
     * an existing custom style.
     */
    private void cleanupConflictingCustomStyles() {
        int cleanedUp = 0;
        for (StylePreset defaultStyle : defaultStyles) {
            StylePreset conflicting = repository.findCustomByName(defaultStyle.getName());
            if (conflicting != null) {
                repository.delete(conflicting.getId());
                cleanedUp++;
                if (config.getLoggingEnabled()) {
                    logger.info("Removed conflicting custom style '{}' (now a default style)",
                            defaultStyle.getName());
                }
            }
        }
        if (cleanedUp > 0 && config.getLoggingEnabled()) {
            logger.info("Cleaned up {} conflicting custom styles", cleanedUp);
        }
    }

    /**
     * Gets all available style presets (default from code + custom from DB).
     */
    @Override
    public List<StylePreset> getAllStyles() {
        List<StylePreset> allStyles = new ArrayList<>(defaultStyles);
        allStyles.addAll(repository.findAllCustom());
        return allStyles;
    }

    /**
     * Gets only the default system style presets (from code, not DB).
     */
    @Override
    public List<StylePreset> getDefaultStyles() {
        return new ArrayList<>(defaultStyles);
    }

    /**
     * Gets only the custom user-created style presets (from DB).
     */
    @Override
    public List<StylePreset> getCustomStyles() {
        return repository.findAllCustom();
    }

    /**
     * Gets a style preset by name (checks defaults first, then custom).
     */
    @Override
    public StylePreset getStyleByName(String name) {
        // First check defaults (from code)
        for (StylePreset style : defaultStyles) {
            if (style.getName().equals(name)) {
                return style;
            }
        }
        // Then check custom styles (from DB)
        return repository.findByName(name);
    }

    /**
     * Gets multiple styles by name for combination.
     */
    @Override
    public List<StylePreset> getStyles(Collection<String> names) {
        return names.stream()
                .map(repository::findByName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Gets the prompt text for a single style by name.
     */
    @Override
    public String getStylePromptText(String styleName) {
        if (styleName == null || styleName.isEmpty()) {
            return "";
        }

        StylePreset style = repository.findByName(styleName);
        return style != null ? style.getPromptText() : "";
    }

    /**
     * Combines multiple style prompts into a single prompt text.
     */
    @Override
    public String combineStylePrompts(Collection<String> styleNames) {
        if (styleNames == null || styleNames.isEmpty()) {
            return "";
        }

        return getStyles(styleNames).stream()
                .map(StylePreset::getPromptText)
                .filter(text -> text != null && !text.isEmpty())
                .collect(Collectors.joining(", "));
    }

    /**
     * Adds a new custom style preset.
     */
    @Override
    public boolean addCustomStyle(StylePreset style) {
        // Check for duplicate names (in defaults or custom)
        if (styleExists(style.getName())) {
            logger.warn("Cannot add style '{}' - name already exists", style.getName());
            return false;
        }

        // Ensure it's marked as custom
        style.setDefault(false);

        // Auto-assign sort order if not set (next available position)
        if (style.getSortOrder() == 0 || style.getSortOrder() == 999) {
            style.setSortOrder(getNextCustomSortOrder());
        }

        boolean success = repository.save(style);

        return success;
    }

    /**
     * Updates an existing custom style preset.
     */
    @Override
    public boolean updateCustomStyle(String oldName, StylePreset updatedStyle) {
        // Cannot update default styles
        if (isDefaultStyle(oldName)) {
            logger.warn("Cannot update default style: {}", oldName);
            return false;
        }

        StylePreset existing = repository.findByName(oldName);
        if (existing == null) {
            logger.warn("Cannot update style '{}' - not found", oldName);
            return false;
        }

        // Check for name conflicts (if name changed)
        if (!oldName.equals(updatedStyle.getName()) && styleExists(updatedStyle.getName())) {
            logger.warn("Cannot update style '{}' - new name '{}' already exists",
                    oldName, updatedStyle.getName());
            return false;
        }

        // Keep the same ID and ensure it's marked as custom
        updatedStyle.setId(existing.getId());
        updatedStyle.setDefault(false);

        boolean success = repository.update(updatedStyle);

        return success;
    }

    /**
     * Deletes a custom style preset.
     */
    @Override
    public boolean deleteCustomStyle(String name) {
        // Find the style in custom styles DB (not in defaults)
        StylePreset existing = repository.findCustomByName(name);
        if (existing == null) {
            // Style not found in custom DB - might be a default style or doesn't exist
            if (isDefaultStyle(name)) {
                logger.warn("Cannot delete default style: {}", name);
            } else {
                logger.warn("Cannot delete style '{}' - not found in custom styles", name);
            }
            return false;
        }

        boolean success = repository.delete(existing.getId());
        if (success && config.getLoggingEnabled()) {
            logger.info("Deleted custom style: {}", name);
        }

        return success;
    }

    /**
     * Resets custom styles to defaults only.
     * 
     * @return number of deleted custom styles
     */
    @Override
    public int resetToDefaults() {
        int deleted = repository.deleteAllCustom();
        if (config.getLoggingEnabled()) {
            logger.info("Reset to defaults - deleted {} custom styles", deleted);
        }
        return deleted;
    }

    /**
     * Checks if a style name already exists (in defaults or custom).
     */
    @Override
    public boolean styleExists(String name) {
        // Check defaults first
        for (StylePreset style : defaultStyles) {
            if (style.getName().equals(name)) {
                return true;
            }
        }
        // Then check custom styles in DB
        return repository.existsCustomByName(name);
    }

    /**
     * Checks if a style is a default system style.
     */
    @Override
    public boolean isDefaultStyle(String name) {
        for (StylePreset style : defaultStyles) {
            if (style.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exports custom style presets to a JSON file.
     */
    @Override
    public boolean exportStyles(File file) {
        try {
            List<StylePreset> customStyles = repository.findAllCustom();
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(customStyles);

            Files.write(file.toPath(), json.getBytes());

            if (config.getLoggingEnabled()) {
                logger.info("Exported {} custom styles to: {}", customStyles.size(), file.getAbsolutePath());
            }
            return true;

        } catch (Exception e) {
            logger.error("Failed to export styles to: " + file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Imports custom style presets from a JSON file.
     */
    @Override
    public int importStyles(File file, boolean mergeWithExisting) {
        try {
            if (!file.exists()) {
                logger.warn("Import file does not exist: {}", file.getAbsolutePath());
                return -1;
            }

            String json = new String(Files.readAllBytes(file.toPath()));
            List<StylePreset> importedStyles = objectMapper.readValue(json,
                    new TypeReference<List<StylePreset>>() {
                    });

            if (!mergeWithExisting) {
                repository.deleteAllCustom();
            }

            int importedCount = 0;
            for (StylePreset style : importedStyles) {
                // Ensure it's marked as custom (not default)
                style.setDefault(false);

                // Check for name conflicts
                String originalName = style.getName();
                String finalName = originalName;
                int counter = 1;

                while (repository.existsByName(finalName)) {
                    finalName = originalName + " (" + counter + ")";
                    counter++;
                }

                if (!finalName.equals(originalName)) {
                    style.setName(finalName);
                }

                if (repository.save(style)) {
                    importedCount++;
                }
            }

            if (config.getLoggingEnabled()) {
                logger.info("Imported {} custom styles from: {}", importedCount, file.getAbsolutePath());
            }
            return importedCount;

        } catch (Exception e) {
            logger.error("Failed to import styles from: " + file.getAbsolutePath(), e);
            return -1;
        }
    }

    /**
     * Exports all styles (default + custom) to a JSON file for backup.
     */
    @Override
    public boolean exportAllStyles(File file) {
        try {
            Map<String, Object> export = new HashMap<>();
            export.put("defaultStyles", repository.findAllDefault());
            export.put("customStyles", repository.findAllCustom());
            export.put("exportDate", new Date().toString());
            export.put("totalCount", repository.findAll().size());

            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(export);

            Files.write(file.toPath(), json.getBytes());

            if (config.getLoggingEnabled()) {
                logger.info("Exported all {} styles to: {}",
                        export.get("totalCount"), file.getAbsolutePath());
            }
            return true;

        } catch (Exception e) {
            logger.error("Failed to export all styles to: " + file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Gets the next available sort order for custom styles.
     * Custom styles start at 1000 to keep them after default styles.
     */
    private int getNextCustomSortOrder() {
        List<StylePreset> customStyles = repository.findAllCustom();
        if (customStyles.isEmpty()) {
            return 1000; // First custom style
        }

        // Find highest sort order and add 1
        int maxSortOrder = customStyles.stream()
                .mapToInt(StylePreset::getSortOrder)
                .max()
                .orElse(999);

        return Math.max(maxSortOrder + 1, 1000);
    }

    /**
     * Cleans up legacy configuration entries that are no longer needed.
     * This method removes the old JSON-based style storage keys from the config
     * file.
     */
    private void cleanupLegacyConfigEntries() {
        // Remove the legacy JSON custom styles key if it exists
        if (config.getProperty(CUSTOM_STYLES_KEY) != null) {
            config.removeProperty(CUSTOM_STYLES_KEY);
        }

        // Keep the migration flag but don't recreate it if removed
        // This prevents re-migration attempts if the user manually cleans config
    }
}