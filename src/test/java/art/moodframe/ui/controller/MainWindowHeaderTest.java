package art.moodframe.ui.controller;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The update hint in the header of MainWindow.fxml: its text right behind the version, a plain label, then its
 * symbol, the only update symbol of the header.
 */
class MainWindowHeaderTest {

    @Test
    void theUpdateHintSitsBehindTheVersion() throws Exception {
        Document fxml;
        try (InputStream in = MainWindowHeaderTest.class.getResourceAsStream("/fxml/MainWindow.fxml")) {
            fxml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        }
        List<Element> elements = new ArrayList<>();
        NodeList all = fxml.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++)
            elements.add((Element) all.item(i));
        Element version = elements.stream().filter((e) -> "appVersionLabel".equals(e.getAttribute("fx:id")))
                .findFirst().orElseThrow();

        Element text = nextElement(version);
        assertEquals("updateHintLabel", text.getAttribute("fx:id"));
        assertEquals("Label", text.getTagName(), "the text is no link");
        Element symbol = nextElement(text);
        assertEquals("updateButton", symbol.getAttribute("fx:id"));
        assertEquals("#onUpdateClicked", symbol.getAttribute("onAction"));

        /*  no second update symbol, e.g. at the right next to info and settings  */
        List<Element> icons = elements.stream()
                .filter((e) -> "mdi2a-arrow-up-circle-outline".equals(e.getAttribute("iconLiteral"))).toList();
        assertEquals(1, icons.size());
        Node button = icons.get(0).getParentNode().getParentNode();
        assertSame(symbol, button);
    }

    /*  the next sibling element, skipping text and comments  */
    private static Element nextElement(Element element) {
        Node next = element.getNextSibling();
        while (next != null && next.getNodeType() != Node.ELEMENT_NODE)
            next = next.getNextSibling();
        return (Element) next;
    }
}
