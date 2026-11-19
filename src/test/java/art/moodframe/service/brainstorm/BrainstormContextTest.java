package art.moodframe.service.brainstorm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrainstormContextTest {

    @Test
    void describesTheStateOfTheMainWindowForTheAssistant() {
        BrainstormContext.WindowState state = new BrainstormContext.WindowState("FLUX.2 [pro]", "2K",
                "\nMood: None\nStyle: Watercolor", "16:9", "  A lighthouse  ");

        assertEquals("Target model: FLUX.2 [pro]\nResolution: 2K\nMood: None\nStyle: Watercolor\nAspect ratio: 16:9"
                + "\nText currently in the input field: A lighthouse", state.describe());
    }

    @Test
    void leavesOutAMissingResolutionAndNamesAnEmptyInput() {
        BrainstormContext.WindowState state = new BrainstormContext.WindowState("Gemini", null, "", "1:1", " ");

        assertEquals("Target model: Gemini\nAspect ratio: 1:1\nText currently in the input field: (empty)",
                state.describe());
    }
}
