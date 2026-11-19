package art.moodframe.ui.controller.settings;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.McpSettings;
import art.moodframe.mcp.McpService;
import art.moodframe.util.I18nManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * The MCP section of the settings ({@code McpSettingsPane.fxml}). A change applies at once: the server starts,
 * starts anew or stops in the background, and the status line shows what it does.
 */
public class McpSettingsPane implements SettingsSection {

    @FXML
    private Label mcpTitleLabel;
    @FXML
    private Label mcpDescLabel;
    @FXML
    private CheckBox mcpEnabledCheckBox;
    @FXML
    private HBox mcpStatusContainer;
    @FXML
    private FontIcon mcpStatusIcon;
    @FXML
    private Label mcpStatusLabel;
    @FXML
    private Label mcpPortLabel;
    @FXML
    private Spinner<Integer> mcpPortSpinner;
    @FXML
    private Label mcpTokenLabel;
    @FXML
    private PasswordField mcpTokenField;
    @FXML
    private Button copyMcpTokenButton;
    @FXML
    private Button newMcpTokenButton;
    @FXML
    private Label mcpCommandLabel;
    @FXML
    private Label mcpCommandDescLabel;
    @FXML
    private TextField mcpCommandField;
    @FXML
    private Button copyMcpCommandButton;

    private ApplicationConfig config;
    private McpService mcpService;
    private McpService.Status mcpStatus = new McpService.Status(McpService.State.OFF, 0);
    private StatusLine status;

    @Override
    public void init(SettingsServices services) {
        this.config = services.config();
        this.mcpService = services.mcp();
        mcpPortSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1024, 65535,
                McpSettings.MCP_DEFAULT_PORT));
        status = new StatusLine(mcpStatusContainer, mcpStatusLabel, mcpStatusIcon);

        mcpEnabledCheckBox.setSelected(config.mcp().isMcpEnabled());
        mcpPortSpinner.getValueFactory().setValue(config.mcp().getMcpPort());
        showMcpToken();
        if (mcpService != null)
            mcpStatus = mcpService.getStatus();
        showMcpStatus();

        /*  The first activation creates the token  */
        mcpEnabledCheckBox.selectedProperty().addListener((obs, was, enabled) -> {
            if (enabled && config.mcp().getMcpToken() == null)
                config.mcp().setMcpToken(McpService.newToken());
            config.mcp().setMcpEnabled(enabled);
            showMcpToken();
            applyMcp();
        });
        mcpPortSpinner.valueProperty().addListener((obs, old, port) -> {
            if (port == null || port == config.mcp().getMcpPort())
                return;
            config.mcp().setMcpPort(port);
            showMcpToken();
            applyMcp();
        });
    }

    /** Shows the server after a reset, which disables it and removes its token. */
    @Override
    public void reset() {
        mcpEnabledCheckBox.setSelected(config.mcp().isMcpEnabled());
        mcpPortSpinner.getValueFactory().setValue(config.mcp().getMcpPort());
        showMcpToken();
        applyMcp();
    }

    /** Takes a port typed but not confirmed as well; the server follows at once. */
    @Override
    public void apply() {
        try {
            mcpPortSpinner.commitValue();
        }
        catch (NumberFormatException e) {
            mcpPortSpinner.cancelEdit();
        }
    }

    @Override
    public void relabel() {
        mcpTitleLabel.setText(I18nManager.getText("settings.mcp.title"));
        mcpDescLabel.setText(I18nManager.getText("settings.mcp.description"));
        mcpEnabledCheckBox.setText(I18nManager.getText("settings.mcp.enable"));
        mcpPortLabel.setText(I18nManager.getText("settings.mcp.port"));
        mcpTokenLabel.setText(I18nManager.getText("settings.mcp.token"));
        copyMcpTokenButton.setText(I18nManager.getText("settings.mcp.token.copy"));
        newMcpTokenButton.setText(I18nManager.getText("settings.mcp.token.new"));
        mcpCommandLabel.setText(I18nManager.getText("settings.mcp.command"));
        mcpCommandDescLabel.setText(I18nManager.getText("settings.mcp.command.description"));
        copyMcpCommandButton.setText(I18nManager.getText("settings.mcp.command.copy"));
        if (!status.isTesting())
            showMcpStatus();
    }

    /*  the token hidden in its field, the command with the token hidden; the copy buttons take the real ones  */
    private void showMcpToken() {
        String token = config.mcp().getMcpToken();
        mcpTokenField.setText(token != null ? token : "");
        mcpCommandField.setText(token != null
                ? McpService.command(config.mcp().getMcpPort(), "•".repeat(12)) : "");
        copyMcpTokenButton.setDisable(token == null);
        copyMcpCommandButton.setDisable(token == null);
        newMcpTokenButton.setDisable(!config.mcp().isMcpTokenEditable());
    }

    /*  applies the MCP settings in the background and shows the status once it is known  */
    private void applyMcp() {
        if (mcpService == null)
            return;
        status.show("testing", I18nManager.getText("settings.mcp.status.applying"));
        mcpService.applyAsync().thenAccept((McpService.Status applied) -> Platform.runLater(() -> {
            mcpStatus = applied;
            showMcpStatus();
        }));
    }

    private void showMcpStatus() {
        String port = String.valueOf(mcpStatus.port());
        switch (mcpStatus.state()) {
            case RUNNING -> status.show("success", I18nManager.getText("settings.mcp.status.running", port));
            case PORT_IN_USE -> status.show("error", I18nManager.getText("settings.mcp.status.port.in.use", port));
            case FAILED -> status.show("error", I18nManager.getText("settings.mcp.status.failed", port));
            case OFF -> status.show("info", I18nManager.getText("settings.mcp.status.off"));
        }
    }

    @FXML
    private void onCopyMcpToken() {
        copyToClipboard(config.mcp().getMcpToken());
    }

    @FXML
    private void onCopyMcpCommand() {
        String token = config.mcp().getMcpToken();
        copyToClipboard(token != null ? McpService.command(config.mcp().getMcpPort(), token) : null);
    }

    /*  A new token locks out the clients with the old one, the server starts anew  */
    @FXML
    private void onNewMcpToken() {
        config.mcp().setMcpToken(McpService.newToken());
        showMcpToken();
        applyMcp();
    }

    private static void copyToClipboard(String text) {
        if (text == null)
            return;
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
