package art.moodframe.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/**
 * The settings of a camera building block, from which the app builds its prompt fragment.
 *
 * <p>Every setting is optional: null stands for "any" and leaves the setting out of the prompt, as BFL warns
 * against too many camera terms in one sentence. Stored as JSON in the column {@code params} of the block.</p>
 *
 * @param body        the camera type
 * @param model       the camera model of the type, null for "Generic"
 * @param lens        the focal length or special lens
 * @param aperture    the aperture, which sets the depth of field
 * @param shutter     the shutter speed
 * @param iso         the ISO, which sets the grain only
 * @param perspective the height and tilt of the camera
 * @param shot        the shot size
 * @since 2.0.0
 */
public record CameraSettings(Body body, String model, Lens lens, Aperture aperture, Shutter shutter, Iso iso,
        Perspective perspective, Shot shot) {

    /** Every setting "any". */
    public static final CameraSettings ANY = new CameraSettings(null, null, null, null, null, null, null, null);

    /*  Removed values and the value that replaces them, so stored settings keep loading  */
    private static final Map<String, String> REMOVED = Map.of("APS_C", "FULL_FRAME", "MM_200", "MM_135",
            "F_2", "F_1_4", "F_8", "F_11", "F_16", "F_11", "ISO_800", "ISO_100");

    /** The camera type. */
    public enum Body {
        SMARTPHONE, COMPACT_35MM, FULL_FRAME, MEDIUM_FORMAT, LARGE_FORMAT, CINEMA, INSTANT, DRONE, ACTION_CAM
    }

    /** The focal lengths from wide to long, followed by the special lenses. */
    public enum Lens {
        MM_14(14), MM_24(24), MM_35(35), MM_50(50), MM_85(85), MM_135(135), MM_400(400),
        MACRO(0), FISHEYE(0), TILT_SHIFT(0), ANAMORPHIC(0);

        private final int millimeters;

        Lens(int millimeters) {
            this.millimeters = millimeters;
        }

        /** The focal length in millimeters, 0 for a special lens. */
        public int millimeters() {
            return millimeters;
        }

        /** True for a special lens (macro, fisheye, tilt-shift, anamorphic). */
        public boolean isSpecial() {
            return millimeters == 0;
        }
    }

    /** The aperture, from open to closed. */
    public enum Aperture {
        F_1_4("f/1.4"), F_4("f/4"), F_11("f/11");

        private final String label;

        Aperture(String label) {
            this.label = label;
        }

        /** The f-number, for example {@code f/1.4}. */
        public String label() {
            return label;
        }
    }

    /** The shutter speed. */
    public enum Shutter {
        FROZEN, NORMAL, MOTION_BLUR, LONG_EXPOSURE
    }

    /** The ISO, from clean to grainy. */
    public enum Iso {
        ISO_100("ISO 100"), ISO_3200("ISO 3200");

        private final String label;

        Iso(String label) {
            this.label = label;
        }

        /** The ISO value, for example {@code ISO 100}. */
        public String label() {
            return label;
        }
    }

    /** The height and tilt of the camera. */
    public enum Perspective {
        EYE_LEVEL, LOW_ANGLE, HIGH_ANGLE, TOP_DOWN, DUTCH_ANGLE
    }

    /** The shot size, from wide to close. */
    public enum Shot {
        EXTREME_WIDE, WIDE, MEDIUM, CLOSE_UP, DETAIL
    }

    /**
     * The settings as JSON text; a setting "any" is left out.
     *
     * @return the JSON text
     */
    public String toJson() {
        JSONObject json = new JSONObject();
        json.putOpt("body", body != null ? body.name() : null);
        json.putOpt("model", model);
        json.putOpt("lens", lens != null ? lens.name() : null);
        json.putOpt("aperture", aperture != null ? aperture.name() : null);
        json.putOpt("shutter", shutter != null ? shutter.name() : null);
        json.putOpt("iso", iso != null ? iso.name() : null);
        json.putOpt("perspective", perspective != null ? perspective.name() : null);
        json.putOpt("shot", shot != null ? shot.name() : null);
        return json.toString();
    }

    /**
     * Reads the settings from JSON text written by {@link #toJson()}; a removed value counts as the value replacing
     * it, a missing or unknown value as "any".
     *
     * @param json the JSON text, null or empty for none
     * @return the settings, {@link #ANY} for text that is no JSON object
     */
    public static CameraSettings fromJson(String json) {
        if (json == null || json.isBlank())
            return ANY;
        try {
            JSONObject object = new JSONObject(json);
            String model = object.optString("model", "");
            return new CameraSettings(value(object, "body", Body.class), model.isEmpty() ? null : model,
                    value(object, "lens", Lens.class), value(object, "aperture", Aperture.class),
                    value(object, "shutter", Shutter.class), value(object, "iso", Iso.class),
                    value(object, "perspective", Perspective.class), value(object, "shot", Shot.class));
        } catch (JSONException e) {
            return ANY;
        }
    }

    private static <E extends Enum<E>> E value(JSONObject object, String key, Class<E> type) {
        try {
            String name = object.optString(key, "");
            return Enum.valueOf(type, REMOVED.getOrDefault(name, name));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
