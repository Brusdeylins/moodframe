package art.moodframe.service.generation;

import art.moodframe.model.GenerationModel;
import art.moodframe.model.ModelFamilies;
import art.moodframe.util.I18nManager;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A chat entry stores the fixed id of its model since 2.1.3, older entries the English display name with price and
 * tagline; both read back to the model and show a human name.
 */
class EntryModelTest {

    /*  the display name an entry of 2.1.2 stored for FLUX.2 [pro]  */
    private static final String OLD_ROW = GenerationModel.FLUX2_PRO.getDisplayName();

    @Test
    void aNewEntryStoresTheFixedIdAndAGatewayModelItsItem() {
        assertEquals("NANO_BANANA2_2K", GenerationModel.entryModelOf("NANO_BANANA2_2K"));
        assertEquals(GenerationModel.OPENAI_PREFIX + "gpt-image-1", GenerationModel.entryModelOf("gpt-image-1"));
        assertNull(GenerationModel.entryModelOf(null));
    }

    @Test
    void newAndOldRowsLeadBackToTheirModel() {
        assertEquals(GenerationModel.NANO_BANANA2_2K, GenerationModel.fromEntryModel("NANO_BANANA2_2K"));
        assertEquals(GenerationModel.FLUX2_PRO, GenerationModel.fromEntryModel(OLD_ROW));
        assertNull(GenerationModel.fromEntryModel(GenerationModel.OPENAI_PREFIX + "gpt-image-1"));
        assertNull(GenerationModel.fromEntryModel("FLUX.1 [pro] - Retired ($0.05)"), "a model that is gone");

        assertEquals("NANO_BANANA2_2K", GenerationModel.idOf("NANO_BANANA2_2K"), "the MCP id of a new row");
        assertEquals("FLUX2_PRO", GenerationModel.idOf(OLD_ROW), "the MCP id of an old row");
        assertEquals("gpt-image-1", GenerationModel.idOf(GenerationModel.OPENAI_PREFIX + "gpt-image-1"));
        assertEquals("Nano Banana 2", ModelFamilies.familyOf("NANO_BANANA2_2K"), "the badge of a new row");
        assertEquals("2K", ModelFamilies.resolutionOf("NANO_BANANA2_2K"));
    }

    @Test
    void newAndOldRowsShowAHumanName() {
        Locale before = I18nManager.getCurrentLocale();
        try {
            I18nManager.setLocale(Locale.GERMAN);
            String german = ModelLabels.displayName(GenerationModel.FLUX2_PRO);
            assertEquals(german, ModelLabels.entryName("FLUX2_PRO"));
            assertEquals(german, ModelLabels.entryName(OLD_ROW));
            assertEquals(GenerationModel.OPENAI_PREFIX + "gpt-image-1",
                    ModelLabels.entryName(GenerationModel.OPENAI_PREFIX + "gpt-image-1"));
            assertEquals("FLUX.1 [pro] - Retired ($0.05)", ModelLabels.entryName("FLUX.1 [pro] - Retired ($0.05)"));
            assertNull(ModelLabels.entryName(null));

            assertEquals(OLD_ROW, GenerationModel.entryDisplayName("FLUX2_PRO"), "English for the MCP server");
            assertEquals(OLD_ROW, GenerationModel.entryDisplayName(OLD_ROW));
        }
        finally {
            I18nManager.setLocale(before);
        }
    }
}
