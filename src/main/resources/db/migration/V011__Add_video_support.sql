-- Migration V011: Add video support for Veo 3.1
-- Adds columns for video generation metadata to generated_images table

ALTER TABLE generated_images ADD COLUMN is_video BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE generated_images ADD COLUMN video_duration_seconds INTEGER DEFAULT 0;
ALTER TABLE generated_images ADD COLUMN video_resolution TEXT;
ALTER TABLE generated_images ADD COLUMN has_audio BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE generated_images ADD COLUMN negative_prompt TEXT;

-- Index for filtering video vs image content
CREATE INDEX IF NOT EXISTS idx_generated_images_is_video ON generated_images(is_video);
