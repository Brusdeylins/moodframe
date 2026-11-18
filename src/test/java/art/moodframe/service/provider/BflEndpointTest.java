package art.moodframe.service.provider;

import art.moodframe.model.ApiProvider;
import art.moodframe.model.GenerationModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Checks that the endpoint of every FLUX model, now derived from its id, is the one the former table named. */
class BflEndpointTest {

    private static final Map<String, String> FORMER_ENDPOINTS = Map.of(
            "flux.3-image", "/v1/flux-3-image",
            "flux.3-image-klein", "/v1/flux-3-image-klein",
            "flux.2-pro", "/v1/flux-2-pro",
            "flux.2-max", "/v1/flux-2-max",
            "flux.2-flex", "/v1/flux-2-flex",
            "flux.2-klein-4b", "/v1/flux-2-klein-4b",
            "flux.2-klein-9b", "/v1/flux-2-klein-9b");

    @Test
    void everyFluxModelKeepsItsEndpoint() {
        for (GenerationModel model : GenerationModel.values()) {
            if (model.getProvider() == ApiProvider.BFL)
                assertEquals(FORMER_ENDPOINTS.get(model.getApiModelId()),
                        BflApiServiceImpl.getEndpointForModel(model.getApiModelId()), model.name());
        }
        assertEquals("/v1/flux-2-max", BflApiServiceImpl.getEndpointForModel("FLUX.2-MAX"));
    }

    @Test
    void anUnknownModelGoesToFlux3Image() {
        assertEquals("/v1/flux-3-image", BflApiServiceImpl.getEndpointForModel(null));
        assertEquals("/v1/flux-3-image", BflApiServiceImpl.getEndpointForModel("flux-pro-1.1"));
        assertEquals("/v1/flux-3-image", BflApiServiceImpl.getEndpointForModel("gemini-3-pro-image"));
    }
}
