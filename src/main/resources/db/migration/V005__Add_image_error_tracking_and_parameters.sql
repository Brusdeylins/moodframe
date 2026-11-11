-- Migration V005: Add image error tracking and additional generation parameters
-- Adds fields for better error handling and parameter storage for image recreation

-- Add error tracking fields to generated_images table
ALTER TABLE generated_images ADD COLUMN error_message TEXT;
ALTER TABLE generated_images ADD COLUMN failure_reason TEXT;
ALTER TABLE generated_images ADD COLUMN api_status TEXT;

-- Add missing generation parameters for full recreation capability
ALTER TABLE generated_images ADD COLUMN aspect_ratio TEXT;
ALTER TABLE generated_images ADD COLUMN style_preset TEXT;
ALTER TABLE generated_images ADD COLUMN safety_tolerance INTEGER;
ALTER TABLE generated_images ADD COLUMN output_format TEXT DEFAULT 'png';

-- Add width/height if not already present (some tables might have them, others might not)
-- SQLite allows adding columns even if they exist (will be ignored)
ALTER TABLE generated_images ADD COLUMN width INTEGER DEFAULT 1024;
ALTER TABLE generated_images ADD COLUMN height INTEGER DEFAULT 1024;

-- Update existing records to have default values
UPDATE generated_images 
SET 
    aspect_ratio = COALESCE(aspect_ratio, '1:1'),
    safety_tolerance = COALESCE(safety_tolerance, 2),
    output_format = COALESCE(output_format, 'png'),
    width = COALESCE(width, image_width, 1024),
    height = COALESCE(height, image_height, 1024)
WHERE 
    aspect_ratio IS NULL 
    OR safety_tolerance IS NULL 
    OR output_format IS NULL
    OR width IS NULL
    OR height IS NULL;

-- Add indexes for new fields
CREATE INDEX IF NOT EXISTS idx_generated_images_api_status ON generated_images(api_status);
CREATE INDEX IF NOT EXISTS idx_generated_images_failure_reason ON generated_images(failure_reason);