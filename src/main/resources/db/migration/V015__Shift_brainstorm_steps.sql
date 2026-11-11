-- Migration V015: The brainstorm process has 8 steps instead of 5.
-- Stored assistant replies keep their step number only inside their JSON: 2 (subject) becomes 6,
-- 3 (composition) and 4 (style) become 7, 5 (review) becomes 8, step 1 stays.

UPDATE brainstorm_messages
SET content = json_set(content, '$.step', CASE json_extract(content, '$.step')
    WHEN 2 THEN 6
    WHEN 3 THEN 7
    WHEN 4 THEN 7
    WHEN 5 THEN 8
END)
WHERE role = 'assistant' AND json_valid(content) AND json_extract(content, '$.step') IN (2, 3, 4, 5);
