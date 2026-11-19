package art.moodframe.ui.controller;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The groups of the right panel in MainWindow.fxml with the parts it includes (GitHub issue #1): each a card with its
 * line, the fold arrow in the header, the summary field always shown and the detail fields below; no field is wider
 * than its group, and fixed explanations are tooltips instead of lines below the fields.
 */
class MainWindowPanelTest {

    private static List<Element> elements;

    @BeforeAll
    static void readTheWindow() throws Exception {
        Document fxml = read("MainWindow.fxml");
        include(fxml, fxml.getDocumentElement());
        elements = new ArrayList<>();
        NodeList all = fxml.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++)
            elements.add((Element) all.item(i));
    }

    @Test
    void eachGroupShowsItsSummaryFieldAboveItsFoldedDetails() {
        Map<String, String> summaries = Map.of("moodGroupPane", "moodComboBox", "frameGroupPane", "frameComboBox",
                "generationGroupPane", "generationSummaryModel");
        summaries.forEach((group, summary) -> {
            List<Element> parts = children(byId(group));
            assertEquals(List.of("panel-group-line", "panel-group-header", "panel-group-summary",
                    "panel-group-details"), parts.stream().map(MainWindowPanelTest::partOf).toList(), group);
            assertEquals("false", parts.get(0).getAttribute("managed"), "the line is drawn beside the fields");
            assertEquals("panel-group-arrow", partOf(children(parts.get(1)).get(0)), "the arrow comes first");
            assertTrue(inside(byId(summary), parts.get(2)), summary + " is the summary field of " + group);
        });
        assertTrue(elements.stream().noneMatch((e) -> "TitledPane".equals(e.getTagName())), "no folding pane");
    }

    @Test
    void generationSummarizesInTwoSmallLinesAndFoldsTheModelWithItsFieldsFirst() {
        List<Element> parts = children(byId("generationGroupPane"));
        assertEquals(List.of("generationSummaryModel", "generationSummaryAttachments"), ids(children(parts.get(2))),
                "the two lines of the summary");
        for (Element line : children(parts.get(2)))
            assertEquals("text-small, text-muted", line.getAttribute("styleClass"), "small like the hints");

        /*  the model with its heading, right below it its fields, then count or duration; the attachment last  */
        List<Element> details = children(parts.get(3));
        assertTrue(inside(byId("modelLabel"), details.get(0)) && inside(byId("modelFamilyChoiceBox"), details.get(0))
                && inside(byId("modelComboBox"), details.get(0)), "the model comes first");
        assertEquals(List.of("geminiAdvancedFeaturesContainer", "geminiThinkingLevelContainer", "bflParametersContainer",
                "seedContainer", "imageCountContainer", "videoParametersContainer", "multiImageAttachmentContainer",
                "imageAttachmentSection"), ids(details.subList(1, details.size())),
                "the fields of the model, then count or duration, then the attachment");

        /*  the fields of FLUX: web search first like grounding, then enhancement, safety, guidance, steps; then the
            seed of every provider with seeds  */
        assertEquals(List.of("flux2GroundingSearchContainer", "bflPromptUpsamplingCheckBox", "flux1SafetyContainer",
                "flux2ParametersContainer"), ids(children(byId("bflParametersContainer"))));
        assertEquals(List.of("flux2SafetyContainer", "flux2GuidanceContainer", "flux2StepsContainer"),
                ids(children(byId("flux2ParametersContainer"))), "safety, guidance, steps");
        assertTrue(inside(byId("seedTextField"), byId("seedContainer")), "the seed after the fields of BFL");
    }

    @Test
    void noFieldIsWiderThanItsGroup() {
        assertEquals("330.0", byId("panelScrollPane").getAttribute("prefWidth"));
        List<Element> fields = elements.stream().filter((e) -> e.getTagName().equals("ComboBox")
                || e.getTagName().equals("ChoiceBox")).filter((e) -> inside(e, byId("panelScrollPane")))
                .filter((e) -> !"false".equals(e.getAttribute("visible"))).toList();
        assertEquals(12, fields.size());
        for (Element field : fields)
            assertEquals("0", field.getAttribute("minWidth"), field.getAttribute("fx:id"));
    }

    @Test
    void onlyHintsOfAnEffectShowBelowTheFields() {
        List<String> hints = elements.stream().filter((e) -> e.getTagName().equals("Label"))
                .filter((e) -> inside(e, byId("panelScrollPane")))
                .filter((e) -> e.getAttribute("fx:id").endsWith("Hint") || e.getAttribute("fx:id").endsWith("HintLabel"))
                .map((e) -> e.getAttribute("fx:id")).toList();
        assertEquals(List.of("lightLayoutHint", "cameraHint", "aspectRatioHintLabel", "resolutionHintLabel"), hints);
        for (String hint : List.of("aspectRatioHintLabel", "resolutionHintLabel"))
            assertEquals("false", byId(hint).getAttribute("managed"), hint + " shows only once the model differs");

        /*  the fixed explanations are tooltips of the field and its label  */
        for (String tooltip : List.of("flux2GuidanceTooltip", "flux2StepsTooltip", "videoDurationTooltip",
                "geminiThinkingLevelTooltip"))
            assertEquals(2, elements.stream().filter((e) -> ("$" + tooltip).equals(e.getAttribute("tooltip")))
                    .count(), tooltip);
        assertEquals("Tooltip", byId("bflPromptUpsamplingTooltip").getTagName());
    }

    private static Document read(String name) throws Exception {
        try (InputStream in = MainWindowPanelTest.class.getResourceAsStream("/fxml/" + name)) {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        }
    }

    /*  replaces each fx:include below an element by the root of the included file, as the loader composes the window  */
    private static void include(Document fxml, Element element) throws Exception {
        for (Element child : children(element)) {
            if (!"fx:include".equals(child.getTagName())) {
                include(fxml, child);
                continue;
            }
            Element root = (Element) fxml.importNode(read(child.getAttribute("source")).getDocumentElement(), true);
            element.replaceChild(root, child);
            include(fxml, root);
        }
    }

    /*  the element with the id  */
    private static Element byId(String id) {
        return elements.stream().filter((e) -> id.equals(e.getAttribute("fx:id"))).findFirst().orElseThrow();
    }

    /*  the ids of the elements  */
    private static List<String> ids(List<Element> elements) {
        return elements.stream().map((e) -> e.getAttribute("fx:id")).toList();
    }

    /*  the child elements, without text and comments  */
    private static List<Element> children(Element element) {
        List<Element> children = new ArrayList<>();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling())
            if (child.getNodeType() == Node.ELEMENT_NODE)
                children.add((Element) child);
        return children;
    }

    /*  the part of a group an element is: its style class starting with "panel-group-"  */
    private static String partOf(Element element) {
        return Arrays.stream(element.getAttribute("styleClass").split(",\\s*"))
                .filter((c) -> c.startsWith("panel-group-")).findFirst().orElse("");
    }

    private static boolean inside(Node node, Node parent) {
        for (Node n = node; n != null; n = n.getParentNode())
            if (n == parent)
                return true;
        return false;
    }
}
