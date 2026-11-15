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
 * main subject", "everything that moves in the scene"); "if present" for every element that may be missing; the
 * visible result with its strength ("strongly blurred"); framing and distance relative to the main subject, with
 * "all people and objects of the scene kept" where the view widens; at most about 200 characters per value.</p>
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
            Body.SMARTPHONE, "shot on a smartphone, a wide view with everything sharp from the main subject to the "
                    + "background, a bright HDR look with lifted shadows",
            Body.COMPACT_35MM, "shot on a 35mm film camera, a candid analog snapshot with clearly visible film grain "
                    + "and soft, slightly faded colors",
            Body.FULL_FRAME, "shot on a full-frame digital camera, crisp fine detail, clean tones, detail kept in the "
                    + "brightest and darkest areas",
            Body.MEDIUM_FORMAT, "shot on a medium format camera, exceptionally fine detail, very smooth tonal "
                    + "transitions",
            Body.INSTANT, "scan of an instant film photo with its plain white border, soft detail, slightly uneven "
                    + "exposure, gentle faded colors",
            Body.DRONE, "aerial photograph from high above, the main subject small, the ground spread out below it; "
                    + "all people and objects of the scene kept",
            Body.ACTION_CAM, "action camera look, an ultra-wide view close to the main subject, straight lines bowing "
                    + "outward near the edges, crisp vivid colors");

    private static final Map<Lens, String> LENSES = Map.ofEntries(
            Map.entry(Lens.MM_14, "14mm ultra-wide lens: the camera far back, the main subject small in the frame, "
                    + "a large part of the surroundings visible around it, stretched perspective; all people and "
                    + "objects of the scene kept"),
            Map.entry(Lens.MM_24, "24mm wide-angle lens: the camera farther back, the main subject smaller in the "
                    + "frame, much of the surroundings visible, pronounced depth; all people and objects of the scene "
                    + "kept"),
            Map.entry(Lens.MM_35, "35mm lens: the main subject shown within its surroundings, a moderately wide view, "
                    + "natural proportions"),
            Map.entry(Lens.MM_50, "50mm lens: a natural view like the human eye, the main subject with its near "
                    + "surroundings, undistorted proportions"),
            Map.entry(Lens.MM_85, "85mm lens: a tight view, the main subject large in the frame, the background "
                    + "clearly enlarged and pulled close behind it"),
            Map.entry(Lens.MM_135, "135mm telephoto lens: a narrow view, the main subject filling the frame, the "
                    + "background strongly enlarged and pulled close behind it, flattened depth"),
            Map.entry(Lens.MM_400, "400mm super-telephoto lens: a very narrow view from far away, the main subject "
                    + "isolated, the background hugely enlarged and stacked flat right behind it"),
            Map.entry(Lens.MACRO, "macro lens: extreme magnification, a tiny part of the main subject filling the "
                    + "whole frame, its fine textures clearly visible, razor-thin focus"),
            Map.entry(Lens.FISHEYE, "fisheye lens: a 180-degree view, all straight lines strongly curved into a "
                    + "round, bulging image; all people and objects of the scene kept"),
            Map.entry(Lens.TILT_SHIFT, "tilt-shift miniature effect: only a narrow horizontal band in sharp focus, "
                    + "the top and bottom of the image strongly blurred, the scene looking like a tiny model"),
            Map.entry(Lens.ANAMORPHIC, "anamorphic lens: a widescreen cinematic look, oval bokeh, long horizontal "
                    + "lens flares from bright lights, if present"));

    private static final Map<Aperture, String> APERTURES = Map.of(
            Aperture.F_1_4, "f/1.4: only the main subject in focus, everything behind it strongly blurred into soft "
                    + "shapes",
            Aperture.F_4, "f/4: the main subject sharp, the background gently blurred but still recognizable",
            Aperture.F_11, "f/11: everything sharp from front to back, the main subject as well as the background");

    /** "Normal" has no text. */
    private static final Map<Shutter, String> SHUTTERS = Map.of(
            Shutter.FROZEN, "a fast shutter: everything that moves in the scene, such as hair, fabric, water, leaves, "
                    + "people or vehicles, if present, is frozen crisply in mid-motion",
            Shutter.NORMAL, "",
            Shutter.MOTION_BLUR, "a slow shutter: everything that moves in the scene, such as hair, fabric, water, "
                    + "leaves, people or vehicles, if present, is smeared into long, clearly visible streaks of "
                    + "motion blur; everything static stays sharp",
            Shutter.LONG_EXPOSURE, "a long exposure of several seconds: flowing water, if present, turns into a "
                    + "smooth white veil, clouds, if present, into soft streaks, moving people, if present, into "
                    + "faint trails and vehicles, if present, into streaks of light; everything static stays sharp");

    private static final Map<Iso, String> ISOS = Map.of(
            Iso.ISO_100, "ISO 100: a clean, smooth image without any visible grain or noise",
            Iso.ISO_3200, "ISO 3200: strong, clearly visible grain and noise across the whole image, most of all in "
                    + "the shadows");

    private static final Map<Perspective, String> PERSPECTIVES = Map.of(
            Perspective.EYE_LEVEL, "eye level: the camera at the height of the main subject, looking straight at it",
            Perspective.LOW_ANGLE, "a low angle: the camera low below the main subject, looking up at it steeply",
            Perspective.HIGH_ANGLE, "a high angle: the camera high above the main subject, looking down at it",
            Perspective.TOP_DOWN, "a top-down view: the camera directly above the main subject, looking straight down",
            Perspective.DUTCH_ANGLE, "a dutch angle: the whole image clearly tilted, the horizon, if present, and all "
                    + "vertical lines running at a slant");

    private static final Map<Shot, String> SHOTS = Map.of(
            Shot.EXTREME_WIDE, "an extreme wide shot: the main subject tiny in a vast view of its surroundings; all "
                    + "people and objects of the scene kept",
            Shot.WIDE, "a wide shot: the whole main subject in the frame with space around it; all people and objects "
                    + "of the scene kept",
            Shot.MEDIUM, "a medium shot: about half of the main subject in the frame, such as a person from the waist "
                    + "up",
            Shot.CLOSE_UP, "a close-up: the camera close to the main subject, which fills the frame",
            Shot.DETAIL, "an extreme close-up: a single small detail of the main subject filling the whole frame");

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
        still works like "Generic": "shot on Leica M6, a 35mm film camera, ..."; an analog one of a digital type
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
