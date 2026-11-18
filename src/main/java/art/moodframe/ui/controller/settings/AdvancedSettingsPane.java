package art.moodframe.ui.controller.settings;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.ConfigurationExporter;
import art.moodframe.util.I18nManager;
import art.moodframe.util.VersionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

/**
 * The advanced section of the settings ({@code AdvancedSettingsPane.fxml}): detailed logging, the backup of the
 * settings and the library ({@link ConfigurationExporter}), the reset to the defaults and the version. An import
 * and a reset change other sections, so the dialog hands in what shows the stored values again.
 */
public class AdvancedSettingsPane implements SettingsSection {

    private static final Logger logger = LoggerFactory.getLogger(AdvancedSettingsPane.class);

    @FXML
    private CheckBox enableLoggingCheckBox;
    @FXML
    private Button exportSettingsButton;
    @FXML
    private Button importSettingsButton;
    @FXML
    private Button resetSettingsButton;
    @FXML
    private Label versionLabel;
    @FXML
    private Label configPathLabel;
    @FXML
    private Label performanceTitleLabel;
    @FXML
    private Label dataTitleLabel;
    @FXML
    private Label aboutTitleLabel;
    @FXML
    private Label configPathTitleLabel;
    @FXML
    private Button openConfigDirectoryButton;

    private SettingsServices services;
    private ApplicationConfig config;
    private Runnable reload = () -> {
    };
    private Runnable resetAll = () -> {
    };

    @Override
    public void init(SettingsServices services) {
        this.services = services;
        this.config = services.config();
        versionLabel.setText(VersionManager.getApplicationNameWithVersion());
        configPathLabel.setText(config.paths().root() + "/");
    }

    /**
     * Sets what shows the stored values of all sections again.
     *
     * @param reload   after an import, see {@link SettingsSection#load()}
     * @param resetAll after a reset, see {@link SettingsSection#reset()}
     */
    public void onChanged(Runnable reload, Runnable resetAll) {
        this.reload = reload;
        this.resetAll = resetAll;
    }

    @Override
    public void load() {
        enableLoggingCheckBox.setSelected(config.getLoggingEnabled());
    }

    @Override
    public void reset() {
        enableLoggingCheckBox.setSelected(config.getLoggingEnabled());
    }

    @Override
    public void apply() {
        config.setLoggingEnabled(enableLoggingCheckBox.isSelected());
    }

    @Override
    public void relabel() {
        performanceTitleLabel.setText(I18nManager.getText("settings.performance.title"));
        dataTitleLabel.setText(I18nManager.getText("settings.data.title"));
        aboutTitleLabel.setText(I18nManager.getText("settings.about.title"));
        configPathTitleLabel.setText(I18nManager.getText("settings.about.config.path"));
        versionLabel.setText(I18nManager.getText("settings.about.version", VersionManager.getVersion()));
        exportSettingsButton.setText(I18nManager.getText("settings.data.export"));
        importSettingsButton.setText(I18nManager.getText("settings.data.import"));
        resetSettingsButton.setText(I18nManager.getText("settings.data.reset"));
        openConfigDirectoryButton.setText(I18nManager.getText("settings.about.config.open"));
        enableLoggingCheckBox.setText(I18nManager.getText("settings.performance.logging"));
    }

    /** Exports the settings and the library into a chosen file. */
    @FXML
    private void onExportSettings() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Configuration");
            ConfigurationExporter exporter = services.exporter();
            fileChooser.setInitialFileName(exporter.generateExportFilename());
            File documentsDir = config.paths().documents().toFile();
            if (documentsDir.exists())
                fileChooser.setInitialDirectory(documentsDir);

            File exportFile = services.prompts().saveFile(fileChooser, exportSettingsButton.getScene().getWindow());
            if (exportFile != null) {
                exporter.exportConfiguration(config, exportFile);
                services.prompts().info("Export Successful",
                        String.format("Configuration exported successfully to:\n%s\n\nFile size: %.1f KB",
                                exportFile.getAbsolutePath(), exportFile.length() / 1024.0));
            }
        } catch (Exception e) {
            logger.error("Failed to export configuration", e);
            services.prompts().error("Export Failed", "Failed to export configuration: " + e.getMessage());
        }
    }

    /** Imports a chosen file; the library services report what changed, the dialog shows the new settings. */
    @FXML
    private void onImportSettings() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Import Configuration");
            File documentsDir = config.paths().documents().toFile();
            if (documentsDir.exists())
                fileChooser.setInitialDirectory(documentsDir);

            File importFile = services.prompts().openFile(fileChooser, importSettingsButton.getScene().getWindow());
            if (importFile == null)
                return;
            ConfigurationExporter.ImportResult result = services.exporter().importConfiguration(importFile, config);
            if (!result.isSuccess()) {
                services.prompts().error("Import Failed", result.getMessage());
            } else if (result.getChangeCount() == 0) {
                services.prompts().info("Import Complete",
                        "Configuration file imported successfully.\nNo changes were needed - your current settings match the imported configuration.");
            } else if (confirmImport(result)) {
                /*  The settings are saved as they are set  */
                reload.run();
                services.prompts().info("Import Successful", String.format(
                        "Configuration imported successfully!\n\n%d settings were updated.\n\nRestart the application to ensure all changes take effect.",
                        result.getChangeCount()));
            } else {
                reload.run();
            }
        } catch (Exception e) {
            logger.error("Failed to import configuration", e);
            services.prompts().error("Import Failed", "Failed to import configuration: " + e.getMessage());
        }
    }

    /** Lists the changes of an import and asks whether to keep them. */
    private boolean confirmImport(ConfigurationExporter.ImportResult result) {
        StringBuilder content = new StringBuilder();
        content.append("The following settings will be updated:\n\n");
        for (Map.Entry<String, Object> change : result.getChanges().entrySet())
            content.append(String.format("• %s: %s\n", change.getKey(), change.getValue()));
        content.append("\nDo you want to apply these changes?");
        return services.prompts().confirm("Confirm Configuration Import",
                String.format("Import will change %d settings", result.getChangeCount()), content.toString(), true);
    }

    /** Resets the settings after a confirmation, keeping the secrets, and deletes the custom styles. */
    @FXML
    private void onResetSettings() {
        if (!services.prompts().confirm("Reset Settings", "Reset all settings to defaults?",
                "This will reset all settings except the API keys, the MCP token and the proxy password. "
                        + "Custom styles will be deleted. This action cannot be undone.", false))
            return;

        try {
            /*  keeps the API keys, the MCP token and the proxy password  */
            config.resetToDefaults();
            if (services.blocks() != null)
                services.blocks().resetToDefaults(PromptBlock.Type.STYLE);
            resetAll.run();
            services.prompts().info("Settings Reset",
                    "All settings have been reset to defaults. API key and proxy password were preserved.");
        } catch (Exception e) {
            logger.error("Failed to reset settings", e);
            services.prompts().error("Reset Failed", "Failed to reset settings: " + e.getMessage());
        }
    }

    /** Opens the folder of the settings in the file manager of the system. */
    @FXML
    private void onOpenConfigDirectory() {
        try {
            File configDir = config.paths().root().toFile();
            if (!configDir.exists())
                configDir.mkdirs();

            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win"))
                Runtime.getRuntime().exec(new String[] { "explorer", configDir.getAbsolutePath() });
            else if (osName.contains("mac"))
                Runtime.getRuntime().exec(new String[] { "open", configDir.getAbsolutePath() });
            else
                Runtime.getRuntime().exec(new String[] { "xdg-open", configDir.getAbsolutePath() });
        } catch (Exception e) {
            logger.error("Failed to open system explorer", e);
            services.prompts().error(I18nManager.getText("error.explorer"),
                    I18nManager.getText("error.explorer.open", e.getMessage()));
        }
    }
}
