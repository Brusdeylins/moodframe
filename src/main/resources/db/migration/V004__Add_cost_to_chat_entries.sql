-- Migration V004: Add cost field to chat_entries table
-- Adds cost tracking for image generation requests

-- Add cost column to chat_entries table
ALTER TABLE chat_entries ADD COLUMN cost REAL NOT NULL DEFAULT 0.0;

-- Add index for cost queries (optional, for performance)
CREATE INDEX idx_chat_entries_cost ON chat_entries(cost);