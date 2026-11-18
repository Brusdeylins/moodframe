package art.moodframe.config;

/**
 * The MCP server for local AI agents: whether it runs, its port on 127.0.0.1 and its bearer token.
 *
 * @since 2.1.0
 */
public class McpSettings {

    /** Default port of the MCP server. */
    public static final int MCP_DEFAULT_PORT = 8765;

    private final ConfigStore store;

    McpSettings(ConfigStore store) {
        this.store = store;
    }

    /** @return true if the server runs while the app runs (default false) */
    public boolean isMcpEnabled() {
        return store.getBoolean("mcp.enabled", false);
    }

    /** @param enabled true to run the server while the app runs */
    public void setMcpEnabled(boolean enabled) {
        store.set("mcp.enabled", String.valueOf(enabled));
    }

    /** @return the port from 1024 to 65535, {@link #MCP_DEFAULT_PORT} for a missing or invalid value */
    public int getMcpPort() {
        try {
            int port = Integer.parseInt(store.get("mcp.port", String.valueOf(MCP_DEFAULT_PORT)).trim());
            return port >= 1024 && port <= 65535 ? port : MCP_DEFAULT_PORT;
        }
        catch (NumberFormatException e) {
            return MCP_DEFAULT_PORT;
        }
    }

    /** @param port the port from 1024 to 65535 */
    public void setMcpPort(int port) {
        store.set("mcp.port", String.valueOf(port));
    }

    /**
     * Gets the bearer token: from {@code MOODFRAME_MCP_TOKEN}, {@code moodframe.mcp.token} or decrypted from the
     * configuration file.
     *
     * @return the token, or null if none is set
     */
    public String getMcpToken() {
        return store.readSecret(Secret.MCP_TOKEN);
    }

    /** @param token the token, stored encrypted; null or empty removes it */
    public void setMcpToken(String token) {
        store.storeSecret(Secret.MCP_TOKEN, token);
    }

    /** @return true if the token comes from the configuration file or is not set, so the settings may replace it */
    public boolean isMcpTokenEditable() {
        return store.isSecretEditable(Secret.MCP_TOKEN);
    }
}
