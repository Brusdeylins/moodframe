package tools.moodframe.ui.component;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom multi-select component for style presets.
 * 
 * <p>
 * This component provides a better UX for selecting multiple style presets
 * compared to the previous " + " syntax workaround. It shows selected styles
 * as removable tags and offers a dropdown for selecting additional styles.
 * </p>
 * 
 * <p>
 * <strong>Features:</strong>
 * </p>
 * <ul>
 * <li>Visual tag representation of selected styles</li>
 * <li>Dropdown for selecting additional styles</li>
 * <li>Easy removal of individual styles</li>
 * <li>Clear all functionality</li>
 * <li>Responsive layout that wraps tags</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class MultiSelectStyleBox extends VBox {

    private final ObservableList<String> availableStyles = FXCollections.observableArrayList();
    private final Set<String> selectedStyles = new HashSet<>();
    private final FlowPane tagsContainer = new FlowPane();
    private final ComboBox<String> styleSelector = new ComboBox<>();
    private final Button clearAllButton = new Button();

    /**
     * Creates a new MultiSelectStyleBox.
     */
    public MultiSelectStyleBox() {
        super(5); // spacing between components
        initializeComponents();
        setupLayout();
        setupEventHandlers();
    }

    /**
     * Initializes all UI components.
     */
    private void initializeComponents() {
        // Configure tags container
        tagsContainer.setHgap(5);
        tagsContainer.setVgap(5);
        tagsContainer.setPadding(new Insets(5));
        tagsContainer.getStyleClass().add("tags-container");
        tagsContainer.setMinHeight(35);

        // Configure style selector
        styleSelector.setPromptText("Add style...");
        styleSelector.setMaxWidth(Double.MAX_VALUE);
        styleSelector.setItems(availableStyles);

        // Configure clear all button
        clearAllButton.setText("Clear All");
        clearAllButton.setGraphic(new FontIcon("mdi2c-close-circle"));
        clearAllButton.getStyleClass().add("button-outlined");
        clearAllButton.setVisible(false); // Hidden until styles are selected
    }

    /**
     * Sets up the layout structure.
     */
    private void setupLayout() {
        // Control row with selector and clear button
        HBox controlRow = new HBox(10);
        controlRow.setAlignment(Pos.CENTER_LEFT);

        HBox.setHgrow(styleSelector, Priority.ALWAYS);
        controlRow.getChildren().addAll(styleSelector, clearAllButton);

        // Add components to main container
        getChildren().addAll(tagsContainer, controlRow);
    }

    /**
     * Sets up event handlers for user interactions.
     */
    private void setupEventHandlers() {
        // Handle style selection
        styleSelector.setOnAction(event -> {
            String selectedStyle = styleSelector.getValue();
            if (selectedStyle != null && !selectedStyle.isEmpty() && !selectedStyles.contains(selectedStyle)) {
                addSelectedStyle(selectedStyle);
                styleSelector.setValue(null); // Clear selection
            }
        });

        // Handle clear all
        clearAllButton.setOnAction(event -> clearAllStyles());
    }

    /**
     * Sets the available styles for selection.
     * 
     * @param styles the list of available style names
     */
    public void setAvailableStyles(List<String> styles) {
        availableStyles.clear();
        availableStyles.addAll(styles);
        updateSelectorItems();
    }

    /**
     * Gets the currently selected style names.
     * 
     * @return list of selected style names
     */
    public List<String> getSelectedStyles() {
        return selectedStyles.stream().collect(Collectors.toList());
    }

    /**
     * Sets the selected styles programmatically.
     * 
     * @param styles the style names to select
     */
    public void setSelectedStyles(List<String> styles) {
        clearAllStyles();
        styles.forEach(this::addSelectedStyle);
    }

    /**
     * Adds a style to the selection.
     * 
     * @param styleName the name of the style to add
     */
    private void addSelectedStyle(String styleName) {
        if (selectedStyles.add(styleName)) {
            createStyleTag(styleName);
            updateSelectorItems();
            updateClearButtonVisibility();
        }
    }

    /**
     * Removes a style from the selection.
     * 
     * @param styleName the name of the style to remove
     */
    private void removeSelectedStyle(String styleName) {
        if (selectedStyles.remove(styleName)) {
            updateTagsDisplay();
            updateSelectorItems();
            updateClearButtonVisibility();
        }
    }

    /**
     * Clears all selected styles.
     */
    private void clearAllStyles() {
        selectedStyles.clear();
        updateTagsDisplay();
        updateSelectorItems();
        updateClearButtonVisibility();
    }

    /**
     * Creates a visual tag for a selected style.
     * 
     * @param styleName the name of the style
     */
    private void createStyleTag(String styleName) {
        HBox tag = new HBox(5);
        tag.setAlignment(Pos.CENTER);
        tag.setPadding(new Insets(3, 8, 3, 8));
        tag.getStyleClass().addAll("tag", "tag-default");
        tag.getStyleClass().addAll("style-tag");

        Label label = new Label(styleName);
        label.getStyleClass().add("style-tag-label");

        Button removeButton = new Button();
        removeButton.setGraphic(new FontIcon("mdi2c-close"));
        removeButton.getStyleClass().addAll("button-icon", "tag-remove");
        removeButton.setOnAction(event -> removeSelectedStyle(styleName));

        tag.getChildren().addAll(label, removeButton);
        tagsContainer.getChildren().add(tag);
    }

    /**
     * Updates the tags display by recreating all tags.
     */
    private void updateTagsDisplay() {
        tagsContainer.getChildren().clear();
        selectedStyles.forEach(this::createStyleTag);
    }

    /**
     * Updates the selector dropdown to exclude already selected styles.
     */
    private void updateSelectorItems() {
        String currentValue = styleSelector.getValue();

        // Filter out already selected styles
        List<String> filteredStyles = availableStyles.stream()
                .filter(style -> !selectedStyles.contains(style))
                .collect(Collectors.toList());

        styleSelector.getItems().clear();
        styleSelector.getItems().addAll(filteredStyles);

        // Restore selection if it's still valid
        if (currentValue != null && filteredStyles.contains(currentValue)) {
            styleSelector.setValue(currentValue);
        }
    }

    /**
     * Updates the visibility of the clear all button.
     */
    private void updateClearButtonVisibility() {
        clearAllButton.setVisible(!selectedStyles.isEmpty());
    }

    /**
     * Checks if any styles are currently selected.
     * 
     * @return true if at least one style is selected
     */
    public boolean hasSelectedStyles() {
        return !selectedStyles.isEmpty();
    }

    /**
     * Gets the number of selected styles.
     * 
     * @return the count of selected styles
     */
    public int getSelectedStyleCount() {
        return selectedStyles.size();
    }

    /**
     * Sets the prompt text for the style selector.
     * 
     * @param promptText the prompt text to display
     */
    public void setPromptText(String promptText) {
        styleSelector.setPromptText(promptText);
    }

    /**
     * Sets whether this component is disabled.
     * 
     * @param disabled true to disable, false to enable
     */
    public void setComponentsDisabled(boolean disabled) {
        styleSelector.setDisable(disabled);
        clearAllButton.setDisable(disabled);
        tagsContainer.setDisable(disabled);
    }
}