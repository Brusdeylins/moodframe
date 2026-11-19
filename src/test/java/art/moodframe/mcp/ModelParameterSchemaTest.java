package art.moodframe.mcp;

import art.moodframe.config.ApplicationConfig;
import art.moodframe.config.ConfigMocks;
import art.moodframe.model.GenerationModel;
import art.moodframe.model.ImageGenerationRequest;
import art.moodframe.model.ModelParameter;
import art.moodframe.model.ModelParameters;
import art.moodframe.service.provider.MockProviders;
import art.moodframe.service.provider.ProviderRegistry;
import art.moodframe.service.provider.RequestOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * The parameters of a model come from one table ({@link ModelParameters}): the MCP schema of get_capabilities, the
 * validation of generate (see McpEditToolsTest) and the preparation of the request by the provider follow it.
 */
class ModelParameterSchemaTest {

    private static ApplicationConfig config() {
        ApplicationConfig config = ConfigMocks.config();
        when(config.generation().getFlux2SafetyTolerance()).thenReturn(2);
        when(config.generation().getFlux2FlexGuidance()).thenReturn(5.0);
        when(config.generation().getFlux2FlexSteps()).thenReturn(50);
        return config;
    }

    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void theSchemaOfEveryModelListsTheParametersOfTheTableWithTheirLimits() {
        ApplicationConfig config = config();
        for (GenerationModel model : GenerationModel.values()) {
            ObjectNode schema = LibraryTools.parameters(config, model);
            assertEquals(model.parameters().stream().map(ModelParameter::name).toList(), names(schema),
                    model.name());
            for (ModelParameter parameter : model.parameters()) {
                JsonNode property = schema.path(parameter.name());
                if (parameter.minimum() != null)
                    assertEquals(parameter.minimum().doubleValue(), property.path("minimum").asDouble());
                if (parameter.maximum() != null)
                    assertEquals(parameter.maximum().doubleValue(), property.path("maximum").asDouble());
                assertEquals(!parameter.choices().isEmpty(), property.has("enum"), parameter.name());
            }
        }
    }

    @Test
    void theSchemaOfFlexIsTheOneOfTheFormerHandWrittenTable() {
        assertEquals("{\"seed\":{\"type\":\"integer\",\"description\":\"Fixed seed; random without\"},"
                + "\"safetyTolerance\":{\"type\":\"integer\",\"description\":\"0 strict to 5 permissive\","
                + "\"minimum\":0,\"maximum\":5,\"default\":2},"
                + "\"promptUpsampling\":{\"type\":\"boolean\",\"description\":\"BFL enhances the prompt\"},"
                + "\"guidance\":{\"type\":\"number\",\"minimum\":1.5,\"maximum\":10.0,\"default\":5.0},"
                + "\"steps\":{\"type\":\"integer\",\"description\":\"Inference steps\",\"minimum\":1,\"maximum\":50,"
                + "\"default\":50}}", LibraryTools.parameters(config(), GenerationModel.FLUX2_FLEX).toString());
        assertEquals("{\"videoSeconds\":{\"type\":\"integer\",\"default\":8,\"enum\":[3,4,5,6,7,8,9,10]}}",
                LibraryTools.parameters(config(), GenerationModel.OMNI_720P).toString());
        assertEquals("{\"thinkingLevel\":{\"type\":\"string\",\"description\":\"Thinking before the image\","
                + "\"enum\":[\"minimal\",\"high\"]},\"googleSearch\":{\"type\":\"boolean\",\"description\":"
                + "\"Grounds the image in a Google Search\"}}",
                LibraryTools.parameters(config(), GenerationModel.NANO_BANANA2_1K).toString());
    }

    @Test
    void theProviderSendsOnlyTheParametersTheModelTakes() {
        ProviderRegistry providers = MockProviders.preparing();
        RequestOptions options = new RequestOptions("item", "1K", 4, null, null, 3.5, 40, null, List.of());
        for (GenerationModel model : List.of(GenerationModel.FLUX2_FLEX, GenerationModel.FLUX2_PRO,
                GenerationModel.FLUX3_IMAGE_1K)) {
            ImageGenerationRequest request = new ImageGenerationRequest("x", model.getApiModelId());
            providers.get(model.getProvider()).prepare(request, model, options);
            boolean flex = model.parameter(ModelParameters.GUIDANCE.name()) != null;
            assertEquals(flex ? 3.5 : null, request.getGuidance(), model.name());
            assertEquals(flex ? Integer.valueOf(40) : null, request.getSteps(), model.name());
            assertEquals(4, request.getSafetyTolerance(), model.name());
        }
    }

    @Test
    void aParameterAcceptsOnlyValuesWithinItsLimitsAndChoices() {
        assertTrue(ModelParameters.GUIDANCE.accepts(1.5));
        assertTrue(ModelParameters.GUIDANCE.accepts(10));
        assertFalse(ModelParameters.GUIDANCE.accepts(10.01));
        assertFalse(ModelParameters.STEPS.accepts(2.5));
        assertFalse(ModelParameters.STEPS.accepts(0));
        assertEquals("from 1.5 to 10", ModelParameters.GUIDANCE.range());
        ModelParameter seconds = GenerationModel.OMNI_720P.parameter(ModelParameters.VIDEO_SECONDS);
        assertTrue(seconds.accepts(3));
        assertFalse(seconds.accepts(11));
        assertNull(GenerationModel.FLUX2_PRO.parameter(ModelParameters.GUIDANCE.name()));
    }
}
