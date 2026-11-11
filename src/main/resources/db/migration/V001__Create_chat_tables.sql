-- Migration V001: Create chat tables
-- Creates the core tables for the chat system

-- Chats table: Main chat conversations
CREATE TABLE chats (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    sort_order INTEGER NOT NULL DEFAULT 0
);

-- Chat entries table: Individual messages/generations in a chat
CREATE TABLE chat_entries (
    id TEXT PRIMARY KEY,
    chat_id TEXT NOT NULL,
    user_prompt TEXT NOT NULL,
    enhanced_prompt TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    
    -- Generation parameters
    model TEXT NOT NULL DEFAULT 'flux-dev',
    size TEXT NOT NULL DEFAULT '1024x576',
    format TEXT NOT NULL DEFAULT 'png',
    prompt_upsampling BOOLEAN NOT NULL DEFAULT FALSE,
    steps INTEGER NOT NULL DEFAULT 50,
    guidance REAL NOT NULL DEFAULT 7.5,
    seed INTEGER NOT NULL DEFAULT -1,
    
    FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
);

-- Generated images table: Actual generated images with metadata
CREATE TABLE generated_images (
    id TEXT PRIMARY KEY,
    chat_entry_id TEXT NOT NULL,
    image_url TEXT NOT NULL,
    local_file_path TEXT,
    file_name TEXT,
    download_status TEXT NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    downloaded_at TIMESTAMP NULL,
    file_size_bytes INTEGER NOT NULL DEFAULT 0,
    image_width INTEGER NOT NULL DEFAULT 0,
    image_height INTEGER NOT NULL DEFAULT 0,
    mime_type TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    
    -- Generation metadata
    generation_job_id TEXT,
    prompt TEXT,
    model TEXT,
    seed INTEGER,
    steps INTEGER,
    guidance REAL,
    prompt_upsampling BOOLEAN,
    
    FOREIGN KEY (chat_entry_id) REFERENCES chat_entries(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_chats_deleted_at ON chats(deleted_at);
CREATE INDEX idx_chats_sort_order ON chats(sort_order);
CREATE INDEX idx_chats_updated_at ON chats(updated_at);

CREATE INDEX idx_chat_entries_chat_id ON chat_entries(chat_id);
CREATE INDEX idx_chat_entries_status ON chat_entries(status);
CREATE INDEX idx_chat_entries_sort_order ON chat_entries(sort_order);
CREATE INDEX idx_chat_entries_created_at ON chat_entries(created_at);

CREATE INDEX idx_generated_images_chat_entry_id ON generated_images(chat_entry_id);
CREATE INDEX idx_generated_images_download_status ON generated_images(download_status);
CREATE INDEX idx_generated_images_sort_order ON generated_images(sort_order);
CREATE INDEX idx_generated_images_generation_job_id ON generated_images(generation_job_id);

-- Trigger to update chats.updated_at when chat_entries are modified
CREATE TRIGGER update_chat_timestamp_after_entry_insert
AFTER INSERT ON chat_entries
BEGIN
    UPDATE chats SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.chat_id;
END;

CREATE TRIGGER update_chat_timestamp_after_entry_update
AFTER UPDATE ON chat_entries
BEGIN
    UPDATE chats SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.chat_id;
END;

CREATE TRIGGER update_chat_timestamp_after_entry_delete
AFTER DELETE ON chat_entries
BEGIN
    UPDATE chats SET updated_at = CURRENT_TIMESTAMP WHERE id = OLD.chat_id;
END;