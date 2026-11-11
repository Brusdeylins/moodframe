-- Migration V014: Add brainstorm sessions
-- Stores the guided prompt brainstorm chats (sessions, messages and draft versions)

CREATE TABLE IF NOT EXISTS brainstorm_sessions (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- role is one of: user, assistant, event
CREATE TABLE IF NOT EXISTS brainstorm_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES brainstorm_sessions(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS brainstorm_drafts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    version INTEGER NOT NULL,
    draft_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES brainstorm_sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_brainstorm_sessions_updated ON brainstorm_sessions(updated_at);
CREATE INDEX IF NOT EXISTS idx_brainstorm_messages_session ON brainstorm_messages(session_id);
CREATE INDEX IF NOT EXISTS idx_brainstorm_drafts_session ON brainstorm_drafts(session_id, version);
