package art.moodframe.ui.controller.settings;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.util.I18nManager;
import art.moodframe.ui.support.ThemeManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The appearance section of the settings ({@code AppearanceSettingsPane.fxml}). A chosen theme is shown at once and
 * saved with "Save"; the background image and the turning preview frame are saved at once.
 */
public class AppearanceSettingsPane implements SettingsSection {

    private static final Logger logger = LoggerFactory.getLogger(AppearanceSettingsPane.class);

    @FXML
    private VBox themeSelectionContainer;
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
    private CheckBox previewFrameAnimatedCheckBox;
    @FXML
    private Label previewFrameAnimatedDescLabel;
    @FXML
    private Label themePreviewLabel;
    @FXML
    private Label themeSampleLabel;

    private ApplicationConfig config;
    private final ToggleGroup themeToggleGroup = new ToggleGroup();

    /*  the theme of the system while the theme follows it, saved as not chosen; null when the user chose one  */
    private String followedSystemTheme;

    @Override
    public void init(SettingsServices services) {
        this.config = services.config();
        for (String theme : ThemeManager.getAvailableThemes()) {
            RadioButton themeRadio = new RadioButton(ThemeManager.getThemeDisplayName(theme));
            themeRadio.setUserData(theme);
            themeRadio.setToggleGroup(themeToggleGroup);
            themeSelectionContainer.getChildren().add(themeRadio);
        }

        /*  live preview of the chosen theme  */
        themeToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null)
                ThemeManager.applyTheme((String) newToggle.getUserData());
        });

        /*  background image of the main window, applied at once  */
        backgroundImageCheckBox.setSelected(config.ui().isBackgroundImageEnabled());
        backgroundImageCheckBox.selectedProperty().addListener((obs, was, on) -> {
            config.ui().setBackgroundImageEnabled(on);
            ThemeManager.notifyAppearanceChanged();
        });

        /*  turning frame of the large preview, taken on its next opening  */
        previewFrameAnimatedCheckBox.setSelected(config.ui().isPreviewFrameAnimated());
        previewFrameAnimatedCheckBox.selectedProperty().addListener((obs, was, on) ->
                config.ui().setPreviewFrameAnimated(on));
    }

    @Override
    public void load() {
        selectTheme(config.ui().getUiTheme());
        followedSystemTheme = config.ui().hasUserSetTheme() ? null : config.ui().getUiTheme();
    }

    /** Applies and selects the theme of the reset settings. */
    @Override
    public void reset() {
        String defaultTheme = config.ui().getUiTheme();
        ThemeManager.applyTheme(defaultTheme);
        selectTheme(defaultTheme);
        followedSystemTheme = config.ui().hasUserSetTheme() ? null : defaultTheme;
    }

    /**
     * Saves the theme, which the live preview already shows; the theme of the system is stored as followed, not as
     * chosen, so the next start asks the system again.
     */
    @Override
    public void apply() {
        Toggle selectedTheme = themeToggleGroup.getSelectedToggle();
        if (selectedTheme == null)
            return;
        String theme = (String) selectedTheme.getUserData();
        if (theme.equals(followedSystemTheme))
            config.ui().setSystemTheme(theme);
        else
            config.ui().setUiTheme(theme);
    }

    @Override
    public void relabel() {
        resetThemeButton.setText(I18nManager.getText("settings.theme.reset"));
        themeTitleLabel.setText(I18nManager.getText("settings.theme.title"));
        themeDescLabel.setText(I18nManager.getText("settings.theme.description"));
        backgroundImageCheckBox.setText(I18nManager.getText("settings.background.image"));
        backgroundImageDescLabel.setText(I18nManager.getText("settings.background.image.description"));
        previewFrameAnimatedCheckBox.setText(I18nManager.getText("settings.preview.frame.animated"));
        previewFrameAnimatedDescLabel.setText(I18nManager.getText("settings.preview.frame.animated.description"));
        themeChangesLabel.setText(I18nManager.getText("settings.theme.changes"));
        themePreviewLabel.setText(I18nManager.getText("settings.theme.preview"));
        themeSampleLabel.setText(I18nManager.getText("settings.theme.sample"));

        /*  the sample buttons of the preview  */
        themePreviewContainer.getChildren().stream()
                .filter(node -> node instanceof Button)
                .forEach(node -> {
                    Button btn = (Button) node;
                    if (btn.getStyleClass().contains("accent"))
                        btn.setText(I18nManager.getText("settings.theme.button"));
                    else if (btn.getStyleClass().contains("button-outlined"))
                        btn.setText(I18nManager.getText("settings.theme.outlined"));
                });
    }

    /**
     * Applies and selects the theme the system prefers, which "Save" stores as followed, not as chosen; asking the
     * system runs programs for up to 2 s, so it runs off the JavaFX thread, the button disabled meanwhile.
     */
    @FXML
    private void onResetToSystemTheme() {
        SectionTask.run(List.of(resetThemeButton), ThemeManager::getSystemPreferredTheme, (systemTheme) -> {
            ThemeManager.applyTheme(systemTheme);
            selectTheme(systemTheme);
            followedSystemTheme = systemTheme;
        }, (failure) -> logger.warn("Failed to detect the theme of the system", failure));
    }

    /*  selects the radio button of a theme, which applies it  */
    private void selectTheme(String theme) {
        for (Toggle toggle : themeToggleGroup.getToggles()) {
            if (toggle.getUserData().equals(theme)) {
                toggle.setSelected(true);
                break;
            }
        }
    }
}
