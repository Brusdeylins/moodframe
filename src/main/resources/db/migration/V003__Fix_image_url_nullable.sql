-- Migration V003: Fix image_url to be nullable
-- The image_url is not available immediately when GeneratedImage is created,
-- it's only set after the BFL API job completes successfully

-- SQLite doesn't support ALTER COLUMN directly, so we need to recreate the table

-- 1. Create new table with correct schema
CREATE TABLE generated_images_new (
    id TEXT PRIMARY KEY,
    chat_entry_id TEXT NOT NULL,
    image_url TEXT NULL,  -- Changed from NOT NULL to NULL
    local_file_path TEXT,
    file_name TEXT,
    download_status TEXT NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    downloaded_at TIMESTAMP NULL,
    file_size_bytes INTEGER NOT NULL DEFAULT 0,
    image_width INTEGER NOT NULL DEFAULT 0,
    image_height INTEGER NOT NULL DEFAULT 0,
    mime_type TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    
    -- Generation metadata
    generation_job_id TEXT,
    prompt TEXT,
    model TEXT,
    seed INTEGER,
    steps INTEGER,
    guidance REAL,
    prompt_upsampling BOOLEAN,
    
    FOREIGN KEY (chat_entry_id) REFERENCES chat_entries(id) ON DELETE CASCADE
);

-- 2. Copy existing data
INSERT INTO generated_images_new SELECT * FROM generated_images;

-- 3. Drop old table
DROP TABLE generated_images;

-- 4. Rename new table
ALTER TABLE generated_images_new RENAME TO generated_images;

-- 5. Recreate indexes
CREATE INDEX idx_generated_images_chat_entry_id ON generated_images(chat_entry_id);
CREATE INDEX idx_generated_images_download_status ON generated_images(download_status);
CREATE INDEX idx_generated_images_sort_order ON generated_images(sort_order);
CREATE INDEX idx_generated_images_generation_job_id ON generated_images(generation_job_id);