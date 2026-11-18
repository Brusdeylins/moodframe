package art.moodframe.ui.controller.settings;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.util.I18nManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * The language section of the settings ({@code LanguageSettingsPane.fxml}). A chosen language applies and is saved at
 * once, every open view relabels itself.
 */
public class LanguageSettingsPane implements SettingsSection {

    private static final Logger logger = LoggerFactory.getLogger(LanguageSettingsPane.class);

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

    private SettingsServices services;
    private ApplicationConfig config;

    @Override
    public void init(SettingsServices services) {
        this.services = services;
        this.config = services.config();
        ToggleGroup languageToggleGroup = new ToggleGroup();
        germanRadio.setToggleGroup(languageToggleGroup);
        englishRadio.setToggleGroup(languageToggleGroup);
        languageToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null)
                changeLanguage(newToggle == germanRadio ? "de" : "en");
        });
    }

    /** Selects the stored language and applies it. */
    @Override
    public void load() {
        if ("de".equals(config.ui().getUiLanguage())) {
            germanRadio.setSelected(true);
            I18nManager.setLocale(Locale.GERMAN);
        } else {
            englishRadio.setSelected(true);
            I18nManager.setLocale(Locale.ENGLISH);
        }
    }

    @Override
    public void reset() {
        boolean german = "de".equals(config.ui().getUiLanguage());
        germanRadio.setSelected(german);
        englishRadio.setSelected(!german);
    }

    @Override
    public void apply() {
        config.ui().setUiLanguage(germanRadio.isSelected() ? "de" : "en");
    }

    @Override
    public void relabel() {
        germanRadio.setText(I18nManager.getText("settings.language.german"));
        englishRadio.setText(I18nManager.getText("settings.language.english"));
        languageTitleLabel.setText(I18nManager.getText("settings.language.title"));
        languageDescLabel.setText(I18nManager.getText("settings.language.description"));
        languageChangesLabel.setText(I18nManager.getText("settings.language.changes"));
    }

    /**
     * Switches the language of the application at once and saves it.
     *
     * @param languageCode the selected language code, "de" or "en"
     */
    private void changeLanguage(String languageCode) {
        try {
            logger.debug("User selected language: {}", languageCode);
            Locale newLocale = "de".equals(languageCode) ? Locale.GERMAN : Locale.ENGLISH;
            I18nManager.setLocale(newLocale);
            config.ui().setUiLanguage(languageCode);
            logger.debug("Language changed to: {} ({})", languageCode, newLocale.getDisplayName());
        } catch (Exception e) {
            logger.error("Failed to change language to: {}", languageCode, e);
            services.prompts().error(I18nManager.getText("error.language.change"),
                    I18nManager.getText("error.language.change.details", e.getMessage()));
        }
    }
}
