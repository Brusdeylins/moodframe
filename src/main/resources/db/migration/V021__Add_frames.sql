-- Migration V021: the frames of the user, named settings of model, aspect ratio and image count.
-- Only the frames of the user are stored, the standard frames come from code. The model is kept as API id plus
-- resolution (an OpenAI model as "openai:" plus its id), so display names and prices may change.

CREATE TABLE IF NOT EXISTS frames (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    model_id TEXT NOT NULL,
    resolution TEXT,
    aspect_ratio TEXT NOT NULL,
    image_count INTEGER NOT NULL DEFAULT 1,
    guidance REAL,
    steps INTEGER,
    sort_order INTEGER NOT NULL DEFAULT 1000,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_frames_sort_order ON frames(sort_order);
