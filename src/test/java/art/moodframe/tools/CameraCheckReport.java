package art.moodframe.tools;

import art.moodframe.tools.CameraCheck.Group;
import art.moodframe.tools.CameraCheck.Job;
import art.moodframe.tools.CameraCheck.Stage;
import art.moodframe.tools.CameraCheck.Verdict;
import art.moodframe.tools.CameraCheckApi.Generator;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * The output of the camera check: a contact sheet per group, context and model (stages side by side, repetitions
 * below each other), an HTML report with all sheets, verdicts and prompts, and the verdicts as plain text.
 *
 * @since 2.0.2
 */
final class CameraCheckReport {

    private static final int THUMB_WIDTH = 280;
    private static final int THUMB_HEIGHT = THUMB_WIDTH * 2 / 3;
    private static final int GAP = 6;

    private final Path dir;
    private final double spent;

    /**
     * Creates the report.
     *
     * @param dir   the directory of the check
     * @param spent the money spent in the directory so far, in USD
     */
    CameraCheckReport(Path dir, double spent) {
        this.dir = dir;
        this.spent = spent;
    }

    /**
     * Writes contact sheets, {@code report.html} and {@code verdicts.txt} (plus a copy with the time, so earlier
     * rounds stay comparable).
     *
     * @param groups       the groups
     * @param jobs         all jobs, also those without image
     * @param verdicts     the verdict on a stage from the jobs of its group, context and model
     * @param ratings      the questionnaire answer per image file
     * @param groupRatings the ratings of several images at once
     * @throws IOException if a file cannot be written
     */
    void write(List<Group> groups, List<Job> jobs, BiFunction<Stage, List<Job>, Verdict> verdicts,
            Map<String, JSONObject> ratings, Map<String, JSONObject> groupRatings) throws IOException {
        Map<String, List<Job>> cells = CameraCheck.cells(jobs);
        StringBuilder html = new StringBuilder();
        StringBuilder text = new StringBuilder();
        StringBuilder sections = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"de\"><head><meta charset=\"utf-8\"><title>Kamera-Prüfung</title>")
                .append("<style>body{font:14px sans-serif;margin:24px;max-width:1800px}table{border-collapse:collapse;")
                .append("margin:8px 0 24px}td,th{border:1px solid #ccc;padding:3px 6px;vertical-align:top;")
                .append("text-align:left}img{max-width:100%;border:1px solid #ccc}.greift,.hält,.verträglich,")
                .append(".eigene{background:#d8f5d8}.schwach,.teilweise{background:#fff3c4}.Fehler,.verfehlt,")
                .append(".gleich,.keine,.kaputt{background:#f8d0d0}.eine{background:#fff3c4}")
                .append(".neutral,.Referenz{background:#e8e8f8}")
                .append("details{margin:2px 0}pre{white-space:pre-wrap;margin:2px 0}</style></head><body>")
                .append("<h1>Kamera-Prüfung</h1><p>").append(LocalDateTime.now().withNano(0))
                .append(String.format(Locale.ROOT,
                        " &middot; ausgegeben bisher: $%.2f &middot; Bewertung blind durch %s</p><p>",
                        spent, CameraCheckApi.VISION_MODEL));
        for (Generator generator : CameraCheckApi.GENERATORS)
            html.append(escape(generator.label())).append(": ").append(generator.key() != null ? "geprüft"
                    : "nicht geprüft (" + generator.envKey() + " fehlt)").append("<br>");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path earlier : files.filter((Path file) -> file.getFileName().toString().matches("report-.+\\.html"))
                    .sorted().toList())
                html.append("<a href=\"").append(earlier.getFileName()).append("\">").append(earlier.getFileName())
                        .append("</a> (frühere Runde)<br>");
        }
        html.append("</p><p>Urteil: <b>greift</b> (in mindestens 2 von 3 Bildern bzw. bei beiden Seeds sichtbar und ")
                .append("blind richtig geordnet oder zugeordnet), <b>schwach</b> (teilweise), <b>gleich wie ")
                .append("Nachbar</b> (nicht unterscheidbar), <b>Fehler</b> (dieselbe Nebenwirkung in der Mehrzahl ")
                .append("der Bilder), <b>neutral</b> (Stufe muss nichts zeigen).</p>");
        text.append(String.format(Locale.ROOT, "Kamera-Prüfung %s, ausgegeben $%.2f%n",
                LocalDateTime.now().withNano(0), spent));

        for (Group group : groups) {
            List<List<Job>> groupCells = new ArrayList<>();
            for (Generator generator : CameraCheckApi.GENERATORS)
                for (String context : group.contexts()) {
                    List<Job> cell = cells.get(group.id() + "|" + context + "|" + generator.id());
                    if (cell != null && cell.stream().anyMatch((Job job) -> Files.exists(png(job))))
                        groupCells.add(cell);
                }
            if (groupCells.isEmpty())
                continue;

            /*  The summary table of the group: stages by context and model  */
            html.append("<h2>").append(escape(group.title())).append("</h2><p>Motiv: ").append(escape(group.subject()))
                    .append("</p><table><tr><th>Stufe</th>");
            text.append(String.format("%n%s%n", group.title().toUpperCase()));
            Map<Stage, Map<List<Job>, Verdict>> table = new LinkedHashMap<>();
            for (List<Job> cell : groupCells)
                html.append("<th>").append(escape(cell.get(0).context())).append(" &middot; ")
                        .append(escape(cell.get(0).generator().label())).append("</th>");
            html.append("</tr>");
            for (Stage stage : group.stages()) {
                html.append("<tr><td>").append(escape(stage.label())).append("</td>");
                text.append(String.format("  %-34s", stage.label()));
                for (List<Job> cell : groupCells) {
                    Verdict verdict = verdicts.apply(stage, cell);
                    table.computeIfAbsent(stage, (Stage key) -> new LinkedHashMap<>()).put(cell, verdict);
                    String code = verdict != null ? verdict.code() : "offen";
                    html.append("<td class=\"").append(code.split(" ")[0]).append("\">").append(escape(code))
                            .append("</td>");
                    text.append(String.format(" %-34s", cell.get(0).context() + ": " + code));
                }
                html.append("</tr>");
                text.append(System.lineSeparator());
            }
            html.append("</table>");

            /*  Per context and model: the contact sheet, the evidence and the prompts  */
            for (List<Job> cell : groupCells) {
                Job first = cell.get(0);
                String name = group.id() + "-" + first.context().toLowerCase().replace(' ', '-') + "-"
                        + first.generator().id();
                sheet(dir.resolve("sheets").resolve(name + ".jpg"), group.title() + " · " + first.context() + " · "
                        + first.generator().label(), group.stages(), cell, table);
                sections.append("<h3 id=\"").append(name).append("\">").append(escape(group.title()))
                        .append(" &middot; ").append(escape(first.context())).append(" &middot; ")
                        .append(escape(first.generator().label())).append("</h3><a href=\"sheets/").append(name)
                        .append(".jpg\"><img src=\"sheets/").append(name).append(".jpg\"></a>");
                for (JSONObject rating : groupRatings.values())
                    if (rating.getString("group").equals(group.id()) && rating.getString("context")
                            .equals(first.context()) && rating.getString("model").equals(first.generator().label())
                            && rating.getString("kind").equals("order") && rating.getJSONArray("files").toList()
                            .stream().allMatch((Object file) -> cell.stream().anyMatch((Job job) -> job.file()
                            .equals(file))))
                        sections.append("<p>Ordnung (").append(escape(String.valueOf(rating.getJSONObject("answer")
                                .opt("order")))).append("): ").append(escape(rating.getJSONObject("answer")
                                .optString("reason"))).append("</p>");
                sections.append("<table><tr><th>Stufe</th><th>Urteil</th><th>Befund</th><th>Prompt</th></tr>");
                for (Stage stage : group.stages()) {
                    Verdict verdict = table.get(stage).get(cell);
                    String prompt = cell.stream().filter((Job job) -> job.stage() == stage).map(Job::prompt)
                            .findFirst().orElse("");
                    String code = verdict != null ? verdict.code() : "offen";
                    sections.append("<tr><td>").append(escape(stage.label())).append("</td><td class=\"")
                            .append(code.split(" ")[0]).append("\">").append(escape(code)).append("</td><td>")
                            .append(escape(verdict != null ? verdict.detail() : "")).append("</td><td><details>")
                            .append("<summary>Prompt</summary><pre>").append(escape(prompt))
                            .append("</pre></details></td></tr>");
                }
                sections.append("</table>");
            }
        }
        html.append("<h2>Kontaktbögen</h2>").append(sections).append("</body></html>");
        Files.writeString(dir.resolve("report.html"), html);
        Files.writeString(dir.resolve("verdicts.txt"), text);
        Files.writeString(dir.resolve("verdicts-" + LocalDateTime.now().withNano(0).toString().replace(':', '-')
                + ".txt"), text);
    }

    /*  Stages side by side, repetitions below each other, the verdict under each column  */
    private void sheet(Path file, String title, List<Stage> stages, List<Job> cell,
            Map<Stage, Map<List<Job>, Verdict>> table) throws IOException {
        int rows = (int) cell.stream().map((Job job) -> job.stage().key()).distinct().mapToLong((String key) -> cell
                .stream().filter((Job job) -> job.stage().key().equals(key)).count()).max().orElse(1);
        int width = stages.size() * (THUMB_WIDTH + GAP) + GAP;
        int height = 40 + 22 + rows * (THUMB_HEIGHT + GAP) + 24;
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        graphics.drawString(title, GAP, 26);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        for (int column = 0; column < stages.size(); column++) {
            Stage stage = stages.get(column);
            int x = GAP + column * (THUMB_WIDTH + GAP);
            graphics.setColor(Color.BLACK);
            graphics.drawString(stage.label(), x, 56);
            List<Job> images = cell.stream().filter((Job job) -> job.stage() == stage).sorted(Comparator
                    .comparingInt(Job::rep)).toList();
            for (int row = 0; row < images.size(); row++) {
                Path png = png(images.get(row));
                if (!Files.exists(png))
                    continue;
                BufferedImage image = ImageIO.read(png.toFile());
                graphics.drawImage(image, x, 64 + row * (THUMB_HEIGHT + GAP), THUMB_WIDTH, THUMB_HEIGHT, null);
            }
            Verdict verdict = table.get(stage).get(cell);
            String code = verdict != null ? verdict.code() : "offen";
            boolean good = code.startsWith("greift") || code.equals("hält") || code.startsWith("eigene")
                    || code.equals("verträglich");
            boolean plain = code.equals("neutral") || code.equals("Referenz");
            boolean weak = code.startsWith("schwach") || code.equals("teilweise") || code.startsWith("eine");
            graphics.setColor(good ? new Color(0, 120, 0) : plain ? Color.DARK_GRAY : weak ? new Color(170, 110, 0)
                    : new Color(190, 0, 0));
            graphics.drawString(code, x, height - 8);
        }
        graphics.dispose();
        ImageIO.write(sheet, "jpg", file.toFile());
    }

    private Path png(Job job) {
        return dir.resolve("images").resolve(job.file() + ".png");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
