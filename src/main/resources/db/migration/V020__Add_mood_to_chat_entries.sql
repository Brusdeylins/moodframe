-- Migration V020: the mood and the building blocks of each chat entry, copied at the time of generation.
-- mood_json holds the copy, mood_id the id of the chosen mood for the list by mood (null for a combination of its own
-- and for entries without blocks). The column style stays and is still written.

ALTER TABLE chat_entries ADD COLUMN mood_json TEXT;
ALTER TABLE chat_entries ADD COLUMN mood_id TEXT;

CREATE INDEX IF NOT EXISTS idx_chat_entries_mood_id ON chat_entries(mood_id);
