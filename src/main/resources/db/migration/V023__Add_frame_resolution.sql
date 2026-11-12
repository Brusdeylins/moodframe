-- Migration V023: a frame keeps a target resolution again next to its target aspect ratio, modelneutral like it
-- (for example 2K), of which each model takes its closest one. Existing frames get none and keep the resolution as is.

ALTER TABLE frames ADD COLUMN resolution TEXT;
