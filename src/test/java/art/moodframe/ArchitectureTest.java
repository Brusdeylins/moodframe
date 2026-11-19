package art.moodframe;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Guards the layers of docs/ARCHITECTURE.md: reads the imports and fully qualified names of {@code src/main/java} and
 * checks which package uses which, that no packages depend on each other in a cycle, that model, util and
 * service.chat need no JavaFX, that no service looks up the services it uses in the locator, that in the user
 * interface only the coordinators do, the order of the parts of the user interface, which classes of mcp and net it
 * uses, that no class imports a package of the application with a wildcard and that the generation and the image
 * clients render no message themselves.
 */
class ArchitectureTest {

    private static final Path SOURCES = Path.of("src/main/java");

    private static final String ROOT = "art.moodframe";

    /*  A reference to a class of the application: lowercase package names, then the class name  */
    private static final Pattern REFERENCE = Pattern.compile("\\bart\\.moodframe((?:\\.[a-z][a-z0-9_]*)*)\\.[A-Z]");

    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    /*  The layers below service and the adapters, with the layers each may use besides itself  */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "model", Set.of(),
            "util", Set.of("config"),
            "config", Set.of("model", "util"),
            "database", Set.of("config", "model", "util"),
            "repository", Set.of("database", "config", "model", "util"),
            "net", Set.of("config", "util"),
            "service", Set.of("repository", "database", "net", "config", "model", "util"),
            "mcp", Set.of("service", "net", "config", "model", "util"),
            "ui", Set.of("service", "mcp", "net", "config", "model", "util"));

    /*  The subpackages of service, each using only those before it; service itself comes last  */
    private static final List<String> SERVICES = List.of("service.provider", "service.prompt", "service.trash",
            "service.library", "service.chat", "service.generation", "service.brainstorm", "service");

    /*  Known deviation: the chat entry and its images, whose few properties and the list of images the views observe  */
    private static final Set<String> FX_MODELS = Set.of("ChatEntry", "GeneratedImage");

    /** The packages below {@code art.moodframe} ("" for the root) with the packages their classes use. */
    private static final Map<String, Set<String>> uses = new TreeMap<>();

    /** The packages below {@code art.moodframe} with the classes that use JavaFX. */
    private static final Map<String, Set<String>> javaFx = new TreeMap<>();

    /** The packages below {@code art.moodframe} with the classes that look up services in the locator. */
    private static final Map<String, Set<String>> locator = new TreeMap<>();

    @BeforeAll
    static void readTheSources() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            files = walk.filter((Path file) -> file.toString().endsWith(".java"))
                    .filter((Path file) -> !file.getFileName().toString().equals("module-info.java")).toList();
        }
        for (Path file : files) {
            String pkg = packageOf(SOURCES.relativize(file.getParent()).toString().replace('/', '.'));
            String code = COMMENTS.matcher(Files.readString(file)).replaceAll("");
            Set<String> used = uses.computeIfAbsent(pkg, (String key) -> new TreeSet<>());
            Matcher reference = REFERENCE.matcher(code);
            while (reference.find()) {
                String target = reference.group(1).isEmpty() ? "" : reference.group(1).substring(1);
                if (!target.equals(pkg))
                    used.add(target);
            }
            if (code.contains("javafx."))
                javaFx.computeIfAbsent(pkg, (String key) -> new TreeSet<>())
                        .add(file.getFileName().toString().replace(".java", ""));
            if (code.contains("ServiceFactory"))
                locator.computeIfAbsent(pkg, (String key) -> new TreeSet<>())
                        .add(file.getFileName().toString().replace(".java", ""));
        }
    }

    /** The package below {@code art.moodframe}: "ui.dialog" of "art.moodframe.ui.dialog", "" of the root. */
    private static String packageOf(String name) {
        return name.equals(ROOT) ? "" : name.substring(ROOT.length() + 1);
    }

    /** The layer of a package: its first name, e.g. "ui" of "ui.dialog"; "" for the root. */
    private static String layerOf(String pkg) {
        int dot = pkg.indexOf('.');
        return dot < 0 ? pkg : pkg.substring(0, dot);
    }

    @Test
    void everyLayerUsesOnlyTheLayersBelowIt() {
        List<String> violations = new ArrayList<>();
        uses.forEach((String pkg, Set<String> targets) -> {
            String layer = layerOf(pkg);
            Set<String> allowed = ALLOWED.get(layer);
            if (allowed == null)
                return;  // the root is the composition root and may use everything
            for (String target : targets) {
                String targetLayer = layerOf(target);
                if (!targetLayer.equals(layer) && !allowed.contains(targetLayer))
                    violations.add(pkg + " -> " + target);
            }
        });
        assertEquals(List.of(), violations);
    }

    @Test
    void everyServiceUsesOnlyTheServicesBeforeIt() {
        List<String> violations = new ArrayList<>();
        for (String service : SERVICES) {
            for (String target : uses.getOrDefault(service, Set.of())) {
                if (SERVICES.contains(target) && SERVICES.indexOf(target) > SERVICES.indexOf(service))
                    violations.add(service + " -> " + target);
            }
        }
        assertEquals(List.of(), violations);
        assertEquals(new TreeSet<>(SERVICES), new TreeSet<>(uses.keySet().stream()
                .filter((String pkg) -> layerOf(pkg).equals("service")).toList()), "every service is ordered");
    }

    @Test
    void noPackagesDependOnEachOtherInACycle() {
        for (String start : uses.keySet()) {
            List<String> cycle = cycleFrom(start, new ArrayList<>(), new TreeSet<>());
            assertNull(cycle, () -> "cycle: " + String.join(" -> ", cycle));
        }
    }

    /** A path from the last package of the path back to the start, null if there is none. */
    private static List<String> cycleFrom(String pkg, List<String> path, Set<String> done) {
        if (!path.isEmpty() && pkg.equals(path.get(0))) {
            List<String> cycle = new ArrayList<>(path);
            cycle.add(pkg);
            return cycle;
        }
        if (!done.add(pkg))
            return null;
        path.add(pkg);
        for (String target : uses.getOrDefault(pkg, Set.of())) {
            List<String> cycle = cycleFrom(target, path, done);
            if (cycle != null)
                return cycle;
        }
        path.remove(path.size() - 1);
        return null;
    }

    @Test
    void modelAndUtilNeedNoJavaFx() {
        assertEquals(Set.of(), javaFx.getOrDefault("util", Set.of()), "util");
        assertEquals(FX_MODELS, javaFx.getOrDefault("model", Set.of()), "model, besides the known deviation");
    }

    @Test
    void theServicesAndTheMcpServerGetTheirDependenciesInjected() {
        List<String> lookups = new ArrayList<>();
        locator.forEach((String pkg, Set<String> classes) -> {
            if (layerOf(pkg).equals("service") || layerOf(pkg).equals("mcp"))
                classes.forEach((String name) -> lookups.add(pkg + "." + name));
        });
        assertEquals(List.of(), lookups, "no service looks up another in the ServiceFactory");
    }

    @Test
    void inTheUserInterfaceOnlyTheCoordinatorsUseTheLocator() {
        List<String> lookups = new ArrayList<>();
        locator.forEach((String pkg, Set<String> classes) -> {
            if (layerOf(pkg).equals("ui"))
                classes.forEach((String name) -> lookups.add(pkg + "." + name));
        });
        assertEquals(List.of("ui.controller.MainWindowController", "ui.controller.SettingsController"), lookups,
                "the coordinators look up the services once and hand them to the views");
    }

    /*  The subpackages of ui, each using only those before it: support at the bottom, the controllers on top, so no
        view uses a controller  */
    private static final List<String> UI = List.of("ui.support", "ui.dnd", "ui.component", "ui.cell", "ui.dialog",
            "ui.panel", "ui.brainstorm", "ui", "ui.controller.settings", "ui.controller");

    @Test
    void everyPartOfTheUserInterfaceUsesOnlyThePartsBelowIt() {
        List<String> violations = new ArrayList<>();
        for (String part : UI) {
            for (String target : uses.getOrDefault(part, Set.of())) {
                if (UI.contains(target) && UI.indexOf(target) > UI.indexOf(part))
                    violations.add(part + " -> " + target);
            }
        }
        assertEquals(List.of(), violations);
        assertEquals(new TreeSet<>(UI), new TreeSet<>(uses.keySet().stream()
                .filter((String pkg) -> layerOf(pkg).equals("ui")).toList()), "every part of ui is ordered");
    }

    /*  The classes of mcp and net the user interface may use: the lifecycle of the MCP server, and the HTTP clients
        with their proxy and the folder of the certificates for the settings  */
    private static final Set<String> UI_USES_OF_MCP_AND_NET = Set.of("mcp.McpService", "net.HttpClients",
            "net.CertificateStore");

    @Test
    void theUserInterfaceUsesOnlyTheLifecycleOfTheMcpServerAndTheSetupOfTheNetwork() throws IOException {
        Pattern reference = Pattern.compile("\\bart\\.moodframe\\.((?:mcp|net)\\.[A-Z]\\w*)");
        Set<String> used = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(SOURCES.resolve("art/moodframe/ui"))) {
            for (Path file : walk.filter((Path path) -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = reference.matcher(COMMENTS.matcher(Files.readString(file)).replaceAll(""));
                while (matcher.find())
                    used.add(matcher.group(1));
            }
        }
        used.removeAll(UI_USES_OF_MCP_AND_NET);
        assertEquals(Set.of(), used, "no view uses the tools of the MCP server or the network pool");
    }

    @Test
    void noClassImportsAPackageOfTheApplicationWithAWildcard() throws IOException {
        Pattern wildcard = Pattern.compile("^import\\s+(static\\s+)?art\\.moodframe\\.[\\w.]*\\*\\s*;",
                Pattern.MULTILINE);
        List<String> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            for (Path file : walk.filter((Path path) -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = wildcard.matcher(Files.readString(file));
                while (matcher.find())
                    found.add(SOURCES.relativize(file) + ": " + matcher.group());
            }
        }
        assertEquals(List.of(), found, "a wildcard hides which classes, and so which layers, a class uses");
    }

    @Test
    void theChatsNeedNoJavaFx() {
        assertEquals(Set.of(), javaFx.getOrDefault("service.chat", Set.of()), "service.chat");
    }

    /*  The image and video clients, whose failures reach the entries of the window and of the MCP server  */
    private static final List<String> IMAGE_CLIENTS = List.of("BflApiServiceImpl", "GeminiImageClient",
            "GeminiOmniClient", "OpenAiImageClient");

    @Test
    void theGenerationAndTheImageClientsRenderNoMessageThemselves() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.list(SOURCES.resolve("art/moodframe/service/generation"))) {
            walk.filter((Path file) -> !file.getFileName().toString().equals("ModelLabels.java")).forEach(files::add);
        }
        for (String client : IMAGE_CLIENTS)
            files.add(SOURCES.resolve("art/moodframe/service/provider/" + client + ".java"));
        List<String> rendering = new ArrayList<>();
        for (Path file : files) {
            if (COMMENTS.matcher(Files.readString(file)).replaceAll("").contains("I18nManager.getText"))
                rendering.add(file.getFileName().toString());
        }
        assertEquals(List.of(), rendering, "a failure is a ProviderException, rendered in the language of its entry");
    }

    @Test
    void noServiceButTheOpenAiClientNamesThePlaceholderOfTheGatewayModels() throws IOException {
        List<String> naming = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(SOURCES.resolve("art/moodframe/service"))) {
            for (Path file : walk.filter((Path path) -> path.toString().endsWith(".java")).toList()) {
                if (COMMENTS.matcher(Files.readString(file)).replaceAll("").contains("OPENAI_IMAGE"))
                    naming.add(file.getFileName().toString());
            }
        }
        assertEquals(List.of("OpenAiApiService.java"), naming, "a provider decides by servesModel and listedModels");
    }

    /*  The only class of the services that uses JavaFX: it changes the models a generation shares with the user
        interface on the JavaFX thread and hands the notifications of a generation there  */
    private static final Set<String> FX_SERVICES = Set.of("service.generation.FxModel");

    @Test
    void onlyFxModelOfTheServicesUsesJavaFx() {
        Set<String> classes = new TreeSet<>();
        javaFx.forEach((String pkg, Set<String> names) -> {
            if (layerOf(pkg).equals("service"))
                names.forEach((String name) -> classes.add(pkg + "." + name));
        });
        assertEquals(new TreeSet<>(FX_SERVICES), classes, "the classes of service that use JavaFX");
    }
}
