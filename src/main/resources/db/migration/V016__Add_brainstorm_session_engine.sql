-- Migration V016: every brainstorm session remembers which language model answers it ("gemini" or "openai").
-- NULL for older sessions: they follow the choice of the brainstorm window.

ALTER TABLE brainstorm_sessions ADD COLUMN engine TEXT;
