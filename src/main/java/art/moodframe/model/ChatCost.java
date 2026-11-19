package art.moodframe.model;

import java.util.List;

/**
 * The money spent in a chat, in one place for the chat header, the trash and the MCP tools, and the share of a single
 * image in the cost of its entry.
 *
 * <p>The costs are booked on deletion as follows: {@link ChatEntry#getCost()} is what all images of an entry cost,
 * deleted or not; {@link ChatEntry#getDeletedCost()} is the part of it whose images are in the trash (all of it for a
 * deleted entry); {@link Chat#getDeletedCost()} is what the entries deleted as a whole cost, as they are no longer
 * among the entries of the chat. A deletion for good keeps the costs, as the images were paid for.</p>
 *
 * @since 2.1.3
 */
public final class ChatCost {

    private ChatCost() {
    }

    /**
     * Gets all a chat ever cost, including what is deleted.
     *
     * @param chat the chat with its entries in use
     * @return the cost in dollars
     */
    public static double total(Chat chat) {
        return total(chat.getEntries(), chat.getDeletedCost());
    }

    /**
     * Gets all a chat ever cost, including what is deleted.
     *
     * @param entries            the entries of the chat in use
     * @param deletedEntriesCost the deleted cost of the chat, what its deleted entries cost
     * @return the cost in dollars
     */
    public static double total(List<ChatEntry> entries, double deletedEntriesCost) {
        return entries.stream().mapToDouble(ChatEntry::getCost).sum() + deletedEntriesCost;
    }

    /**
     * Gets the part of the {@link #total(Chat) total} of a chat that went to the trash: deleted images of the entries
     * in use and the deleted entries.
     *
     * @param chat the chat with its entries in use
     * @return the cost in dollars
     */
    public static double deleted(Chat chat) {
        return chat.getEntries().stream().mapToDouble(ChatEntry::getDeletedCost).sum() + chat.getDeletedCost();
    }

    /**
     * Gets the share of an image in the cost of its entry that is still open, not yet booked for deleted images. The
     * share follows the cost stored with the images (generation, search, reasoning), the same the entry cost was
     * summed from; without stored costs (older entries) the open cost is split evenly.
     *
     * @param openCost the cost of the entry less its deleted cost
     * @param images   the images the open cost is split among, the image included
     * @param image    the image
     * @return the cost in dollars, never negative
     */
    public static double imageShare(double openCost, List<GeneratedImage> images, GeneratedImage image) {
        if (openCost <= 0 || images.isEmpty()) {
            return 0.0;
        }
        double stored = images.stream().mapToDouble(ChatCost::stored).sum();
        return stored > 0 ? openCost * stored(image) / stored : openCost / images.size();
    }

    /**
     * Gets the share of an image in the cost of its entry that is still open, see
     * {@link #imageShare(double, List, GeneratedImage)}. The cost of an entry still running, or cancelled while it
     * ran, is the estimate of all its images, while only the finished ones are stored or carry a cost, so an image of
     * it is booked with what it cost by itself; the entry adds what its deleted images cost to the sum of the others
     * once all are finished, and its last image books the rest with the entry.
     *
     * @param entry  the entry with its cost and deleted cost
     * @param images the images the open cost is split among, the image included
     * @param image  the image
     * @return the cost in dollars, never negative
     */
    public static double imageShare(ChatEntry entry, List<GeneratedImage> images, GeneratedImage image) {
        if (entry.getStatus() == ChatEntry.Status.PENDING || entry.getStatus() == ChatEntry.Status.PROCESSING
                || entry.getStatus() == ChatEntry.Status.CANCELLED)
            return stored(image);
        return imageShare(entry.getCost() - entry.getDeletedCost(), images, image);
    }

    /*  what the image cost by itself, as the entry cost is summed from it (see ChatEntryTracker)  */
    private static double stored(GeneratedImage image) {
        return value(image.getBaseCost()) + value(image.getSearchCost()) + value(image.getReasoningCost());
    }

    private static double value(Double cost) {
        return cost != null ? cost : 0.0;
    }
}
