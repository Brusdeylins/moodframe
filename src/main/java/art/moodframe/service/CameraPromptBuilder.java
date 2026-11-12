package art.moodframe.service;

import art.moodframe.model.CameraSettings;
import art.moodframe.model.CameraSettings.Aperture;
import art.moodframe.model.CameraSettings.Body;
import art.moodframe.model.CameraSettings.Iso;
import art.moodframe.model.CameraSettings.Lens;
import art.moodframe.model.CameraSettings.Perspective;
import art.moodframe.model.CameraSettings.Shot;
import art.moodframe.model.CameraSettings.Shutter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the prompt fragment of a camera from its settings, out of fixed text tables.
 *
 * <p>The texts follow BFL "Style, Aesthetics &amp; Text" and "Prompt Reference" (name camera, lens or film
 * concretely and describe the visible effect) and Google "Nano Banana image generation" ("Control the Camera").
 * The order is type, focal length, aperture, shutter, ISO, perspective, shot size; a setting "any" is left out.
 * For a style that is not photographic only perspective and shot size apply.</p>
 *
 * @since 2.0.0
 */
public final class CameraPromptBuilder {

    /**
     * A camera model offered next to the camera type.
     *
     * @param body   the camera type
     * @param name   the model name as it goes into the prompt
     * @param analog true for a film camera of a digital type, shown as "(analog)"
     */
    public record Model(Body body, String name, boolean analog) {

        /** The name as the list shows it. */
        public String label() {
            return analog ? name + " (analog)" : name;
        }
    }

    /** The camera models per type; a model unknown to an image model works like "Generic". */
    public static final List<Model> MODELS = List.of(
            new Model(Body.SMARTPHONE, "iPhone 17 Pro", false),
            new Model(Body.SMARTPHONE, "Google Pixel 10 Pro", false),
            new Model(Body.COMPACT_35MM, "Contax T2", false),
            new Model(Body.COMPACT_35MM, "Olympus mju-II", false),
            new Model(Body.COMPACT_35MM, "Leica M6", false),
            new Model(Body.FULL_FRAME, "Sony A7 IV", false),
            new Model(Body.FULL_FRAME, "Canon EOS R5", false),
            new Model(Body.FULL_FRAME, "Nikon Z8", false),
            new Model(Body.FULL_FRAME, "Leica Q3", false),
            new Model(Body.FULL_FRAME, "Fujifilm X-T5", false),
            new Model(Body.FULL_FRAME, "Fujifilm X100VI", false),
            new Model(Body.MEDIUM_FORMAT, "Hasselblad X2D", false),
            new Model(Body.MEDIUM_FORMAT, "Fujifilm GFX 100S", false),
            new Model(Body.MEDIUM_FORMAT, "Hasselblad 500C/M", true),
            new Model(Body.MEDIUM_FORMAT, "Pentax 67", true),
            new Model(Body.LARGE_FORMAT, "Linhof Technika", false),
            new Model(Body.CINEMA, "ARRI Alexa 35", false),
            new Model(Body.CINEMA, "RED V-Raptor", false),
            new Model(Body.CINEMA, "IMAX film camera", false),
            new Model(Body.INSTANT, "Polaroid SX-70", false),
            new Model(Body.INSTANT, "Fujifilm Instax", false),
            new Model(Body.DRONE, "DJI Mavic 3", false),
            new Model(Body.ACTION_CAM, "GoPro Hero", false));

    /** "Generic": the device class and its effect. */
    private static final Map<Body, String> BODIES = Map.of(
            Body.SMARTPHONE, "shot on a smartphone, deep focus, natural unstaged look",
            Body.COMPACT_35MM, "shot on a 35mm compact film camera, candid analog snapshot look",
            Body.FULL_FRAME, "shot on a full-frame digital camera, clean sharp detail, high dynamic range",
            Body.MEDIUM_FORMAT, "shot on a medium format camera, exceptional detail, smooth tonal transitions",
            Body.LARGE_FORMAT, "shot on a 4x5 large format view camera, extreme detail, "
                    + "precisely corrected vertical lines",
            Body.CINEMA, "shot on a digital cinema camera, motion-picture look",
            Body.INSTANT, "instant film photo, soft detail, slightly imperfect exposure",
            Body.DRONE, "aerial photograph from high above",
            Body.ACTION_CAM, "action camera look, ultra-wide view with barrel distortion");

    private static final Map<Lens, String> LENSES = Map.ofEntries(
            Map.entry(Lens.MM_14, "14mm ultra-wide lens, an extremely wide field of view, "
                    + "dramatically stretched perspective"),
            Map.entry(Lens.MM_24, "24mm wide-angle lens, a wide field of view, pronounced depth"),
            Map.entry(Lens.MM_35, "35mm lens, natural documentary perspective"),
            Map.entry(Lens.MM_50, "50mm lens, neutral perspective close to human vision"),
            Map.entry(Lens.MM_85, "85mm portrait lens, flattering proportions, slight background compression"),
            Map.entry(Lens.MM_135, "135mm telephoto lens, strong background compression, the subject isolated"),
            Map.entry(Lens.MM_400, "400mm super-telephoto lens, very narrow field of view, "
                    + "the background stacked close behind the subject"),
            Map.entry(Lens.MACRO, "macro lens, extreme close-up revealing fine detail"),
            Map.entry(Lens.FISHEYE, "fisheye lens, strongly curved lines, 180-degree view"),
            Map.entry(Lens.TILT_SHIFT, "tilt-shift lens, a narrow slice in focus, miniature effect"),
            Map.entry(Lens.ANAMORPHIC, "anamorphic lens, widescreen cinematic look, oval bokeh, "
                    + "horizontal lens flare"));

    private static final Map<Aperture, String> APERTURES = Map.of(
            Aperture.F_1_4, "f/1.4, very shallow depth of field, the background melted into soft blur",
            Aperture.F_4, "f/4, moderate depth of field, the background gently soft",
            Aperture.F_11, "f/11, deep depth of field, front and back in sharp focus");

    /** "Normal" has no text. */
    private static final Map<Shutter, String> SHUTTERS = Map.of(
            Shutter.FROZEN, "fast shutter speed, motion frozen crisply",
            Shutter.NORMAL, "",
            Shutter.MOTION_BLUR, "slow shutter speed, moving elements streaked with motion blur, static elements sharp",
            Shutter.LONG_EXPOSURE, "long exposure, anything in motion smoothed into a silky veil or drawn into trails");

    private static final Map<Iso, String> ISOS = Map.of(
            Iso.ISO_100, "ISO 100, clean image without visible grain or noise",
            Iso.ISO_3200, "ISO 3200, pronounced grain and high-ISO noise");

    private static final Map<Perspective, String> PERSPECTIVES = Map.of(
            Perspective.EYE_LEVEL, "shot at eye level",
            Perspective.LOW_ANGLE, "low-angle shot from below, looking up",
            Perspective.HIGH_ANGLE, "high-angle shot from above, looking down",
            Perspective.TOP_DOWN, "top-down view from directly above",
            Perspective.DUTCH_ANGLE, "dutch angle with a tilted horizon");

    private static final Map<Shot, String> SHOTS = Map.of(
            Shot.EXTREME_WIDE, "extreme wide shot, the subject small in the frame",
            Shot.WIDE, "wide shot, the full figure in frame",
            Shot.MEDIUM, "medium shot",
            Shot.CLOSE_UP, "close-up framing",
            Shot.DETAIL, "extreme close-up on a single detail");

    private CameraPromptBuilder() {
    }

    /**
     * The camera models of a type.
     *
     * @param body the camera type
     * @return the models in the order of the table
     */
    public static List<Model> models(Body body) {
        return MODELS.stream().filter((Model model) -> model.body() == body).toList();
    }

    /**
     * Builds the prompt fragment, without the label "Camera:" or "Framing:", which the composer adds.
     *
     * @param settings     the settings; a stage that contradicts an earlier one is left out (see
     *                     {@link CameraSettings#withoutConflicts()})
     * @param photographic whether the style describes a photograph; otherwise only perspective and shot size apply
     * @return the fragment, empty if no setting applies
     */
    public static String build(CameraSettings settings, boolean photographic) {
        settings = settings.withoutConflicts();
        List<String> parts = new ArrayList<>();
        if (photographic) {
            if (settings.body() != null)
                parts.add(body(settings.body(), settings.model()));
            if (settings.lens() != null)
                parts.add(LENSES.get(settings.lens()));
            if (settings.aperture() != null)
                parts.add(APERTURES.get(settings.aperture()));
            if (settings.shutter() != null)
                parts.add(SHUTTERS.get(settings.shutter()));
            if (settings.iso() != null)
                parts.add(ISOS.get(settings.iso()));
        }
        if (settings.perspective() != null)
            parts.add(PERSPECTIVES.get(settings.perspective()));
        if (settings.shot() != null)
            parts.add(SHOTS.get(settings.shot()));
        parts.removeIf(String::isEmpty);
        return String.join(", ", parts);
    }

    /*  A model names the concrete camera first and keeps the device class and its effect, so an unknown model
        still works like "Generic": "shot on Leica M6, a 35mm compact film camera, ..."  */
    private static String body(Body body, String model) {
        String generic = BODIES.get(body);
        if (model == null || model.isBlank())
            return generic;
        return "shot on " + model.trim() + ", " + (generic.startsWith("shot on ") ? generic.substring(8) : generic);
    }
}
