package tools.moodframe.ui.component;

import tools.moodframe.config.ApplicationConfig;
import tools.moodframe.config.ServiceFactory;
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
 * <li><strong>Loaded Images</strong>: Number of thumbnail images currently loaded in ImageViews</li>
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
    

    // UI Components
    private final FontIcon memoryIcon;
    private final Label memoryLabel;
    private final FontIcon tasksIcon;
    private final Label tasksLabel;

    // Update timeline
    private final Timeline updateTimeline;


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

        // Add components to layout
        getChildren().addAll(
            memoryIcon, memoryLabel,
            tasksIcon, tasksLabel
        );

        // Setup update timeline
        updateTimeline = new Timeline(
            new KeyFrame(Duration.seconds(UPDATE_INTERVAL_SECONDS), e -> updateStatus())
        );
        updateTimeline.setCycleCount(Timeline.INDEFINITE);

        // Start monitoring
        startMonitoring();

        // Initial status update
        updateStatus();

        // Only log if logging is enabled
        ApplicationConfig config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
        if (config != null && config.getLoggingEnabled()) {
            logger.info("MemoryStatusComponent initialized with image and listener tracking");
        }
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
        String memoryText = String.format("%.0f%% (%dMB)", usagePercentage, usedMemory / 1024 / 1024);
        
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
        String tooltipText = String.format(
            "Memory: %dMB used / %dMB max (%.1f%%)\n" +
            "Allocated: %dMB / Free: %dMB",
            usedMemory / 1024 / 1024, 
            maxMemory / 1024 / 1024, 
            usagePercentage,
            totalMemory / 1024 / 1024,
            freeMemory / 1024 / 1024
        );
        
        Tooltip memoryTooltip = new Tooltip(tooltipText);
        Tooltip.install(memoryIcon, memoryTooltip);
        Tooltip.install(memoryLabel, memoryTooltip);
    }

    /**
     * Updates image loading status display.
     * Shows: number of loaded thumbnail images
     */
    private void updateTasksStatus() {
        int loadedImages = tools.moodframe.ui.component.ChatEntryCard.getTotalLoadedImages();

        tasksLabel.setText(String.format("%d imgs", loadedImages));

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

        // Update tooltip
        String tooltipText = String.format(
            "Loaded Thumbnails: %d\n" +
            "Images are loaded on-demand as you scroll",
            loadedImages
        );

        Tooltip tasksTooltip = new Tooltip(tooltipText);
        Tooltip.install(tasksIcon, tasksTooltip);
        Tooltip.install(tasksLabel, tasksTooltip);
    }

    /**
     * Gets a summary of current memory status.
     * 
     * @return formatted status summary
     */
    public String getStatusSummary() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double memoryUsage = (double) usedMemory / runtime.maxMemory() * 100.0;
        
        return String.format(
            "Memory Status: %.1f%% (%dMB)",
            memoryUsage,
            usedMemory / 1024 / 1024
        );
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