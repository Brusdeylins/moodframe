package art.moodframe.service;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.model.ColorStyle;
import art.moodframe.repository.ColorStyleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Service for managing color styles with SQLite persistence.
 *
 * <p>
 * This service provides 17 default color palettes that are hardcoded and
 * cannot be modified. Custom color styles are stored in SQLite and can be
 * added, edited, and deleted by users.
 * </p>
 *
 * <p>
 * <strong>Default Color Palettes (17 styles):</strong>
 * </p>
 *
 * <p>
 * <em>Cinematic & Film:</em>
 * </p>
 * <ul>
 * <li>Cinematic Teal-Orange - Hollywood film look (complementary)</li>
 * <li>Noir Classic - Film noir, mystery, dramatic contrast</li>
 * </ul>
 *
 * <p>
 * <em>Retro & Synthwave:</em>
 * </p>
 * <ul>
 * <li>Synthwave Sunset - 80s retro, neon gradients</li>
 * <li>Vaporwave Dreams - Pastel neon, retrofuturism</li>
 * <li>Cyberpunk 2077 - High-tech dystopia, neon contrast</li>
 * </ul>
 *
 * <p>
 * <em>Nature & Seasons:</em>
 * </p>
 * <ul>
 * <li>Golden Hour - Warm sunset tones for portraits and landscapes</li>
 * <li>Ocean Breeze - Cool coastal blues for serene scenes</li>
 * <li>Forest Dusk - Earthy green tones for nature photography</li>
 * <li>Autumn Leaves - Rich fall colors for warm seasonal imagery</li>
 * <li>Arctic Ice - Cold whites and blues for winter/clean themes</li>
 * <li>Coral Reef - Tropical colors for vibrant underwater/summer scenes</li>
 * </ul>
 *
 * <p>
 * <em>Artistic & Mood:</em>
 * </p>
 * <ul>
 * <li>Midnight Purple - Deep purples for mysterious/elegant themes</li>
 * <li>Pastel Serenity - Soft pastels for calm, feminine aesthetics</li>
 * <li>Vintage Film - Muted nostalgic tones for retro aesthetics</li>
 * <li>Bold Retro - 70s vintage, split-complementary</li>
 * </ul>
 *
 * <p>
 * <em>Professional:</em>
 * </p>
 * <ul>
 * <li>Monochrome Pro - Professional grays for business/minimal design</li>
 * <li>Neon Nights - Vibrant cyberpunk colors for modern/tech themes</li>
 * </ul>
 *
 * @since 1.3.0
 */
public class ColorStyleService implements ColorStyles {

    private static final Logger logger = LoggerFactory.getLogger(ColorStyleService.class);

    private final ColorStyleRepository repository;
    private final ApplicationConfig config;
    private final ObjectMapper objectMapper;
    private final List<ColorStyle> defaultStyles;

    /**
     * Creates a new ColorStyleService.
     */
    public ColorStyleService() {
        this.repository = new ColorStyleRepository();
        this.config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        this.objectMapper = new ObjectMapper();
        this.defaultStyles = initializeDefaultStyles();
    }

    /**
     * Initializes the 17 default color palettes.
     *
     * <p>
     * These color palettes are designed to enhance AI-generated images
     * by providing consistent color guidance to the model.
     * </p>
     */
    private List<ColorStyle> initializeDefaultStyles() {
        List<ColorStyle> defaults = new ArrayList<>();

        // === CINEMATIC & FILM LOOKS ===

        // 1. Cinematic Teal-Orange - Hollywood film look (complementary)
        defaults.add(new ColorStyle("Cinematic Teal-Orange",
                List.of("#FF7F50", "#FFA62B", "#101820", "#00BCD4", "#008BA3"), true));

        // 2. Noir Classic - Film noir, mystery, dramatic contrast
        defaults.add(new ColorStyle("Noir Classic",
                List.of("#0D0D0D", "#1A1A2E", "#16213E", "#E94560", "#FFFFFF"), true));

        // === RETRO & SYNTHWAVE ===

        // 3. Synthwave Sunset - 80s retro, neon gradients
        defaults.add(new ColorStyle("Synthwave Sunset",
                List.of("#FFD319", "#FF901F", "#FF2975", "#F222FF", "#8C1EFF"), true));

        // 4. Vaporwave Dreams - Pastel neon, retrofuturism
        defaults.add(new ColorStyle("Vaporwave Dreams",
                List.of("#A653F5", "#8F8CF2", "#65B8BF", "#F96CFF", "#FA92FB"), true));

        // 5. Cyberpunk 2077 - High-tech dystopia, neon contrast
        defaults.add(new ColorStyle("Cyberpunk 2077",
                List.of("#F9C54E", "#3A0A4D", "#FF5C8A", "#A42DB4", "#090720"), true));

        // === NATURE & SEASONS ===

        // 6. Golden Hour - Warm sunset tones for portraits and landscapes
        defaults.add(new ColorStyle("Golden Hour",
                List.of("#FFB347", "#FF9F1C", "#E85D04", "#D00000", "#370617"), true));

        // 7. Ocean Breeze - Cool coastal blues for serene scenes
        defaults.add(new ColorStyle("Ocean Breeze",
                List.of("#48BDFF", "#0096C7", "#0077B6", "#023E8A", "#03045E"), true));

        // 8. Forest Dusk - Earthy green tones for nature photography
        defaults.add(new ColorStyle("Forest Dusk",
                List.of("#2D6A4F", "#40916C", "#74C69D", "#B7E4C7", "#D8F3DC"), true));

        // 9. Autumn Leaves - Rich fall colors for warm seasonal imagery
        defaults.add(new ColorStyle("Autumn Leaves",
                List.of("#9B2226", "#AE2012", "#BB3E03", "#CA6702", "#EE9B00"), true));

        // 10. Arctic Ice - Cold whites and blues for winter/clean themes
        defaults.add(new ColorStyle("Arctic Ice",
                List.of("#CAF0F8", "#ADE8F4", "#90E0EF", "#48CAE4", "#00B4D8"), true));

        // 11. Coral Reef - Tropical colors for vibrant underwater/summer scenes
        defaults.add(new ColorStyle("Coral Reef",
                List.of("#FF6B6B", "#F8A978", "#FFC75F", "#A8E6CF", "#88D8B0"), true));

        // === ARTISTIC & MOOD ===

        // 12. Midnight Purple - Deep purples for mysterious/elegant themes
        defaults.add(new ColorStyle("Midnight Purple",
                List.of("#10002B", "#240046", "#3C096C", "#5A189A", "#7B2CBF"), true));

        // 13. Pastel Serenity - Soft pastels for calm, feminine aesthetics
        defaults.add(new ColorStyle("Pastel Serenity",
                List.of("#FFD1DC", "#E6E6FA", "#B5EAD7", "#FFDAC1", "#C7CEEA"), true));

        // 14. Vintage Film - Muted nostalgic tones for retro aesthetics
        defaults.add(new ColorStyle("Vintage Film",
                List.of("#D4A373", "#CCD5AE", "#E9EDC9", "#FEFAE0", "#FAEDCD"), true));

        // 15. Bold Retro - 70s vintage, split-complementary
        defaults.add(new ColorStyle("Bold Retro",
                List.of("#003366", "#FF6600", "#FFD700", "#8B0000", "#006400"), true));

        // === PROFESSIONAL ===

        // 16. Monochrome Pro - Professional grays for business/minimal design
        defaults.add(new ColorStyle("Monochrome Pro",
                List.of("#212529", "#495057", "#6C757D", "#ADB5BD", "#DEE2E6"), true));

        // 17. Neon Nights - Vibrant cyberpunk colors for modern/tech themes
        defaults.add(new ColorStyle("Neon Nights",
                List.of("#FF00FF", "#00FFFF", "#FF006E", "#8338EC", "#3A0CA3"), true));

        // Set sort order for defaults
        for (int i = 0; i < defaults.size(); i++) {
            defaults.get(i).setSortOrder(i);
            defaults.get(i).setId(UUID.randomUUID().toString());
        }

        return defaults;
    }

    /**
     * Gets all available color styles (default from code + custom from DB).
     */
    @Override
    public List<ColorStyle> getAllStyles() {
        List<ColorStyle> allStyles = new ArrayList<>(defaultStyles);
        allStyles.addAll(repository.findAllCustom());
        return allStyles;
    }

    /**
     * Gets only the default system color styles (from code, not DB).
     */
    @Override
    public List<ColorStyle> getDefaultStyles() {
        return new ArrayList<>(defaultStyles);
    }

    /**
     * Gets only the custom user-created color styles (from DB).
     */
    @Override
    public List<ColorStyle> getCustomStyles() {
        return repository.findAllCustom();
    }

    /**
     * Gets a color style by name (checks defaults first, then custom).
     */
    @Override
    public ColorStyle getStyleByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // First check defaults (from code)
        for (ColorStyle style : defaultStyles) {
            if (style.getName().equals(name)) {
                return style;
            }
        }

        // Then check custom styles (from DB)
        return repository.findByName(name);
    }

    /**
     * Gets the formatted color prompt text for a style.
     *
     * <p>
     * Format: "Color 1 = #XXXXXX, Color 2 = #YYYYYY, ..."
     * </p>
     */
    @Override
    public String getColorPromptText(String styleName) {
        if (styleName == null || styleName.isEmpty() || "None".equals(styleName)) {
            return "";
        }

        ColorStyle style = getStyleByName(styleName);
        return style != null ? style.toPromptString() : "";
    }

    /**
     * Adds a new custom color style.
     */
    @Override
    public boolean addCustomStyle(ColorStyle style) {
        // Check for duplicate names (in defaults or custom)
        if (styleExists(style.getName())) {
            logger.warn("Cannot add color style '{}' - name already exists", style.getName());
            return false;
        }

        // Ensure it's marked as custom
        style.setDefault(false);

        // Auto-assign sort order if not set
        if (style.getSortOrder() == 0 || style.getSortOrder() == 999) {
            style.setSortOrder(getNextCustomSortOrder());
        }

        boolean success = repository.save(style);

        if (success && config != null && config.getLoggingEnabled()) {
            logger.info("Added custom color style: {}", style.getName());
        }

        return success;
    }

    /**
     * Updates an existing custom color style.
     */
    @Override
    public boolean updateCustomStyle(String oldName, ColorStyle updatedStyle) {
        // Cannot update default styles
        if (isDefaultStyle(oldName)) {
            logger.warn("Cannot update default color style: {}", oldName);
            return false;
        }

        ColorStyle existing = repository.findByName(oldName);
        if (existing == null) {
            logger.warn("Cannot update color style '{}' - not found", oldName);
            return false;
        }

        // Check for name conflicts (if renaming)
        if (!oldName.equals(updatedStyle.getName()) && styleExists(updatedStyle.getName())) {
            logger.warn("Cannot rename color style to '{}' - name already exists", updatedStyle.getName());
            return false;
        }

        // Preserve ID and metadata
        updatedStyle.setId(existing.getId());
        updatedStyle.setDefault(false);
        updatedStyle.setSortOrder(existing.getSortOrder());

        boolean success = repository.update(updatedStyle);

        if (success && config != null && config.getLoggingEnabled()) {
            logger.info("Updated custom color style: {} -> {}", oldName, updatedStyle.getName());
        }

        return success;
    }

    /**
     * Deletes a custom color style.
     */
    @Override
    public boolean deleteCustomStyle(String name) {
        // Cannot delete default styles
        if (isDefaultStyle(name)) {
            logger.warn("Cannot delete default color style: {}", name);
            return false;
        }

        ColorStyle existing = repository.findByName(name);
        if (existing == null) {
            logger.warn("Cannot delete color style '{}' - not found", name);
            return false;
        }

        boolean success = repository.delete(existing.getId());

        if (success && config != null && config.getLoggingEnabled()) {
            logger.info("Deleted custom color style: {}", name);
        }

        return success;
    }

    /**
     * Resets all styles to defaults (deletes all custom styles).
     */
    @Override
    public int resetToDefaults() {
        int deleted = repository.deleteAllCustom();

        if (config != null && config.getLoggingEnabled()) {
            logger.info("Reset color styles to defaults, deleted {} custom styles", deleted);
        }

        return deleted;
    }

    /**
     * Checks if a style exists (in defaults or custom).
     */
    @Override
    public boolean styleExists(String name) {
        // Check defaults first
        for (ColorStyle style : defaultStyles) {
            if (style.getName().equals(name)) {
                return true;
            }
        }

        // Check custom styles
        return repository.existsByName(name);
    }

    /**
     * Checks if a style is a default (system) style.
     */
    @Override
    public boolean isDefaultStyle(String name) {
        for (ColorStyle style : defaultStyles) {
            if (style.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exports custom styles to a JSON file.
     */
    @Override
    public boolean exportStyles(File file) {
        try {
            List<ColorStyle> customStyles = getCustomStyles();
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(customStyles);
            Files.writeString(file.toPath(), json);

            if (config != null && config.getLoggingEnabled()) {
                logger.info("Exported {} custom color styles to {}", customStyles.size(), file.getAbsolutePath());
            }

            return true;
        } catch (Exception e) {
            logger.error("Failed to export color styles to " + file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Imports styles from a JSON file.
     */
    @Override
    public int importStyles(File file, boolean mergeWithExisting) {
        try {
            String json = Files.readString(file.toPath());
            List<ColorStyle> importedStyles = objectMapper.readValue(json,
                    new TypeReference<List<ColorStyle>>() {
                    });

            if (!mergeWithExisting) {
                repository.deleteAllCustom();
            }

            int imported = 0;
            for (ColorStyle style : importedStyles) {
                style.setDefault(false);

                if (!styleExists(style.getName())) {
                    style.setId(UUID.randomUUID().toString());
                    style.setSortOrder(getNextCustomSortOrder());
                    if (repository.save(style)) {
                        imported++;
                    }
                }
            }

            if (config != null && config.getLoggingEnabled()) {
                logger.info("Imported {} color styles from {}", imported, file.getAbsolutePath());
            }

            return imported;

        } catch (Exception e) {
            logger.error("Failed to import color styles from " + file.getAbsolutePath(), e);
            return 0;
        }
    }

    /**
     * Gets the next available sort order for custom styles.
     */
    private int getNextCustomSortOrder() {
        List<ColorStyle> customStyles = getCustomStyles();
        if (customStyles.isEmpty()) {
            return 100; // Start custom styles at 100 (after defaults 0-9)
        }

        int maxOrder = customStyles.stream()
                .mapToInt(ColorStyle::getSortOrder)
                .max()
                .orElse(99);

        return maxOrder + 1;
    }
}
