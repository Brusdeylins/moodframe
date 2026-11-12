-- Migration V022: a frame names no model any more, as several providers serve the images. It keeps name, target
-- aspect ratio, sort order and trash; model, resolution, image count, guidance and steps are set in the main window.

ALTER TABLE frames DROP COLUMN model_id;
ALTER TABLE frames DROP COLUMN resolution;
ALTER TABLE frames DROP COLUMN image_count;
ALTER TABLE frames DROP COLUMN guidance;
ALTER TABLE frames DROP COLUMN steps;
