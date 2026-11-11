-- Migration V017: one table for the text building blocks of the prompt (style, atmosphere, light).
-- Only the blocks of the user are stored, the standard blocks come from code.
-- The custom styles of style_presets are taken over; they count as photographic until the user deselects it.

CREATE TABLE IF NOT EXISTS prompt_blocks (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    name TEXT NOT NULL,
    text TEXT NOT NULL,
    block_group TEXT,
    photographic BOOLEAN NOT NULL DEFAULT TRUE,
    params TEXT,
    sort_order INTEGER NOT NULL DEFAULT 1000,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prompt_blocks_type ON prompt_blocks(type, sort_order);

INSERT OR IGNORE INTO prompt_blocks (id, type, name, text, photographic, sort_order, created_at, updated_at)
SELECT id, 'STYLE', name, prompt_text, TRUE, sort_order, created_at, updated_at
FROM style_presets
WHERE is_default = FALSE;
