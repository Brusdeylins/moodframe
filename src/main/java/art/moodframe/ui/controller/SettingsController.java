package art.moodframe.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ServiceFactory;
import art.moodframe.config.CorporateProxyConfig;
import art.moodframe.util.ThemeManager;
import art.moodframe.util.I18nChangeListener;
import art.moodframe.util.I18nManager;
import art.moodframe.service.BflApiService;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller for the Settings Dialog.
 * 
 * <p>
 * This controller manages all settings-related UI interactions including:
 * </p>
 * <ul>
 * <li>API key management and validation</li>
 * <li>Live theme preview - changes apply immediately</li>
 * <li>Language switching with rollback support</li>
 * <li>Advanced configuration options</li>
 * <li>Silent settings persistence (no success messages)</li>
 * </ul>
 * 
 * <p>
 * <strong>Live Preview System:</strong> Theme changes are applied immediately
 * when radio buttons are selected. On Apply, the current theme is saved to
 * configuration. On Cancel, the original theme is restored.
 * </p>
 * 
 * <p>
 * <strong>External API References:</strong>
 * </p>
 * <ul>
 * <li><a href="https://docs.bfl.ai">BFL API Documentation</a> - for API key
 * validation</li>
 * <li><a href="https://github.com/mkpaz/atlantafx">AtlantaFX Documentation</a>
 * - for theme management</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class SettingsController implements Initializable, I18nChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    // Main Dialog Pane
    @FXML
    private DialogPane settingsDialogPane;

    // Navigation Controls
    @FXML
    private ListView<String> settingsCategoryList;
    @FXML
    private StackPane settingsContentPane;

    // Content Panes
    @FXML
    private VBox apiConfigurationPane;
    @FXML
    private ScrollPane corporateProxyPane;
    @FXML
    private VBox appearancePane;
    @FXML
    private VBox languagePane;
    @FXML
    private VBox advancedPane;

    // API Configuration Tab Controls
    @FXML
    private PasswordField apiKeyField;
    private javafx.scene.control.TextInputControl apiKeyInputControl; // Generic reference
    @FXML
    private Button showApiKeyButton;
    @FXML
    private FontIcon showApiKeyIcon;
    @FXML
    private Button testConnectionButton;
    @FXML
    private HBox connectionStatusContainer;
    @FXML
    private FontIcon connectionStatusIcon;
    @FXML
    private Label connectionStatusLabel;
    private ProgressIndicator connectionStatusProgress;
    @FXML
    private ChoiceBox<String> regionComboBox;
    @FXML
    private Spinner<Integer> timeoutSpinner;
    @FXML
    private Label apiRegionLabel;
    @FXML
    private Label apiRegionDescLabel;
    @FXML
    private Label apiAdvancedLabel;
    @FXML
    private Label apiTimeoutLabel;
    @FXML
    private CheckBox bflProviderEnabledCheckBox;

    // Google Gemini API Configuration Controls
    @FXML
    private PasswordField geminiApiKeyField;
    private javafx.scene.control.TextInputControl geminiApiKeyInputControl; // Generic reference
    @FXML
    private Button showGeminiApiKeyButton;
    @FXML
    private FontIcon showGeminiApiKeyIcon;
    @FXML
    private Button testGeminiConnectionButton;
    @FXML
    private HBox geminiConnectionStatusContainer;
    @FXML
    private FontIcon geminiConnectionStatusIcon;
    @FXML
    private Label geminiConnectionStatusLabel;
    private ProgressIndicator geminiConnectionStatusProgress;
    @FXML
    private CheckBox geminiProviderEnabledCheckBox;

    // OpenAI API Configuration Controls
    @FXML
    private TextField openaiBaseUrlField;
    @FXML
    private PasswordField openaiApiKeyField;
    private javafx.scene.control.TextInputControl openaiApiKeyInputControl; // Generic reference
    @FXML
    private Button showOpenaiApiKeyButton;
    @FXML
    private FontIcon showOpenaiApiKeyIcon;
    @FXML
    private Button testOpenaiConnectionButton;
    @FXML
    private HBox openaiConnectionStatusContainer;
    @FXML
    private FontIcon openaiConnectionStatusIcon;
    @FXML
    private Label openaiConnectionStatusLabel;
    private ProgressIndicator openaiConnectionStatusProgress;
    @FXML
    private CheckBox openaiProviderEnabledCheckBox;
    @FXML
    private VBox openaiModelsContainer;
    @FXML
    private ListView<String> openaiModelList;
    @FXML
    private ComboBox<String> openaiChatModelBox;

    // Appearance Tab Controls
    @FXML
    private VBox themeSelectionContainer;
    @FXML
    private Button previewThemeButton;
    @FXML
    private Button resetThemeButton;
    @FXML
    private HBox themePreviewContainer;
    @FXML
    private Label themeTitleLabel;
    @FXML
    private Label themeDescLabel;
    @FXML
    private Label themeChangesLabel;
    @FXML
    private CheckBox backgroundImageCheckBox;
    @FXML
    private Label backgroundImageDescLabel;
    @FXML
    private Label themePreviewLabel;
    @FXML
    private Label themeSampleLabel;

    // Language Tab Controls
    @FXML
    private VBox languageSelectionContainer;
    @FXML
    private RadioButton germanRadio;
    @FXML
    private RadioButton englishRadio;
    @FXML
    private Label languageTitleLabel;
    @FXML
    private Label languageDescLabel;
    @FXML
    private Label languageChangesLabel;

    // Corporate Proxy Tab Controls
    @FXML
    private CheckBox enableProxyCheckBox;
    @FXML
    private TextField proxyHostField;
    @FXML
    private Spinner<Integer> proxyPortSpinner;
    @FXML
    private TextField proxyUsernameField;
    @FXML
    private PasswordField proxyPasswordField;
    @FXML
    private Label proxyTitleLabel;
    @FXML
    private Label proxyDescLabel;
    @FXML
    private Label proxyHostLabel;
    @FXML
    private Label proxyPortLabel;
    @FXML
    private Label proxyAuthLabel;
    @FXML
    private Label proxyUsernameLabel;
    @FXML
    private Label proxyPasswordLabel;
    @FXML
    private Label certificatesTitleLabel;
    @FXML
    private Label certificatesDescLabel;
    @FXML
    private Label certificatesDirectoryLabel;
    @FXML
    private Label certificatesUploadTitleLabel;
    @FXML
    private Label certificatesFormatsLabel;
    @FXML
    private Label certificatesInstalledLabel;
    @FXML
    private HBox proxyConfigSourceContainer;
    @FXML
    private FontIcon proxyConfigSourceIcon;
    @FXML
    private Label proxyConfigSourceLabel;
    @FXML
    private Label certificatePathLabel;
    @FXML
    private Button uploadCertificateButton;
    @FXML
    private Label certificateUploadStatus;
    @FXML
    private ListView<String> installedCertificatesListView;
    @FXML
    private Button removeCertificateButton;
    @FXML
    private Button refreshCertificatesButton;
    @FXML
    private Button testProxyConnectionButton;
    @FXML
    private Button resetProxyConfigButton;
    @FXML
    private HBox proxyConnectionStatusContainer;
    @FXML
    private FontIcon proxyConnectionStatusIcon;
    @FXML
    private Label proxyConnectionStatusLabel;
    private ProgressIndicator proxyConnectionStatusProgress;
    @FXML
    private Button testCertificateButton;
    @FXML
    private HBox certificateConnectionStatusContainer;
    @FXML
    private FontIcon certificateConnectionStatusIcon;
    @FXML
    private Label certificateConnectionStatusLabel;
    private ProgressIndicator certificateConnectionStatusProgress;

    // Additional Labels for Live Updates
    @FXML
    private Label settingsLabel;
    @FXML
    private Label apiKeyLabel;
    @FXML
    private Label apiKeyDescLabel;

    // Advanced Tab Controls
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

    // State management
    private ApplicationConfig config;
    private ToggleGroup themeToggleGroup;
    private ToggleGroup languageToggleGroup;
    private boolean apiKeyVisible = false;
    private boolean apiKeyValidated = false;
    private String validatedApiKey = null;
    private boolean openaiApiKeyVisible = false;
    private boolean openaiApiKeyValidated = false;
    private String validatedOpenaiApiKey = null;
    private String validatedOpenaiBaseUrl = null;
    private boolean openaiChatModelUpdating = false;
    private final java.util.Map<String, javafx.beans.property.BooleanProperty> openaiModelChecks = new java.util.LinkedHashMap<>();
    private boolean geminiApiKeyVisible = false;
    private boolean geminiApiKeyValidated = false;
    private String validatedGeminiApiKey = null;
    // Removed animation variables - now using ProgressIndicators instead
    private ObservableList<String> certificatesList;
    private ObservableList<String> categories;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        try {
            // Get application config
            config = ServiceFactory.getInstance().getService(ApplicationConfig.class);
            if (config == null) {
                throw new RuntimeException("ApplicationConfig service not available");
            }

            // Register for I18n change notifications
            I18nManager.addChangeListener(this);

            // Initialize navigation system
            initializeNavigation();

            // Initialize all tabs
            initializeApiConfigTab();
            initializeAppearanceTab();
            initializeLanguageTab();
            initializeCorporateProxyTab();
            initializeAdvancedTab();

            // Load current settings
            loadCurrentSettings();

            // Apply initial localization
            onLanguageChanged();

            // Configure Close button width initially
            Platform.runLater(() -> {
                settingsDialogPane.getButtonTypes().stream()
                        .filter(buttonType -> buttonType == ButtonType.CLOSE)
                        .findFirst()
                        .ifPresent(buttonType -> {
                            Button closeButton = (Button) settingsDialogPane.lookupButton(buttonType);
                            if (closeButton != null) {
                                closeButton.setMinWidth(100);
                            }
                        });
            });

        } catch (Exception e) {
            logger.error("Failed to initialize Settings controller", e);
            showError(I18nManager.getText("error.initialization"),
                    I18nManager.getText("error.settings.load", e.getMessage()));
        }
    }

    /**
     * Initializes the navigation system with ListView categories and content pane
     * management.
     */
    private void initializeNavigation() {
        // Initialize the ListView with localized categories
        updateNavigationCategories();

        settingsCategoryList.setItems(categories);

        // Add selection listener to handle navigation
        settingsCategoryList.getSelectionModel().selectedIndexProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null && newValue.intValue() >= 0) {
                        showContentPane(newValue.intValue());
                    }
                });

        // Set "API Configuration" as selected by default
        settingsCategoryList.getSelectionModel().selectFirst();
    }

    /**
     * Updates the navigation categories with localized text.
     */
    private void updateNavigationCategories() {
        int selectedIndex = settingsCategoryList != null ? settingsCategoryList.getSelectionModel().getSelectedIndex()
                : 0;

        if (categories == null) {
            categories = FXCollections.observableArrayList();
        }

        categories.clear();
        categories.addAll(
                I18nManager.getText("settings.category.api"),
                I18nManager.getText("settings.category.proxy"),
                I18nManager.getText("settings.category.appearance"),
                I18nManager.getText("settings.category.language"),
                I18nManager.getText("settings.category.advanced"));

        // Restore selection after updating
        if (settingsCategoryList != null && selectedIndex >= 0 && selectedIndex < categories.size()) {
            Platform.runLater(() -> settingsCategoryList.getSelectionModel().select(selectedIndex));
        }
    }

    /**
     * Shows the appropriate content pane based on the selected category index.
     */
    private void showContentPane(int categoryIndex) {
        // Hide all content panes first
        apiConfigurationPane.setVisible(false);
        apiConfigurationPane.setManaged(false);
        corporateProxyPane.setVisible(false);
        corporateProxyPane.setManaged(false);
        appearancePane.setVisible(false);
        appearancePane.setManaged(false);
        languagePane.setVisible(false);
        languagePane.setManaged(false);
        advancedPane.setVisible(false);
        advancedPane.setManaged(false);

        // Show the selected content pane
        switch (categoryIndex) {
            case 0: // API Configuration
                apiConfigurationPane.setVisible(true);
                apiConfigurationPane.setManaged(true);
                break;
            case 1: // Corporate Proxy
                corporateProxyPane.setVisible(true);
                corporateProxyPane.setManaged(true);
                break;
            case 2: // Appearance
                appearancePane.setVisible(true);
                appearancePane.setManaged(true);
                break;
            case 3: // Language
                languagePane.setVisible(true);
                languagePane.setManaged(true);
                break;
            case 4: // Advanced
                advancedPane.setVisible(true);
                advancedPane.setManaged(true);
                break;
            default:
                // Default to API Configuration if unknown category
                apiConfigurationPane.setVisible(true);
                apiConfigurationPane.setManaged(true);
                logger.warn("Unknown category selected: {}, defaulting to API Configuration", categoryIndex);
                break;
        }
    }

    /**
     * Initializes the API Configuration tab controls.
     */
    private void initializeApiConfigTab() {
        // Setup region ComboBox
        regionComboBox.getItems().addAll("Global", "EU", "US");
        regionComboBox.setValue("Global");

        // Setup spinners with proper value factories
        timeoutSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(60, 3600, 600));

        // Initialize ProgressIndicator for connection testing with fixed container
        connectionStatusProgress = new ProgressIndicator();
        connectionStatusProgress.setMaxSize(16.0, 16.0);
        connectionStatusProgress.setPrefSize(16.0, 16.0);
        connectionStatusProgress.setMinSize(16.0, 16.0);
        connectionStatusProgress.setVisible(false);

        // Wrap the existing FontIcon and ProgressIndicator in a fixed-width container
        StackPane iconContainer = new StackPane();
        iconContainer.setPrefSize(16.0, 16.0);
        iconContainer.setMinSize(16.0, 16.0);
        iconContainer.setMaxSize(16.0, 16.0);
        iconContainer.getChildren().addAll(connectionStatusIcon, connectionStatusProgress);

        // Replace the FontIcon with the container
        connectionStatusContainer.getChildren().remove(connectionStatusIcon);
        connectionStatusContainer.getChildren().add(0, iconContainer);

        // Initially hide connection status
        connectionStatusContainer.setVisible(false);

        // Initialize generic field reference
        apiKeyInputControl = apiKeyField;

        // Initialize Gemini API controls
        geminiConnectionStatusProgress = new ProgressIndicator();
        geminiConnectionStatusProgress.setMaxSize(16.0, 16.0);
        geminiConnectionStatusProgress.setPrefSize(16.0, 16.0);
        geminiConnectionStatusProgress.setMinSize(16.0, 16.0);
        geminiConnectionStatusProgress.setVisible(false);

        // Wrap the Gemini icon and progress indicator
        StackPane geminiIconContainer = new StackPane();
        geminiIconContainer.setPrefSize(16.0, 16.0);
        geminiIconContainer.setMinSize(16.0, 16.0);
        geminiIconContainer.setMaxSize(16.0, 16.0);
        geminiIconContainer.getChildren().addAll(geminiConnectionStatusIcon, geminiConnectionStatusProgress);

        // Replace the Gemini FontIcon with the container
        geminiConnectionStatusContainer.getChildren().remove(geminiConnectionStatusIcon);
        geminiConnectionStatusContainer.getChildren().add(0, geminiIconContainer);

        // Initially hide Gemini connection status
        geminiConnectionStatusContainer.setVisible(false);

        // Initialize Gemini generic field reference
        geminiApiKeyInputControl = geminiApiKeyField;

        // Initialize OpenAI API controls
        openaiConnectionStatusProgress = new ProgressIndicator();
        openaiConnectionStatusProgress.setMaxSize(16.0, 16.0);
        openaiConnectionStatusProgress.setPrefSize(16.0, 16.0);
        openaiConnectionStatusProgress.setMinSize(16.0, 16.0);
        openaiConnectionStatusProgress.setVisible(false);

        StackPane openaiIconContainer = new StackPane();
        openaiIconContainer.setPrefSize(16.0, 16.0);
        openaiIconContainer.setMinSize(16.0, 16.0);
        openaiIconContainer.setMaxSize(16.0, 16.0);
        openaiIconContainer.getChildren().addAll(openaiConnectionStatusIcon, openaiConnectionStatusProgress);
        openaiConnectionStatusContainer.getChildren().remove(openaiConnectionStatusIcon);
        openaiConnectionStatusContainer.getChildren().add(0, openaiIconContainer);
        openaiConnectionStatusContainer.setVisible(false);
        openaiApiKeyInputControl = openaiApiKeyField;
        openaiChatModelBox.valueProperty().addListener((obs, old, chosen) -> onOpenaiChatModelChosen(chosen));
        openaiModelList.setCellFactory(javafx.scene.control.cell.CheckBoxListCell.forListView(openaiModelChecks::get));

        // Initialize provider checkboxes based on validation status AND API key presence
        bflProviderEnabledCheckBox.setSelected(config.isBflProviderEnabled());
        // Checkbox is only enabled if API key exists AND is validated
        boolean bflCanBeEnabled = config.hasApiKey() && config.isBflProviderValidated();
        bflProviderEnabledCheckBox.setDisable(!bflCanBeEnabled);

        geminiProviderEnabledCheckBox.setSelected(config.isGeminiProviderEnabled());
        // Checkbox is only enabled if API key exists AND is validated
        boolean geminiCanBeEnabled = config.hasGeminiApiKey() && config.isGeminiProviderValidated();
        geminiProviderEnabledCheckBox.setDisable(!geminiCanBeEnabled);

        openaiProviderEnabledCheckBox.setSelected(config.isOpenAiProviderEnabled());
        boolean openaiCanBeEnabled = config.hasOpenAiApiKey() && config.isOpenAiProviderValidated();
        openaiProviderEnabledCheckBox.setDisable(!openaiCanBeEnabled);
    }

    /**
     * Initializes the Appearance tab controls.
     */
    private void initializeAppearanceTab() {
        // Create theme toggle group
        themeToggleGroup = new ToggleGroup();

        // Add theme options
        String[] themes = ThemeManager.getAvailableThemes();
        for (String theme : themes) {
            RadioButton themeRadio = new RadioButton(ThemeManager.getThemeDisplayName(theme));
            themeRadio.setUserData(theme);
            themeRadio.setToggleGroup(themeToggleGroup);
            themeSelectionContainer.getChildren().add(themeRadio);
        }

        // Add change listener for live theme preview
        themeToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                String themeName = (String) newToggle.getUserData();
                ThemeManager.applyTheme(themeName);
            }
        });

        /*  background image of the main window, applied at once  */
        if (backgroundImageCheckBox != null) {
            backgroundImageCheckBox.setSelected(config == null || config.isBackgroundImageEnabled());
            backgroundImageCheckBox.selectedProperty().addListener((obs, was, on) -> {
                if (config != null)
                    config.setBackgroundImageEnabled(on);
                ThemeManager.notifyAppearanceChanged();
            });
        }
    }

    /**
     * Initializes the Language tab controls.
     */
    private void initializeLanguageTab() {
        // Create language toggle group
        languageToggleGroup = new ToggleGroup();
        germanRadio.setToggleGroup(languageToggleGroup);
        englishRadio.setToggleGroup(languageToggleGroup);

        // Add change listener for live language preview
        languageToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                String language = newToggle == germanRadio ? "de" : "en";
                handleLanguageChange(language);
            }
        });
    }

    /**
     * Initializes the Corporate Proxy tab controls.
     */
    private void initializeCorporateProxyTab() {
        try {
            // Initialize corporate proxy configuration
            new CorporateProxyConfig(config);

            // Setup proxy port spinner
            proxyPortSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 65535, 8080));

            // Initialize certificates list
            certificatesList = FXCollections.observableArrayList();
            installedCertificatesListView.setItems(certificatesList);

            // Set up ListView appearance
            installedCertificatesListView
                    .setPlaceholder(new Label(I18nManager.getText("settings.certificates.none.found")));
            installedCertificatesListView.setPrefHeight(120.0);
            installedCertificatesListView.setMaxHeight(150.0);

            // Add selection listener for certificate list
            installedCertificatesListView.getSelectionModel().selectedItemProperty().addListener(
                    (obs, oldSelection, newSelection) -> {
                        boolean hasSelection = newSelection != null && !newSelection.trim().isEmpty();
                        removeCertificateButton.setDisable(!hasSelection);

                        if (hasSelection) {
                            // Certificate selected
                        }
                    });

            // Enable single selection mode
            installedCertificatesListView.getSelectionModel()
                    .setSelectionMode(javafx.scene.control.SelectionMode.SINGLE);

            // Initialize ProgressIndicators for proxy and certificate testing with fixed
            // containers
            proxyConnectionStatusProgress = new ProgressIndicator();
            proxyConnectionStatusProgress.setMaxSize(16.0, 16.0);
            proxyConnectionStatusProgress.setPrefSize(16.0, 16.0);
            proxyConnectionStatusProgress.setMinSize(16.0, 16.0);
            proxyConnectionStatusProgress.setVisible(false);

            // Wrap proxy icon and progress in fixed-width container
            StackPane proxyIconContainer = new StackPane();
            proxyIconContainer.setPrefSize(16.0, 16.0);
            proxyIconContainer.setMinSize(16.0, 16.0);
            proxyIconContainer.setMaxSize(16.0, 16.0);
            proxyIconContainer.getChildren().addAll(proxyConnectionStatusIcon, proxyConnectionStatusProgress);
            proxyConnectionStatusContainer.getChildren().remove(proxyConnectionStatusIcon);
            proxyConnectionStatusContainer.getChildren().add(0, proxyIconContainer);

            certificateConnectionStatusProgress = new ProgressIndicator();
            certificateConnectionStatusProgress.setMaxSize(16.0, 16.0);
            certificateConnectionStatusProgress.setPrefSize(16.0, 16.0);
            certificateConnectionStatusProgress.setMinSize(16.0, 16.0);
            certificateConnectionStatusProgress.setVisible(false);

            // Wrap certificate icon and progress in fixed-width container
            StackPane certificateIconContainer = new StackPane();
            certificateIconContainer.setPrefSize(16.0, 16.0);
            certificateIconContainer.setMinSize(16.0, 16.0);
            certificateIconContainer.setMaxSize(16.0, 16.0);
            certificateIconContainer.getChildren().addAll(certificateConnectionStatusIcon,
                    certificateConnectionStatusProgress);
            certificateConnectionStatusContainer.getChildren().remove(certificateConnectionStatusIcon);
            certificateConnectionStatusContainer.getChildren().add(0, certificateIconContainer);

            // Initially hide proxy status containers
            proxyConfigSourceContainer.setVisible(false);
            proxyConnectionStatusContainer.setVisible(false);
            certificateConnectionStatusContainer.setVisible(false);

            // Add proxy enable/disable listener
            enableProxyCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                updateProxyFieldsEnabledState();
            });

        } catch (Exception e) {
            logger.error("Failed to initialize Corporate Proxy tab", e);
            showError("Initialization Error", "Failed to initialize Corporate Proxy settings: " + e.getMessage());
        }
    }

    /**
     * Updates the enabled state of proxy configuration fields.
     */
    private void updateProxyFieldsEnabledState() {
        boolean proxyEnabled = enableProxyCheckBox.isSelected();
        boolean isEditable = config.isProxyConfigurationEditable();

        proxyHostField.setDisable(!proxyEnabled || !isEditable);
        proxyPortSpinner.setDisable(!proxyEnabled || !isEditable);
        proxyUsernameField.setDisable(!proxyEnabled || !isEditable);
        proxyPasswordField.setDisable(!proxyEnabled || !isEditable);
        testProxyConnectionButton.setDisable(!proxyEnabled);

        if (!isEditable) {
            showProxyConfigSourceInfo();
        }
    }

    /**
     * Shows information about the proxy configuration source.
     */
    private void showProxyConfigSourceInfo() {
        String source = config.getProxyConfigurationSource();
        if (!"none".equals(source) && !"configuration".equals(source)) {
            String sourceText = switch (source) {
                case "environment" -> "Proxy configuration loaded from environment variables (read-only)";
                case "system_property" -> "Proxy configuration loaded from system properties (read-only)";
                default -> "Proxy configuration source: " + source;
            };

            proxyConfigSourceContainer.setVisible(true);
            proxyConfigSourceIcon.setIconLiteral("mdi2i-information");
            proxyConfigSourceIcon.getStyleClass().clear();
            proxyConfigSourceIcon.getStyleClass().addAll("ikonli-font-icon", "accent");
            proxyConfigSourceLabel.setText(sourceText);
        } else {
            proxyConfigSourceContainer.setVisible(false);
        }
    }

    /**
     * Initializes the Advanced tab controls.
     */
    private void initializeAdvancedTab() {
        // Setup cache size spinner

        // Set version and config path
        versionLabel.setText(art.moodframe.util.VersionManager.getApplicationNameWithVersion());
        configPathLabel.setText(System.getProperty("user.home") + "/.moodframe/");
    }

    /**
     * Loads current settings from configuration into UI controls.
     */
    private void loadCurrentSettings() {
        try {
            // Load API settings - handle different sources
            String apiKeySource = config.getApiKeySource();
            String apiKey = config.getApiKey();

            if (apiKey != null && !apiKey.trim().isEmpty()) {
                apiKeyInputControl.setText(apiKey);
                // If API key exists from any source, consider it validated
                // (Environment/System keys are trusted, Config keys were validated before
                // saving)
                apiKeyValidated = true;
                validatedApiKey = apiKey;
            } else {
                // No BFL key - ensure checkbox is disabled and unchecked
                bflProviderEnabledCheckBox.setDisable(true);
                bflProviderEnabledCheckBox.setSelected(false);
                // Also update config to ensure consistency
                if (config.isBflProviderEnabled()) {
                    config.setBflProviderEnabled(false);
                }
            }

            // Update UI based on API key source
            boolean isEditable = config.isApiKeyEditable();
            apiKeyInputControl.setDisable(!isEditable);
            showApiKeyButton.setDisable(!isEditable);
            testConnectionButton.setDisable(!isEditable);

            // Show source information
            if (!"none".equals(apiKeySource) && !"configuration".equals(apiKeySource)) {
                String sourceText = switch (apiKeySource) {
                    case "environment" -> "API key loaded from environment variable BFL_API_KEY (read-only)";
                    case "system_property" -> "API key loaded from system property bfl.api.key (read-only)";
                    default -> "";
                };
                showConnectionStatus("info", sourceText);
            } else if (apiKeyValidated && "configuration".equals(apiKeySource)) {
                showConnectionStatus("success", I18nManager.getText("settings.test.api.validated.ready"));
            }

            // Add text change listener to reset validation when key is modified
            if (isEditable) {
                apiKeyInputControl.textProperty().addListener((obs, oldText, newText) -> {
                    if (!newText.equals(validatedApiKey)) {
                        apiKeyValidated = false;
                        validatedApiKey = null;
                        connectionStatusContainer.setVisible(false);
                    }
                });
            }

            timeoutSpinner.getValueFactory().setValue(config.getApiTimeoutSeconds());

            // Load region settings
            String currentRegion = config.getApiRegion();
            regionComboBox.setValue(currentRegion);

            // Load Gemini API settings
            String geminiApiKeySource = config.getGeminiApiKeySource();
            String geminiApiKey = config.getGeminiApiKey();

            if (geminiApiKey != null && !geminiApiKey.trim().isEmpty()) {
                geminiApiKeyInputControl.setText(geminiApiKey);
                geminiApiKeyValidated = true;
                validatedGeminiApiKey = geminiApiKey;
            } else {
                // No Gemini key - ensure checkbox is disabled and unchecked
                geminiProviderEnabledCheckBox.setDisable(true);
                geminiProviderEnabledCheckBox.setSelected(false);
                // Also update config to ensure consistency
                if (config.isGeminiProviderEnabled()) {
                    config.setGeminiProviderEnabled(false);
                }
            }

            // Update Gemini UI based on API key source
            boolean geminiIsEditable = config.isGeminiApiKeyEditable();
            geminiApiKeyInputControl.setDisable(!geminiIsEditable);
            showGeminiApiKeyButton.setDisable(!geminiIsEditable);
            testGeminiConnectionButton.setDisable(!geminiIsEditable);

            // Show Gemini source information
            if (!"none".equals(geminiApiKeySource) && !"configuration".equals(geminiApiKeySource)) {
                String sourceText = switch (geminiApiKeySource) {
                    case "environment" -> "Gemini API key loaded from environment variable GEMINI_API_KEY (read-only)";
                    case "system_property" -> "Gemini API key loaded from system property gemini.api.key (read-only)";
                    default -> "";
                };
                showGeminiConnectionStatus("info", sourceText);
            } else if (geminiApiKeyValidated && "configuration".equals(geminiApiKeySource)) {
                showGeminiConnectionStatus("success", I18nManager.getText("settings.gemini.validated.ready"));
            }

            // Add text change listener to reset Gemini validation when key is modified
            if (geminiIsEditable) {
                geminiApiKeyInputControl.textProperty().addListener((obs, oldText, newText) -> {
                    if (!newText.equals(validatedGeminiApiKey)) {
                        geminiApiKeyValidated = false;
                        validatedGeminiApiKey = null;
                        geminiConnectionStatusContainer.setVisible(false);
                        // Disable checkbox when key changes
                        geminiProviderEnabledCheckBox.setDisable(true);
                        geminiProviderEnabledCheckBox.setSelected(false);
                    }
                });
            }

            // Load OpenAI API settings
            String openaiApiKeySource = config.getOpenAiApiKeySource();
            String openaiApiKey = config.getOpenAiApiKey();
            openaiBaseUrlField.setText(config.getOpenAiBaseUrl());

            if (openaiApiKey != null && !openaiApiKey.trim().isEmpty()) {
                openaiApiKeyInputControl.setText(openaiApiKey);
                openaiApiKeyValidated = true;
                validatedOpenaiApiKey = openaiApiKey;
                validatedOpenaiBaseUrl = config.getOpenAiBaseUrl();
            } else {
                openaiProviderEnabledCheckBox.setDisable(true);
                openaiProviderEnabledCheckBox.setSelected(false);
                if (config.isOpenAiProviderEnabled()) {
                    config.setOpenAiProviderEnabled(false);
                }
            }

            boolean openaiIsEditable = config.isOpenAiApiKeyEditable();
            openaiApiKeyInputControl.setDisable(!openaiIsEditable);
            showOpenaiApiKeyButton.setDisable(!openaiIsEditable);

            if (!"none".equals(openaiApiKeySource) && !"configuration".equals(openaiApiKeySource)) {
                String sourceText = switch (openaiApiKeySource) {
                    case "environment" -> "OpenAI API key loaded from environment variable OPENAI_API_KEY (read-only)";
                    case "system_property" -> "OpenAI API key loaded from system property openai.api.key (read-only)";
                    default -> "OpenAI API key loaded from " + openaiApiKeySource;
                };
                showOpenaiConnectionStatus("info", sourceText);
            } else if (openaiApiKeyValidated) {
                showOpenaiConnectionStatus("success", java.text.MessageFormat.format(
                        I18nManager.getText("settings.openai.validated.ready"), config.getOpenAiModels().size()));
                showOpenaiModels(config.getOpenAiAvailableModels(), config.getOpenAiModels());
            }

            /*  A changed token or base URL needs a new connection test  */
            javafx.beans.value.ChangeListener<String> openaiResetListener = (obs, oldText, newText) -> resetOpenaiValidation();
            if (openaiIsEditable) {
                openaiApiKeyInputControl.textProperty().addListener(openaiResetListener);
            }
            openaiBaseUrlField.textProperty().addListener(openaiResetListener);

            // Load logging settings
            enableLoggingCheckBox.setSelected(config.getLoggingEnabled());

            // Load theme settings
            String currentTheme = config.getUiTheme();
            for (Toggle toggle : themeToggleGroup.getToggles()) {
                if (toggle.getUserData().equals(currentTheme)) {
                    toggle.setSelected(true);
                    break;
                }
            }

            // Load language settings and apply to I18nManager
            String currentLanguage = config.getUiLanguage();
            if ("de".equals(currentLanguage)) {
                germanRadio.setSelected(true);
                I18nManager.setLocale(java.util.Locale.GERMAN);
            } else {
                englishRadio.setSelected(true);
                I18nManager.setLocale(java.util.Locale.ENGLISH);
            }

            // Load proxy settings
            loadProxySettings();

        } catch (Exception e) {
            logger.error("Failed to load current settings", e);
            showError("Settings Load Error", "Failed to load current settings: " + e.getMessage());
        }
    }

    /**
     * Loads current proxy settings from configuration into UI controls.
     */
    private void loadProxySettings() {
        try {
            // Load proxy enable state
            enableProxyCheckBox.setSelected(config.isProxyEnabled());

            // Load proxy host and port
            String proxyHost = config.getProxyHost();
            if (proxyHost != null) {
                proxyHostField.setText(proxyHost);
            }

            proxyPortSpinner.getValueFactory().setValue(config.getProxyPort());

            // Load proxy credentials
            String proxyUsername = config.getProxyUsername();
            if (proxyUsername != null) {
                proxyUsernameField.setText(proxyUsername);
            }

            String proxyPassword = config.getProxyPassword();
            if (proxyPassword != null) {
                proxyPasswordField.setText(proxyPassword);
            }

            // Load certificate path (auto-detected)
            String certPath = config.getCertificatePath();
            certificatePathLabel.setText(certPath);

            // Update field enabled states
            updateProxyFieldsEnabledState();

            // Load installed certificates
            loadInstalledCertificates();

        } catch (Exception e) {
            logger.error("Failed to load proxy settings", e);
            showError("Proxy Settings Load Error", "Failed to load proxy settings: " + e.getMessage());
        }
    }

    /**
     * Loads the list of installed certificates from the certificate directory.
     */
    private void loadInstalledCertificates() {
        try {
            // Remember current selection to restore it after reload
            String previousSelection = installedCertificatesListView.getSelectionModel().getSelectedItem();

            certificatesList.clear();

            // Use auto-detected certificate path
            String certPathStr = config.getCertificatePath();

            Path certDirectory = Paths.get(certPathStr.trim());
            if (Files.exists(certDirectory) && Files.isDirectory(certDirectory)) {
                List<String> certFiles = Files.list(certDirectory)
                        .filter(path -> Files.isRegularFile(path))
                        .filter(path -> {
                            String fileName = path.getFileName().toString().toLowerCase();
                            return fileName.endsWith(".crt") || fileName.endsWith(".cer") || fileName.endsWith(".pem");
                        })
                        .map(path -> path.getFileName().toString())
                        .sorted()
                        .collect(Collectors.toList());

                certificatesList.addAll(certFiles);

                if (certFiles.isEmpty()) {
                    certificateUploadStatus.setText(I18nManager.getText("settings.certificates.none.found"));
                    removeCertificateButton.setDisable(true);
                } else {
                    certificateUploadStatus
                            .setText(I18nManager.getText("settings.certificates.count.found", certFiles.size()));

                    // Restore previous selection if it still exists
                    if (previousSelection != null && certFiles.contains(previousSelection)) {
                        installedCertificatesListView.getSelectionModel().select(previousSelection);
                    } else {
                        // Clear selection if previous item no longer exists
                        installedCertificatesListView.getSelectionModel().clearSelection();
                        removeCertificateButton.setDisable(true);
                    }
                }
            } else {
                // Directory doesn't exist - will be created when uploading certificates
                certificateUploadStatus.setText("Certificate directory will be created when uploading");
                removeCertificateButton.setDisable(true);
            }

        } catch (Exception e) {
            logger.error("Failed to load installed certificates", e);
            certificateUploadStatus.setText("Error loading certificates");
            removeCertificateButton.setDisable(true);
        }
    }

    // Event Handlers

    /**
     * Shows the result of a test in a status line: icon and color for {@code success}, {@code error} and
     * {@code info}, a spinner for {@code testing}.
     */
    private static void showStatus(HBox container, Label label, FontIcon icon, ProgressIndicator progress,
            String status, String message) {
        container.setVisible(true);
        label.setText(message);
        label.getStyleClass().clear();
        progress.setVisible(false);
        icon.setVisible(true);
        icon.getStyleClass().clear();
        icon.getStyleClass().add("ikonli-font-icon");

        switch (status) {
            case "success" -> {
                icon.setIconLiteral("mdi2c-check-circle");
                icon.getStyleClass().add("success");
                label.setStyle("-fx-text-fill: -color-success-emphasis;");
            }
            case "error" -> {
                icon.setIconLiteral("mdi2c-close-circle");
                icon.getStyleClass().add("danger");
                label.setStyle("-fx-text-fill: -color-danger-emphasis;");
            }
            case "testing" -> {
                icon.setVisible(false);
                progress.setVisible(true);
                progress.setProgress(-1.0);
                label.setStyle("-fx-text-fill: -color-accent-emphasis;");
            }
            case "info" -> {
                icon.setIconLiteral("mdi2i-information");
                icon.getStyleClass().add("accent");
                label.setStyle("-fx-text-fill: -color-accent-emphasis;");
            }
            default -> {
            }
        }
    }

    /**
     * Replaces a token field by a field that shows the token as text, or back by a password field. The text,
     * width and place in the row stay.
     *
     * @param current  the field shown now
     * @param showText true for a text field, false for a password field
     * @param prompt   the prompt text of the new field
     * @return the new field
     */
    private static javafx.scene.control.TextInputControl swapKeyField(javafx.scene.control.TextInputControl current,
            boolean showText, String prompt) {
        HBox row = (HBox) current.getParent();
        int index = row.getChildren().indexOf(current);
        javafx.scene.control.TextInputControl field = showText ? new TextField() : new PasswordField();
        field.setText(current.getText());
        field.setPromptText(prompt);
        field.setPrefWidth(current.getPrefWidth());
        field.setMaxWidth(current.getMaxWidth());
        HBox.setHgrow(field, javafx.scene.layout.Priority.ALWAYS);
        row.getChildren().remove(current);
        row.getChildren().add(index, field);
        return field;
    }

    /**
     * Handles the show/hide API key button click.
     * Dynamically switches between PasswordField and TextField for security.
     */
    @FXML
    private void onShowApiKeyToggle() {
        try {
            apiKeyInputControl = swapKeyField(apiKeyInputControl, !apiKeyVisible, "Enter your BFL API key...");
            if (apiKeyInputControl instanceof PasswordField passwordField)
                apiKeyField = passwordField; // Keep FXML reference for compatibility
            showApiKeyIcon.setIconLiteral(apiKeyVisible ? "mdi2e-eye" : "mdi2e-eye-off");
            apiKeyVisible = !apiKeyVisible;
            apiKeyInputControl.requestFocus();
        } catch (Exception e) {
            logger.error("Failed to toggle API key visibility", e);
            showError("Toggle Error", "Failed to toggle API key visibility");
        }
    }

    /**
     * Handles the test connection button click.
     */
    @FXML
    private void onTestConnection() {
        String apiKey = apiKeyInputControl.getText();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            showConnectionStatus("error", I18nManager.getText("settings.test.api.enter.key"));
            return;
        }

        String selectedRegion = regionComboBox.getValue();
        showConnectionStatus("testing", I18nManager.getText("settings.test.api.testing.status", selectedRegion));
        runConnectionTest(testConnectionButton, I18nManager.getText("settings.api.test"), () -> {
            BflApiService apiService = ServiceFactory.getInstance().getService(BflApiService.class);
            if (apiService == null)
                throw new RuntimeException("BFL API service not available");
            apiService.setApiKey(apiKey);

            /*  The test runs against the selected region, the saved one comes back afterwards  */
            String currentRegion = config.getApiRegion();
            boolean otherRegion = selectedRegion != null && !selectedRegion.equals(currentRegion);
            if (otherRegion)
                config.setApiRegion(selectedRegion);
            try {
                return apiService.testConnection().get();
            } finally {
                if (otherRegion)
                    config.setApiRegion(currentRegion);
            }
        }, (success) -> {
            String testedRegion = regionComboBox.getValue();
            apiKeyValidated = success;
            validatedApiKey = success ? apiKey : null;
            if (success) {
                showConnectionStatus("success", I18nManager.getText("settings.test.api.connection.success", testedRegion));
            } else {
                showConnectionStatus("error", I18nManager.getText("settings.test.api.connection.failed", testedRegion));
                restoreSavedKey("bfl");
                logger.warn("API key validation failed for region: {}", testedRegion);
            }
        }, (exception) -> {
            showConnectionStatus("error", bflTestError(exception));
            apiKeyValidated = false;
            validatedApiKey = null;
            restoreSavedKey("bfl");
            logger.warn("API connection test failed: {}", exception != null ? exception.getMessage() : "Unknown error");
        });
    }

    /** The message for a BFL connection test that ended with an exception. */
    private static String bflTestError(Throwable exception) {
        String message = exception != null ? exception.getMessage() : null;
        if (message == null)
            return "Connection test failed";
        if (message.contains("Unauthorized") || message.contains("401"))
            return "Invalid API key - Authentication failed";
        if (message.contains("Forbidden") || message.contains("403"))
            return "API key lacks necessary permissions";
        if (message.contains("timeout") || message.contains("SocketTimeout"))
            return "Connection timeout - Check your internet connection";
        if (message.contains("UnknownHost") || message.contains("ConnectException"))
            return "Cannot connect to BFL API - Check internet connection";
        if (message.contains("API key not configured"))
            return "Please enter a valid API key";
        return "Connection test failed: " + message;
    }

    /**
     * Runs a connection test on a worker thread. The button is disabled while it runs; the result, or the
     * exception the test ended with, is handed over on the FX thread.
     */
    private void runConnectionTest(Button button, String buttonText, java.util.concurrent.Callable<Boolean> test,
            java.util.function.Consumer<Boolean> onResult, java.util.function.Consumer<Throwable> onError) {
        button.setDisable(true);
        button.setText(I18nManager.getText("settings.test.testing"));

        javafx.concurrent.Task<Boolean> task = new javafx.concurrent.Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return test.call();
            }
        };
        task.setOnSucceeded((event) -> {
            button.setDisable(false);
            button.setText(buttonText);
            onResult.accept(Boolean.TRUE.equals(task.getValue()));
        });
        task.setOnFailed((event) -> {
            button.setDisable(false);
            button.setText(buttonText);
            onError.accept(task.getException());
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Shows connection status with appropriate theme-aware styling.
     */
    private void showConnectionStatus(String status, String message) {
        showStatus(connectionStatusContainer, connectionStatusLabel, connectionStatusIcon, connectionStatusProgress, status, message);
    }

    // Google Gemini API Handler Methods

    /**
     * Handles the show/hide Gemini API key button click.
     */
    @FXML
    private void onShowGeminiApiKeyToggle() {
        try {
            geminiApiKeyInputControl = swapKeyField(geminiApiKeyInputControl, !geminiApiKeyVisible,
                    I18nManager.getText("settings.gemini.key.placeholder"));
            if (geminiApiKeyInputControl instanceof PasswordField passwordField)
                geminiApiKeyField = passwordField;
            showGeminiApiKeyIcon.setIconLiteral(geminiApiKeyVisible ? "mdi2e-eye" : "mdi2e-eye-off");
            geminiApiKeyVisible = !geminiApiKeyVisible;

            // Re-attach the listener for validation reset
            geminiApiKeyInputControl.textProperty().addListener((obs, oldText, newText) -> {
                if (!newText.equals(validatedGeminiApiKey)) {
                    geminiApiKeyValidated = false;
                    validatedGeminiApiKey = null;
                    geminiConnectionStatusContainer.setVisible(false);
                    geminiProviderEnabledCheckBox.setDisable(true);
                    geminiProviderEnabledCheckBox.setSelected(false);
                }
            });
        } catch (Exception e) {
            logger.error("Failed to toggle Gemini API key visibility", e);
        }
    }

    /**
     * Handles the test Gemini connection button click.
     */
    @FXML
    private void onTestGeminiConnection() {
        String apiKey = geminiApiKeyInputControl.getText();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            showGeminiConnectionStatus("error", I18nManager.getText("settings.gemini.enter.key"));
            return;
        }

        showGeminiConnectionStatus("testing", I18nManager.getText("settings.gemini.testing.status"));
        runConnectionTest(testGeminiConnectionButton, I18nManager.getText("settings.gemini.test"), () -> {
            try {
                art.moodframe.service.GeminiApiService apiService = ServiceFactory.getInstance()
                        .getService(art.moodframe.service.GeminiApiService.class);
                if (apiService == null)
                    throw new RuntimeException("Gemini API service not available");
                apiService.setApiKey(apiKey);
                return apiService.testConnection().get();
            } catch (Exception e) {
                logger.error("Gemini API connection test failed", e);
                return false;
            }
        }, (success) -> {
            if (success) {
                geminiApiKeyValidated = true;
                validatedGeminiApiKey = apiKey;
                showGeminiConnectionStatus("success", I18nManager.getText("settings.gemini.test.success"));

                /*  A failed test disables the provider, so re-enable it after a successful one  */
                geminiProviderEnabledCheckBox.setDisable(false);
                if (!config.isGeminiProviderEnabled()) {
                    geminiProviderEnabledCheckBox.setSelected(true);
                    config.setGeminiProviderEnabled(true);
                }
                config.setGeminiProviderValidated(true);
            } else {
                geminiTestFailed("settings.gemini.test.failed");
            }
        }, (exception) -> geminiTestFailed("settings.gemini.test.error"));
    }

    /** Shows a failed Gemini test, puts the saved token back and switches the provider off. */
    private void geminiTestFailed(String messageKey) {
        geminiApiKeyValidated = false;
        validatedGeminiApiKey = null;
        restoreSavedKey("gemini");
        showGeminiConnectionStatus("error", I18nManager.getText(messageKey));
        geminiProviderEnabledCheckBox.setDisable(true);
        geminiProviderEnabledCheckBox.setSelected(false);
        config.setGeminiProviderValidated(false);
        config.setGeminiProviderEnabled(false);
    }

    /**
     * Resets the OpenAI validation when the token or the base URL differs from the tested ones.
     */
    private void resetOpenaiValidation() {
        if (!openaiApiKeyValidated)
            return;
        if (java.util.Objects.equals(openaiApiKeyInputControl.getText(), validatedOpenaiApiKey)
                && java.util.Objects.equals(openaiBaseUrlField.getText().trim().replaceAll("/+$", ""), validatedOpenaiBaseUrl))
            return;

        openaiApiKeyValidated = false;
        validatedOpenaiApiKey = null;
        validatedOpenaiBaseUrl = null;
        openaiConnectionStatusContainer.setVisible(false);
        showOpenaiModels(java.util.List.of(), java.util.List.of());
        openaiProviderEnabledCheckBox.setDisable(true);
        openaiProviderEnabledCheckBox.setSelected(false);
    }

    /**
     * Handles the show/hide OpenAI bearer token button click.
     */
    @FXML
    private void onShowOpenaiApiKeyToggle() {
        try {
            openaiApiKeyInputControl = swapKeyField(openaiApiKeyInputControl, !openaiApiKeyVisible,
                    I18nManager.getText("settings.openai.key.placeholder"));
            showOpenaiApiKeyIcon.setIconLiteral(openaiApiKeyVisible ? "mdi2e-eye" : "mdi2e-eye-off");
            openaiApiKeyVisible = !openaiApiKeyVisible;
            openaiApiKeyInputControl.textProperty().addListener((obs, oldText, newText) -> resetOpenaiValidation());
        } catch (Exception e) {
            logger.error("Failed to toggle OpenAI API key visibility", e);
        }
    }

    /**
     * Handles the test OpenAI connection button click: lists the models with the bearer token and shows
     * the image models found.
     */
    @FXML
    private void onTestOpenaiConnection() {
        String apiKey = openaiApiKeyInputControl.getText();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            showOpenaiConnectionStatus("error", I18nManager.getText("settings.openai.enter.key"));
            return;
        }
        String baseUrl = openaiBaseUrlField.getText() == null || openaiBaseUrlField.getText().isBlank()
                ? art.moodframe.config.ApplicationConfig.OPENAI_DEFAULT_BASE_URL
                : openaiBaseUrlField.getText().trim().replaceAll("/+$", "");

        showOpenaiConnectionStatus("testing", I18nManager.getText("settings.openai.testing.status"));
        runConnectionTest(testOpenaiConnectionButton, I18nManager.getText("settings.openai.test"), () -> {
            try {
                art.moodframe.service.OpenAiApiService apiService = ServiceFactory.getInstance()
                        .getService(art.moodframe.service.OpenAiApiService.class);
                if (apiService == null)
                    throw new RuntimeException("OpenAI API service not available");

                /*  The token and the URL are only set in the service, they are saved with "Save"  */
                apiService.setApiKey(apiKey);
                apiService.setBaseUrl(baseUrl);
                return apiService.testConnection().get();
            } catch (Exception e) {
                logger.error("OpenAI API connection test failed", e);
                return false;
            }
        }, (success) -> {
            if (!success) {
                openaiTestFailed("settings.openai.test.failed");
                return;
            }

            openaiApiKeyValidated = true;
            validatedOpenaiApiKey = apiKey;
            validatedOpenaiBaseUrl = baseUrl;

            /*  The service may have added "/v1"; the field shows it, so "Save" keeps it  */
            art.moodframe.service.OpenAiApiService testedService = ServiceFactory.getInstance()
                    .getService(art.moodframe.service.OpenAiApiService.class);
            if (testedService != null && !baseUrl.equals(testedService.getBaseUrl())) {
                validatedOpenaiBaseUrl = testedService.getBaseUrl();
                openaiBaseUrlField.setText(validatedOpenaiBaseUrl);
            }
            java.util.List<String> available = config.getOpenAiAvailableModels();
            showOpenaiConnectionStatus("success", available.isEmpty()
                    ? I18nManager.getText("settings.openai.test.success.nomodels")
                    : java.text.MessageFormat.format(I18nManager.getText("settings.openai.test.success"),
                            available.size()));
            showOpenaiModels(available, config.getOpenAiModels());

            openaiProviderEnabledCheckBox.setDisable(false);
            if (!config.isOpenAiProviderEnabled()) {
                openaiProviderEnabledCheckBox.setSelected(true);
                config.setOpenAiProviderEnabled(true);
            }
            config.setOpenAiProviderValidated(true);
        }, (exception) -> openaiTestFailed("settings.openai.test.error"));
    }

    /** Shows a failed OpenAI test, puts the saved token and URL back and switches the provider off. */
    private void openaiTestFailed(String messageKey) {
        openaiApiKeyValidated = false;
        validatedOpenaiApiKey = null;
        validatedOpenaiBaseUrl = null;
        restoreSavedKey("openai");
        showOpenaiConnectionStatus("error", I18nManager.getText(messageKey));
        openaiProviderEnabledCheckBox.setDisable(true);
        openaiProviderEnabledCheckBox.setSelected(false);
        config.setOpenAiProviderValidated(false);
        config.setOpenAiProviderEnabled(false);
    }

    /**
     * Puts the saved token (and URL) back into the live service after a failed test, as a test sets the typed
     * values in the service that generation and the brainstorm use.
     *
     * @param provider {@code bfl}, {@code gemini} or {@code openai}
     */
    private void restoreSavedKey(String provider) {
        ServiceFactory factory = ServiceFactory.getInstance();
        switch (provider) {
            case "bfl" -> {
                BflApiService service = factory.getService(BflApiService.class);
                if (service != null)
                    service.setApiKey(config.getApiKey());
            }
            case "gemini" -> {
                art.moodframe.service.GeminiApiService service = factory.getService(art.moodframe.service.GeminiApiService.class);
                if (service != null)
                    service.setApiKey(config.getGeminiApiKey());
            }
            default -> {
                art.moodframe.service.OpenAiApiService service = factory.getService(art.moodframe.service.OpenAiApiService.class);
                if (service != null) {
                    service.setApiKey(config.getOpenAiApiKey());
                    service.setBaseUrl(config.getOpenAiBaseUrl());
                }
            }
        }
    }

    /**
     * Handles OpenAI provider activation checkbox toggle.
     */
    @FXML
    private void onOpenaiProviderToggle() {
        boolean enabled = openaiProviderEnabledCheckBox.isSelected();
        config.setOpenAiProviderEnabled(enabled);
        logger.info("OpenAI provider " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Tests a chosen language model (plain answer and function call). A model that fails is not accepted: the
     * choice goes back to "none" and the status shows the reason.
     *
     * @param chosen the chosen entry of the language model choice
     */
    private void onOpenaiChatModelChosen(String chosen) {
        if (openaiChatModelUpdating || chosen == null || chosen.equals(I18nManager.getText("settings.openai.chat.none")))
            return;

        art.moodframe.service.OpenAiApiService apiService = ServiceFactory.getInstance()
                .getService(art.moodframe.service.OpenAiApiService.class);
        if (apiService == null)
            return;

        openaiChatModelBox.setDisable(true);
        showOpenaiConnectionStatus("testing", I18nManager.getText("settings.openai.chat.testing", chosen));
        apiService.checkChatModel(chosen).whenComplete((String problem, Throwable error) -> javafx.application.Platform.runLater(() -> {
            openaiChatModelBox.setDisable(false);
            String reason = error != null ? error.getMessage() : problem;
            if (reason == null) {
                showOpenaiConnectionStatus("success", I18nManager.getText("settings.openai.chat.ok", chosen));
                return;
            }

            openaiChatModelUpdating = true;
            openaiChatModelBox.setValue(I18nManager.getText("settings.openai.chat.none"));
            openaiChatModelUpdating = false;
            showOpenaiConnectionStatus("error", I18nManager.getText("settings.openai.chat.failed", chosen, reason));
        }));
    }

    /**
     * Shows the models of the OpenAI API with a check box each, the selected ones are the image models.
     *
     * @param available all model ids, hides the list if empty
     * @param selected  the ids to check
     */
    private void showOpenaiModels(java.util.List<String> available, java.util.List<String> selected) {
        openaiModelChecks.clear();
        for (String id : available)
            openaiModelChecks.put(id, new javafx.beans.property.SimpleBooleanProperty(selected.contains(id)));
        openaiModelList.getItems().setAll(available);

        /*  The first entry is "none", the chosen language model stays selected if the API still lists it  */
        String none = I18nManager.getText("settings.openai.chat.none");
        String chatModel = openaiChatModelBox.getValue() == null || openaiChatModelBox.getValue().equals(none)
                ? config.getOpenAiChatModel() : openaiChatModelBox.getValue();
        openaiChatModelUpdating = true;
        openaiChatModelBox.getItems().setAll(none);
        openaiChatModelBox.getItems().addAll(available);
        openaiChatModelBox.setValue(available.contains(chatModel) ? chatModel : none);
        openaiChatModelUpdating = false;
        openaiModelsContainer.setVisible(!available.isEmpty());
        openaiModelsContainer.setManaged(!available.isEmpty());
    }

    /**
     * Shows connection status for OpenAI API testing.
     */
    private void showOpenaiConnectionStatus(String status, String message) {
        showStatus(openaiConnectionStatusContainer, openaiConnectionStatusLabel, openaiConnectionStatusIcon, openaiConnectionStatusProgress, status, message);
    }

    /**
     * Handles BFL provider activation checkbox toggle.
     */
    @FXML
    private void onBflProviderToggle() {
        boolean enabled = bflProviderEnabledCheckBox.isSelected();
        config.setBflProviderEnabled(enabled);
        logger.info("BFL provider " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Handles Gemini provider activation checkbox toggle.
     */
    @FXML
    private void onGeminiProviderToggle() {
        boolean enabled = geminiProviderEnabledCheckBox.isSelected();
        config.setGeminiProviderEnabled(enabled);
        logger.info("Gemini provider " + (enabled ? "enabled" : "disabled"));
    }

    /**
     * Shows connection status for Gemini API testing.
     */
    private void showGeminiConnectionStatus(String status, String message) {
        showStatus(geminiConnectionStatusContainer, geminiConnectionStatusLabel, geminiConnectionStatusIcon, geminiConnectionStatusProgress, status, message);
    }

    /**
     * Handles theme preview button click (now redundant due to live preview).
     */
    @FXML
    private void onPreviewTheme() {
        // This method is now redundant since theme changes apply immediately
        // through the live preview listener, but kept for compatibility
    }

    /**
     * Handles reset to system theme button click.
     */
    @FXML
    private void onResetToSystemTheme() {
        String systemTheme = ThemeManager.getSystemPreferredTheme();
        ThemeManager.applyTheme(systemTheme);

        // Update radio button selection
        for (Toggle toggle : themeToggleGroup.getToggles()) {
            if (toggle.getUserData().equals(systemTheme)) {
                toggle.setSelected(true);
                break;
            }
        }

    }

    /**
     * Handles the export settings button click.
     */
    @FXML
    private void onExportSettings() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Configuration");

            // Set default filename with timestamp
            art.moodframe.service.ConfigurationExporter exporter = new art.moodframe.service.ConfigurationExporter();
            fileChooser.setInitialFileName(exporter.generateExportFilename());

            // Set initial directory to user's Documents folder
            String documentsPath = System.getProperty("user.home") + "/Documents";
            java.io.File documentsDir = new java.io.File(documentsPath);
            if (documentsDir.exists()) {
                fileChooser.setInitialDirectory(documentsDir);
            }

            java.io.File exportFile = fileChooser.showSaveDialog(exportSettingsButton.getScene().getWindow());
            if (exportFile != null) {
                // Export all configuration
                exporter.exportConfiguration(config, exportFile);

                showInfo("Export Successful",
                        String.format("Configuration exported successfully to:\n%s\n\nFile size: %.1f KB",
                                exportFile.getAbsolutePath(), exportFile.length() / 1024.0));

            }

        } catch (Exception e) {
            logger.error("Failed to export configuration", e);
            showError("Export Failed", "Failed to export configuration: " + e.getMessage());
        }
    }

    /**
     * Handles the import settings button click.
     */
    @FXML
    private void onImportSettings() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Import Configuration");

            // Set initial directory to user's Documents folder
            String documentsPath = System.getProperty("user.home") + "/Documents";
            java.io.File documentsDir = new java.io.File(documentsPath);
            if (documentsDir.exists()) {
                fileChooser.setInitialDirectory(documentsDir);
            }

            java.io.File importFile = fileChooser.showOpenDialog(importSettingsButton.getScene().getWindow());
            if (importFile != null) {
                art.moodframe.service.ConfigurationExporter exporter = new art.moodframe.service.ConfigurationExporter();
                art.moodframe.service.ConfigurationExporter.ImportResult result = exporter.importConfiguration(importFile,
                        config);

                if (result.isSuccess()) {
                    if (result.getChangeCount() > 0) {
                        // Show confirmation dialog with changes
                        boolean confirmed = showImportConfirmationDialog(result);
                        if (confirmed) {
                            // Configuration is auto-saved when properties are set

                            // Refresh UI to reflect changes
                            loadCurrentSettings();

                            showInfo("Import Successful",
                                    String.format(
                                            "Configuration imported successfully!\n\n%d settings were updated.\n\nRestart the application to ensure all changes take effect.",
                                            result.getChangeCount()));
                        } else {
                            // User cancelled, reload original settings
                            loadCurrentSettings();
                        }
                    } else {
                        showInfo("Import Complete",
                                "Configuration file imported successfully.\nNo changes were needed - your current settings match the imported configuration.");
                    }
                } else {
                    showError("Import Failed", result.getMessage());
                }

            }

        } catch (Exception e) {
            logger.error("Failed to import configuration", e);
            showError("Import Failed", "Failed to import configuration: " + e.getMessage());
        }
    }

    /**
     * Shows a confirmation dialog with the list of changes that will be applied.
     */
    private boolean showImportConfirmationDialog(art.moodframe.service.ConfigurationExporter.ImportResult result) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Configuration Import");
        alert.setHeaderText(String.format("Import will change %d settings", result.getChangeCount()));

        // Create detailed content with changes
        StringBuilder content = new StringBuilder();
        content.append("The following settings will be updated:\n\n");

        for (Map.Entry<String, Object> change : result.getChanges().entrySet()) {
            content.append(String.format("• %s: %s\n", change.getKey(), change.getValue()));
        }

        content.append("\nDo you want to apply these changes?");

        alert.setContentText(content.toString());

        // Set button types
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        Optional<ButtonType> result2 = alert.showAndWait();
        return result2.isPresent() && result2.get() == ButtonType.YES;
    }

    // Corporate Proxy Event Handlers

    // Note: Browse certificate path functionality removed - path is now
    // auto-detected

    /**
     * Handles the upload certificate button click.
     */
    @FXML
    private void onUploadCertificate() {

        // Use auto-detected certificate directory
        String certPathStr = config.getCertificatePath();

        Path certDirectory = Paths.get(certPathStr.trim());
        try {
            if (!Files.exists(certDirectory)) {
                Files.createDirectories(certDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create certificate directory: {}", certDirectory, e);
            showError("Directory Error", "Failed to create certificate directory: " + e.getMessage());
            return;
        }

        // Open file chooser for certificate files
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Certificate Files");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Certificate Files", "*.crt", "*.cer", "*.pem"),
                new FileChooser.ExtensionFilter("CRT Files", "*.crt"),
                new FileChooser.ExtensionFilter("CER Files", "*.cer"),
                new FileChooser.ExtensionFilter("PEM Files", "*.pem"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(uploadCertificateButton.getScene().getWindow());

        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            int successCount = 0;
            int failedCount = 0;
            StringBuilder failedFiles = new StringBuilder();

            for (File sourceFile : selectedFiles) {
                try {
                    Path targetPath = certDirectory.resolve(sourceFile.getName());

                    // Check if file already exists
                    if (Files.exists(targetPath)) {
                        Alert overwriteConfirm = new Alert(Alert.AlertType.CONFIRMATION);
                        overwriteConfirm.setTitle("File Exists");
                        overwriteConfirm.setHeaderText("Overwrite existing certificate?");
                        overwriteConfirm
                                .setContentText("The file '" + sourceFile.getName() + "' already exists. Replace it?");

                        if (overwriteConfirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                            failedCount++;
                            failedFiles.append("\n- ").append(sourceFile.getName()).append(" (skipped)");
                            continue;
                        }
                    }

                    // Copy file to certificate directory
                    Files.copy(sourceFile.toPath(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    successCount++;

                } catch (IOException e) {
                    logger.error("Failed to upload certificate: {}", sourceFile.getName(), e);
                    failedCount++;
                    failedFiles.append("\n- ").append(sourceFile.getName()).append(" (error: ").append(e.getMessage())
                            .append(")");
                }
            }

            // Update certificate list
            loadInstalledCertificates();

            // Show result
            if (successCount > 0 && failedCount == 0) {
                certificateUploadStatus.setText(successCount + " certificate(s) uploaded successfully");
            } else if (successCount > 0 && failedCount > 0) {
                String message = successCount + " certificate(s) uploaded, " + failedCount + " failed:"
                        + failedFiles.toString();
                showError("Partial Upload Success", message);
                certificateUploadStatus.setText(successCount + " uploaded, " + failedCount + " failed");
            } else {
                String message = "All certificate uploads failed:" + failedFiles.toString();
                showError("Upload Failed", message);
                certificateUploadStatus.setText("Upload failed");
            }
        }
    }

    /**
     * Handles the remove certificate button click.
     */
    @FXML
    private void onRemoveCertificate() {
        String selectedCertificate = installedCertificatesListView.getSelectionModel().getSelectedItem();
        if (selectedCertificate == null) {
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Remove Certificate");
        confirmation.setHeaderText("Remove Certificate");
        confirmation.setContentText("Are you sure you want to remove the certificate '" + selectedCertificate + "'?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    String certPathStr = config.getCertificatePath();
                    Path certificatePath = Paths.get(certPathStr, selectedCertificate);

                    if (Files.exists(certificatePath)) {
                        Files.delete(certificatePath);
                        loadInstalledCertificates();

                        certificateUploadStatus.setText("Certificate removed: " + selectedCertificate);
                    } else {
                        showError("File Not Found", "Certificate file not found: " + selectedCertificate);
                    }

                } catch (IOException e) {
                    logger.error("Failed to remove certificate: {}", selectedCertificate, e);
                    showError("Remove Failed", "Failed to remove certificate: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Handles the refresh certificates button click.
     */
    @FXML
    private void onRefreshCertificates() {
        loadInstalledCertificates();
    }

    /**
     * Handles the test proxy connection button click.
     */
    @FXML
    private void onTestProxyConnection() {

        // Disable test button during test
        testProxyConnectionButton.setDisable(true);
        testProxyConnectionButton.setText(I18nManager.getText("settings.test.testing"));

        // Show testing status
        showProxyConnectionStatus("testing", I18nManager.getText("settings.test.proxy.testing.status"));

        // Test proxy connection using BFL API service
        javafx.concurrent.Task<Boolean> testTask = new javafx.concurrent.Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    // Get BFL API service from ServiceFactory
                    BflApiService apiService = ServiceFactory.getInstance().getService(BflApiService.class);
                    if (apiService == null) {
                        throw new RuntimeException("BFL API service not available");
                    }

                    // Test the proxy connection
                    Boolean result = apiService.testConnectionWithProxy().get();
                    return result;

                } catch (Exception e) {
                    logger.warn("Proxy connection test failed: {}", e.getMessage());
                    throw e;
                }
            }
        };

        testTask.setOnSucceeded(e -> {
            boolean success = testTask.getValue();
            if (success) {
                showProxyConnectionStatus("success", I18nManager.getText("settings.test.proxy.success"));
            } else {
                showProxyConnectionStatus("error", I18nManager.getText("settings.test.proxy.failed"));
            }

            testProxyConnectionButton.setDisable(false);
            testProxyConnectionButton.setText(I18nManager.getText("settings.proxy.test"));
        });

        testTask.setOnFailed(e -> {
            Throwable exception = testTask.getException();
            String errorMessage = "Proxy connection test failed";

            if (exception != null) {
                String exceptionMessage = exception.getMessage();
                if (exceptionMessage != null) {
                    if (exceptionMessage.contains("timeout")) {
                        errorMessage = "Connection timeout - Check proxy host and port";
                    } else if (exceptionMessage.contains("refused") || exceptionMessage.contains("ConnectException")) {
                        errorMessage = "Connection refused - Check proxy host and port";
                    } else if (exceptionMessage.contains("certificate") || exceptionMessage.contains("SSL")) {
                        errorMessage = "SSL/Certificate error - Check ZScaler certificates";
                    } else if (exceptionMessage.contains("Unauthorized") || exceptionMessage.contains("407")) {
                        errorMessage = "Proxy authentication failed - Check username and password";
                    } else {
                        errorMessage = "Proxy test failed: " + exceptionMessage;
                    }
                }
            }

            showProxyConnectionStatus("error", errorMessage);

            testProxyConnectionButton.setDisable(false);
            testProxyConnectionButton.setText(I18nManager.getText("settings.proxy.test"));

            logger.warn("Proxy connection test failed: {}",
                    exception != null ? exception.getMessage() : "Unknown error");
        });

        Thread testThread = new Thread(testTask);
        testThread.setDaemon(true);
        testThread.start();
    }

    /**
     * Shows proxy connection status with appropriate styling.
     */
    private void showProxyConnectionStatus(String status, String message) {
        showStatus(proxyConnectionStatusContainer, proxyConnectionStatusLabel, proxyConnectionStatusIcon, proxyConnectionStatusProgress, status, message);
    }

    /**
     * Handles the reset proxy configuration button click.
     */
    @FXML
    private void onResetProxyConfig() {

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Reset Proxy Configuration");
        confirmation.setHeaderText("Reset Proxy Settings");
        confirmation.setContentText("This will clear all proxy settings and certificates. Continue?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // Clear proxy settings
                    config.setProxyHost(null);
                    config.setProxyPort(8080);
                    config.setProxyUsername(null);
                    config.setProxyPassword(null);
                    config.setProxyEnabled(false);
                    // Note: Certificate path is auto-detected, no reset needed

                    // Reload proxy settings in UI
                    loadProxySettings();

                    // Clear status displays
                    proxyConfigSourceContainer.setVisible(false);
                    proxyConnectionStatusContainer.setVisible(false);
                    certificateUploadStatus.setText("");

                    showInfo("Configuration Reset", "Proxy configuration has been reset to defaults.");

                } catch (Exception e) {
                    logger.error("Failed to reset proxy configuration", e);
                    showError("Reset Failed", "Failed to reset proxy configuration: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Handles the test certificate connection button click.
     */
    @FXML
    private void onTestCertificateConnection() {

        // Check if certificates are available
        if (certificatesList.isEmpty()) {
            showCertificateConnectionStatus("error", I18nManager.getText("settings.certificates.none.found"));
            return;
        }

        // Disable test button during test
        testCertificateButton.setDisable(true);
        testCertificateButton.setText(I18nManager.getText("settings.test.testing"));

        // Show testing status
        showCertificateConnectionStatus("testing", I18nManager.getText("settings.test.certificate.testing.status"));

        // Test certificate connection using BFL API service
        javafx.concurrent.Task<Boolean> testTask = new javafx.concurrent.Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    // Get BFL API service from ServiceFactory
                    BflApiService apiService = ServiceFactory.getInstance().getService(BflApiService.class);
                    if (apiService == null) {
                        throw new RuntimeException("BFL API service not available");
                    }

                    // Refresh the HTTP client to use updated certificates
                    apiService.refreshProxyConfiguration();

                    // Test SSL connectivity (no API key needed)
                    Boolean result = apiService.testSslConnection().get();
                    return result;

                } catch (Exception e) {
                    logger.warn("Certificate connection test failed: {}", e.getMessage());
                    throw e;
                }
            }
        };

        testTask.setOnSucceeded(e -> {
            boolean success = testTask.getValue();
            if (success) {
                showCertificateConnectionStatus("success",
                        I18nManager.getText("settings.test.certificate.success"));
            } else {
                showCertificateConnectionStatus("error",
                        "Certificate connection failed - Check certificate validity and format");
            }

            testCertificateButton.setDisable(false);
            testCertificateButton.setText(I18nManager.getText("settings.certificates.test"));
        });

        testTask.setOnFailed(e -> {
            Throwable exception = testTask.getException();
            String errorMessage = "Certificate connection test failed";

            if (exception != null) {
                String exceptionMessage = exception.getMessage();
                if (exceptionMessage != null) {
                    if (exceptionMessage.contains("certificate") || exceptionMessage.contains("SSL")
                            || exceptionMessage.contains("TLS")) {
                        errorMessage = "SSL/Certificate error - Check certificate format and validity";
                    } else if (exceptionMessage.contains("trust") || exceptionMessage.contains("PKIX")) {
                        errorMessage = "Certificate trust error - Certificate may not be properly installed";
                    } else if (exceptionMessage.contains("expired") || exceptionMessage.contains("validity")) {
                        errorMessage = "Certificate expired or not yet valid";
                    } else if (exceptionMessage.contains("format") || exceptionMessage.contains("parsing")) {
                        errorMessage = "Certificate format error - Check file format (.crt, .cer, .pem)";
                    } else {
                        errorMessage = "Certificate test failed: " + exceptionMessage;
                    }
                }
            }

            showCertificateConnectionStatus("error", errorMessage);

            testCertificateButton.setDisable(false);
            testCertificateButton.setText(I18nManager.getText("settings.certificates.test"));

            logger.warn("Certificate connection test failed: {}",
                    exception != null ? exception.getMessage() : "Unknown error");
        });

        Thread testThread = new Thread(testTask);
        testThread.setDaemon(true);
        testThread.start();
    }

    /**
     * Shows certificate connection status with appropriate styling.
     */
    private void showCertificateConnectionStatus(String status, String message) {
        showStatus(certificateConnectionStatusContainer, certificateConnectionStatusLabel, certificateConnectionStatusIcon, certificateConnectionStatusProgress, status, message);
    }

    /**
     * Handles reset settings button click.
     */
    @FXML
    private void onResetSettings() {

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Reset Settings");
        confirmation.setHeaderText("Reset all settings to defaults?");
        confirmation.setContentText(
                "This will reset all settings except API key and proxy password. Custom styles will be deleted. This action cannot be undone.");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    // Reset configuration to defaults (preserves API key and proxy password)
                    config.resetToDefaults();

                    // Delete all custom styles
                    art.moodframe.service.PromptBlockService styleService = ServiceFactory.getInstance()
                            .getService(art.moodframe.service.PromptBlockService.class);
                    if (styleService != null) {
                        styleService.resetToDefaults(art.moodframe.model.PromptBlock.Type.STYLE);
                    }

                    // Refresh all UI elements to show reset values
                    refreshSettingsFromConfig();

                    showInfo("Settings Reset",
                            "All settings have been reset to defaults. API key and proxy password were preserved.");

                } catch (Exception e) {
                    logger.error("Failed to reset settings", e);
                    showError("Reset Failed", "Failed to reset settings: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Refreshes all UI elements to reflect current configuration values.
     * Used after settings reset to update the displayed values.
     */
    private void refreshSettingsFromConfig() {
        try {
            // Refresh API settings
            regionComboBox.setValue(config.getApiRegion());
            timeoutSpinner.getValueFactory().setValue(config.getApiTimeoutSeconds());

            // Refresh UI settings (theme and language)
            // Theme: Reset to default theme
            String defaultTheme = config.getUiTheme();
            ThemeManager.applyTheme(defaultTheme); // Apply the reset theme

            // Update theme radio button selection
            for (Toggle toggle : themeToggleGroup.getToggles()) {
                if (toggle.getUserData().equals(defaultTheme)) {
                    toggle.setSelected(true);
                    break;
                }
            }

            // Language: Update radio buttons based on reset language
            String defaultLanguage = config.getUiLanguage();
            if ("de".equals(defaultLanguage)) {
                germanRadio.setSelected(true);
                englishRadio.setSelected(false);
            } else {
                germanRadio.setSelected(false);
                englishRadio.setSelected(true);
            }

            // Refresh proxy settings (but not password - it's preserved)
            // Load current proxy settings from config (may be defaults after reset)
            enableProxyCheckBox.setSelected(config.isProxyEnabled());

            String proxyHost = config.getProxyHost();
            if (proxyHost != null) {
                proxyHostField.setText(proxyHost);
            } else {
                proxyHostField.clear();
            }

            proxyPortSpinner.getValueFactory().setValue(config.getProxyPort());

            String proxyUsername = config.getProxyUsername();
            if (proxyUsername != null) {
                proxyUsernameField.setText(proxyUsername);
            } else {
                proxyUsernameField.clear();
            }

            // Note: proxyPasswordField is intentionally not cleared (preserved)
            updateProxyFieldsEnabledState();

            // Refresh certificate path (auto-detected)
            certificatePathLabel.setText(config.getCertificatePath());

            // Refresh advanced settings
            enableLoggingCheckBox.setSelected(config.getLoggingEnabled());

        } catch (Exception e) {
            logger.error("Failed to refresh settings UI from configuration", e);
        }
    }

    /**
     * Applies all current settings and closes the dialog.
     * Theme and Language changes are already visible due to live preview.
     */
    public void applySettings() {
        try {

            // Apply API settings (only if editable and validated)
            if (config.isApiKeyEditable()) {
                String currentApiKey = apiKeyInputControl.getText();
                if (currentApiKey != null && !currentApiKey.trim().isEmpty()) {

                    // An unvalidated key is not saved, but all other settings still are
                    if (!apiKeyValidated || !currentApiKey.equals(validatedApiKey)) {
                        showError("API Key Validation Required",
                                "Please test the API key connection before saving.\n" +
                                        "Click the 'Test' button to validate your API key.");
                        logger.warn("Attempted to save unvalidated API key");
                    } else {
                        config.setApiKey(currentApiKey.trim());
                        if (config != null && config.getLoggingEnabled()) {
                            logger.info("Validated API key saved to configuration");
                        }
                    }
                } else if (config.hasApiKey() && "configuration".equals(config.getApiKeySource())) {
                    // User cleared the API key field - remove from config
                    config.setApiKey("");
                    apiKeyValidated = false;
                    validatedApiKey = null;
                    if (config != null && config.getLoggingEnabled()) {
                        logger.info("API key removed from configuration");
                    }
                }
            }

            // Save Gemini API key (only if editable)
            if (config.isGeminiApiKeyEditable()) {
                String currentGeminiApiKey = geminiApiKeyInputControl.getText();

                if (currentGeminiApiKey != null && !currentGeminiApiKey.trim().isEmpty()) {
                    // Only save if validated, all other settings still are
                    if (!geminiApiKeyValidated || !currentGeminiApiKey.equals(validatedGeminiApiKey)) {
                        logger.warn("Attempted to save unvalidated Gemini API key");
                    } else {
                        config.setGeminiApiKey(currentGeminiApiKey.trim());
                        if (config != null && config.getLoggingEnabled()) {
                            logger.info("Validated Gemini API key saved to configuration");
                        }
                    }
                } else if (config.hasGeminiApiKey() && "configuration".equals(config.getGeminiApiKeySource())) {
                    // User cleared the Gemini API key field - remove from config
                    config.setGeminiApiKey("");
                    geminiApiKeyValidated = false;
                    validatedGeminiApiKey = null;
                    if (config != null && config.getLoggingEnabled()) {
                        logger.info("Gemini API key removed from configuration");
                    }
                }
            }

            // Save OpenAI base URL and bearer token (the token only if editable)
            config.setOpenAiBaseUrl(openaiBaseUrlField.getText());
            if (!openaiModelChecks.isEmpty()) {
                String chatModel = openaiChatModelBox.getValue();
                config.setOpenAiChatModel(chatModel == null || chatModel.equals(I18nManager.getText("settings.openai.chat.none"))
                        ? "" : chatModel);
            }
            if (!openaiModelChecks.isEmpty())
                config.setOpenAiModels(openaiModelChecks.entrySet().stream()
                        .filter((e) -> e.getValue().get()).map(java.util.Map.Entry::getKey).toList());
            if (config.isOpenAiApiKeyEditable()) {
                String currentOpenaiApiKey = openaiApiKeyInputControl.getText();

                if (currentOpenaiApiKey != null && !currentOpenaiApiKey.trim().isEmpty()) {
                    // Only save if validated, all other settings still are
                    if (!openaiApiKeyValidated || !currentOpenaiApiKey.equals(validatedOpenaiApiKey)) {
                        logger.warn("Attempted to save unvalidated OpenAI API key");
                    } else {
                        config.setOpenAiApiKey(currentOpenaiApiKey.trim());
                        logger.info("Validated OpenAI API key saved to configuration");
                    }
                } else if (config.hasOpenAiApiKey() && "configuration".equals(config.getOpenAiApiKeySource())) {
                    // User cleared the OpenAI API key field - remove from config
                    config.setOpenAiApiKey("");
                    config.setOpenAiProviderEnabled(false);
                    config.setOpenAiProviderValidated(false);
                    openaiApiKeyValidated = false;
                    validatedOpenaiApiKey = null;
                    logger.info("OpenAI API key removed from configuration");
                }
            }

            config.setProperty("api.timeout_seconds", timeoutSpinner.getValue().toString());

            // Save region settings
            config.setApiRegion(regionComboBox.getValue());

            // Save logging settings
            config.setLoggingEnabled(enableLoggingCheckBox.isSelected());

            // Save current theme settings (already applied via live preview)
            RadioButton selectedTheme = (RadioButton) themeToggleGroup.getSelectedToggle();
            if (selectedTheme != null) {
                String themeName = (String) selectedTheme.getUserData();
                config.setUiTheme(themeName);
                // Theme already applied through live preview
            }

            // Save current language settings
            String language = germanRadio.isSelected() ? "de" : "en";
            config.setUiLanguage(language);

            // Save proxy settings (only if editable)
            saveProxySettings();

            // No success message shown per user requirement

        } catch (Exception e) {
            logger.error("Failed to apply settings", e);
            showError("Apply Settings Error", "Failed to save settings: " + e.getMessage());
        }
    }


    /**
     * Saves current proxy settings from UI controls to configuration.
     */
    private void saveProxySettings() {
        try {
            if (config.isProxyConfigurationEditable()) {
                // Save proxy enable state
                config.setProxyEnabled(enableProxyCheckBox.isSelected());

                // Save proxy host and port
                String proxyHost = proxyHostField.getText();
                if (proxyHost != null && !proxyHost.trim().isEmpty()) {
                    config.setProxyHost(proxyHost.trim());
                } else {
                    config.setProxyHost(null);
                }

                config.setProxyPort(proxyPortSpinner.getValue());

                // Save proxy credentials
                String proxyUsername = proxyUsernameField.getText();
                if (proxyUsername != null && !proxyUsername.trim().isEmpty()) {
                    config.setProxyUsername(proxyUsername.trim());
                } else {
                    config.setProxyUsername(null);
                }

                String proxyPassword = proxyPasswordField.getText();
                if (proxyPassword != null && !proxyPassword.trim().isEmpty()) {
                    config.setProxyPassword(proxyPassword.trim());
                } else {
                    config.setProxyPassword(null);
                }

                // Note: Certificate path is auto-detected, no saving needed

                if (config != null && config.getLoggingEnabled()) {
                    logger.info("Proxy settings saved to configuration");
                }

                // Refresh proxy configuration in the service
                BflApiService apiService = ServiceFactory.getInstance().getService(BflApiService.class);
                if (apiService != null) {
                    apiService.refreshProxyConfiguration();
                    if (config != null && config.getLoggingEnabled()) {
                        logger.info("BFL API service proxy configuration refreshed");
                    }
                }

            } else {
                logger.warn("Proxy settings are not editable due to policy configuration");
            }

        } catch (Exception e) {
            logger.error("Failed to save proxy settings", e);
            throw new RuntimeException("Failed to save proxy settings", e);
        }
    }

    // Utility methods

    /**
     * Shows an error dialog.
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows an information dialog.
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Opens the system file explorer at the configuration directory.
     */
    @FXML
    private void onOpenConfigDirectory() {
        try {
            String configPath = System.getProperty("user.home") + "/.moodframe/";
            java.io.File configDir = new java.io.File(configPath);

            // Create directory if it doesn't exist
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            // Open system explorer based on OS
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                // Windows
                Runtime.getRuntime().exec(new String[] { "explorer", configDir.getAbsolutePath() });
            } else if (osName.contains("mac")) {
                // macOS
                Runtime.getRuntime().exec(new String[] { "open", configDir.getAbsolutePath() });
            } else {
                // Linux/Unix
                Runtime.getRuntime().exec(new String[] { "xdg-open", configDir.getAbsolutePath() });
            }

        } catch (Exception e) {
            logger.error("Failed to open system explorer", e);
            showError(I18nManager.getText("error.explorer"),
                    I18nManager.getText("error.explorer.open", e.getMessage()));
        }
    }

    /**
     * Handles language change events from the radio buttons.
     * 
     * @param languageCode the selected language code ("de" or "en")
     */
    private void handleLanguageChange(String languageCode) {
        try {
            if (config != null && config.getLoggingEnabled()) {
                logger.info("User selected language: {}", languageCode);
            }

            // Update I18nManager locale immediately for live preview
            java.util.Locale newLocale = "de".equals(languageCode) ? java.util.Locale.GERMAN : java.util.Locale.ENGLISH;

            I18nManager.setLocale(newLocale);

            // Save to configuration for persistence
            config.setUiLanguage(languageCode);

            if (config != null && config.getLoggingEnabled()) {
                logger.info("Language changed to: {} ({})", languageCode, newLocale.getDisplayName());
            }

        } catch (Exception e) {
            logger.error("Failed to change language to: {}", languageCode, e);
            showError(I18nManager.getText("error.language.change"),
                    I18nManager.getText("error.language.change.details", e.getMessage()));
        }
    }

    @Override
    public void onLanguageChanged() {
        try {

            // Update API Configuration tab
            updateApiConfigTexts();

            // Update Appearance tab
            updateAppearanceTexts();

            // Update Language tab
            updateLanguageTexts();

            // Update Corporate Proxy tab
            updateCorporateProxyTexts();

            // Update Advanced tab
            updateAdvancedTexts();

            // Update navigation categories
            updateNavigationCategories();

            // Update dialog header text
            if (settingsDialogPane != null) {
                settingsDialogPane.setHeaderText(I18nManager.getText("settings.title"));

                // Re-apply title-3 style class to header label after language change
                Platform.runLater(() -> {
                    javafx.scene.Node header = settingsDialogPane.lookup(".header-panel");
                    if (header instanceof javafx.scene.layout.Region headerRegion) {
                        javafx.scene.Node headerText = headerRegion.lookup(".label");
                        if (headerText instanceof javafx.scene.control.Label label) {
                            if (!label.getStyleClass().contains("title-3")) {
                                label.getStyleClass().add("title-3");
                            }
                        }
                    }
                });

                // Update the Close button text and width
                settingsDialogPane.getButtonTypes().stream()
                        .filter(buttonType -> buttonType == ButtonType.CLOSE)
                        .findFirst()
                        .ifPresent(buttonType -> {
                            Button closeButton = (Button) settingsDialogPane.lookupButton(buttonType);
                            if (closeButton != null) {
                                closeButton.setText(I18nManager.getText("button.close"));
                                // Set minimum width to accommodate German text "Schließen"
                                closeButton.setMinWidth(100);
                            }
                        });
            }
            if (getStage() != null) {
                getStage().setTitle(I18nManager.getText("settings.window.title"));
            }

            // Update settings label above category list
            if (settingsLabel != null) {
                settingsLabel.setText(I18nManager.getText("settings.title"));
            }

        } catch (Exception e) {
            logger.error("Failed to update Settings UI texts", e);
        }
    }

    /**
     * Updates API Configuration tab texts.
     */
    private void updateApiConfigTexts() {
        if (testConnectionButton != null) {
            testConnectionButton.setText(I18nManager.getText("settings.api.test"));
        }

        if (connectionStatusLabel != null) {
            String currentText = connectionStatusLabel.getText();
            if (currentText.contains("No connection")) {
                connectionStatusLabel.setText(I18nManager.getText("settings.api.connection.none"));
            } else if (currentText.contains("API key validated and ready")
                    || currentText.contains("API-Schlüssel validiert und bereit")) {
                connectionStatusLabel.setText(I18nManager.getText("settings.test.api.validated.ready"));
            } else if (currentText.contains("Connection successful")
                    || currentText.contains("Verbindung erfolgreich")) {
                // For connection success messages with region, we need to extract the region
                // Look for pattern like "(Region: xyz)" in the text
                String region = "Global"; // default fallback
                int regionStart = currentText.indexOf("(Region: ");
                if (regionStart == -1)
                    regionStart = currentText.indexOf("(Region: ");
                if (regionStart != -1) {
                    int regionEnd = currentText.indexOf(")", regionStart);
                    if (regionEnd != -1) {
                        region = currentText.substring(regionStart + 9, regionEnd);
                    }
                }
                connectionStatusLabel.setText(I18nManager.getText("settings.test.api.connection.success", region));
            }
        }

        // Update API configuration labels
        if (apiKeyLabel != null) {
            apiKeyLabel.setText(I18nManager.getText("settings.api.key"));
        }
        if (apiKeyDescLabel != null) {
            apiKeyDescLabel.setText(I18nManager.getText("settings.api.key.description"));
        }
        if (apiRegionLabel != null) {
            apiRegionLabel.setText(I18nManager.getText("settings.api.region"));
        }
        if (apiRegionDescLabel != null) {
            apiRegionDescLabel.setText(I18nManager.getText("settings.api.region.description"));
        }
        if (apiAdvancedLabel != null) {
            apiAdvancedLabel.setText(I18nManager.getText("settings.api.advanced"));
        }
        if (apiTimeoutLabel != null) {
            apiTimeoutLabel.setText(I18nManager.getText("settings.api.timeout"));
        }

        // Update prompt texts for input fields
        if (apiKeyField != null) {
            apiKeyField.setPromptText(I18nManager.getText("settings.api.key.placeholder"));
        }

        // Update tooltips
        if (showApiKeyButton != null) {
            showApiKeyButton.setTooltip(new Tooltip(I18nManager.getText("settings.api.key.toggle.tooltip")));
        }
    }

    /**
     * Updates Appearance tab texts.
     */
    private void updateAppearanceTexts() {
        if (resetThemeButton != null) {
            resetThemeButton.setText(I18nManager.getText("settings.theme.reset"));
        }
        if (themeTitleLabel != null) {
            themeTitleLabel.setText(I18nManager.getText("settings.theme.title"));
        }
        if (themeDescLabel != null) {
            themeDescLabel.setText(I18nManager.getText("settings.theme.description"));
        }
        if (backgroundImageCheckBox != null) {
            backgroundImageCheckBox.setText(I18nManager.getText("settings.background.image"));
        }
        if (backgroundImageDescLabel != null) {
            backgroundImageDescLabel.setText(I18nManager.getText("settings.background.image.description"));
        }
        if (themeChangesLabel != null) {
            themeChangesLabel.setText(I18nManager.getText("settings.theme.changes"));
        }
        if (themePreviewLabel != null) {
            themePreviewLabel.setText(I18nManager.getText("settings.theme.preview"));
        }
        if (themeSampleLabel != null) {
            themeSampleLabel.setText(I18nManager.getText("settings.theme.sample"));
        }

        // Update theme preview button text
        if (themePreviewContainer != null) {
            themePreviewContainer.getChildren().stream()
                    .filter(node -> node instanceof Button)
                    .forEach(node -> {
                        Button btn = (Button) node;
                        if (btn.getStyleClass().contains("accent")) {
                            btn.setText(I18nManager.getText("settings.theme.button"));
                        } else if (btn.getStyleClass().contains("button-outlined")) {
                            btn.setText(I18nManager.getText("settings.theme.outlined"));
                        }
                    });
        }
    }

    /**
     * Updates Language tab texts.
     */
    private void updateLanguageTexts() {
        if (germanRadio != null) {
            germanRadio.setText(I18nManager.getText("settings.language.german"));
        }
        if (englishRadio != null) {
            englishRadio.setText(I18nManager.getText("settings.language.english"));
        }
        if (languageTitleLabel != null) {
            languageTitleLabel.setText(I18nManager.getText("settings.language.title"));
        }
        if (languageDescLabel != null) {
            languageDescLabel.setText(I18nManager.getText("settings.language.description"));
        }
        if (languageChangesLabel != null) {
            languageChangesLabel.setText(I18nManager.getText("settings.language.changes"));
        }
    }

    /**
     * Updates Corporate Proxy tab texts.
     */
    private void updateCorporateProxyTexts() {
        // Update button texts
        if (testProxyConnectionButton != null) {
            testProxyConnectionButton.setText(I18nManager.getText("settings.proxy.test"));
        }
        if (resetProxyConfigButton != null) {
            resetProxyConfigButton.setText(I18nManager.getText("settings.proxy.clear"));
        }
        if (uploadCertificateButton != null) {
            uploadCertificateButton.setText(I18nManager.getText("settings.certificates.upload.button"));
        }
        if (removeCertificateButton != null) {
            removeCertificateButton.setText(I18nManager.getText("settings.certificates.remove"));
        }
        if (refreshCertificatesButton != null) {
            refreshCertificatesButton.setText(I18nManager.getText("settings.certificates.refresh"));
        }
        if (testCertificateButton != null) {
            testCertificateButton.setText(I18nManager.getText("settings.certificates.test"));
        }

        // Update label texts
        if (proxyTitleLabel != null) {
            proxyTitleLabel.setText(I18nManager.getText("settings.proxy.title"));
        }
        if (proxyDescLabel != null) {
            proxyDescLabel.setText(I18nManager.getText("settings.proxy.description"));
        }
        if (proxyHostLabel != null) {
            proxyHostLabel.setText(I18nManager.getText("settings.proxy.host"));
        }
        if (proxyPortLabel != null) {
            proxyPortLabel.setText(I18nManager.getText("settings.proxy.port"));
        }
        if (proxyAuthLabel != null) {
            proxyAuthLabel.setText(I18nManager.getText("settings.proxy.auth"));
        }
        if (proxyUsernameLabel != null) {
            proxyUsernameLabel.setText(I18nManager.getText("settings.proxy.username"));
        }
        if (proxyPasswordLabel != null) {
            proxyPasswordLabel.setText(I18nManager.getText("settings.proxy.password"));
        }

        // Update checkbox texts
        if (enableProxyCheckBox != null) {
            enableProxyCheckBox.setText(I18nManager.getText("settings.proxy.enable"));
        }

        // Update certificate section labels
        if (certificatesTitleLabel != null) {
            certificatesTitleLabel.setText(I18nManager.getText("settings.certificates.title"));
        }
        if (certificatesDescLabel != null) {
            certificatesDescLabel.setText(I18nManager.getText("settings.certificates.description"));
        }
        if (certificatesDirectoryLabel != null) {
            certificatesDirectoryLabel.setText(I18nManager.getText("settings.certificates.directory"));
        }
        if (certificatesUploadTitleLabel != null) {
            certificatesUploadTitleLabel.setText(I18nManager.getText("settings.certificates.upload.title"));
        }
        if (certificatesFormatsLabel != null) {
            certificatesFormatsLabel.setText(I18nManager.getText("settings.certificates.formats"));
        }
        if (certificatesInstalledLabel != null) {
            certificatesInstalledLabel.setText(I18nManager.getText("settings.certificates.installed"));
        }

        // Update prompt texts for input fields
        if (proxyHostField != null) {
            proxyHostField.setPromptText(I18nManager.getText("settings.proxy.host.placeholder"));
        }
        if (proxyUsernameField != null) {
            proxyUsernameField.setPromptText(I18nManager.getText("settings.proxy.username.placeholder"));
        }
        if (proxyPasswordField != null) {
            proxyPasswordField.setPromptText(I18nManager.getText("settings.proxy.password.placeholder"));
        }

        // Update status labels with default text if they show default values
        if (proxyConnectionStatusLabel != null) {
            String currentText = proxyConnectionStatusLabel.getText();
            if (currentText.contains("No proxy connection")) {
                proxyConnectionStatusLabel.setText(I18nManager.getText("settings.proxy.connection.none"));
            } else if (currentText.contains("Proxy connection successful")
                    || currentText.contains("Proxy-Verbindung erfolgreich")) {
                proxyConnectionStatusLabel.setText(I18nManager.getText("settings.test.proxy.success"));
            }
        }
        if (certificateConnectionStatusLabel != null) {
            String currentText = certificateConnectionStatusLabel.getText();
            if (currentText.contains("No certificate connection")) {
                certificateConnectionStatusLabel.setText(I18nManager.getText("settings.certificates.connection.none"));
            } else if (currentText.contains("Certificate connection successful")
                    || currentText.contains("Zertifikat-Verbindung erfolgreich")) {
                certificateConnectionStatusLabel.setText(I18nManager.getText("settings.test.certificate.success"));
            }
        }
    }

    /**
     * Updates Advanced tab texts.
     */
    private void updateAdvancedTexts() {
        // Update section titles
        if (performanceTitleLabel != null) {
            performanceTitleLabel.setText(I18nManager.getText("settings.performance.title"));
        }
        if (dataTitleLabel != null) {
            dataTitleLabel.setText(I18nManager.getText("settings.data.title"));
        }
        if (aboutTitleLabel != null) {
            aboutTitleLabel.setText(I18nManager.getText("settings.about.title"));
        }
        if (configPathTitleLabel != null) {
            configPathTitleLabel.setText(I18nManager.getText("settings.about.config.path"));
        }

        // Update content labels
        if (versionLabel != null) {
            versionLabel.setText(I18nManager.getText("settings.about.version",
                    art.moodframe.util.VersionManager.getVersion()));
        }

        // Update buttons
        if (exportSettingsButton != null) {
            exportSettingsButton.setText(I18nManager.getText("settings.data.export"));
        }
        if (importSettingsButton != null) {
            importSettingsButton.setText(I18nManager.getText("settings.data.import"));
        }
        if (resetSettingsButton != null) {
            resetSettingsButton.setText(I18nManager.getText("settings.data.reset"));
        }
        if (openConfigDirectoryButton != null) {
            openConfigDirectoryButton.setText(I18nManager.getText("settings.about.config.open"));
        }

        // Update checkbox texts
        if (enableLoggingCheckBox != null) {
            enableLoggingCheckBox.setText(I18nManager.getText("settings.performance.logging"));
        }
    }

    /**
     * Gets the stage containing this controller.
     * 
     * @return the stage or null if not found
     */
    private Stage getStage() {
        if (settingsCategoryList != null && settingsCategoryList.getScene() != null) {
            return (Stage) settingsCategoryList.getScene().getWindow();
        }
        return null;
    }
}
