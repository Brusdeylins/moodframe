package art.moodframe.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The palette file names the default flag once, "isDefault", and reads the files written before as well. */
class ColorStyleJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesTheDefaultFlagOnceAsIsDefault() {
        JsonNode written = mapper.valueToTree(new ColorStyle("Sea", List.of("#112233"), true));

        assertTrue(written.get("isDefault").asBoolean());
        assertFalse(written.has("default"), "no second name of the flag: " + written);
    }

    @Test
    void readsTheFilesOfEarlierVersionsWithBothNames() throws Exception {
        ColorStyle both = mapper.readValue("""
                {"name": "Sea", "colors": ["#112233"], "isDefault": true, "default": true, "sortOrder": 0}
                """, ColorStyle.class);
        ColorStyle custom = mapper.readValue("""
                {"name": "Mine", "colors": ["#445566"], "isDefault": false}
                """, ColorStyle.class);

        assertTrue(both.isDefault());
        assertEquals(List.of("#112233"), both.getColors());
        assertFalse(custom.isDefault());
    }
}
