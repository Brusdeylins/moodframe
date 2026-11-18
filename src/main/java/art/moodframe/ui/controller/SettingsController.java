package art.moodframe.ui.controller;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.mcp.McpService;
import art.moodframe.service.ConfigurationExporter;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.provider.ApiProviderValidator;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.ui.controller.settings.AdvancedSettingsPane;
import art.moodframe.ui.controller.settings.AppearanceSettingsPane;
import art.moodframe.ui.controller.settings.BflSettingsPane;
import art.moodframe.ui.controller.settings.GeminiSettingsPane;
import art.moodframe.ui.controller.settings.LanguageSettingsPane;
import art.moodframe.ui.controller.settings.McpSettingsPane;
import art.moodframe.ui.controller.settings.OpenAiSettingsPane;
import art.moodframe.ui.controller.settings.ProxySettingsPane;
import art.moodframe.ui.controller.settings.SettingsPrompts;
import art.moodframe.ui.controller.settings.SettingsSection;
import art.moodframe.ui.controller.settings.SettingsServices;
import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller of the settings dialog ({@code SettingsDialog.fxml}), a thin coordinator of its sections.
 *
 * <p>Every section is an FXML file of its own under {@code fxml/settings}, included with {@code fx:include}, with a
 * controller in {@code ui.controller.settings} that loads, saves, resets and relabels its own values (see
 * {@link SettingsSection}). This controller looks up the services once and hands them to the sections, shows the
 * section chosen in the list on the left, and passes "Save", an import, a reset and a change of the language on to
 * all of them.</p>
 *
 * <p>Changes of the theme and the language show at once; the main window calls {@link #applySettings()} when the
 * dialog closes, which saves the rest. A typed API key is only tested, never set on the live provider, and saved
 * with "Save" once its test passed.</p>
 *
 * @since 1.0.0
 */
public class SettingsController implements Initializable, I18nChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    @FXML
    private DialogPane settingsDialogPane;
    @FXML
    private ListView<String> settingsCategoryList;

    /*  the content panes in the order of the categories  */
    @FXML
    private Region apiConfigurationPane;
    @FXML
    private Region corporateProxyPane;
    @FXML
    private Region appearancePane;
    @FXML
    private Region languagePane;
    @FXML
    private Region advancedPane;
    @FXML
    private Region mcpPane;

    /*  the controllers of the sections, injected by their fx:include  */
    @FXML
    private BflSettingsPane bflPaneController;
    @FXML
    private GeminiSettingsPane geminiPaneController;
    @FXML
    private OpenAiSettingsPane openAiPaneController;
    @FXML
    private ProxySettingsPane corporateProxyPaneController;
    @FXML
    private AppearanceSettingsPane appearancePaneController;
    @FXML
    private LanguageSettingsPane languagePaneController;
    @FXML
    private AdvancedSettingsPane advancedPaneController;
    @FXML
    private McpSettingsPane mcpPaneController;

    private SettingsServices services;
    private List<SettingsSection> sections;
    private final ObservableList<String> categories = FXCollections.observableArrayList();

    /** Creates the controller of the dialog, which looks up its services when it is loaded. */
    public SettingsController() {
    }

    /**
     * Creates the controller with the given services, for a test.
     *
     * @param services the services of the sections
     */
    SettingsController(SettingsServices services) {
        this.services = services;
    }

    /**
     * Looks up the services of the sections, the one place where the dialog asks the {@link ServiceFactory}.
     *
     * @return the services
     */
    private static SettingsServices lookUpServices() {
        ServiceFactory factory = ServiceFactory.getInstance();
        ApplicationConfig config = factory.getService(ApplicationConfig.class);
        if (config == null)
            throw new RuntimeException("ApplicationConfig service not available");
        return new SettingsServices(config, factory.getService(ProviderRegistry.class),
                factory.getService(ApiProviderValidator.class), factory.getService(McpService.class),
                factory.getService(ConfigurationExporter.class), factory.getService(PromptBlockService.class),
                SettingsPrompts.standard());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            if (services == null)
                services = lookUpServices();
            I18nManager.addChangeListener(this);

            initializeNavigation();

            /*  in the order of loading, saving and relabeling  */
            sections = List.of(bflPaneController, geminiPaneController, openAiPaneController,
                    advancedPaneController, appearancePaneController, languagePaneController,
                    corporateProxyPaneController, mcpPaneController);
            for (SettingsSection section : List.of(bflPaneController, geminiPaneController, openAiPaneController,
                    appearancePaneController, languagePaneController, corporateProxyPaneController,
                    advancedPaneController, mcpPaneController))
                section.init(services);
            advancedPaneController.onChanged(this::loadCurrentSettings, this::refreshSettingsFromConfig);

            loadCurrentSettings();
            onLanguageChanged();

            /*  The Close button is wide enough for "Schließen"  */
            Platform.runLater(this::widenCloseButton);
        } catch (Exception e) {
            logger.error("Failed to initialize Settings controller", e);
            SettingsPrompts prompts = services != null ? services.prompts() : SettingsPrompts.standard();
            prompts.error(I18nManager.getText("error.initialization"),
                    I18nManager.getText("error.settings.load", e.getMessage()));
        }
    }

    /** Fills the list of categories and shows the first section. */
    private void initializeNavigation() {
        updateNavigationCategories();
        settingsCategoryList.setItems(categories);
        settingsCategoryList.getSelectionModel().selectedIndexProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null && newValue.intValue() >= 0)
                        showContentPane(newValue.intValue());
                });
        settingsCategoryList.getSelectionModel().selectFirst();
    }

    /** Sets the categories in the current language, keeping the selection. */
    private void updateNavigationCategories() {
        int selectedIndex = settingsCategoryList.getSelectionModel().getSelectedIndex();
        categories.setAll(
                I18nManager.getText("settings.category.api"),
                I18nManager.getText("settings.category.proxy"),
                I18nManager.getText("settings.category.appearance"),
                I18nManager.getText("settings.category.language"),
                I18nManager.getText("settings.category.advanced"),
                I18nManager.getText("settings.category.mcp"));
        if (selectedIndex >= 0 && selectedIndex < categories.size())
            Platform.runLater(() -> settingsCategoryList.getSelectionModel().select(selectedIndex));
    }

    /** Shows the content pane of a category and hides the others; an unknown one shows the API configuration. */
    private void showContentPane(int categoryIndex) {
        List<Region> panes = List.of(apiConfigurationPane, corporateProxyPane, appearancePane, languagePane,
                advancedPane, mcpPane);
        if (categoryIndex >= panes.size())
            logger.warn("Unknown category selected: {}, defaulting to API Configuration", categoryIndex);
        Region shown = panes.get(categoryIndex < panes.size() ? categoryIndex : 0);
        for (Region pane : panes) {
            pane.setVisible(pane == shown);
            pane.setManaged(pane == shown);
        }
    }

    /** Shows the stored values in all sections, at the opening and after an import. */
    private void loadCurrentSettings() {
        try {
            for (SettingsSection section : sections)
                section.load();
        } catch (Exception e) {
            logger.error("Failed to load current settings", e);
            services.prompts().error("Settings Load Error", "Failed to load current settings: " + e.getMessage());
        }
    }

    /** Shows the values of all sections after a reset to the defaults. */
    private void refreshSettingsFromConfig() {
        try {
            for (SettingsSection section : sections)
                section.reset();
        } catch (Exception e) {
            logger.error("Failed to refresh settings UI from configuration", e);
        }
    }

    /**
     * Saves the settings of all sections; the main window calls it when the dialog closes. Theme and language
     * changes are already shown by their live preview.
     */
    public void applySettings() {
        if (sections == null)
            return;
        try {
            for (SettingsSection section : sections)
                section.apply();
        } catch (Exception e) {
            logger.error("Failed to apply settings", e);
            services.prompts().error("Apply Settings Error", "Failed to save settings: " + e.getMessage());
        }
    }

    @Override
    public void onLanguageChanged() {
        try {
            for (SettingsSection section : sections)
                section.relabel();
            updateNavigationCategories();

            settingsDialogPane.setHeaderText(I18nManager.getText("settings.title"));
            /*  The header label gets its style class again after the change  */
            Platform.runLater(() -> {
                Node header = settingsDialogPane.lookup(".header-panel");
                if (header instanceof Region headerRegion
                        && headerRegion.lookup(".label") instanceof javafx.scene.control.Label label
                        && !label.getStyleClass().contains("title-3"))
                    label.getStyleClass().add("title-3");
            });
            Button closeButton = closeButton();
            if (closeButton != null)
                closeButton.setText(I18nManager.getText("button.close"));
            widenCloseButton();

            Stage stage = getStage();
            if (stage != null)
                stage.setTitle(I18nManager.getText("settings.window.title"));
        } catch (Exception e) {
            logger.error("Failed to update Settings UI texts", e);
        }
    }

    /*  the Close button of the dialog, or null  */
    private Button closeButton() {
        return settingsDialogPane.getButtonTypes().stream()
                .filter(buttonType -> buttonType.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE)
                .findFirst()
                .map(buttonType -> (Button) settingsDialogPane.lookupButton(buttonType))
                .orElse(null);
    }

    /*  makes the Close button wide enough for the German "Schließen"  */
    private void widenCloseButton() {
        Button closeButton = closeButton();
        if (closeButton != null)
            closeButton.setMinWidth(100);
    }

    /** @return the stage of the dialog, or null if it is not shown */
    private Stage getStage() {
        if (settingsCategoryList.getScene() != null)
            return (Stage) settingsCategoryList.getScene().getWindow();
        return null;
    }
}
