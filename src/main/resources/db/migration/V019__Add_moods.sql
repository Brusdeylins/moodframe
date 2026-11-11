-- Migration V019: the moods of the user, named combinations of building blocks.
-- Only the moods of the user are stored, the standard moods come from code. A mood refers to its blocks by id
-- (prompt_blocks, color_styles or the fixed standard ids); every reference is optional.

CREATE TABLE IF NOT EXISTS moods (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    atmosphere_id TEXT,
    style_id TEXT,
    color_id TEXT,
    light_id TEXT,
    camera_id TEXT,
    sort_order INTEGER NOT NULL DEFAULT 1000,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_moods_sort_order ON moods(sort_order);
