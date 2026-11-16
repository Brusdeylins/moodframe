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
 * <p>Rules for the texts, so they work for any motif and also on a reference photo: roles instead of things ("the
 * main subject", "everything that moves"); "if present" for every element that may be missing; the visible result
 * with its strength ("strongly blurred") and only the effect of the setting itself; a lens tells the angle of view and
 * the zoom from the same position, the shot size the size of the main subject; keeping people and objects is said
 * once by {@link PromptComposer#EDIT_CAMERA_INTRO}; at most 200 characters per value.</p>
 *
 * @since 2.0.0
 */
public final class CameraPromptBuilder {

    /**
     * A camera model offered next to the camera type.
     *
     * @param body   the camera type
     * @param name   the model name as it goes into the prompt
     * @param analog true for a film camera of a digital type, shown as "(analog)" and named a film camera in the
     *               prompt
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
            new Model(Body.COMPACT_35MM, "Leica M6", false),
            new Model(Body.FULL_FRAME, "Sony A7 IV", false),
            new Model(Body.FULL_FRAME, "Canon EOS R5", false),
            new Model(Body.FULL_FRAME, "Nikon Z8", false),
            new Model(Body.FULL_FRAME, "Leica Q3", false),
            new Model(Body.MEDIUM_FORMAT, "Hasselblad X2D", false),
            new Model(Body.MEDIUM_FORMAT, "Fujifilm GFX 100S", false),
            new Model(Body.MEDIUM_FORMAT, "Hasselblad 500C/M", true),
            new Model(Body.MEDIUM_FORMAT, "Pentax 67", true),
            new Model(Body.INSTANT, "Polaroid SX-70", false));

    /** "Generic": the device class and its effect. */
    private static final Map<Body, String> BODIES = Map.of(
            Body.SMARTPHONE, "shot on a smartphone: everything sharp from front to back, bright HDR with lifted "
                    + "shadows, oversharpened edges, punchy colors, a small hard sunstar with green lens ghosts, if "
                    + "the sun is present",
            Body.COMPACT_35MM, "shot on a 35mm film compact camera: a candid analog snapshot, coarse, clearly visible "
                    + "film grain, faded film colors, darker corners, a warm halation glow around the sun and bright "
                    + "lights, if present",
            Body.FULL_FRAME, "shot on a full-frame digital camera: crisp fine detail, clean tones, a wide dynamic "
                    + "range with detail in the brightest and darkest areas, natural colors",
            Body.MEDIUM_FORMAT, "shot on a medium format camera: exceptionally fine detail, very smooth tonal "
                    + "transitions, rich lifelike midtones, the main subject sharp, the background falling off softly "
                    + "into gentle blur",
            Body.INSTANT, "scan of an instant film photo: a square picture in a cream-white frame with a much wider "
                    + "bottom border, soft detail, low contrast, pastel colors",
            Body.DRONE, "aerial drone photo from far above, about 50 meters high, looking steeply down: the main "
                    + "subject tiny, the terrain around it spread out below like a map",
            Body.ACTION_CAM, "action camera: the camera very close to the main subject, which looms large, the "
                    + "background small and far away, extreme barrel distortion filling the rectangular frame, the "
                    + "horizon curved into an arc");

    private static final Map<Lens, String> LENSES = Map.ofEntries(
            Map.entry(Lens.MM_14, "14mm ultra-wide lens, 114° view: a very wide view, everything in the frame much "
                    + "smaller, far more of the surroundings at every edge, lines stretched and converging near the "
                    + "edges"),
            Map.entry(Lens.MM_24, "24mm wide-angle lens, 84° view: a wide view, everything in the frame smaller, much "
                    + "more of the surroundings at the edges, a strong sense of depth"),
            Map.entry(Lens.MM_35, "35mm lens, 63° view: a moderately wide view, the main subject together with its "
                    + "surroundings, natural perspective"),
            Map.entry(Lens.MM_50, "50mm lens, 47° view: a natural perspective like the human eye, undistorted "
                    + "proportions, near and far in natural relation"),
            Map.entry(Lens.MM_85, "85mm lens, 29° view: a narrower view, everything in the frame larger, less of the "
                    + "surroundings, the background enlarged and pulled closer, softly out of focus"),
            Map.entry(Lens.MM_135, "135mm telephoto lens, 18° view: a narrow view, everything much larger, only a "
                    + "small slice of the background, strongly enlarged and pulled close behind the main subject, "
                    + "flattened depth"),
            Map.entry(Lens.MM_400, "400mm super-telephoto lens, 6° view from very far away: the background hugely "
                    + "enlarged and stacked flat right behind the main subject, distant mountains or buildings, if "
                    + "present, looming huge"),
            Map.entry(Lens.MACRO, "macro lens at 1:1: a tiny detail of the main subject, such as the eye of a person, "
                    + "filling the frame, nothing between the lens and the detail, finest textures visible, a "
                    + "paper-thin plane in focus"),
            Map.entry(Lens.FISHEYE, "circular fisheye lens: a round 180° image circle on pure black, nothing outside "
                    + "it, everything inside heavily bulging from the center, straight lines bent into strong curves, "
                    + "the horizon a deep arc"),
            Map.entry(Lens.TILT_SHIFT, "tilt-shift lens with tilted focus: a realistic photo with only a narrow "
                    + "horizontal band in sharp focus, everything above and below it progressively and strongly "
                    + "blurred"),
            Map.entry(Lens.ANAMORPHIC, "anamorphic lens: a widescreen cinematic look, oval out-of-focus highlights, "
                    + "long horizontal blue lens flares from bright lights, if present, slight barrel distortion"));

    private static final Map<Aperture, String> APERTURES = Map.of(
            Aperture.F_1_4, "f/1.4: razor-thin focus on the main subject only; the background melts into smooth, "
                    + "featureless color areas, foreground things near the camera into soft blurs; bright lights as "
                    + "round glows",
            Aperture.F_4, "f/4: a moderate depth of field, the main subject sharp, things well in front of or behind "
                    + "it gently blurred but recognizable; the sun, if present, with short soft rays",
            Aperture.F_11, "f/11: a deep depth of field, everything sharp from the nearest foreground to the far "
                    + "background; the sun or bright point lights, if present, as crisp starbursts with long rays");

    /** "Normal" has no text. */
    private static final Map<Shutter, String> SHUTTERS = Map.of(
            Shutter.FROZEN, "a very fast shutter of 1/4000 s: everything that moves, such as hair, fabric, water "
                    + "drops, people or vehicles, if present, frozen crisply in mid-motion, no blur",
            Shutter.NORMAL, "",
            Shutter.MOTION_BLUR, "a slow shutter of about 1/15 s: everything that moves, such as hair, fabric, water, "
                    + "leaves, people or vehicles, if present, smeared into long, clearly visible streaks; static "
                    + "parts sharp",
            Shutter.LONG_EXPOSURE, "a 5-second exposure of exactly this scene, nothing added: whatever moves, such as "
                    + "hair, fabric or leaves, smeared strongly; water a silky veil, walkers ghosts, vehicles light "
                    + "trails; still parts sharp");

    private static final Map<Iso, String> ISOS = Map.of(
            Iso.ISO_100, "ISO 100: a clean, smooth image without any visible noise, the finest detail, rich colors "
                    + "and the widest tonal range",
            Iso.ISO_3200, "ISO 3200: strong, clearly visible grain and color noise across the whole image, strongest "
                    + "in the shadows, slightly softer detail and muted colors");

    private static final Map<Perspective, String> PERSPECTIVES = Map.of(
            Perspective.EYE_LEVEL, "eye level: the camera at the height of the main subject, looking straight at it, "
                    + "vertical lines upright",
            Perspective.LOW_ANGLE, "a low angle: the camera low, looking up at the main subject, which looms large, "
                    + "the sky or ceiling behind it, vertical lines, if present, converging upward",
            Perspective.HIGH_ANGLE, "a high angle: the camera above the main subject, looking down at it, the main "
                    + "subject smaller, the ground behind it",
            Perspective.TOP_DOWN, "a top-down view from high above, looking straight down: the main subject seen from "
                    + "above like everything around it, no larger than people or things beside it, only ground and "
                    + "tops visible, no horizon",
            Perspective.DUTCH_ANGLE, "a dutch angle: the camera rolled sideways by about 20 degrees, the whole image "
                    + "clearly tilted, the horizon, if present, and all vertical lines running diagonally");

    private static final Map<Shot, String> SHOTS = Map.of(
            Shot.EXTREME_WIDE, "an extreme wide shot: the main subject tiny within a vast view of its surroundings",
            Shot.WIDE, "a wide shot: the whole main subject in the frame with space around it, such as a person from "
                    + "head to toe",
            Shot.MEDIUM, "a medium shot: the camera closer, about half of the main subject in the frame, such as a "
                    + "person from the waist up, the rest cut off by the frame edge",
            Shot.CLOSE_UP, "a close-up: the main subject fills the frame, such as a face with head and shoulders",
            Shot.DETAIL, "an extreme close-up: a single small detail of the main subject fills the whole frame, such "
                    + "as the eyes or a hand");

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

        /*  the texts contain commas themselves, so the settings are separated by semicolons  */
        return String.join("; ", parts);
    }

    /*  A model names the concrete camera first and keeps the device class and its effect, so an unknown model
        still works like "Generic": "shot on Leica M6, a 35mm film compact camera: ..."; an analog one of a digital type
        names a film camera  */
    private static String body(Body body, String model) {
        String generic = BODIES.get(body);
        if (model == null || model.isBlank())
            return generic;
        String device = generic.startsWith("shot on ") ? generic.substring(8) : generic;
        if (MODELS.stream().anyMatch((Model known) -> known.name().equals(model.trim()) && known.analog()))
            device = device.replaceFirst(" camera", " film camera");
        return "shot on " + model.trim() + ", " + device;
    }
}
