package art.moodframe.service.provider;

import art.moodframe.model.ImageGenerationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the FLUX.2 and FLUX 3 request bodies against the BFL OpenAPI schemas (unknown fields give HTTP 422). */
class BflRequestBodyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String build(ImageGenerationRequest request) throws Exception {
        return BflRequestBody.json(request, MAPPER);
    }

    private static JSONObject body(String model, Boolean upsampling) throws Exception {
        ImageGenerationRequest request = new ImageGenerationRequest("an apple", model);
        request.setWidth(1024);
        request.setHeight(1024);
        request.setPromptUpsampling(upsampling);
        return new JSONObject(build(request));
    }

    @Test
    void proAndMaxUseDisablePupNeverPromptUpsampling() throws Exception {
        for (String model : new String[] {"flux.2-pro", "flux.2-max"}) {
            assertTrue(body(model, false).getBoolean("disable_pup"), model + " with upsampling off");
            assertFalse(body(model, true).getBoolean("disable_pup"), model + " with upsampling on");
            assertFalse(body(model, true).has("prompt_upsampling"), model);
        }
    }

    @Test
    void flexUsesPromptUpsamplingNeverDisablePup() throws Exception {
        assertFalse(body("flux.2-flex", false).getBoolean("prompt_upsampling"));
        assertTrue(body("flux.2-flex", true).getBoolean("prompt_upsampling"));
        assertFalse(body("flux.2-flex", false).has("disable_pup"));
    }

    @Test
    void kleinGetsNeitherUpsamplingField() throws Exception {
        for (String model : new String[] {"flux.2-klein-4b", "flux.2-klein-9b"}) {
            JSONObject json = body(model, false);
            assertFalse(json.has("disable_pup"), model);
            assertFalse(json.has("prompt_upsampling"), model);
            assertEquals(1024, json.getInt("width"));
        }
    }

    @Test
    void nothingIsSentWithoutAnUpsamplingChoice() throws Exception {
        JSONObject json = body("flux.2-pro", null);
        assertFalse(json.has("disable_pup"));
        assertFalse(json.has("prompt_upsampling"));
    }

    private static JSONObject flux3Body(String model) throws Exception {
        ImageGenerationRequest request = new ImageGenerationRequest("an apple", model);
        request.setWidth(4096);
        request.setHeight(2304);
        request.setAspectRatio("16:9");
        request.setImageSize("4k");
        request.setSeed(7L);
        request.setSafetyTolerance(2);
        request.setPromptUpsampling(true);
        request.setInputImages(java.util.List.of("aW1hZ2Ux", "aW1hZ2Uy"));
        return new JSONObject(build(request));
    }

    @Test
    void flux3ImageSendsResolutionAspectRatioAndImagesOnly() throws Exception {
        JSONObject json = flux3Body("flux.3-image");
        assertEquals(java.util.Set.of("prompt", "aspect_ratio", "resolution", "safety_tolerance", "images"), json.keySet());
        assertEquals("4k", json.getString("resolution"));
        assertEquals(2, json.getJSONArray("images").length());
    }

    @Test
    void flux3KleinAlsoSendsSeedAndOutputFormat() throws Exception {
        JSONObject json = flux3Body("flux.3-image-klein");
        assertEquals(7, json.getInt("seed"));
        assertEquals("png", json.getString("output_format"));
        assertFalse(json.has("width"));
        assertFalse(json.has("prompt_upsampling"));
    }

    /*  a request with every field and 14 reference images, more than any FLUX model takes  */
    private static ImageGenerationRequest full(String model) {
        ImageGenerationRequest request = new ImageGenerationRequest("an apple", model);
        request.setWidth(1024);
        request.setHeight(768);
        request.setSeed(7L);
        request.setSafetyTolerance(2);
        request.setPromptUpsampling(false);
        request.setGuidance(4.5);
        request.setSteps(30);
        request.setAspectRatio("4:3");
        request.setImageSize("1k");
        List<String> images = new ArrayList<>();
        for (int i = 1; i <= 14; i++)
            images.add("img" + i);
        request.setInputImages(images);
        return request;
    }

    /*  The bodies as the former numbered image fields of the request wrote them, byte for byte  */
    @Test
    void fourteenImagesGiveTheBodiesOfTheNumberedFieldsByteForByte() throws Exception {
        String flux2Images = "\"input_image\":\"img1\",\"input_image_2\":\"img2\",\"input_image_3\":\"img3\","
                + "\"input_image_4\":\"img4\",\"input_image_5\":\"img5\",\"input_image_6\":\"img6\","
                + "\"input_image_7\":\"img7\",\"input_image_8\":\"img8\"}";
        String flux2 = "{\"prompt\":\"an apple\",\"width\":1024,\"height\":768,\"seed\":7,\"safety_tolerance\":2,"
                + "\"output_format\":\"png\",";
        assertEquals(flux2 + "\"prompt_upsampling\":false,\"guidance\":4.5,\"steps\":30," + flux2Images,
                build(full("flux.2-flex")));
        assertEquals(flux2 + "\"disable_pup\":true," + flux2Images, build(full("flux.2-pro")));
        assertEquals(flux2 + flux2Images, build(full("flux.2-klein-4b")));

        String flux3Images = "\"images\":[\"img1\",\"img2\",\"img3\",\"img4\",\"img5\",\"img6\",\"img7\",\"img8\","
                + "\"img9\",\"img10\"]}";
        String flux3 = "{\"prompt\":\"an apple\",\"aspect_ratio\":\"4:3\",\"resolution\":\"1k\",\"safety_tolerance\":2,";
        assertEquals(flux3 + flux3Images, build(full("flux.3-image")));
        assertEquals(flux3 + "\"seed\":7,\"output_format\":\"png\"," + flux3Images, build(full("flux.3-image-klein")));
    }

    @Test
    void oneImageIsTheFirstNumberedField() throws Exception {
        ImageGenerationRequest request = new ImageGenerationRequest("an apple", "flux.2-pro");
        request.setInputImage("img1");
        JSONObject json = new JSONObject(build(request));
        assertEquals("img1", json.getString("input_image"));
        assertFalse(json.has("input_image_2"));
    }
}
