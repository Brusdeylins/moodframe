-- Migration V013: Drop the orphan task_presets table
-- The TaskPreset feature (added in the removed V012) was rolled back. Existing
-- databases still carry an empty task_presets table; drop it for consistency.
-- IF EXISTS makes this a safe no-op on fresh databases that never had the table.

DROP TABLE IF EXISTS task_presets;
