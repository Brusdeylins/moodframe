package art.moodframe.service.brainstorm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrainstormContextTest {

    @Test
    void describesTheStateOfTheMainWindowForTheAssistant() {
        BrainstormContext.WindowState state = new BrainstormContext.WindowState("FLUX.2 [pro]", "2K",
                "\nMood: None\nStyle: Watercolor", "16:9", null, List.of(), "  A lighthouse  ");

        assertEquals("Target model: FLUX.2 [pro]\nResolution: 2K\nMood: None\nStyle: Watercolor\nAspect ratio: 16:9"
                + "\nText currently in the input field: A lighthouse", state.describe());
    }

    @Test
    void leavesOutAMissingResolutionAndNamesAnEmptyInput() {
        BrainstormContext.WindowState state = new BrainstormContext.WindowState("Gemini", null, "", "1:1", null, List.of(), " ");

        assertEquals("Target model: Gemini\nAspect ratio: 1:1\nText currently in the input field: (empty)",
                state.describe());
    }

    @Test
    void namesTheFrameAndTheAttachedSavedImages() {
        BrainstormContext.WindowState state = new BrainstormContext.WindowState("Gemini", null, "", "9:16",
                "Instagram Story", List.of("Logo white", "Product shot"), "");

        assertEquals("Target model: Gemini\nAspect ratio: 9:16\nFrame: Instagram Story"
                + "\nAttached saved images: Logo white, Product shot\nText currently in the input field: (empty)",
                state.describe());
    }
}
