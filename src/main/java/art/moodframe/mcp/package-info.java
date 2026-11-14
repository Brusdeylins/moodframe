/**
 * MCP server for clients on this computer, like Claude Code.
 *
 * Streamable HTTP on 127.0.0.1 with a bearer token, off until enabled in the settings.
 *
 * Classes:
 * - McpService: starts and stops the server as the settings say
 * - McpServer: transport and JSON-RPC
 * - McpTools: the tools with their input schemas
 * - ChatTools, LibraryTools: the reading tools
 * - ChatEditTools, LibraryEditTools: the tools that change chats and library
 * - GenerateTools: preview and generation
 */
package art.moodframe.mcp;
