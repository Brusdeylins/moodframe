package art.moodframe.mcp;

import art.moodframe.model.Chat;
import art.moodframe.model.ChatEntry;
import art.moodframe.model.EntryFrame;
import art.moodframe.model.EntryMood;
import art.moodframe.model.GeneratedImage;
import art.moodframe.model.GenerationModel;
import art.moodframe.service.ChatLibrary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The tools that read the chats: {@code list_chats}, {@code get_chat}, {@code get_entry} and {@code get_image}. They
 * see what the main window shows, nothing in the trash, and read the JavaFX models through the {@link ChatLibrary}
 * on the JavaFX thread; files are read on the thread of the call.
 *
 * @since 2.1.0
 */
class ChatTools {

    /*  the long edge of a preview  */
    private static final int PREVIEW_EDGE = 1024;

    private static final List<String> SIZES = List.of("preview", "original");

    /*  an image found in the chats, copied off the JavaFX thread  */
    private record Found(String entryId, String path, boolean video, int width, int height) {
    }

    private final ChatLibrary chats;

    /**
     * Creates the tools.
     *
     * @param chats the chats of the main window
     */
    ChatTools(ChatLibrary chats) {
        this.chats = chats;
    }

    /**
     * Adds the tools.
     *
     * @param tools where they are added
     */
    void addTo(McpTools tools) {
        tools.add("list_chats", "Lists the chats of MoodFrame in their order: id, name, number of entries, total cost "
                + "in USD (deleted images included), created and changed.",
                McpTools.input(McpTools.JSON.createObjectNode()), this::listChats);

        ObjectNode chat = McpTools.JSON.createObjectNode();
        chat.set("chatId", McpTools.string("The ID of the chat, see list_chats"));
        chat.set("offset", McpTools.integer("The number of entries to skip", 0, Integer.MAX_VALUE, 0));
        chat.set("limit", McpTools.integer("The maximum number of entries", 1, 100, 20));
        tools.add("get_chat", "Gets the entries of a chat page by page, oldest first: prompt of the user, mood, frame, "
                + "model (name and fixed id), status, image IDs, cost in USD, source (UI for the send button of "
                + "MoodFrame, MCP for generate).", McpTools.input(chat, "chatId"), this::getChat);

        ObjectNode entry = McpTools.JSON.createObjectNode();
        entry.set("entryId", McpTools.string("The ID of the chat entry, see get_chat"));
        tools.add("get_entry", "Gets a chat entry: status, error text, prompt of the user and final prompt, model, "
                + "mood and frame as used, cost in USD, source (UI for the send button of MoodFrame, MCP for "
                + "generate) and the images with file path, pixel size and file size.",
                McpTools.input(entry, "entryId"), this::getEntry);

        ObjectNode image = McpTools.JSON.createObjectNode();
        image.set("imageId", McpTools.string("The ID of the image, see get_entry or get_chat"));
        image.set("size", McpTools.choice("'preview' scales the long edge to " + PREVIEW_EDGE + " px (JPEG), "
                + "'original' returns the file as it is", SIZES, "preview"));
        tools.add("get_image", "Gets an image as MCP image content, as preview or original, with its file path.",
                McpTools.input(image, "imageId"), this::getImage);
    }

    /*  what the main window shows: chats, entries and images not in the trash  */
    static boolean shown(Chat chat) {
        return !chat.isDeleted();
    }

    static boolean shown(ChatEntry entry) {
        return entry.getDeletedAt() == null;
    }

    static boolean shown(GeneratedImage image) {
        return !image.isInTrash() && image.getDeletedAt() == null;
    }

    private McpTools.Result listChats(JsonNode arguments) {
        ObjectNode result = McpTools.JSON.createObjectNode();
        result.set("chats", chats.read((List<Chat> all) -> {
            ArrayNode list = McpTools.JSON.createArrayNode();
            all.stream().filter(ChatTools::shown).forEach((Chat chat) -> list.add(summary(chat)));
            return list;
        }));
        return new McpTools.Result(result);
    }

    private McpTools.Result getChat(JsonNode arguments) throws McpTools.ToolException {
        String chatId = McpTools.text(arguments, "chatId");
        int offset = McpTools.number(arguments, "offset", 0, Integer.MAX_VALUE, 0);
        int limit = McpTools.number(arguments, "limit", 1, 100, 20);
        ObjectNode result = chats.read((List<Chat> all) -> {
            Chat chat = all.stream().filter((Chat candidate) -> candidate.getId().equals(chatId) && shown(candidate))
                    .findFirst().orElse(null);
            if (chat == null)
                return null;
            List<ChatEntry> entries = chat.getEntries().stream().filter(ChatTools::shown).toList();
            ObjectNode page = McpTools.JSON.createObjectNode();
            page.set("chat", summary(chat));
            page.put("total", entries.size()).put("offset", offset).put("limit", limit);
            ArrayNode items = page.putArray("entries");
            entries.stream().skip(offset).limit(limit).forEach((ChatEntry entry) -> items.add(entrySummary(entry)));
            return page;
        });
        if (result == null)
            throw new McpTools.ToolException("No chat with the ID '" + chatId + "', see list_chats");
        return new McpTools.Result(result);
    }

    private McpTools.Result getEntry(JsonNode arguments) throws McpTools.ToolException {
        String entryId = McpTools.text(arguments, "entryId");
        ObjectNode result = chats.read((List<Chat> all) -> all.stream().filter(ChatTools::shown)
                .flatMap((Chat chat) -> chat.getEntries().stream())
                .filter((ChatEntry entry) -> entry.getId().equals(entryId) && shown(entry)).findFirst()
                .map(ChatTools::entryDetails).orElse(null));
        if (result == null)
            throw new McpTools.ToolException("No chat entry with the ID '" + entryId + "', see get_chat");
        return new McpTools.Result(result);
    }

    private McpTools.Result getImage(JsonNode arguments) throws McpTools.ToolException, IOException {
        String imageId = McpTools.text(arguments, "imageId");
        String size = McpTools.choice(arguments, "size", SIZES, "preview");
        Found found = find(chats, imageId);

        /*  The original as it is; a preview only if the image is larger, else the original serves as preview  */
        byte[] data = Files.readAllBytes(Path.of(found.path()));
        String mimeType = mimeType(data);
        int width = found.width();
        int height = found.height();
        if ("preview".equals(size)) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null)
                throw new McpTools.ToolException("No preview for the format of '" + imageId + "', ask for the "
                        + "original");
            width = image.getWidth();
            height = image.getHeight();
            if (Math.max(width, height) > PREVIEW_EDGE) {
                double scale = (double) PREVIEW_EDGE / Math.max(width, height);
                width = Math.max(1, (int) Math.round(width * scale));
                height = Math.max(1, (int) Math.round(height * scale));
                data = jpeg(image, width, height);
                mimeType = "image/jpeg";
            }
        }

        ObjectNode result = McpTools.JSON.createObjectNode().put("imageId", imageId).put("entryId", found.entryId())
                .put("path", found.path()).put("size", size).put("mimeType", mimeType).put("width", width)
                .put("height", height).put("bytes", data.length);
        return new McpTools.Result(result, new McpTools.Image(data, mimeType));
    }

    /*  a shown image with its file, which must not be a video  */
    private static Found find(ChatLibrary chats, String imageId) throws McpTools.ToolException {
        Found found = chats.read((List<Chat> all) -> all.stream().filter(ChatTools::shown)
                .flatMap((Chat chat) -> chat.getEntries().stream()).filter(ChatTools::shown)
                .flatMap((ChatEntry entry) -> entry.getImages().stream())
                .filter((GeneratedImage image) -> image.getId().equals(imageId) && shown(image)).findFirst()
                .map((GeneratedImage image) -> new Found(image.getChatEntryId(), image.getLocalFilePath(),
                        image.isVideo(), image.getImageWidth(), image.getImageHeight()))
                .orElse(null));
        if (found == null)
            throw new McpTools.ToolException("No image with the ID '" + imageId + "', see get_entry");
        if (found.video())
            throw new McpTools.ToolException("'" + imageId + "' is a video, its file is " + found.path());
        if (found.path() == null || !Files.isRegularFile(Path.of(found.path())))
            throw new McpTools.ToolException("The file of the image '" + imageId + "' is not available");
        return found;
    }

    /**
     * The file of an image the client names: an image of the chats by its ID, or an image file by its absolute path.
     *
     * @param chats     the chats
     * @param reference the ID of an image or the absolute path of a file
     * @return the file
     * @throws McpTools.ToolException if it is neither, or a video
     */
    static Path imageFile(ChatLibrary chats, String reference) throws McpTools.ToolException {
        Path path;
        try {
            path = Path.of(reference);
        }
        catch (InvalidPathException e) {
            path = null;
        }
        if (path == null || !path.isAbsolute())
            return Path.of(find(chats, reference).path());
        if (!Files.isRegularFile(path))
            throw new McpTools.ToolException("No file at '" + reference + "'");
        return path;
    }

    /*  the image scaled down, as JPEG on white  */
    private static byte[] jpeg(BufferedImage image, int width, int height) throws IOException {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(image, 0, 0, width, height, Color.WHITE, null);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(scaled, "jpg", out);
        return out.toByteArray();
    }

    /*  the MIME type by the first bytes of the file  */
    private static String mimeType(byte[] data) {
        if (data.length >= 4 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G')
            return "image/png";
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF)
            return "image/jpeg";
        if (data.length >= 12 && new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WEBP"))
            return "image/webp";
        return "application/octet-stream";
    }

    /*  ===== JSON of the models, built on the JavaFX thread =====  */

    private static ObjectNode summary(Chat chat) {
        List<ChatEntry> entries = chat.getEntries().stream().filter(ChatTools::shown).toList();

        /*  as the header of the chat: all entry costs plus those of the deleted entries  */
        double cost = chat.getEntries().stream().mapToDouble(ChatEntry::getCost).sum() + chat.getDeletedCost();
        return McpTools.JSON.createObjectNode().put("id", chat.getId()).put("name", chat.getName())
                .put("entries", entries.size()).put("cost", McpTools.usd(cost))
                .put("createdAt", McpTools.time(chat.getCreatedAt()))
                .put("updatedAt", McpTools.time(chat.getUpdatedAt()));
    }

    private static ObjectNode entrySummary(ChatEntry entry) {
        ObjectNode node = McpTools.JSON.createObjectNode().put("id", entry.getId())
                .put("prompt", entry.getUserPrompt());
        node.set("mood", moodSummary(entry.getMood()));
        node.set("frame", frameSummary(entry.getFrame()));
        node.put("model", entry.getModel()).put("modelId", GenerationModel.idOf(entry.getModel()))
                .put("status", entry.getStatus().name());
        ArrayNode images = node.putArray("imageIds");
        entry.getImages().stream().filter(ChatTools::shown)
                .forEach((GeneratedImage image) -> images.add(image.getId()));
        return node.put("cost", McpTools.usd(entry.getCost())).put("createdAt", McpTools.time(entry.getCreatedAt()))
                .put("source", entry.getSource().name());
    }

    private static ObjectNode entryDetails(ChatEntry entry) {
        ObjectNode node = McpTools.JSON.createObjectNode().put("id", entry.getId()).put("chatId", entry.getChatId())
                .put("status", entry.getStatus().name()).put("error", entry.getErrorMessage())
                .put("prompt", entry.getUserPrompt()).put("finalPrompt", entry.getEnhancedPrompt())
                .put("model", entry.getModel()).put("modelId", GenerationModel.idOf(entry.getModel()));
        node.set("mood", entry.getMood() != null ? McpTools.parse(entry.getMood().toJson()) : null);
        node.set("frame", entry.getFrame() != null ? McpTools.parse(entry.getFrame().toJson()) : null);
        node.put("cost", McpTools.usd(entry.getCost())).put("createdAt", McpTools.time(entry.getCreatedAt()))
                .put("completedAt", McpTools.time(entry.getCompletedAt())).put("source", entry.getSource().name());
        ArrayNode images = node.putArray("images");
        entry.getImages().stream().filter(ChatTools::shown).forEach((GeneratedImage image) -> images.addObject()
                .put("id", image.getId()).put("path", image.getLocalFilePath()).put("width", image.getImageWidth())
                .put("height", image.getImageHeight()).put("fileSize", image.getFileSizeBytes())
                .put("mimeType", image.getMimeType()).put("video", image.isVideo())
                .put("downloadStatus", image.getDownloadStatus() != null ? image.getDownloadStatus().name() : null));
        return node;
    }

    /*  the mood of an entry: the chosen one, or the one a changed combination started from  */
    private static ObjectNode moodSummary(EntryMood mood) {
        if (mood == null)
            return null;
        boolean changed = mood.baseId() != null;
        ObjectNode node = McpTools.JSON.createObjectNode().put("id", changed ? mood.baseId() : mood.moodId())
                .put("name", changed ? mood.baseName() : mood.moodName()).put("changed", changed);
        ObjectNode blocks = node.putObject("blocks");
        mood.blocks().forEach((field, block) -> blocks.put(field.name().toLowerCase(Locale.ROOT), block.name()));
        return node;
    }

    /*  the frame of an entry: the chosen one, or the one a changed setting started from  */
    private static ObjectNode frameSummary(EntryFrame frame) {
        if (frame == null)
            return null;
        boolean changed = frame.baseId() != null;
        return McpTools.JSON.createObjectNode().put("id", changed ? frame.baseId() : frame.frameId())
                .put("name", changed ? frame.baseName() : frame.frameName()).put("changed", changed)
                .put("aspectRatio", frame.aspectRatio()).put("resolution", frame.resolution());
    }
}
