package art.moodframe.config;

/**
 * Every secret of the configuration: where its value can come from, in this order of priority -- environment
 * variable, system property, the encrypted value in {@code config.properties} -- and its name in the log.
 *
 * <p>A reset of the settings keeps all of them; a new secret only needs its constant here.</p>
 */
enum Secret {

    BFL_API_KEY("BFL_API_KEY", "bfl.api.key", "api.key", "API key"),
    GEMINI_API_KEY("GEMINI_API_KEY", "gemini.api.key", "gemini.api.key", "Gemini API key"),
    OPENAI_API_KEY("OPENAI_API_KEY", "openai.api.key", "openai.api.key", "OpenAI API key"),
    MCP_TOKEN("MOODFRAME_MCP_TOKEN", "moodframe.mcp.token", "mcp.token", "MCP token"),
    PROXY_PASSWORD("PROXY_PASSWORD", "proxy.password", "proxy.password", "Proxy password");

    private final String environment;
    private final String property;
    private final String key;
    private final String label;

    Secret(String environment, String property, String key, String label) {
        this.environment = environment;
        this.property = property;
        this.key = key;
        this.label = label;
    }

    /** @return the environment variable */
    String environment() {
        return environment;
    }

    /** @return the system property */
    String property() {
        return property;
    }

    /** @return the key of the encrypted value in the configuration file */
    String key() {
        return key;
    }

    /** @return the name in the log, for example "Gemini API key" */
    String label() {
        return label;
    }
}
