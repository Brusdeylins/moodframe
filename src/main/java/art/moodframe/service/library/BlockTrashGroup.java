package art.moodframe.service.library;

import art.moodframe.model.ColorStyle;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.trash.TrashEntry;
import art.moodframe.service.trash.TrashGroup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The building blocks of the user in the central trash: text blocks (style, atmosphere, light) and palettes. Both
 * keep their row with {@code deleted_at} set while they are in the trash.
 *
 * @since 2.0.0
 */
public class BlockTrashGroup implements TrashGroup {

    private final PromptBlockService blocks;
    private final ColorStyleService palettes;
    private final BlockUsage usage;

    /**
     * Creates the group.
     *
     * @param blocks   the text blocks
     * @param palettes the palettes
     * @param usage    tells which moods use a block, null as long as there are no moods
     */
    public BlockTrashGroup(PromptBlockService blocks, ColorStyleService palettes, BlockUsage usage) {
        this.blocks = blocks;
        this.palettes = palettes;
        this.usage = usage;
    }

    @Override
    public Kind kind() {
        return Kind.BLOCKS;
    }

    /**
     * Lists the deleted blocks with their type and the number of moods that use them.
     *
     * @param locale the language of the context, null for that of the user interface
     * @return the blocks in the trash, the latest deletion first
     */
    @Override
    public List<TrashEntry> list(Locale locale) {
        List<TrashEntry> entries = new ArrayList<>();
        for (PromptBlock block : blocks.getDeleted())
            entries.add(new TrashEntry(block.getId(), block.getName(), block.getDeletedAt(),
                    context(locale, block.getType().name().toLowerCase(Locale.ROOT), block.getId())));
        for (ColorStyle palette : palettes.getDeletedStyles())
            entries.add(new TrashEntry(palette.getId(), palette.getName(), palette.getDeletedAt(),
                    context(locale, "palette", palette.getId())));
        entries.sort(Comparator.comparing(TrashEntry::deletedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return entries;
    }

    /*  the type of the block, plus the number of moods that use it  */
    private String context(Locale locale, String type, String id) {
        String typeName = TrashGroup.text(locale, "block.type." + type);
        int moods = usage != null ? usage.moodsUsing(id).size() : 0;
        return moods == 0 ? typeName : TrashGroup.text(locale,
                moods == 1 ? "trash.context.block.singular" : "trash.context.block.plural", typeName, moods);
    }

    @Override
    public boolean restore(String id) {
        return blocks.restore(id) || palettes.restoreStyle(id);
    }

    @Override
    public boolean deletePermanently(String id) {
        return blocks.deletePermanently(id) || palettes.deleteStylePermanently(id);
    }
}
