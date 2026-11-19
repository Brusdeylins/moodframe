package art.moodframe.ui.component;

import art.moodframe.ui.support.ImageCache;
import art.moodframe.util.I18nManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UI component that displays real-time JVM memory status and image loading metrics.
 *
 * <p>This component shows actual JVM memory usage and image loading statistics in a compact status bar format.</p>
 *
 * <p><strong>Displayed Metrics:</strong></p>
 * <ul>
 * <li><strong>Memory Usage</strong>: Current JVM memory usage percentage and MB</li>
 * <li><strong>Loaded Images</strong>: Number of decoded thumbnails the window holds in its {@link ImageCache}</li>
 * </ul>
 *
 * @since 1.2.0 - Simplified Memory Monitoring
 * @since 1.3.9 - Added image loading metrics
 * @since 1.4.0 - Removed redundant listener tracking (ImageStateManager removed)
 */
public class MemoryStatusComponent extends HBox {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryStatusComponent.class);
    
    // Update interval for status display
    private static final double UPDATE_INTERVAL_SECONDS = 2.0;

    /*  the tooltips appear quicker than the JavaFX default of 1 s  */
    private static final Duration TOOLTIP_SHOW_DELAY = Duration.millis(500);


    // UI Components
    private final FontIcon memoryIcon;
    private final Label memoryLabel;
    private final Tooltip memoryTooltip;
    private final FontIcon tasksIcon;
    private final Label tasksLabel;
    private final Tooltip tasksTooltip;

    // Update timeline
    private final Timeline updateTimeline;

    /*  the decoded thumbnails counted, none until the window hands them  */
    private ImageCache images;


    /**
     * Creates a new MemoryStatusComponent.
     */
    public MemoryStatusComponent() {
        // Setup UI
        setupUI();

        // Initialize icons and labels
        memoryIcon = new FontIcon("mdi2m-memory");
        memoryIcon.setIconSize(14);
        memoryLabel = new Label();

        tasksIcon = new FontIcon("mdi2i-image-multiple");
        tasksIcon.setIconSize(14);
        tasksLabel = new Label("0 imgs");

        /*  one tooltip per display, installed once on the group of symbol and text, so it stays while the pointer
            moves between them, and only re-texted on updates: a new one per update restarted the show delay  */
        HBox memoryGroup = new HBox(getSpacing(), memoryIcon, memoryLabel);
        memoryTooltip = new Tooltip();
        memoryTooltip.setShowDelay(TOOLTIP_SHOW_DELAY);
        memoryTooltip.setShowDuration(Duration.INDEFINITE);
        Tooltip.install(memoryGroup, memoryTooltip);

        HBox tasksGroup = new HBox(getSpacing(), tasksIcon, tasksLabel);
        tasksTooltip = new Tooltip();
        tasksTooltip.setShowDelay(TOOLTIP_SHOW_DELAY);
        tasksTooltip.setShowDuration(Duration.INDEFINITE);
        Tooltip.install(tasksGroup, tasksTooltip);

        // Add components to layout
        getChildren().addAll(memoryGroup, tasksGroup);

        // Setup update timeline
        updateTimeline = new Timeline(
            new KeyFrame(Duration.seconds(UPDATE_INTERVAL_SECONDS), e -> updateStatus())
        );
        updateTimeline.setCycleCount(Timeline.INDEFINITE);

        // Start monitoring
        startMonitoring();

        // Initial status update
        updateStatus();

        logger.debug("MemoryStatusComponent initialized with image and listener tracking");
    }
    
    /**
     * Counts the decoded thumbnails of a window from now on.
     *
     * @param images the decoded thumbnails
     */
    public void countImagesOf(ImageCache images) {
        this.images = images;
        updateStatus();
    }

    /**
     * Sets up the UI layout and styling.
     */
    private void setupUI() {
        setSpacing(15.0);
        setPadding(new Insets(5, 10, 5, 10));
        getStyleClass().add("memory-status");
    }
    
    /**
     * Starts memory monitoring.
     */
    private void startMonitoring() {
        // Start update timeline
        updateTimeline.play();
        
        logger.info("Memory monitoring started");
    }
    
    /**
     * Updates the status display with current metrics.
     */
    private void updateStatus() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateStatus);
            return;
        }

        try {
            updateMemoryStatus();
            updateTasksStatus();
        } catch (Exception e) {
            logger.debug("Error updating performance status", e);
        }
    }
    
    /**
     * Updates memory status display.
     */
    private void updateMemoryStatus() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();      // Maximum memory JVM can use
        long totalMemory = runtime.totalMemory();  // Current memory allocated to JVM
        long freeMemory = runtime.freeMemory();    // Free memory within allocated
        long usedMemory = totalMemory - freeMemory; // Actually used memory
        
        // Calculate percentage based on maximum available memory
        double usagePercentage = (double) usedMemory / maxMemory * 100.0;
        String memoryText = String.format(I18nManager.getCurrentLocale(), "%.0f%% (%dMB)", usagePercentage, usedMemory / 1024 / 1024);
        
        // Update UI
        memoryLabel.setText(memoryText);
        
        // Update icon color based on memory usage
        if (usagePercentage > 90) {
            memoryIcon.getStyleClass().removeAll("text-success", "text-warning");
            memoryIcon.getStyleClass().add("text-danger");
        } else if (usagePercentage > 75) {
            memoryIcon.getStyleClass().removeAll("text-success", "text-danger");
            memoryIcon.getStyleClass().add("text-warning");
        } else {
            memoryIcon.getStyleClass().removeAll("text-warning", "text-danger");
            memoryIcon.getStyleClass().add("text-success");
        }
        
        // Update tooltip with memory information
        memoryTooltip.setText(I18nManager.getText("memory.status.tooltip", usedMemory / 1024 / 1024,
                maxMemory / 1024 / 1024, usagePercentage, totalMemory / 1024 / 1024, freeMemory / 1024 / 1024));
    }

    /**
     * Updates image loading status display.
     * Shows: number of loaded thumbnail images
     */
    private void updateTasksStatus() {
        int loadedImages = images != null ? images.size() : 0;

        tasksLabel.setText(I18nManager.getText("memory.status.images", loadedImages));

        // Update icon color based on loaded images count
        if (loadedImages > 500) {
            tasksIcon.getStyleClass().removeAll("text-success", "text-warning");
            tasksIcon.getStyleClass().add("text-danger");
        } else if (loadedImages > 300) {
            tasksIcon.getStyleClass().removeAll("text-success", "text-danger");
            tasksIcon.getStyleClass().add("text-warning");
        } else {
            tasksIcon.getStyleClass().removeAll("text-warning", "text-danger");
            tasksIcon.getStyleClass().add("text-success");
        }

        tasksTooltip.setText(I18nManager.getText("memory.status.images.tooltip", loadedImages));
    }
    
    /**
     * Stops monitoring and cleanup resources.
     */
    public void shutdown() {
        logger.info("Shutting down MemoryStatusComponent");
        
        // Stop timeline
        if (updateTimeline != null) {
            updateTimeline.stop();
        }
        
        logger.info("MemoryStatusComponent shutdown completed");
    }
}