/**
 * MCP server for clients on this computer, like Claude Code: the second adapter beside the UI on the same services.
 * Streamable HTTP on 127.0.0.1 with a bearer token, off until enabled in the settings.
 *
 * <p>Main classes:</p>
 * <ul>
 * <li>{@link McpService}: starts and stops the server as the settings say</li>
 * <li>{@link McpServer}: transport and JSON-RPC</li>
 * <li>{@link McpTools}: the tools with their input schemas</li>
 * <li>{@link ChatTools}: the reading tools of the chats, {@link LibraryTools} those of the library,
 *     {@link SavedImageTools} that of the saved images</li>
 * <li>{@link ChatEditTools}: the tools that change chats, {@link LibraryEditTools} those that change the
 *     library</li>
 * <li>{@link GenerateTools}: preview and generation</li>
 * </ul>
 */
package art.moodframe.mcp;
