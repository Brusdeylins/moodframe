-- V006: Add soft delete support and deleted cost tracking
-- This migration adds support for tracking deleted items and their costs

-- Add deleted_at to chat_entries (for soft delete)
ALTER TABLE chat_entries ADD COLUMN deleted_at TIMESTAMP NULL;

-- Add deleted_cost to chat_entries (tracks cost of deleted images)
ALTER TABLE chat_entries ADD COLUMN deleted_cost REAL NOT NULL DEFAULT 0.0;

-- Add deleted_at to generated_images (for soft delete)
ALTER TABLE generated_images ADD COLUMN deleted_at TIMESTAMP NULL;

-- Add deleted_cost to generated_images (individual image cost when deleted)
ALTER TABLE generated_images ADD COLUMN deleted_cost REAL NOT NULL DEFAULT 0.0;

-- Add deleted_cost to chats (total deleted cost for entire chat)
-- Note: deleted_at already exists in chats from V001
ALTER TABLE chats ADD COLUMN deleted_cost REAL NOT NULL DEFAULT 0.0;

-- Add is_in_trash flag to track if files have been moved to trash
ALTER TABLE generated_images ADD COLUMN is_in_trash BOOLEAN NOT NULL DEFAULT FALSE;

-- Create indexes for efficient queries
CREATE INDEX idx_chat_entries_deleted_at ON chat_entries(deleted_at);
CREATE INDEX idx_generated_images_deleted_at ON generated_images(deleted_at);
CREATE INDEX idx_generated_images_in_trash ON generated_images(is_in_trash);

-- Update existing deleted chats to calculate their deleted costs
UPDATE chats 
SET deleted_cost = (
    SELECT COALESCE(SUM(ce.cost), 0.0)
    FROM chat_entries ce
    WHERE ce.chat_id = chats.id
)
WHERE deleted_at IS NOT NULL;

-- Mark all images from deleted chats as needing trash migration
UPDATE generated_images 
SET is_in_trash = FALSE
WHERE chat_entry_id IN (
    SELECT ce.id 
    FROM chat_entries ce 
    JOIN chats c ON ce.chat_id = c.id 
    WHERE c.deleted_at IS NOT NULL
);