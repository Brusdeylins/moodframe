-- Migration V027: the favorites of the user among the moods, the frames and the building blocks, standard ones
-- (fixed ids from code) and those of the user. A favorite refers to its entry by kind and id; an entry of the user
-- deleted for good loses its favorite. A trigger ends on a bare END line, where the migration runner closes it.

CREATE TABLE IF NOT EXISTS favorites (
    kind TEXT NOT NULL,
    entry_id TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (kind, entry_id)
);

CREATE TRIGGER IF NOT EXISTS delete_favorite_after_mood_delete
AFTER DELETE ON moods
BEGIN
    DELETE FROM favorites WHERE kind = 'MOOD' AND entry_id = OLD.id;
END

CREATE TRIGGER IF NOT EXISTS delete_favorite_after_frame_delete
AFTER DELETE ON frames
BEGIN
    DELETE FROM favorites WHERE kind = 'FRAME' AND entry_id = OLD.id;
END

CREATE TRIGGER IF NOT EXISTS delete_favorite_after_block_delete
AFTER DELETE ON prompt_blocks
BEGIN
    DELETE FROM favorites WHERE kind = OLD.type AND entry_id = OLD.id;
END

CREATE TRIGGER IF NOT EXISTS delete_favorite_after_color_delete
AFTER DELETE ON color_styles
BEGIN
    DELETE FROM favorites WHERE kind = 'COLOR' AND entry_id = OLD.id;
END
