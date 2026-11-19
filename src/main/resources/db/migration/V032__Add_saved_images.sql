-- Migration V032: the saved images of the library, e.g. logos, attached to an order again and again without a new
-- upload. The file lies in the folder library-images of the library under its name in "file"; the same file is stored
-- once (sha256 over all rows, also those in the trash), a name only once among the images in use. A chat entry keeps
-- the IDs of the saved images it was ordered with, comma separated in their order, null for none.

CREATE TABLE IF NOT EXISTS saved_images (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    file TEXT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    sha256 TEXT NOT NULL UNIQUE,
    description TEXT,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_saved_images_name ON saved_images(name) WHERE deleted_at IS NULL;

ALTER TABLE chat_entries ADD COLUMN saved_image_ids TEXT;
