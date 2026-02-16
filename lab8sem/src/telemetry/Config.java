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
 * Загружает файл config.xml (необязательный). Содержит пути к файлам и настройки.
 */
public class Config {
    private String tmFile;
    private String xmlFile;
    private String dimFile;
    // другие параметры можно добавить при необходимости

    public void load(String filename) throws Exception {
        File f = new File(filename);
        if (!f.exists()) {
            // Если файла нет, используем значения по умолчанию
            setDefaults();
            return;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(f);
        doc.getDocumentElement().normalize();

        NodeList nodes = doc.getElementsByTagName("tmFile");
        if (nodes.getLength() > 0)
            tmFile = nodes.item(0).getTextContent().trim();

        nodes = doc.getElementsByTagName("xmlFile");
        if (nodes.getLength() > 0)
            xmlFile = nodes.item(0).getTextContent().trim();

        nodes = doc.getElementsByTagName("dimFile");
        if (nodes.getLength() > 0)
            dimFile = nodes.item(0).getTextContent().trim();

        // Если какой-то элемент отсутствует, подставляем умолчания
        setDefaults();
    }

    private void setDefaults() {
        if (tmFile == null) tmFile = "190829_v29854.KNP";
        if (xmlFile == null) xmlFile = "KNP-173.14.33.58.dat.xml";
        if (dimFile == null) dimFile = "dimens.ion";
    }

    public String getTmFile() { return tmFile; }
    public String getXmlFile() { return xmlFile; }
    public String getDimFile() { return dimFile; }
}
