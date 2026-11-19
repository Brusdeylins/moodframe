package art.moodframe.ui.component;

import art.moodframe.model.ModelFamilies;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.scene.control.ChoiceBox;

import java.util.List;

/**
 * Lets the user choose a model as family, while a hidden choice keeps the full variant name (for example
 * "Nano Banana 2 2K ...") that the main window and the brainstorm work with. The variant is always the one of its
 * family closest to the target resolution of the frame ({@link ModelFamilies#variantForTarget}): when the family is
 * chosen, when the target changes, when a variant is set elsewhere (attachment filter, saved selection, reused entry,
 * brainstorm) and when the list of variants is rebuilt. Whoever wants another resolution sets it as target.
 *
 * @since 1.9.1
 */
public class ModelFamilySelector {

    private final ChoiceBox<String> variants;
    private final ChoiceBox<String> families;
    private final ObservableValue<String> target;

    private boolean syncing;
    private boolean rebuildPending;

    /**
     * Connects the choices.
     *
     * @param variants the hidden choice of variants
     * @param families the visible choice of families
     * @param target   the choice of the target resolution, one of {@link ModelFamilies#RESOLUTIONS}
     */
    public ModelFamilySelector(ChoiceBox<String> variants, ChoiceBox<String> families, ChoiceBox<String> target) {
        this(variants, families, target.valueProperty());
    }

    /**
     * Connects the choices to a target resolution chosen elsewhere, e.g. in another part of the window.
     *
     * @param variants the hidden choice of variants
     * @param families the visible choice of families
     * @param target   the target resolution, one of {@link ModelFamilies#RESOLUTIONS}
     */
    public ModelFamilySelector(ChoiceBox<String> variants, ChoiceBox<String> families,
            ObservableValue<String> target) {
        this.variants = variants;
        this.families = families;
        this.target = target;
        bind();
    }

    /** Keeps the choices in step: a new variant list rebuilds the families, family, target and variant align. */
    private void bind() {
        variants.getItems().addListener((ListChangeListener<String>) (change) -> {
            if (!rebuildPending) {
                rebuildPending = true;
                Platform.runLater(this::rebuildFamilyChoices);
            }
        });
        variants.valueProperty().addListener((obs, old, item) -> {
            showVariant(item);
            alignVariant();
        });
        families.valueProperty().addListener((obs, old, family) -> {
            if (!syncing && family != null)
                onFamilyChosen(family);
        });
        target.addListener((obs, old, resolution) -> {
            if (resolution != null && variants.getValue() != null)
                alignVariant();
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
        alignVariant();
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
        String item = ModelFamilies.variantForTarget(variants.getItems(), family, target.getValue());
        if (item != null && !item.equals(variants.getValue()))
            variants.setValue(item);
    }

    /** Switches a variant to the one of its family closest to the target, as long as there is a target. */
    private void alignVariant() {
        if (target.getValue() != null && variants.getValue() != null)
            onFamilyChosen(ModelFamilies.familyOf(variants.getValue()));
    }
}
