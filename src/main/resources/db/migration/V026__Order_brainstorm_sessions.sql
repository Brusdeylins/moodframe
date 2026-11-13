-- Migration V026: the brainstorm sessions can be put in any order now. Until now the list showed the latest changed
-- session first, so each session is numbered by the sessions changed after it, to keep that order.

ALTER TABLE brainstorm_sessions ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

UPDATE brainstorm_sessions SET sort_order = (
    SELECT COUNT(*) FROM brainstorm_sessions AS later
    WHERE later.updated_at > brainstorm_sessions.updated_at
        OR (later.updated_at = brainstorm_sessions.updated_at AND later.id < brainstorm_sessions.id)
);
