package art.moodframe.mcp;

import art.moodframe.model.SavedImage;
import art.moodframe.service.library.SavedImageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The tool that reads the saved images of the library, e.g. logos: {@code list_saved_images}. The tool
 * {@code generate} attaches them by their names ({@link GenerateTools}).
 *
 * @since 2.1.4
 */
class SavedImageTools {

    private final SavedImageService savedImages;

    /**
     * Creates the tool.
     *
     * @param savedImages the saved images
     */
    SavedImageTools(SavedImageService savedImages) {
        this.savedImages = savedImages;
    }

    /**
     * Adds the tool.
     *
     * @param tools where it is added
     */
    void addTo(McpTools tools) {
        tools.add("list_saved_images", "Lists the saved images of the library, e.g. logos or product photos the user "
                + "attaches again and again, favorites first: id, name, width and height in pixels, format, "
                + "description (what it shows and when it fits, null for none), favorite and the file path. generate "
                + "attaches them by name with 'savedImages'.", McpTools.input(McpTools.JSON.createObjectNode()),
                this::list);
    }

    private McpTools.Result list(JsonNode arguments) {
        ObjectNode result = McpTools.JSON.createObjectNode();
        ArrayNode list = result.putArray("savedImages");
        for (SavedImage image : savedImages.getAll())
            list.addObject().put("id", image.id()).put("name", image.name()).put("width", image.width())
                    .put("height", image.height()).put("format", image.format())
                    .put("description", image.description()).put("favorite", image.favorite())
                    .put("path", savedImages.fileOf(image).toString());
        return new McpTools.Result(result);
    }
}
