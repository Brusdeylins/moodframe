package art.moodframe.service;

import java.util.List;

/**
 * Tells which moods use a building block, so that deleting the block can name them. The moods register their
 * implementation at the {@link art.moodframe.config.ServiceFactory}; as long as none is registered, no block is in
 * use.
 *
 * @since 2.0.0
 */
public interface BlockUsage {

    /**
     * Lists the moods that use a building block.
     *
     * @param blockId the ID of a text block or a palette
     * @return the names of the moods, empty if none uses the block
     */
    List<String> moodsUsing(String blockId);
}
