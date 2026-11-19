package art.moodframe.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatCostTest {

    @Test
    void theTotalCountsTheEntriesInUseWithTheirDeletedImagesAndTheDeletedEntries() {
        Chat chat = new Chat.Builder().name("Kosten").deletedCost(0.20).build();
        chat.getEntries().add(new ChatEntry.Builder().cost(0.50).deletedCost(0.25).build());
        chat.getEntries().add(new ChatEntry.Builder().cost(0.10).build());

        assertEquals(0.80, ChatCost.total(chat), 1e-9, "entries in use plus the deleted entries");
        assertEquals(0.45, ChatCost.deleted(chat), 1e-9, "deleted images plus the deleted entries");
        assertEquals(ChatCost.total(chat), ChatCost.total(chat.getEntries(), chat.getDeletedCost()), 1e-9);
    }

    @Test
    void aChatWithoutEntriesCostsWhatItsDeletedEntriesCost() {
        Chat chat = new Chat.Builder().name("Leer").deletedCost(0.12).build();

        assertEquals(0.12, ChatCost.total(chat), 1e-9);
        assertEquals(0.12, ChatCost.deleted(chat), 1e-9);
    }

    @Test
    void anImageSharesTheEntryCostByItsStoredCostForEveryModel() {
        GeneratedImage flux2 = image(GenerationModel.FLUX2_PRO.costForMegapixels(4.0), 0.0, 0.0);
        GeneratedImage small = image(GenerationModel.FLUX2_PRO.costForMegapixels(1.0), 0.0, 0.0);
        double entryCost = GenerationModel.FLUX2_PRO.costForMegapixels(4.0)
                + GenerationModel.FLUX2_PRO.costForMegapixels(1.0);
        assertEquals(GenerationModel.FLUX2_PRO.costForMegapixels(4.0),
                ChatCost.imageShare(entryCost, List.of(flux2, small), flux2), 1e-9, "FLUX.2 by its megapixels");

        GeneratedImage gemini = image(0.039, 0.035, 0.002);
        GeneratedImage blocked = image(0.0, 0.0, 0.0);
        assertEquals(0.076, ChatCost.imageShare(0.076, List.of(gemini, blocked), gemini), 1e-9,
                "Gemini with its search and reasoning cost");
        assertEquals(0.0, ChatCost.imageShare(0.076, List.of(gemini, blocked), blocked), 1e-9,
                "a blocked image costs nothing");

        double video = GenerationModel.OMNI_720P.videoPrice(8);
        GeneratedImage clip = image(video, 0.0, 0.0);
        assertEquals(video, ChatCost.imageShare(video, List.of(clip), clip), 1e-9, "a video its seconds");
    }

    @Test
    void theSharesOfAllImagesAddUpToTheEntryCost() {
        GeneratedImage first = image(0.03, 0.0, 0.0);
        GeneratedImage second = image(0.05, 0.0, 0.0);
        GeneratedImage third = image(0.04, 0.0, 0.0);

        /*  The cost the providers reported differs from the stored estimate: the shares follow the entry cost  */
        double cost = 0.15;
        double firstShare = ChatCost.imageShare(cost, List.of(first, second, third), first);
        double secondShare = ChatCost.imageShare(cost - firstShare, List.of(second, third), second);
        double thirdShare = ChatCost.imageShare(cost - firstShare - secondShare, List.of(third), third);

        assertEquals(cost * 0.03 / 0.12, firstShare, 1e-9);
        assertEquals(cost, firstShare + secondShare + thirdShare, 1e-9);
    }

    @Test
    void withoutStoredCostsTheEntryCostIsSplitEvenly() {
        GeneratedImage first = image(0.0, null, null);
        GeneratedImage second = image(null, null, null);

        assertEquals(0.04, ChatCost.imageShare(0.08, List.of(first, second), second), 1e-9);
        assertEquals(0.0, ChatCost.imageShare(0.0, List.of(first, second), second), 1e-9);
        assertEquals(0.0, ChatCost.imageShare(-0.01, List.of(first), first), 1e-9, "never negative");
    }

    @Test
    void anImageOfAnEntryThatDidNotSettleItsCostIsBookedWithWhatItCostItself() {
        GeneratedImage made = image(0.04, null, null);
        GeneratedImage running = image(null, null, null);
        ChatEntry entry = new ChatEntry.Builder().cost(0.12).status(ChatEntry.Status.PROCESSING).build();

        /*  The cost of a running entry is the estimate of all three images, only one of them is made  */
        assertEquals(0.04, ChatCost.imageShare(entry, List.of(made, running), made), 1e-9, "not the estimate");
        entry.setStatus(ChatEntry.Status.CANCELLED);
        assertEquals(0.04, ChatCost.imageShare(entry, List.of(made, running), made), 1e-9,
                "a cancelled entry keeps the estimate as well");
        assertEquals(0.0, ChatCost.imageShare(entry, List.of(made, running), running), 1e-9);

        entry.setStatus(ChatEntry.Status.COMPLETED);
        entry.setDeletedCost(0.02);
        assertEquals(0.10, ChatCost.imageShare(entry, List.of(made), made), 1e-9, "a settled entry shares its cost");
    }

    private static GeneratedImage image(Double base, Double search, Double reasoning) {
        return new GeneratedImage.Builder().baseCost(base).searchCost(search).reasoningCost(reasoning).build();
    }
}
