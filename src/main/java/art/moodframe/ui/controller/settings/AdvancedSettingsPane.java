package art.moodframe.ui.controller.settings;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.ConfigurationExporter;
import art.moodframe.ui.support.SystemOpen;
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
import java.util.List;
import java.util.Map;

/**
 * The advanced section of the settings ({@code AdvancedSettingsPane.fxml}): detailed logging, the backup of the
 * settings and the library ({@link ConfigurationExporter}), the reset to the defaults and the version. An import
 * and a reset change other sections, so the dialog hands in what shows the stored values again. Export, import and
 * reset read and write the library on a thread of their own ({@link SectionTask}), their buttons disabled meanwhile.
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

    /*  the buttons of the backup and the reset, disabled while one of them runs  */
    private List<Button> dataButtons() {
        return List.of(exportSettingsButton, importSettingsButton, resetSettingsButton);
    }

    /** Exports the settings and the library into a chosen file, off the JavaFX thread. */
    @FXML
    private void onExportSettings() {
        ConfigurationExporter exporter = services.exporter();
        if (exporter == null) {
            services.prompts().error(I18nManager.getText("settings.data.export.error.title"),
                    I18nManager.getText("settings.data.export.error", "-"));
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18nManager.getText("settings.data.export.chooser"));
        fileChooser.setInitialFileName(exporter.generateExportFilename());
        File documentsDir = config.paths().documents().toFile();
        if (documentsDir.exists())
            fileChooser.setInitialDirectory(documentsDir);

        File exportFile = services.prompts().saveFile(fileChooser, exportSettingsButton.getScene().getWindow());
        if (exportFile == null)
            return;
        SectionTask.run(dataButtons(), () -> {
            exporter.exportConfiguration(config, exportFile);
            return exportFile.length();
        }, (length) -> services.prompts().info(I18nManager.getText("settings.data.export.done.title"),
                I18nManager.getText("settings.data.export.done", exportFile.getAbsolutePath(), length / 1024.0)),
                (e) -> {
            logger.error("Failed to export configuration", e);
            services.prompts().error(I18nManager.getText("settings.data.export.error.title"),
                    I18nManager.getText("settings.data.export.error", e.getMessage()));
        });
    }

    /**
     * Imports a chosen file off the JavaFX thread; the library services report what changed, the dialog shows the new
     * settings.
     */
    @FXML
    private void onImportSettings() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18nManager.getText("settings.data.import.chooser"));
        File documentsDir = config.paths().documents().toFile();
        if (documentsDir.exists())
            fileChooser.setInitialDirectory(documentsDir);

        File importFile = services.prompts().openFile(fileChooser, importSettingsButton.getScene().getWindow());
        if (importFile == null)
            return;
        SectionTask.run(dataButtons(), () -> services.exporter().importConfiguration(importFile, config),
                this::showImport, (e) -> {
            logger.error("Failed to import configuration", e);
            services.prompts().error(I18nManager.getText("settings.data.import.error.title"),
                    I18nManager.getText("settings.data.import.error", e.getMessage()));
        });
    }

    /*  shows the outcome of an import and asks whether to keep its changes  */
    private void showImport(ConfigurationExporter.ImportResult result) {
        try {
            if (!result.isSuccess()) {
                services.prompts().error(I18nManager.getText("settings.data.import.error.title"), result.getMessage());
            } else if (result.getChangeCount() == 0) {
                services.prompts().info(I18nManager.getText("settings.data.import.none.title"),
                        I18nManager.getText("settings.data.import.none"));
            } else if (confirmImport(result)) {
                /*  The settings are saved as they are set  */
                reload.run();
                services.prompts().info(I18nManager.getText("settings.data.import.done.title"),
                        I18nManager.getText("settings.data.import.done", result.getChangeCount()));
            } else {
                reload.run();
            }
        } catch (Exception e) {
            logger.error("Failed to import configuration", e);
            services.prompts().error(I18nManager.getText("settings.data.import.error.title"),
                    I18nManager.getText("settings.data.import.error", e.getMessage()));
        }
    }

    /** Lists the changes of an import and asks whether to keep them. */
    private boolean confirmImport(ConfigurationExporter.ImportResult result) {
        StringBuilder content = new StringBuilder();
        content.append(I18nManager.getText("settings.data.import.confirm.intro")).append("\n\n");
        for (Map.Entry<String, Object> change : result.getChanges().entrySet())
            content.append(String.format("• %s: %s\n", change.getKey(), change.getValue()));
        content.append("\n").append(I18nManager.getText("settings.data.import.confirm.question"));
        return services.prompts().confirm(I18nManager.getText("settings.data.import.confirm.title"),
                I18nManager.getText("settings.data.import.confirm.header", result.getChangeCount()),
                content.toString(), true);
    }

    /**
     * Resets the settings after a confirmation, keeping the secrets, and deletes the custom styles, off the JavaFX
     * thread.
     */
    @FXML
    private void onResetSettings() {
        if (!services.prompts().confirm(I18nManager.getText("settings.data.reset.title"),
                I18nManager.getText("settings.data.reset.header"), I18nManager.getText("settings.data.reset.confirm"),
                false))
            return;

        SectionTask.run(dataButtons(), () -> {

            /*  keeps the API keys, the MCP token and the proxy password  */
            config.resetToDefaults();
            if (services.blocks() != null)
                services.blocks().resetToDefaults(PromptBlock.Type.STYLE);
            return null;
        }, (done) -> {
            resetAll.run();
            services.prompts().info(I18nManager.getText("settings.data.reset.done.title"),
                    I18nManager.getText("settings.data.reset.done"));
        }, (e) -> {
            logger.error("Failed to reset settings", e);
            services.prompts().error(I18nManager.getText("settings.data.reset.error.title"),
                    I18nManager.getText("settings.data.reset.error", e.getMessage()));
        });
    }

    /** Opens the folder of the settings in the file manager of the system. */
    @FXML
    private void onOpenConfigDirectory() {
        File configDir = config.paths().root().toFile();
        if (!configDir.exists())
            configDir.mkdirs();
        if (!SystemOpen.isAvailable()) {
            services.prompts().error(I18nManager.getText("error.explorer"),
                    I18nManager.getText("error.explorer.open", configDir.getAbsolutePath()));
            return;
        }
        SystemOpen.open(configDir.toPath());
    }
}
