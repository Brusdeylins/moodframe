package art.moodframe.tools;

import art.moodframe.database.DatabaseConfig;
import art.moodframe.model.CameraSettings;
import art.moodframe.model.CameraSettings.Aperture;
import art.moodframe.model.CameraSettings.Body;
import art.moodframe.model.CameraSettings.Iso;
import art.moodframe.model.CameraSettings.Lens;
import art.moodframe.model.CameraSettings.Perspective;
import art.moodframe.model.CameraSettings.Shot;
import art.moodframe.model.CameraSettings.Shutter;
import art.moodframe.model.Mood;
import art.moodframe.model.PromptBlock;
import art.moodframe.service.prompt.CameraPromptBuilder;
import art.moodframe.service.library.ColorStyleService;
import art.moodframe.service.library.MoodService;
import art.moodframe.service.library.PromptBlockService;
import art.moodframe.service.prompt.PromptComposer;
import art.moodframe.tools.CameraCheckApi.Generator;
import art.moodframe.tools.CameraCheckApi.Rating;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Camera check: generates test images for every stage of the camera building block, its camera models, standard
 * cameras and locks, rates them blindly with a vision model and writes contact sheets and an HTML report.
 *
 * <p>The prompts come from the real code only ({@link CameraPromptBuilder}, {@link PromptComposer}, the standard
 * blocks and moods), in two contexts: the subject with the camera line alone, and inside a standard mood. Every image
 * gets a manifest next to it; an image with the same prompt, model and seed (or repetition) is never generated
 * twice. Every run starts with a dry run of the missing images and their cost; the cost cap counts everything
 * spent in the directory so far and ends the generation when it is reached.</p>
 *
 * <p>Not part of {@code ./gradlew test}, as it costs money: {@code ./gradlew cameraCheck [-PcameraCheck.dry]
 * [-PcameraCheck.only=aperture,lens] [-PcameraCheck.cap=60] [-PcameraCheck.dir=...]}, by default into
 * {@code build/camera-check}. Keys come from the environment only ({@code GEMINI_API_KEY}, {@code BFL_API_KEY},
 * {@code OPENAI_API_KEY}).</p>
 *
 * @since 2.0.2
 */
public final class CameraCheck {

    /*  The contexts: the subject with the camera line alone, or inside the prompt of a standard mood  */
    static final String ALONE = "allein";
    static final String MORNING = "Quiet Morning";
    static final String CANDLE = "Candle Glow";
    static final String PASTEL = "Dreamy Pastel";

    /*  Subjects with a place, per axis chosen so the effect has to show and the model needs to invent nothing  */
    private static final String PARK = "A young woman reading a book on a bench in a city park, a row of old trees "
            + "and a stone fountain 10 to 30 meters behind her.";
    private static final String STREET = "A cyclist riding along a city street, pedestrians on the sidewalk and a "
            + "stone fountain on a small square.";
    private static final String INTERIOR = "A man reading a newspaper in an armchair in a living room with "
            + "bookshelves and a floor lamp, normal daylight from a window.";
    private static final String FACADE = "A woman in a red coat standing in front of the facade of an old town house "
            + "with tall windows and a wooden door.";
    private static final String PORTRAIT = "Street portrait of a man in his thirties with a short beard and a denim "
            + "jacket on a sidewalk, shop windows behind him.";
    private static final String RING = "A woman's hand with a silver ring with a small blue stone, resting on a "
            + "wooden table.";

    /*  The fixed ordering questions: property, first image, last image  */
    private static final List<String> BY_FIELD = List.of("field of view",
            "the widest field of view (most of the surroundings visible, strongest wide-angle perspective)",
            "the narrowest field of view (strongest telephoto compression, background large and close behind the "
                    + "subject)");
    private static final List<String> BY_DEPTH = List.of("depth of field",
            "the shallowest depth of field (background most blurred)",
            "the deepest depth of field (foreground and background sharp)");
    private static final List<String> BY_MOTION = List.of("motion blur", "moving things most crisply frozen",
            "the strongest blur, smoothing or light trails of moving things");
    private static final List<String> BY_GRAIN = List.of("grain and noise", "the cleanest image",
            "the most grain and noise");
    private static final List<String> BY_HEIGHT = List.of("camera height", "the lowest camera, looking up",
            "the highest camera, looking straight down");
    private static final List<String> BY_FRAMING = List.of("tightness of framing",
            "the widest framing (subject smallest in the frame)",
            "the tightest framing (a single detail fills the frame)");
    private static final List<String> BY_MAGNIFICATION = List.of("magnification", "the least magnified",
            "the most magnified (the finest structures largest)");

    /*  Side effects that count as a defect; an added prop ("object") is only listed  */
    private static final Set<String> DEFECTS = Set.of("text", "frame", "equipment", "location", "person",
            "composition", "broken");

    private static final String QUESTIONNAIRE = """
            You rate a generated image for a camera test. You do not know how it was made.
            The image was requested to show: "%s"
            Answer with one JSON object with exactly these keys:
            "shot_size": one of "extreme_wide" (subject tiny in a large scene), "wide" (full figure or whole object \
            with surroundings), "medium" (person from the waist up), "close_up" (a face or an object fills the frame), \
            "detail" (a single small detail fills the frame);
            "camera_height": one of "low_angle" (camera below, looking up), "eye_level", "high_angle" (camera above, \
            looking down), "top_down" (looking straight down from directly above);
            "horizon_tilted": true or false (the whole frame clearly rotated, a dutch angle);
            "depth_of_field": one of "shallow" (background strongly blurred), "moderate" (background softly blurred \
            but recognizable), "deep" (foreground and background sharp);
            "field_of_view": one of "ultra_wide", "wide", "normal", "telephoto", "super_telephoto" (judge by \
            perspective, distortion and background compression);
            "special_look": one of "none", "fisheye" (strongly curved lines, circular distortion), "tilt_shift" \
            (miniature look, a narrow sharp band), "anamorphic" (widescreen cinema look, horizontal streak flares, \
            oval bokeh), "macro" (extreme magnification of tiny structures);
            "motion": one of "none_visible", "frozen" (fast motion crisply frozen), "motion_blur" (moving things \
            streaked, static things sharp), "long_exposure" (moving things smoothed into veils or light trails);
            "grain": one of "clean", "some", "heavy";
            "camera_look": the camera this most likely came from, one of "smartphone", "film_compact" (35mm film \
            snapshot), "full_frame_digital", "medium_format", "large_format", "cinema", "instant_film", \
            "aerial_drone", "action_cam";
            "side_effects": a list of what the image shows that the request did not ask for and that changes its \
            content, each as {"category": one of "text" (letters, numbers, date stamps, logos, brand names, \
            watermarks), "frame" (borders, film edges, an instant photo frame, letterbox bars), "equipment" \
            (cameras, drones, tripods, photographers), "location" (a different kind of place than requested), \
            "person" (a different or additional main person, a different action), "object" (other added props), \
            "composition" (a duplicated, stacked or collaged image), "description": "short"}; an empty list if \
            nothing; ordinary details that belong to the requested scene, lighting, colors and style do not count;
            "broken": one of "no", "partly", "yes" (broken or nonsensical as a picture: impossible viewpoint, \
            duplicated or stacked image, distorted anatomy, contradictory optics);
            "broken_reason": "short", empty if "no".""";

    private static final String ORDERING = """
            These %d images show the same requested scene in random order, made with different camera settings, \
            and each is a separate generation, so person and details may differ. Sort all of them by %s: first the \
            image with %s, last the image with %s. Every image number appears exactly once. Answer with one JSON \
            object: {"order": [image numbers], "reason": "one sentence"}.""";

    /** What a group checks and how it is rated. */
    enum Kind {
        /** The stages of one setting, ordered or assigned by the rater. */
        AXIS,
        /** The standard cameras as a whole. */
        STANDARD,
        /** Pairs of stages, locked or candidates for a lock. */
        CONFLICT
    }

    /**
     * A stage under test.
     *
     * @param key      the key, used in the report
     * @param label    the name in the report
     * @param settings the camera settings, built into the prompt by the real code
     * @param second   a stage locked by the settings, joined to the camera line without the lock; null if none
     * @param subject  the subject, null for the one of the group
     * @param expected per question of the questionnaire the answers that show the effect, alternatives split by "|"
     * @param rank     the place in the ordering of the group, -1 if not ordered
     * @param neutral  whether the stage need not change anything visibly
     */
    record Stage(String key, String label, CameraSettings settings, CameraSettings second, String subject,
            Map<String, String> expected, int rank, boolean neutral) {
    }

    /**
     * A group of stages, rated together.
     *
     * @param id       the id, used in file names
     * @param title    the name in the report
     * @param kind     how the group is rated
     * @param subject  the subject
     * @param contexts the contexts
     * @param stages   the stages
     * @param order    the ordering question (property, first, last), null if not ordered
     * @param reps     the images per stage of a model without seed
     */
    record Group(String id, String title, Kind kind, String subject, List<String> contexts, List<Stage> stages,
            List<String> order, int reps) {
    }

    /**
     * One image to generate.
     *
     * @param group     the group
     * @param stage     the stage
     * @param context   the context
     * @param generator the image model
     * @param seed      the seed, null for a model without seed
     * @param rep       the repetition, 1 to the number of images per stage
     * @param prompt    the prompt
     * @param file      the file name without extension, a hash of model, seed or repetition and prompt
     */
    record Job(Group group, Stage stage, String context, Generator generator, Long seed, int rep, String prompt,
            String file) {

        /** The subject the rater is told. */
        String subject() {
            return stage.subject() != null ? stage.subject() : group.subject();
        }
    }

    /**
     * The verdict on a stage in one context with one model.
     *
     * @param code   "greift", "schwach", "gleich wie Nachbar", "Fehler", "neutral" or the like of the group kind
     * @param detail the evidence
     */
    record Verdict(String code, String detail) {
    }

    private final Path dir;
    private final double cap;
    private final PromptComposer composer = new PromptComposer(Integer.MAX_VALUE);
    private final MoodService moods;
    private final PromptBlockService blocks;
    private final Map<String, JSONObject> ratings = new LinkedHashMap<>();
    private final Map<String, JSONObject> groupRatings = new LinkedHashMap<>();
    private double spent;

    private CameraCheck(Path dir, double cap) throws Exception {
        this.dir = dir;
        this.cap = cap;
        Files.createDirectories(dir.resolve("images"));
        Files.createDirectories(dir.resolve("ratings"));
        Files.createDirectories(dir.resolve("sheets"));
        blocks = new PromptBlockService();
        moods = new MoodService(blocks, new ColorStyleService());
        try (Stream<Path> files = Stream.concat(Files.list(dir.resolve("images")),
                Files.list(dir.resolve("ratings")))) {
            for (Path file : files.filter((Path path) -> path.toString().endsWith(".json")).toList()) {
                JSONObject json = new JSONObject(Files.readString(file));
                spent += json.optDouble("cost", 0) + json.optDouble("ratingCost", 0);
                if (file.getParent().endsWith("images") && json.has("rating"))
                    ratings.put(json.getString("file"), json.getJSONObject("rating"));
                else if (file.getParent().endsWith("ratings"))
                    groupRatings.put(json.getString("key"), json);
            }
        }
    }

    /**
     * Runs the check.
     *
     * @param args the directory, the cost cap in USD, "true" for a dry run only, and the ids of the groups to
     *             generate and rate (comma-separated, empty for all)
     * @throws Exception if the check cannot run at all
     */
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]).toAbsolutePath();
        Set<String> only = args.length > 3 && !args[3].isBlank() ? Set.of(args[3].split(",")) : Set.of();
        DatabaseConfig.initialize(Files.createTempDirectory("camera-check").resolve("check.db").toString());
        CameraCheck check = new CameraCheck(dir, Double.parseDouble(args[1]));
        List<Group> groups = check.groups();
        List<Job> jobs = check.jobs(groups);
        List<Job> chosen = jobs.stream().filter((Job job) -> only.isEmpty() || only.contains(job.group().id())
                || only.contains(job.group().id().replaceAll("-.*", ""))).toList();
        check.dryRun(chosen);
        if (!Boolean.parseBoolean(args[2])) {
            check.generate(chosen);
            check.rate(chosen);
        }
        new CameraCheckReport(dir, check.spent).write(groups, jobs, check::verdict, check.ratings, check.groupRatings);
        log("Bericht: " + dir.resolve("report.html"));
        System.exit(0);
    }

    /*  The groups of the check, in the order of the report; the axis groups first, as they have priority  */
    private List<Group> groups() {
        List<Group> groups = new ArrayList<>();
        groups.add(axis("type", "Typ", PORTRAIT, List.of(ALONE, CANDLE), null, Body.values()));
        groups.add(axis("lens", "Brennweite", PARK, List.of(ALONE, MORNING), BY_FIELD, Lens.MM_14, Lens.MM_24,
                Lens.MM_35, Lens.MM_50, Lens.MM_85, Lens.MM_135, Lens.MM_400));
        groups.add(axis("special", "Sonderobjektive", PARK, List.of(ALONE, MORNING), null, Lens.FISHEYE,
                Lens.TILT_SHIFT, Lens.SHIFT, Lens.ANAMORPHIC));
        groups.add(axis("macro", "Makro und Detail", RING, List.of(ALONE, CANDLE), BY_MAGNIFICATION, Shot.DETAIL,
                Lens.MACRO));
        groups.add(axis("aperture", "Blende", PARK, List.of(ALONE, MORNING), BY_DEPTH, Aperture.values()));
        groups.add(axis("shutter", "Verschluss", STREET, List.of(ALONE, MORNING), BY_MOTION, Shutter.FROZEN,
                Shutter.MOTION_BLUR, Shutter.LONG_EXPOSURE));
        groups.add(axis("iso", "ISO", INTERIOR, List.of(ALONE, CANDLE), BY_GRAIN, Iso.values()));
        groups.add(axis("perspective", "Perspektive", FACADE, List.of(ALONE, MORNING, PASTEL), BY_HEIGHT,
                Perspective.LOW_ANGLE, Perspective.EYE_LEVEL, Perspective.HIGH_ANGLE, Perspective.TOP_DOWN,
                Perspective.DUTCH_ANGLE));
        groups.add(axis("shot", "Bildausschnitt", FACADE, List.of(ALONE, CANDLE, PASTEL), BY_FRAMING, Shot.values()));

        /*  The standard cameras as a whole, each on its typical subject  */
        Map<String, String> typical = Map.ofEntries(
                Map.entry("Street 35mm", "A man in a coat walking past a cafe on a city street, people and shop "
                        + "windows around him."),
                Map.entry("Classic Portrait", "Portrait of a woman in her forties with curly hair in a city park, "
                        + "trees behind her."),
                Map.entry("Epic Wide Low Angle", "A man standing on a town square in front of a tall old church."),
                Map.entry("Telephoto Compression", "A cyclist on a long straight street lined with houses, hills in "
                        + "the distance."),
                Map.entry("Macro Detail", "A ladybug on a green leaf with dew drops."),
                Map.entry("Drone Overview", "An old town with red roofs, a river and a stone bridge."),
                Map.entry("Cinematic Anamorphic", "A woman waiting at a bus stop on a rainy evening, street lights "
                        + "and cars behind her."),
                Map.entry("Product Tabletop", "A ceramic coffee cup and a small potted plant on a wooden table."),
                Map.entry("Product Bokeh", "A glass perfume bottle on a cafe table, the room behind it."),
                Map.entry("Long Exposure Landscape", "A rocky coast with breaking waves and a lighthouse."),
                Map.entry("Phone Snapshot", "Two friends laughing at a table in a small restaurant."));
        List<Stage> standards = new ArrayList<>();
        for (PromptBlock camera : blocks.getDefaults(PromptBlock.Type.CAMERA)) {
            CameraSettings settings = CameraSettings.fromJson(camera.getParams());
            standards.add(new Stage(PromptBlockService.slug(camera.getName()), camera.getName(), settings, null,
                    typical.get(camera.getName()), expected(settings), -1, false));
        }
        groups.add(new Group("standard", "Standard-Kameras", Kind.STANDARD, PORTRAIT, List.of(ALONE), standards,
                null, 2));

        /*  The locks: one pair per entry of the table, then the candidates rejected for a lock  */
        groups.add(new Group("conflict", "Sperren", Kind.CONFLICT, PARK, List.of(ALONE), List.of(
                pair(Body.DRONE, Perspective.LOW_ANGLE, FACADE, true),
                pair(Body.SMARTPHONE, Aperture.F_1_4, PARK, true),
                pair(Body.SMARTPHONE, Lens.MM_400, PARK, true),
                pair(Body.INSTANT, Lens.MM_400, PARK, true),
                pair(Body.ACTION_CAM, Lens.MM_135, PARK, true),
                pair(Body.ACTION_CAM, Shot.DETAIL, PARK, true),
                pair(Lens.MACRO, Shot.WIDE, RING, true),
                pair(Lens.MACRO, Shot.CLOSE_UP, RING, true),
                pair(Lens.TILT_SHIFT, Aperture.F_11, STREET, true),
                pair(Lens.SHIFT, Perspective.LOW_ANGLE, FACADE, true),
                pair(Body.COMPACT_35MM, Iso.ISO_100, INTERIOR, false),
                pair(Lens.MACRO, Aperture.F_11, RING, false),
                pair(Perspective.TOP_DOWN, Shot.EXTREME_WIDE, FACADE, false),
                pair(Lens.FISHEYE, Perspective.DUTCH_ANGLE, FACADE, false)), null, 2));
        return groups;
    }

    private static Group axis(String id, String title, String subject, List<String> contexts, List<String> order,
            Enum<?>... values) {
        List<Stage> stages = new ArrayList<>();
        int rank = 0;
        for (Enum<?> value : values)
            stages.add(stage(value, order == null || value == Perspective.DUTCH_ANGLE ? -1 : rank++));
        return new Group(id, title, Kind.AXIS, subject, contexts, stages, order, 3);
    }

    private static Stage stage(Enum<?> value, int rank) {
        return new Stage(value.name(), label(value), only(value), null, null, expected(value, true), rank,
                value == Perspective.EYE_LEVEL || value == Shot.MEDIUM || value == Body.FULL_FRAME);
    }

    /*  A pair of stages; a locked one is joined to the camera line past the lock, a candidate goes through as is  */
    private static Stage pair(Enum<?> first, Enum<?> second, String subject, boolean locked) {
        CameraSettings both = merge(only(first), only(second));
        Map<String, String> expected = new LinkedHashMap<>(expected(first, false));
        expected(second, false).forEach((String key, String value) -> expected.merge(key, value,
                (String a, String b) -> a + "&" + b));
        return new Stage(first.name() + "+" + second.name(), label(first) + " + " + label(second) + (locked ? ""
                : " (ohne Sperre)"), locked ? only(first) : both, locked ? only(second) : null, subject, expected, -1,
                false);
    }

    private static CameraSettings only(Enum<?> value) {
        return new CameraSettings(value instanceof Body body ? body : null, value instanceof Lens lens ? lens : null,
                value instanceof Aperture aperture ? aperture : null,
                value instanceof Shutter shutter ? shutter : null, value instanceof Iso iso ? iso : null,
                value instanceof Perspective perspective ? perspective : null,
                value instanceof Shot shot ? shot : null);
    }

    private static CameraSettings merge(CameraSettings a, CameraSettings b) {
        return new CameraSettings(a.body() != null ? a.body() : b.body(), a.lens() != null ? a.lens() : b.lens(),
                a.aperture() != null ? a.aperture() : b.aperture(),
                a.shutter() != null ? a.shutter() : b.shutter(), a.iso() != null ? a.iso() : b.iso(),
                a.perspective() != null ? a.perspective() : b.perspective(), a.shot() != null ? a.shot() : b.shot());
    }

    /*  The answers of the questionnaire that show the effect of a stage. A type shows as the camera the rater
        guesses ("look"), unless its text promises a visible effect (drone, action cam, the wide view of a
        smartphone); without "look", for the
        standard cameras and locks, only such a visible effect counts, as the guess mostly says "full frame"  */
    private static Map<String, String> expected(Enum<?> value, boolean look) {
        return switch (value) {
            case Body body when body == Body.DRONE -> Map.of("camera_height", "high_angle|top_down");
            case Body body when body == Body.ACTION_CAM -> Map.of("field_of_view", "ultra_wide");
            case Body body when body == Body.SMARTPHONE && look -> Map.of("field_of_view", "wide");
            case Body body when !look -> body == Body.SMARTPHONE ? Map.of("depth_of_field", "deep") : Map.of();
            case Body body -> Map.of("camera_look", switch (body) {
                case SMARTPHONE -> "smartphone";
                case COMPACT_35MM -> "film_compact";
                case FULL_FRAME -> "full_frame_digital";
                case MEDIUM_FORMAT -> "medium_format";
                default -> "instant_film";
            });
            case Lens lens -> lens.isSpecial() ? Map.of("special_look", lens.name().toLowerCase())
                    : Map.of("field_of_view", switch (lens) {
                        case MM_14 -> "ultra_wide";
                        case MM_24 -> "wide|ultra_wide";
                        case MM_35 -> "wide|normal";
                        case MM_50 -> "normal";
                        case MM_85 -> "normal|telephoto";
                        case MM_135 -> "telephoto";
                        default -> "super_telephoto|telephoto";
                    });
            case Aperture aperture -> Map.of("depth_of_field", switch (aperture) {
                case F_1_4 -> "shallow";
                case F_4 -> "moderate";
                case F_11 -> "deep";
            });
            case Shutter shutter -> shutter == Shutter.NORMAL ? Map.of() : Map.of("motion", switch (shutter) {
                case FROZEN -> "frozen";
                case MOTION_BLUR -> "motion_blur";
                default -> "long_exposure";
            });
            case Iso iso -> Map.of("grain", iso == Iso.ISO_100 ? "clean" : "heavy");
            case Perspective perspective -> perspective == Perspective.DUTCH_ANGLE ? Map.of("horizon_tilted", "true")
                    : Map.of("camera_height", perspective.name().toLowerCase());
            case Shot shot -> Map.of("shot_size", shot.name().toLowerCase());
            default -> Map.of();
        };
    }

    private static Map<String, String> expected(CameraSettings settings) {
        Map<String, String> expected = new LinkedHashMap<>();
        for (Enum<?> value : new Enum<?>[] { settings.body(), settings.lens(), settings.aperture(), settings.shutter(),
                settings.iso(), settings.perspective(), settings.shot() })
            if (value != null)
                expected.putAll(expected(value, false));
        return expected;
    }

    static String label(Enum<?> value) {
        return switch (value) {
            case Body body -> switch (body) {
                case SMARTPHONE -> "Smartphone";
                case COMPACT_35MM -> "35mm-Film";
                case FULL_FRAME -> "Vollformat";
                case MEDIUM_FORMAT -> "Mittelformat";
                case INSTANT -> "Sofortbild";
                case DRONE -> "Drohne";
                case ACTION_CAM -> "Action-Cam";
            };
            case Lens lens -> lens.isSpecial() ? switch (lens) {
                case MACRO -> "Makro";
                case FISHEYE -> "Fischauge";
                case TILT_SHIFT -> "Tilt-Shift";
                case SHIFT -> "Shift";
                default -> "Anamorphot";
            } : lens.millimeters() + " mm";
            case Aperture aperture -> aperture.label();
            case Shutter shutter -> switch (shutter) {
                case FROZEN -> "Eingefroren";
                case NORMAL -> "Normal";
                case MOTION_BLUR -> "Bewegungsunschärfe";
                case LONG_EXPOSURE -> "Langzeitbelichtung";
            };
            case Iso iso -> iso.label();
            case Perspective perspective -> switch (perspective) {
                case EYE_LEVEL -> "Augenhöhe";
                case LOW_ANGLE -> "Froschperspektive";
                case HIGH_ANGLE -> "Aufsicht";
                case TOP_DOWN -> "Draufsicht";
                case DUTCH_ANGLE -> "Gekippt";
            };
            case Shot shot -> switch (shot) {
                case EXTREME_WIDE -> "Supertotale";
                case WIDE -> "Totale";
                case MEDIUM -> "Halbnah";
                case CLOSE_UP -> "Groß";
                case DETAIL -> "Detail";
            };
            default -> value.name();
        };
    }

    /*  All images, the model with the highest priority first; a model with seed gets two seeds per group, shared by
        all stages of the group  */
    private List<Job> jobs(List<Group> groups) throws Exception {
        List<Job> jobs = new ArrayList<>();
        for (Generator generator : CameraCheckApi.GENERATORS)
            for (Group group : groups)
                for (String context : group.contexts())
                    for (Stage stage : group.stages()) {
                        String prompt = prompt(group, stage, context);
                        for (int rep = 1; rep <= (generator.seeded() ? 2 : group.reps()); rep++) {
                            Long seed = generator.seeded() ? Math.floorMod(group.id().hashCode(), 100000) * 10L + rep
                                    : null;
                            String file = sha1(generator.id() + "\n" + (seed != null ? "seed " + seed : "rep " + rep)
                                    + "\n" + prompt).substring(0, 16);
                            jobs.add(new Job(group, stage, context, generator, seed, rep, prompt, file));
                        }
                    }
        return jobs;
    }

    /*  The prompt as the app composes it, with the camera block of the stage instead of the camera of the mood  */
    private String prompt(Group group, Stage stage, String context) {
        PromptComposer.Selection mood = context.equals(ALONE) ? new PromptComposer.Selection(null, null, null, null,
                null) : moods.selection(moods.getDefaults().stream().filter((Mood candidate) -> candidate.getName()
                .equals(context)).findFirst().orElseThrow());
        PromptBlock camera = PromptBlockService.camera(null, stage.label(), stage.settings());
        String prompt = composer.compose(stage.subject() != null ? stage.subject() : group.subject(),
                new PromptComposer.Selection(mood.style(), mood.atmosphere(), mood.light(), camera, mood.palette()));
        if (stage.second() == null)
            return prompt;
        String first = CameraPromptBuilder.build(stage.settings(), true);
        return prompt.replace(first, first + ", " + CameraPromptBuilder.build(stage.second(), true));
    }

    /*  The missing images and their cost; the prompts go to "prompts.txt" for a look before any money is spent  */
    private void dryRun(List<Job> jobs) throws Exception {
        Map<Generator, Integer> missing = new LinkedHashMap<>();
        Set<String> files = new HashSet<>();
        Map<String, String> prompts = new LinkedHashMap<>();
        int present = 0;
        for (Job job : jobs) {
            prompts.putIfAbsent(job.group().id() + "/" + job.stage().key() + "/" + job.context(), job.prompt());
            if (Files.exists(png(job)))
                present += files.add(job.file()) ? 1 : 0;
            else if (files.add(job.file()))
                missing.merge(job.generator(), 1, Integer::sum);
        }
        StringBuilder text = new StringBuilder();
        prompts.forEach((String key, String prompt) -> text.append("== ").append(key).append("\n").append(prompt)
                .append("\n\n"));
        Files.writeString(dir.resolve("prompts.txt"), text);
        double total = 0;
        log("Trockenlauf: " + present + " Bilder vorhanden, Prompts in " + dir.resolve("prompts.txt"));
        for (Generator generator : CameraCheckApi.GENERATORS) {
            int count = missing.getOrDefault(generator, 0);
            if (generator.key() == null) {
                log(String.format("  %-18s nicht geprüft (%s fehlt)", generator.label(), generator.envKey()));
                continue;
            }
            total += count * generator.cost();
            log(String.format(Locale.ROOT, "  %-18s %4d Bilder x $%.3f = $%.2f", generator.label(), count,
                    generator.cost(), count * generator.cost()));
        }
        long unrated = jobs.stream().filter((Job job) -> job.generator().key() != null).map(Job::file).distinct()
                .filter((String file) -> !ratings.containsKey(file)).count();
        double rating = unrated * 0.004 + 0.3;
        log(String.format(Locale.ROOT, "  Bewertung (%s)  ~$%.2f (Schätzung)", CameraCheckApi.VISION_MODEL, rating));
        log(String.format(Locale.ROOT, "  Bisher ausgegeben $%.2f, Deckel $%.2f, nach dem Lauf ~$%.2f%s", spent, cap,
                spent + total + rating, spent + total + rating > cap ? " -- der Deckel endet den Lauf vorher" : ""));
    }

    private void generate(List<Job> jobs) throws InterruptedException {
        Map<String, Job> missing = new LinkedHashMap<>();
        for (Job job : jobs)
            if (job.generator().key() != null && !Files.exists(png(job)))
                missing.putIfAbsent(job.file(), job);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        AtomicInteger done = new AtomicInteger();
        for (Job job : missing.values())
            pool.submit(() -> {
                if (!reserve(job.generator().cost())) {
                    log("Deckel erreicht, nicht erzeugt: " + name(job));
                    return;
                }
                try {
                    byte[] image = job.generator().generate(job.prompt(), job.seed());
                    Files.write(png(job), image);
                    JSONObject manifest = new JSONObject().put("file", job.file()).put("group", job.group().id())
                            .put("stage", job.stage().key()).put("label", job.stage().label())
                            .put("context", job.context()).put("model", job.generator().label())
                            .put("seed", job.seed() != null ? job.seed() : JSONObject.NULL).put("rep", job.rep())
                            .put("subject", job.subject()).put("prompt", job.prompt())
                            .put("cost", job.generator().cost()).put("created", LocalDateTime.now().toString());
                    Files.writeString(dir.resolve("images").resolve(job.file() + ".json"), manifest.toString(2));
                    log(done.incrementAndGet() + "/" + missing.size() + " " + name(job));
                }
                catch (Exception e) {
                    refund(job.generator().cost());
                    log("FEHLER " + name(job) + ": " + e.getMessage());
                }
            });
        pool.shutdown();
        pool.awaitTermination(12, TimeUnit.HOURS);
    }

    /*  The blind rating: the questionnaire per image, then the ordering or comparison per group, context and model  */
    private void rate(List<Job> jobs) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        Map<String, Job> unrated = new LinkedHashMap<>();
        for (Job job : jobs)
            if (Files.exists(png(job)) && !ratings.containsKey(job.file()))
                unrated.putIfAbsent(job.file(), job);
        for (Job job : unrated.values())
            pool.submit(() -> {
                try {
                    Rating rating = CameraCheckApi.ask(String.format(QUESTIONNAIRE, job.subject()),
                            List.of(Files.readAllBytes(png(job))), null);
                    Path file = dir.resolve("images").resolve(job.file() + ".json");
                    synchronized (this) {
                        JSONObject manifest = new JSONObject(Files.readString(file));
                        Files.writeString(file, manifest.put("rating", rating.json()).put("ratingCost", rating.cost())
                                .toString(2));
                        ratings.put(job.file(), rating.json());
                        spent += rating.cost();
                    }
                }
                catch (Exception e) {
                    log("FEHLER Bewertung " + name(job) + ": " + e.getMessage());
                }
            });
        for (List<Job> cell : cells(jobs).values()) {
            Job first = cell.get(0);
            if (first.group().order() != null) {
                List<Job> ordered = cell.stream().filter((Job job) -> job.stage().rank() >= 0).toList();
                List<String> order = first.group().order();
                for (int attempt = 1; attempt <= 3; attempt++) {
                    int number = attempt;
                    pool.submit(() -> askGroup("order", ordered, String.format(ORDERING, ordered.size(),
                            order.get(0), order.get(1), order.get(2)), null, number));
                }
            }
        }
        pool.shutdown();
        pool.awaitTermination(6, TimeUnit.HOURS);
    }

    /*  One question about several images, in an order shuffled by their names and the attempt (an ordering is asked
        three times, as one answer varies); a model comparison names its sets "A" and "B", the model A or B by
        chance  */
    private void askGroup(String kind, List<Job> jobs, String question, Stage model, int attempt) {
        List<Job> present = new ArrayList<>(jobs.stream().filter((Job job) -> Files.exists(png(job))).toList());
        if (present.size() < 2)
            return;
        String key = sha1(kind + (attempt > 1 ? " " + attempt : "") + "\n" + question + "\n" + String.join(",",
                present.stream().map(Job::file).sorted().toList())).substring(0, 16);
        if (groupRatings.containsKey(key))
            return;
        Random random = new Random(key.hashCode());
        Collections.shuffle(present, random);
        boolean modelIsA = random.nextBoolean();
        List<String> labels = null;
        if (model != null) {
            present.sort((Job a, Job b) -> Boolean.compare((a.stage() == model) != modelIsA,
                    (b.stage() == model) != modelIsA));
            labels = present.stream().map((Job job) -> "Set " + ((job.stage() == model) == modelIsA ? "A" : "B")
                    + ":").toList();
        }
        try {
            List<byte[]> images = new ArrayList<>();
            for (Job job : present)
                images.add(Files.readAllBytes(png(job)));
            Rating rating = CameraCheckApi.ask(question, images, labels);
            JSONObject json = new JSONObject().put("key", key).put("kind", kind).put("group", jobs.get(0).group().id())
                    .put("context", jobs.get(0).context()).put("model", jobs.get(0).generator().label())
                    .put("files", new JSONArray(present.stream().map(Job::file).toList())).put("modelIsA", modelIsA)
                    .put("question", question).put("answer", rating.json()).put("ratingCost", rating.cost());
            synchronized (this) {
                Files.writeString(dir.resolve("ratings").resolve(key + ".json"), json.toString(2));
                groupRatings.put(key, json);
                spent += rating.cost();
            }
        }
        catch (Exception e) {
            log("FEHLER Bewertung " + kind + " " + jobs.get(0).group().id() + ": " + e.getMessage());
        }
    }

    /*  The jobs per group, context and model  */
    static Map<String, List<Job>> cells(List<Job> jobs) {
        Map<String, List<Job>> cells = new LinkedHashMap<>();
        for (Job job : jobs)
            cells.computeIfAbsent(job.group().id() + "|" + job.context() + "|" + job.generator().id(),
                    (String key) -> new ArrayList<>()).add(job);
        return cells;
    }

    /**
     * The verdict on a stage in one context with one model, from the ratings of its images.
     *
     * @param stage the stage
     * @param cell  all jobs of the group, context and model
     * @return the verdict, null if no image of the stage exists
     */
    Verdict verdict(Stage stage, List<Job> cell) {
        List<Job> own = cell.stream().filter((Job job) -> job.stage() == stage && Files.exists(png(job))
                && ratings.containsKey(job.file())).toList();
        if (own.isEmpty())
            return null;
        Group group = cell.get(0).group();
        String effects = sideEffects(stage, own);
        String defect = defect(stage, own);
        String fault = defect != null ? "Fehler" : null;
        switch (group.kind()) {
            case STANDARD -> {
                double sum = 0;
                List<String> misses = new ArrayList<>();
                for (Job job : own) {
                    int hits = 0;
                    for (Map.Entry<String, String> entry : stage.expected().entrySet())
                        if (matches(ratings.get(job.file()), entry.getKey(), entry.getValue()))
                            hits++;
                        else
                            misses.add(entry.getKey() + "=" + ratings.get(job.file()).opt(entry.getKey()));
                    sum += stage.expected().isEmpty() ? 1 : (double) hits / stage.expected().size();
                }
                double ratio = sum / own.size();
                String code = ratio >= 0.67 ? "hält" : ratio >= 0.34 ? "teilweise" : "verfehlt";
                return new Verdict(fault != null ? fault : code, String.format("%.0f%% der Merkmale", ratio * 100)
                        + (misses.isEmpty() ? "" : "; abweichend: " + String.join(", ", misses)) + effects);
            }
            case CONFLICT -> {
                int broken = 0;
                int lost = 0;
                List<String> reasons = new ArrayList<>();
                for (Job job : own) {
                    JSONObject rating = ratings.get(job.file());
                    List<String> missing = stage.expected().entrySet().stream().filter((Map.Entry<String, String>
                            entry) -> !matches(rating, entry.getKey(), entry.getValue())).map((Map.Entry<String,
                            String> entry) -> entry.getKey() + "=" + rating.opt(entry.getKey())).toList();
                    if (!"no".equals(rating.optString("broken", "no"))) {
                        broken++;
                        reasons.add("kaputt: " + rating.optString("broken_reason"));
                    }
                    if (!missing.isEmpty()) {
                        lost++;
                        reasons.add("fehlt: " + String.join(", ", missing));
                    }
                }
                String code = broken * 2 > own.size() ? "kaputt" : lost * 2 > own.size() ? "eine Wirkung fehlt"
                        : "verträglich";
                return new Verdict(code, broken + "/" + own.size() + " kaputt, " + lost + "/" + own.size()
                        + " mit fehlender Wirkung" + (reasons.isEmpty() ? "" : "; " + String.join("; ", reasons))
                        + effects);
            }
            default -> {
                return axisVerdict(stage, cell, own, group, fault, effects);
            }
        }
    }

    /*  An axis stage shows its effect in an image if the rater orders the image right against the neighbor stages,
        or assigns it the expected answer where the answers of the stages of the group differ  */
    private Verdict axisVerdict(Stage stage, List<Job> cell, List<Job> own, Group group, String fault,
            String effects) {
        Map<String, Double> ranks = new LinkedHashMap<>();
        List<JSONObject> orderings = groupRatings("order", cell, own.get(0));
        for (JSONObject rating : orderings) {
            JSONArray files = rating.getJSONArray("files");
            JSONArray order = rating.getJSONObject("answer").optJSONArray("order");
            for (int i = 0; order != null && i < order.length(); i++) {
                int index = order.optInt(i) - 1;
                if (index >= 0 && index < files.length())
                    ranks.merge(files.getString(index), (double) i / orderings.size(), Double::sum);
            }
        }
        boolean assignable = distinct(group);
        int ordered = 0;
        int assigned = 0;
        int visible = 0;
        List<String> confused = new ArrayList<>();
        for (Job job : own) {
            boolean byOrder = stage.rank() >= 0 && !ranks.isEmpty() && orderedRight(job, cell, ranks, confused);
            boolean byAssignment = assignable && stage.expected().entrySet().stream().allMatch((Map.Entry<String,
                    String> entry) -> matches(ratings.get(job.file()), entry.getKey(), entry.getValue()));
            ordered += byOrder ? 1 : 0;
            assigned += byAssignment ? 1 : 0;
            visible += byOrder || byAssignment ? 1 : 0;
        }
        List<String> answers = own.stream().map((Job job) -> String.join("/", stage.expected().keySet().stream()
                .map((String key) -> String.valueOf(ratings.get(job.file()).opt(key))).toList())).toList();
        String detail = (stage.rank() >= 0 ? ordered + "/" + own.size() + " richtig geordnet, " : "")
                + (assignable ? assigned + "/" + own.size() + " richtig zugeordnet, " : "")
                + "Antworten " + answers + (confused.isEmpty() ? "" : "; vertauscht mit " + String.join(", ",
                confused.stream().distinct().toList())) + effects;
        String code = fault != null ? fault : stage.neutral() ? "neutral" : visible * 3 >= 2 * own.size() ? "greift"
                : visible > 0 ? "schwach" : "gleich wie Nachbar";
        return new Verdict(code, detail);
    }

    /*  Right against every neighbor stage: on the right side of at least half of the neighbor's images  */
    private static boolean orderedRight(Job job, List<Job> cell, Map<String, Double> ranks, List<String> confused) {
        Double rank = ranks.get(job.file());
        if (rank == null)
            return false;
        boolean right = true;
        boolean any = false;
        for (Stage neighbor : job.group().stages()) {
            if (neighbor.rank() < 0 || Math.abs(neighbor.rank() - job.stage().rank()) != 1)
                continue;
            List<Double> others = cell.stream().filter((Job other) -> other.stage() == neighbor
                    && ranks.containsKey(other.file())).map((Job other) -> ranks.get(other.file())).toList();
            if (others.isEmpty())
                continue;
            boolean above = neighbor.rank() < job.stage().rank();
            long correct = others.stream().filter((Double other) -> above ? rank > other : rank < other).count();
            any = true;
            if (correct * 2 < others.size()) {
                right = false;
                confused.add(neighbor.label());
            }
        }
        return any && right;
    }

    /*  Whether no two stages of the group share an expected answer, so an answer assigns an image to one stage  */
    private static boolean distinct(Group group) {
        for (Stage a : group.stages())
            for (Stage b : group.stages())
                for (Map.Entry<String, String> entry : a.expected().entrySet())
                    if (a != b && b.expected().containsKey(entry.getKey()) && !Collections.disjoint(
                            List.of(entry.getValue().split("\\|")), List.of(b.expected().get(entry.getKey())
                            .split("\\|"))))
                        return false;
        return true;
    }

    private static boolean matches(JSONObject rating, String key, String expected) {
        return rating != null && List.of(expected.split("\\|")).contains(String.valueOf(rating.opt(key)));
    }

    /*  A side effect category that most images of the stage show. Not counted: what the stage brings itself (the
        frame of an instant photo or a fisheye, no place in a detail), the palette written into the image and the
        paper edge of a watercolor, both artifacts of the mood  */
    private String defect(Stage stage, List<Job> own) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        CameraSettings settings = stage.settings();
        for (Job job : own) {
            JSONObject rating = ratings.get(job.file());
            Set<String> categories = new HashSet<>();
            JSONArray effects = rating.optJSONArray("side_effects");
            for (int i = 0; effects != null && i < effects.length(); i++) {
                JSONObject effect = effects.optJSONObject(i);
                String category = effect != null ? effect.optString("category") : "other";
                String description = effect != null ? effect.optString("description") : "";
                boolean palette = description.matches("(?i).*(hex|palette|swatch|color code).*");
                boolean paper = job.context().equals(PASTEL) && category.equals("frame")
                        && description.matches("(?i).*(paper|watercolou?r|deckle|painted).*");
                if (!palette && !paper)
                    categories.add(category);
            }
            if ("yes".equals(rating.optString("broken")))
                categories.add("broken");
            if (settings.body() == Body.INSTANT || settings.lens() == Lens.FISHEYE)
                categories.remove("frame");
            if (settings.lens() == Lens.MACRO || settings.shot() == Shot.DETAIL || settings.shot() == Shot.CLOSE_UP)
                categories.remove("location");
            categories.retainAll(DEFECTS);
            for (String category : categories)
                counts.merge(category, 1, Integer::sum);
        }
        return counts.entrySet().stream().filter((Map.Entry<String, Integer> entry) -> entry.getValue() * 2
                > own.size()).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private String sideEffects(Stage stage, List<Job> own) {
        List<String> found = new ArrayList<>();
        for (Job job : own) {
            JSONArray effects = ratings.get(job.file()).optJSONArray("side_effects");
            for (int i = 0; effects != null && i < effects.length(); i++)
                if (effects.optJSONObject(i) != null)
                    found.add(effects.getJSONObject(i).optString("category") + ": "
                            + effects.getJSONObject(i).optString("description"));
            if (!"no".equals(ratings.get(job.file()).optString("broken", "no")))
                found.add("broken " + ratings.get(job.file()).optString("broken") + ": "
                        + ratings.get(job.file()).optString("broken_reason"));
        }
        return found.isEmpty() ? "" : "; Nebenwirkungen: " + String.join("; ", found);
    }

    /*  The ratings of a group question that cover the job and only images of the cell (not of an earlier text)  */
    private List<JSONObject> groupRatings(String kind, List<Job> cell, Job job) {
        return groupRatings.values().stream().filter((JSONObject rating) -> rating.getString("kind").equals(kind)
                && rating.getString("group").equals(job.group().id())
                && rating.getString("context").equals(job.context())
                && rating.getString("model").equals(job.generator().label())
                && rating.getJSONArray("files").toList().contains(job.file())
                && rating.getJSONArray("files").toList().stream().allMatch((Object file) -> cell.stream()
                .anyMatch((Job other) -> other.file().equals(file)))).toList();
    }

    private JSONObject groupAnswer(String kind, List<Job> cell, Job job) {
        List<JSONObject> ratings = groupRatings(kind, cell, job);
        return ratings.isEmpty() ? null : ratings.getLast().getJSONObject("answer");
    }

    private synchronized boolean reserve(double cost) {
        if (spent + cost > cap)
            return false;
        spent += cost;
        return true;
    }

    private synchronized void refund(double cost) {
        spent -= cost;
    }

    private Path png(Job job) {
        return dir.resolve("images").resolve(job.file() + ".png");
    }

    private static String name(Job job) {
        return job.group().id() + "/" + job.stage().key() + "/" + job.context() + "/" + job.generator().id() + "/"
                + (job.seed() != null ? "seed " + job.seed() : "rep " + job.rep());
    }

    static String sha1(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(text.getBytes(
                    StandardCharsets.UTF_8)));
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void log(String line) {
        System.out.println("[" + LocalDateTime.now().toLocalTime().withNano(0) + "] " + line);
    }
}
