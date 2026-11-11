-- Migration V010: Add color styles table
-- Adds support for custom color palettes for image generation
-- Color styles can contain 1-5 hex color codes that are appended to prompts

CREATE TABLE IF NOT EXISTS color_styles (
    id TEXT PRIMARY KEY,
    name TEXT UNIQUE NOT NULL,
    color1 TEXT,           -- Hex color code (e.g., "#48BDFF")
    color2 TEXT,
    color3 TEXT,
    color4 TEXT,
    color5 TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_color_styles_sort_order ON color_styles(sort_order);
CREATE INDEX IF NOT EXISTS idx_color_styles_name ON color_styles(name);
