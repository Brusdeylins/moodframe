-- Migration V002: Add style presets and configuration tables
-- Adds support for custom style presets and application configuration

-- Style presets table for custom image generation styles  
CREATE TABLE IF NOT EXISTS style_presets (
    id TEXT PRIMARY KEY,
    name TEXT UNIQUE NOT NULL,
    prompt_text TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Configuration table for application settings
CREATE TABLE IF NOT EXISTS configuration (
    key TEXT PRIMARY KEY,
    value TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- API requests table for monitoring and debugging
CREATE TABLE IF NOT EXISTS api_requests (
    id TEXT PRIMARY KEY,
    endpoint TEXT NOT NULL,
    method TEXT NOT NULL,
    request_body TEXT,
    response_body TEXT,
    status_code INTEGER,
    duration_ms INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_style_presets_sort_order ON style_presets(sort_order);
CREATE INDEX IF NOT EXISTS idx_style_presets_is_default ON style_presets(is_default);
CREATE INDEX IF NOT EXISTS idx_api_requests_created_at ON api_requests(created_at);
CREATE INDEX IF NOT EXISTS idx_api_requests_endpoint ON api_requests(endpoint);

-- Insert default style presets
INSERT OR IGNORE INTO style_presets (id, name, prompt_text, is_default, sort_order) VALUES
('default-photographic', 'Photographic', 'photographic, realistic, detailed', true, 1),
('default-artistic', 'Artistic', 'artistic, creative, stylized', true, 2),
('default-anime', 'Anime', 'anime style, manga, illustration', true, 3),
('default-digital-art', 'Digital Art', 'digital art, concept art, high quality', true, 4),
('default-abstract', 'Abstract', 'abstract, geometric, modern art', true, 5);