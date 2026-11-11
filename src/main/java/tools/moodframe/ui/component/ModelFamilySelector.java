package tools.moodframe.ui.component;

import tools.moodframe.config.ApplicationConfig;
import tools.moodframe.model.ModelFamilies;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;

import java.util.List;

/**
 * Lets the user choose a model as family and resolution, while a hidden choice keeps the full variant name
 * (for example "Nano Banana 2 2K ...") that the main window works with. The family and the resolution choose
 * the variant, and a variant set elsewhere (attachment filter, saved selection, brainstorm) is shown as family
 * and resolution. The resolution of each family is remembered in the configuration.
 *
 * @since 1.9.1
 */
public class ModelFamilySelector {

    private final ChoiceBox<String> variants;
    private final ChoiceBox<String> families;
    private final ChoiceBox<String> resolutions;
    private final Node resolutionRow;
    private final ApplicationConfig config;

    private boolean syncing;
    private boolean rebuildPending;

    /**
     * Connects the choices.
     *
     * @param variants      the hidden choice of variants
     * @param families      the visible choice of families
     * @param resolutions   the visible choice of resolutions
     * @param resolutionRow shown only if there is more than one resolution
     * @param config        remembers the resolution of each family (may be null)
     */
    public ModelFamilySelector(ChoiceBox<String> variants, ChoiceBox<String> families, ChoiceBox<String> resolutions,
            Node resolutionRow, ApplicationConfig config) {
        this.variants = variants;
        this.families = families;
        this.resolutions = resolutions;
        this.resolutionRow = resolutionRow;
        this.config = config;
        bind();
    }

    /**
     * Remembers the resolution of the family of a variant, as if it was chosen.
     *
     * @param variant    the variant
     * @param resolution the resolution
     */
    public void remember(String variant, String resolution) {
        if (config != null)
            config.setProperty(ModelFamilies.resolutionKey(ModelFamilies.familyOf(variant)), resolution);
    }

    /** Keeps the three choices in step: a new variant list rebuilds the families, every choice updates the others. */
    private void bind() {
        variants.getItems().addListener((ListChangeListener<String>) (change) -> {
            if (!rebuildPending) {
                rebuildPending = true;
                Platform.runLater(this::rebuildFamilyChoices);
            }
        });
        variants.valueProperty().addListener((obs, old, item) -> showVariant(item));
        families.valueProperty().addListener((obs, old, family) -> {
            if (!syncing && family != null)
                onFamilyChosen(family);
        });
        resolutions.valueProperty().addListener((obs, old, resolution) -> {
            if (!syncing && resolution != null)
                onResolutionChosen(resolution);
        });
        rebuildFamilyChoices();
    }

    /** The dropdown items (variants) of a family that are available now. */
    private List<String> variantsOf(String family) {
        return ModelFamilies.variantsOf(variants.getItems(), family);
    }

    private void rebuildFamilyChoices() {
        rebuildPending = false;
        List<String> familyNames = variants.getItems().stream().map(ModelFamilies::familyOf).distinct().toList();
        syncing = true;
        try {
            families.getItems().setAll(familyNames);
        } finally {
            syncing = false;
        }
        showVariant(variants.getValue());
    }

    /** Shows the family and the resolution of the variant in the dropdown item. */
    private void showVariant(String item) {
        if (item == null)
            return;

        String family = ModelFamilies.familyOf(item);
        List<String> choices;
        String resolution;
        List<String> free = ModelFamilies.freeResolutions(item);
        if (free != null) {
            choices = free;
            resolution = config != null ? config.getProperty(ModelFamilies.resolutionKey(family), choices.get(0))
                    : choices.get(0);
            if (!choices.contains(resolution))
                resolution = choices.get(0);
        } else {
            choices = ModelFamilies.resolutionsOf(variants.getItems(), item);
            resolution = ModelFamilies.resolutionOf(item);
        }

        syncing = true;
        try {
            families.setValue(family);
            resolutions.getItems().setAll(choices);
            resolutions.setValue(resolution != null && choices.contains(resolution) ? resolution : null);
            boolean show = choices.size() > 1;
            resolutionRow.setVisible(show);
            resolutionRow.setManaged(show);
        } finally {
            syncing = false;
        }
    }

    /** Chooses the variant of a family: the remembered resolution, else the shown one, else 1K, else the first. */
    private void onFamilyChosen(String family) {
        String remembered = config != null ? config.getProperty(ModelFamilies.resolutionKey(family)) : null;
        String item = ModelFamilies.variantFor(variants.getItems(), family, remembered, resolutions.getValue());
        if (item == null)
            return;

        if (item.equals(variants.getValue()))
            showVariant(item);
        else
            variants.setValue(item);
    }

    /** Remembers the resolution of the family and switches to the variant with it. */
    private void onResolutionChosen(String resolution) {
        String family = families.getValue();
        if (family == null)
            return;
        if (config != null)
            config.setProperty(ModelFamilies.resolutionKey(family), resolution);

        String item = ModelFamilies.variantFor(variants.getItems(), family, resolution, null);
        if (item != null && resolution.equals(ModelFamilies.resolutionOf(item))
                && !item.equals(variants.getValue()))
            variants.setValue(item);
    }
}
