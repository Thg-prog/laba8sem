package telemetry;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.List;

/**
 * Загружает файл KNP-173.14.33.58.dat.xml, содержащий информацию о параметрах:
 * номер, имя, полное имя, возможные текстовые значения.
 */
public class DatXML {
    private final Map<Integer, String> paramNames = new TreeMap<>();
    private final Map<Integer, String> paramFullNames = new TreeMap<>();
    private final Map<Integer, List<String>> paramTextValues = new HashMap<>();

    public void load(String filename) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(filename));
        doc.getDocumentElement().normalize();

        NodeList paramNodes = doc.getElementsByTagName("Param");
        for (int i = 0; i < paramNodes.getLength(); i++) {
            Element paramElem = (Element) paramNodes.item(i);
            int number = Integer.parseInt(paramElem.getAttribute("number"));
            String name = paramElem.getAttribute("name");
            String fullName = paramElem.getAttribute("fullname");

            paramNames.put(number, name);
            paramFullNames.put(number, fullName);

            // Чтение дочерних элементов TextValue
            NodeList textNodes = paramElem.getElementsByTagName("TextValue");
            List<String> texts = new ArrayList<>();
            for (int j = 0; j < textNodes.getLength(); j++) {
                Element textElem = (Element) textNodes.item(j);
                texts.add(textElem.getAttribute("value"));
            }
            paramTextValues.put(number, texts);
        }
    }

    public String getName(int number) {
        return paramNames.getOrDefault(number, "UNKNOWN_" + number);
    }

    public String getFullName(int number) {
        return paramFullNames.getOrDefault(number, "");
    }

    public List<String> getTextValues(int number) {
        return paramTextValues.getOrDefault(number, Collections.emptyList());
    }
}
