-- Add base generation cost column for accurate model cost tracking
-- Migration V008: Base Cost Storage
-- Fixes issue where Gemini 3 Pro 2K and 4K models share the same API ID
-- but have different costs ($0.134 vs $0.240)

-- Add base_cost column to store the actual model's base generation cost
ALTER TABLE generated_images ADD COLUMN base_cost REAL DEFAULT 0.0;

-- Add comment for documentation
-- base_cost: The base model generation cost (e.g., $0.240 for Gemini 3 Pro 4K)
--            This is stored at generation time to avoid lookup ambiguity
--            for models that share the same API model ID
