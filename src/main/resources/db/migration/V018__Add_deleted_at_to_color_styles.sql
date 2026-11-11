-- Migration V018: color styles can be moved to the trash like the other building blocks.

ALTER TABLE color_styles ADD COLUMN deleted_at TIMESTAMP NULL;
