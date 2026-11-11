-- Migration V009: Add style column to chat_entries table
-- Stores the style preset name for each chat entry (for both BFL and Gemini)

ALTER TABLE chat_entries ADD COLUMN style TEXT DEFAULT 'None';

-- Update existing records to have 'None' as default style
UPDATE chat_entries SET style = 'None' WHERE style IS NULL;
