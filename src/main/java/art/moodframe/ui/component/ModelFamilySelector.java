package art.moodframe.ui.component;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.ModelFamilies;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.ChoiceBox;

import java.util.List;

/**
 * Lets the user choose a model as family, while a hidden choice keeps the full variant name (for example
 * "Nano Banana 2 2K ...") that the main window works with. The family and the target resolution of the frame choose
 * the variant closest to the target ({@link ModelFamilies#nearestForItem}), and a variant set elsewhere (attachment
 * filter, saved selection, brainstorm) is shown as its family. The resolution each family takes is remembered in the
 * configuration for the brainstorm popup, but never chooses the variant here.
 *
 * @since 1.9.1
 */
public class ModelFamilySelector {

    private final ChoiceBox<String> variants;
    private final ChoiceBox<String> families;
    private final ChoiceBox<String> target;
    private final ApplicationConfig config;

    private boolean syncing;
    private boolean rebuildPending;

    /**
     * Connects the choices.
     *
     * @param variants the hidden choice of variants
     * @param families the visible choice of families
     * @param target   the choice of the target resolution, one of {@link ModelFamilies#RESOLUTIONS}
     * @param config   remembers the resolution of each family (may be null)
     */
    public ModelFamilySelector(ChoiceBox<String> variants, ChoiceBox<String> families, ChoiceBox<String> target,
            ApplicationConfig config) {
        this.variants = variants;
        this.families = families;
        this.target = target;
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

    /** Keeps the choices in step: a new variant list rebuilds the families, a family or target chooses the variant. */
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
        target.valueProperty().addListener((obs, old, resolution) -> {
            if (resolution != null && families.getValue() != null)
                onTargetChosen(resolution);
        });
        rebuildFamilyChoices();
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

    /** Shows the family of the variant in the dropdown item. */
    private void showVariant(String item) {
        if (item == null)
            return;

        syncing = true;
        try {
            families.setValue(ModelFamilies.familyOf(item));
        } finally {
            syncing = false;
        }
    }

    /** Chooses the variant of a family closest to the target resolution, else 1K, else the first. */
    private void onFamilyChosen(String family) {
        List<String> familyVariants = ModelFamilies.variantsOf(variants.getItems(), family);
        if (familyVariants.isEmpty())
            return;

        String resolution = ModelFamilies.nearestForItem(target.getValue(), variants.getItems(), familyVariants.get(0));
        String item = ModelFamilies.variantFor(variants.getItems(), family, resolution, null);
        if (!item.equals(variants.getValue()))
            variants.setValue(item);
    }

    /** Switches to the variant of the family closest to the new target and remembers the resolution it takes. */
    private void onTargetChosen(String resolution) {
        onFamilyChosen(families.getValue());
        String taken = ModelFamilies.nearestForItem(resolution, variants.getItems(), variants.getValue());
        if (taken != null)
            remember(variants.getValue(), taken);
    }
}
