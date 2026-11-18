package art.moodframe.mcp;

import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.GeneratedImage;
import art.moodframe.service.chat.ChatLibrary;
import art.moodframe.service.generation.GenerationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.SQLException;
import java.util.List;

/**
 * The tools that change the chats: {@code create_chat}, {@code rename_chat}, {@code delete_chat},
 * {@code delete_entry}, {@code delete_image} and {@code cancel_generation}. Deleting moves to the trash, from where
 * {@code restore} brings it back. The changes go through the {@link ChatLibrary}, so the main window shows them at
 * once without opening another chat.
 *
 * @since 2.1.0
 */
class ChatEditTools {

    /*  an image with its entry, found on the JavaFX thread  */
    private record Found(ChatEntry entry, GeneratedImage image, boolean running) {
    }

    private final ChatLibrary chats;
    private final GenerationService generation;

    /**
     * Creates the tools.
     *
     * @param chats      the chats of the main window
     * @param generation cancels running entries
     */
    ChatEditTools(ChatLibrary chats, GenerationService generation) {
        this.chats = chats;
        this.generation = generation;
    }

    /**
     * Adds the tools.
     *
     * @param tools where they are added
     */
    void addTo(McpTools tools) {
        ObjectNode create = McpTools.JSON.createObjectNode();
        create.set("name", McpTools.string("The name of the chat"));
        tools.add("create_chat", "Creates a chat on top of the list and stores it at once; returns its id.",
                McpTools.input(create, "name"), this::createChat);

        ObjectNode rename = McpTools.JSON.createObjectNode();
        rename.set("chatId", McpTools.string("The ID of the chat, see list_chats"));
        rename.set("name", McpTools.string("The new name"));
        tools.add("rename_chat", "Renames a chat.", McpTools.input(rename, "chatId", "name"), this::renameChat);

        ObjectNode chat = McpTools.JSON.createObjectNode();
        chat.set("chatId", McpTools.string("The ID of the chat, see list_chats"));
        tools.add("delete_chat", "Moves a chat with its entries and images to the trash; restore brings it back.",
                McpTools.input(chat, "chatId"), this::deleteChat);

        ObjectNode entry = McpTools.JSON.createObjectNode();
        entry.set("entryId", McpTools.string("The ID of the chat entry, see get_chat"));
        tools.add("delete_entry", "Moves a finished chat entry with its images to the trash; restore brings it back. "
                + "A running entry has to be cancelled first.", McpTools.input(entry, "entryId"), this::deleteEntry);

        ObjectNode image = McpTools.JSON.createObjectNode();
        image.set("imageId", McpTools.string("The ID of the image, see get_entry"));
        tools.add("delete_image", "Moves an image of a finished entry to the trash; with its last image the entry "
                + "goes as well. restore brings it back.", McpTools.input(image, "imageId"), this::deleteImage);

        tools.add("cancel_generation", "Cancels a running chat entry: MoodFrame stops waiting for its images and the "
                + "entry ends as CANCELLED. A provider that got the request already may still charge it; an image "
                + "Gemini or OpenAI still delivers is kept.", McpTools.input(entry.deepCopy(), "entryId"),
                this::cancelGeneration);
    }

    private McpTools.Result createChat(JsonNode arguments) throws McpTools.ToolException, SQLException {
        Chat chat = chats.createChat(McpTools.text(arguments, "name"));
        return new McpTools.Result(chat(chat));
    }

    private McpTools.Result renameChat(JsonNode arguments) throws McpTools.ToolException, SQLException {
        Chat chat = shownChat(McpTools.text(arguments, "chatId"));
        chats.renameChat(chat, McpTools.text(arguments, "name"));
        return new McpTools.Result(chat(chat));
    }

    private McpTools.Result deleteChat(JsonNode arguments) throws McpTools.ToolException, SQLException {
        Chat chat = shownChat(McpTools.text(arguments, "chatId"));
        if (!chats.moveToTrash(chat))
            throw new McpTools.ToolException("The chat '" + chat.getName() + "' could not be moved to the trash");
        return new McpTools.Result(chat(chat).put("inTrash", true));
    }

    /*  a chat the main window shows  */
    private Chat shownChat(String chatId) throws McpTools.ToolException {
        Chat chat = chats.read((List<Chat> all) -> all.stream().filter((Chat candidate) -> candidate.getId()
                .equals(chatId) && ChatTools.shown(candidate)).findFirst().orElse(null));
        if (chat == null)
            throw new McpTools.ToolException("No chat with the ID '" + chatId + "', see list_chats");
        return chat;
    }

    private static ObjectNode chat(Chat chat) {
        return McpTools.JSON.createObjectNode().put("id", chat.getId()).put("name", chat.getName());
    }

    private McpTools.Result deleteEntry(JsonNode arguments) throws McpTools.ToolException, SQLException {
        String entryId = McpTools.text(arguments, "entryId");
        Found found = find(entryId, null);
        if (found.running())
            throw new McpTools.ToolException("The entry '" + entryId + "' is still running, see cancel_generation");
        chats.deleteEntry(found.entry());
        return new McpTools.Result(McpTools.JSON.createObjectNode().put("id", entryId).put("inTrash", true));
    }

    private McpTools.Result deleteImage(JsonNode arguments) throws McpTools.ToolException, SQLException {
        String imageId = McpTools.text(arguments, "imageId");
        Found found = find(null, imageId);
        if (found.running())
            throw new McpTools.ToolException("The entry of the image '" + imageId + "' is still running, see "
                    + "cancel_generation");
        boolean entryDeleted = chats.deleteImage(found.entry(), found.image());
        return new McpTools.Result(McpTools.JSON.createObjectNode().put("id", imageId)
                .put("entryId", found.entry().getId()).put("inTrash", true).put("entryInTrash", entryDeleted));
    }

    private McpTools.Result cancelGeneration(JsonNode arguments) throws McpTools.ToolException {
        String entryId = McpTools.text(arguments, "entryId");
        Found found = find(entryId, null);
        if (!generation.cancel(found.entry()).join())
            throw new McpTools.ToolException("The entry '" + entryId + "' is not running, its status is "
                    + chats.read((List<Chat> all) -> found.entry().getStatus().name()));
        return new McpTools.Result(McpTools.JSON.createObjectNode().put("id", entryId)
                .put("status", ChatEntry.Status.CANCELLED.name()));
    }

    /*  a shown entry by its ID, or a shown image by its ID with its entry  */
    private Found find(String entryId, String imageId) throws McpTools.ToolException {
        Found found = chats.read((List<Chat> all) -> {
            for (Chat chat : all) {
                if (!ChatTools.shown(chat))
                    continue;
                for (ChatEntry entry : chat.getEntries()) {
                    if (!ChatTools.shown(entry))
                        continue;
                    boolean running = entry.getStatus() == ChatEntry.Status.PENDING
                            || entry.getStatus() == ChatEntry.Status.PROCESSING;
                    if (entry.getId().equals(entryId))
                        return new Found(entry, null, running);
                    for (GeneratedImage image : entry.getImages())
                        if (image.getId().equals(imageId) && ChatTools.shown(image))
                            return new Found(entry, image, running);
                }
            }
            return null;
        });
        if (found == null)
            throw new McpTools.ToolException(entryId != null ? "No chat entry with the ID '" + entryId + "', see "
                    + "get_chat" : "No image with the ID '" + imageId + "', see get_entry");
        return found;
    }
}
