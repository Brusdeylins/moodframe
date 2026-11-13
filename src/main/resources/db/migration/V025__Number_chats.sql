-- Migration V025: the chats can be put in any order now. Until now sort_order was 0 for all of them and the list
-- showed the latest changed chat first, so each chat is numbered by the chats changed after it, to keep that order.

UPDATE chats SET sort_order = (
    SELECT COUNT(*) FROM chats AS later
    WHERE later.updated_at > chats.updated_at
        OR (later.updated_at = chats.updated_at AND later.id < chats.id)
);
