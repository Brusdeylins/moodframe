-- Migration V031: V001 meant three triggers to set chats.updated_at on every change of an entry, but the migration
-- runner joined them into one statement, so a new library got only the first one and an old library none. The code
-- keeps updated_at itself (ChatLibrary.addEntry) and the chats are listed by sort_order since V025, so all libraries
-- go without them. The trigger wrote the time as UTC text 'YYYY-MM-DD HH:MM:SS', the code writes epoch milliseconds:
-- the text it left is converted, so updated_at holds one format again.

DROP TRIGGER IF EXISTS update_chat_timestamp_after_entry_insert;
DROP TRIGGER IF EXISTS update_chat_timestamp_after_entry_update;
DROP TRIGGER IF EXISTS update_chat_timestamp_after_entry_delete;

UPDATE chats SET updated_at = CAST(strftime('%s', updated_at) AS INTEGER) * 1000
WHERE typeof(updated_at) = 'text' AND length(updated_at) = 19 AND strftime('%s', updated_at) IS NOT NULL;
