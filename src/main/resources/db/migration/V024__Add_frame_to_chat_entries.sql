-- Migration V024: the frame and the target aspect ratio and resolution of each chat entry, copied at the time of
-- generation. frame_json holds the copy, frame_id the id of the chosen frame for the list by frame (null for a setting
-- of its own and for entries from before, which carry no copy).

ALTER TABLE chat_entries ADD COLUMN frame_json TEXT;
ALTER TABLE chat_entries ADD COLUMN frame_id TEXT;

CREATE INDEX IF NOT EXISTS idx_chat_entries_frame_id ON chat_entries(frame_id);
