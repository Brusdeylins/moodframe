package art.moodframe.ui.component;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;

/**
 * UI component for Gemini Phase 2 Advanced Features.
 * Provides controls for Google Search grounding and text rendering hints.
 *
 * NOTE: Reasoning Quality has been moved to general parameters (always visible).
 * Only visible when a Gemini model is selected.
 *
 * @author Matthias Brusdeylins
 * @since 1.6.0
 */
public class GeminiAdvancedFeaturesSection extends VBox {

    private final CheckBox enableSearchGrounding;

    public GeminiAdvancedFeaturesSection() {
        super(15); // 15px spacing between sections, like other parameters
        getStyleClass().add("gemini-advanced-section");
        setPadding(new Insets(0)); // No padding, integrated like other parameters

        // Google Search Grounding - styled like other parameters, explained in its tooltip
        enableSearchGrounding = new CheckBox("Google Search Grounding");
        enableSearchGrounding.setTooltip(new Tooltip("Real-time data integration (+~$0.03 per request)"));

        // Add sections without title and separators (like other parameters)
        // Note: Text Rendering Info removed as it's now shown in the prompt input field
        getChildren().addAll(
            enableSearchGrounding
        );
    }

    /**
     * Gets whether Google Search grounding is enabled.
     *
     * @return true if enabled
     */
    public boolean isSearchGroundingEnabled() {
        return enableSearchGrounding.isSelected();
    }

    /**
     * Sets whether Google Search grounding is enabled.
     *
     * @param enabled true to enable
     */
    public void setSearchGroundingEnabled(boolean enabled) {
        enableSearchGrounding.setSelected(enabled);
    }

    /**
     * Resets all controls to default values.
     */
    public void reset() {
        enableSearchGrounding.setSelected(false);
    }

    /**
     * Gets the search grounding checkbox for persistence listeners.
     *
     * @return the CheckBox control
     */
    public CheckBox getSearchGroundingCheckBox() {
        return enableSearchGrounding;
    }

}
