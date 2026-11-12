package art.moodframe.util;

import art.moodframe.model.AspectRatios;

import art.moodframe.model.GenerationModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AspectRatiosTest {

    @Test
    void nanoBanana2AndLiteGetTheExtendedRatios() {
        assertArrayEquals(AspectRatios.NANO_BANANA_2, AspectRatios.forModel(GenerationModel.NANO_BANANA2_1K));
        assertArrayEquals(AspectRatios.NANO_BANANA_2, AspectRatios.forModel(GenerationModel.NANO_BANANA2_LITE));
    }

    @Test
    void otherModelsKeepTheirOwnLists() {
        assertArrayEquals(AspectRatios.GEMINI, AspectRatios.forModel(GenerationModel.GEMINI_PRO_2K));
        assertArrayEquals(AspectRatios.BFL, AspectRatios.forModel(GenerationModel.FLUX2_PRO));
        assertArrayEquals(AspectRatios.FLUX_3, AspectRatios.forModel(GenerationModel.FLUX3_IMAGE_2K));
        assertArrayEquals(AspectRatios.FLUX_3, AspectRatios.forModel(GenerationModel.FLUX3_KLEIN_768));
        assertArrayEquals(AspectRatios.VIDEO, AspectRatios.forModel(GenerationModel.OMNI_1080P));
        assertArrayEquals(AspectRatios.VIDEO, AspectRatios.forModel(GenerationModel.OMNI_720P));
    }

    @Test
    void anItemOfTheModelDropdownGetsTheRatiosOfItsModel() {
        assertArrayEquals(AspectRatios.OPENAI, AspectRatios.forItem(GenerationModel.OPENAI_PREFIX + "gpt-image-1"));
        assertArrayEquals(AspectRatios.OPENAI_COMPATIBLE,
                AspectRatios.forItem(GenerationModel.OPENAI_PREFIX + "qwen-image-2.1"));
        assertArrayEquals(AspectRatios.NANO_BANANA_2,
                AspectRatios.forItem(GenerationModel.NANO_BANANA2_2K.getLocalizedDisplayName()));
        assertArrayEquals(AspectRatios.BFL, AspectRatios.forItem(null));
    }

    @Test
    void anInvalidRatioFallsBackToTheClosestRatioOfTheModel() {
        String[] openAi = AspectRatios.forItem(GenerationModel.OPENAI_PREFIX + "gpt-image-1");
        assertEquals("2:3", AspectRatios.nearest("4:5", openAi));
        assertEquals("2:3", AspectRatios.nearest("9:16", openAi));
        assertEquals("3:2", AspectRatios.nearest("16:9", openAi));
        assertEquals("1:1", AspectRatios.nearest("1:1", openAi));
        assertEquals("9:16", AspectRatios.nearest("9:21", AspectRatios.forModel(GenerationModel.GEMINI_PRO_2K)));
        assertEquals("21:9", AspectRatios.nearest("8:1", AspectRatios.forModel(GenerationModel.FLUX2_PRO)));
        assertEquals("16:9", AspectRatios.nearest("21:9", AspectRatios.forModel(GenerationModel.OMNI_1080P)));

        /*  A ratio that cannot be read counts as square  */
        assertEquals("1:1", AspectRatios.nearest("wide", openAi));
        assertEquals("1:1", AspectRatios.nearest("0:0", openAi));
        assertEquals("1:1", AspectRatios.nearest(null, openAi));
    }

    @Test
    void everyModelKeepsAValidRatioAndReplacesAnInvalidOneByOneOfItsOwn() {
        List<String> ratios = Stream.of(AspectRatios.BFL, AspectRatios.FLUX_3, AspectRatios.GEMINI,
                AspectRatios.NANO_BANANA_2, AspectRatios.OPENAI, AspectRatios.OPENAI_COMPATIBLE, AspectRatios.VIDEO)
                .flatMap(Arrays::stream).distinct().toList();
        for (GenerationModel model : GenerationModel.values()) {
            List<String> valid = List.of(AspectRatios.forModel(model));
            for (String ratio : ratios) {
                String nearest = AspectRatios.nearest(ratio, AspectRatios.forModel(model));
                assertTrue(valid.contains(nearest), model + " cannot do " + nearest);
                if (valid.contains(ratio))
                    assertEquals(ratio, nearest, model + " changed its own ratio " + ratio);

                /*  a portrait stays a portrait and a landscape a landscape wherever the model has one  */
                if (!ratio.equals("1:1") && !nearest.equals("1:1"))
                    assertEquals(portrait(ratio), portrait(nearest), model + ": " + ratio + " became " + nearest);
            }
        }
    }

    @Test
    void allHoldsEveryRatioOfEveryModelOnceFromTheWidestToTheTallest() {
        List<String> all = List.of(AspectRatios.ALL);
        assertEquals(all.size(), all.stream().distinct().count(), "a ratio is listed twice");
        for (String[] ratios : List.of(AspectRatios.BFL, AspectRatios.FLUX_3, AspectRatios.GEMINI,
                AspectRatios.NANO_BANANA_2, AspectRatios.OPENAI, AspectRatios.OPENAI_COMPATIBLE, AspectRatios.VIDEO)) {
            for (String ratio : ratios)
                assertTrue(all.contains(ratio), ratio + " is missing");
        }
        assertEquals("8:1", all.get(0));
        assertEquals("1:8", all.get(all.size() - 1));
        for (int i = 1; i < all.size(); i++)
            assertTrue(width(all.get(i - 1)) > width(all.get(i)), all.get(i - 1) + " before " + all.get(i));
    }

    @Test
    void aTargetTakesTheClosestRatioOfTheModelOfADropdownItem() {
        assertEquals("5:4", AspectRatios.nearestForItem("4:3", GenerationModel.FLUX2_PRO.getLocalizedDisplayName()));
        assertEquals("4:3", AspectRatios.nearestForItem("4:3", GenerationModel.FLUX3_IMAGE_1K.getLocalizedDisplayName()));
        assertEquals("1:1", AspectRatios.nearestForItem("1:1", GenerationModel.OPENAI_PREFIX + "gpt-image-1"));
    }

    private static double width(String ratio) {
        String[] parts = ratio.split(":");
        return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
    }

    private static boolean portrait(String ratio) {
        String[] parts = ratio.split(":");
        return Integer.parseInt(parts[0]) < Integer.parseInt(parts[1]);
    }
}
