-- Add Gemini advanced features cost tracking and metadata columns
-- Migration V007: Gemini Phase 2 Advanced Features
-- Adds support for Google Search Grounding, Thinking Process, and detailed cost tracking

-- Add cost breakdown columns
ALTER TABLE generated_images ADD COLUMN search_cost REAL DEFAULT 0.0;
ALTER TABLE generated_images ADD COLUMN reasoning_cost REAL DEFAULT 0.0;
ALTER TABLE generated_images ADD COLUMN thinking_tokens INTEGER;
ALTER TABLE generated_images ADD COLUMN thought_summary TEXT;

-- Add index for efficient cost queries
CREATE INDEX IF NOT EXISTS idx_generated_images_costs ON generated_images(search_cost, reasoning_cost);

-- Add comment for documentation
-- search_cost: Cost of Google Search grounding (~$0.03 per request)
-- reasoning_cost: Cost of thinking tokens (calculated as: tokens / 1M * $12)
-- thinking_tokens: Number of thinking/reasoning tokens used by the model
-- thought_summary: AI's reasoning process summary (if includeThoughts enabled)
