-- Migration V028: who placed the order of each chat entry, the user over the send button (UI) or an AI agent over the
-- MCP server (MCP). The entries from before were all placed in the main window and become UI.

ALTER TABLE chat_entries ADD COLUMN source TEXT NOT NULL DEFAULT 'UI';
