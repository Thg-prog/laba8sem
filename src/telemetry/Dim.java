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
 * Загружает файл dimens.ion, где каждая строка — текст размерности.
 * Номер строки (начиная с 1) соответствует коду размерности.
 */
public class Dim {
    private final Map<Integer, String> dimensions = new TreeMap<>();

    public void load(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    dimensions.put(lineNum, line);
                }
                lineNum++;
            }
        }
        // Проверка: по заданию код 32 должен быть "%"
        // (если файл корректен, это выполняется автоматически)
    }

    /** Возвращает размерность по коду, или строку "[код]" если код не найден. */
    public String getDimension(int code) {
        return dimensions.getOrDefault(code, "[" + code + "]");
    }
}