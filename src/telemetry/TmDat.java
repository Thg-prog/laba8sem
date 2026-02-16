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
 * Базовый класс для всех телеметрических записей.
 * Содержит общие поля и методы, включая сравнение для сортировки.
 */
public abstract class TmDat implements Comparable<TmDat> {
    protected int number;          // номер параметра (2 байта)
    protected String name;         // имя параметра (из XML)
    protected long time;           // время в миллисекундах от начала суток
    protected String dimension;    // размерность (строка)
    protected int attribute;       // атрибут (0..15)
    protected int valueType;       // тип значения (0..3)

    // Геттеры и сеттеры
    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getTime() { return time; }
    public void setTime(long time) { this.time = time; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public int getAttribute() { return attribute; }
    public void setAttribute(int attribute) { this.attribute = attribute; }

    public int getValueType() { return valueType; }
    public void setValueType(int valueType) { this.valueType = valueType; }

    /** Возвращает строковое представление значения параметра (с размерностью). */
    public abstract String getValueAsString();

    /** Форматирует время в ЧЧ:ММ:СС,мс */
    public static String formatTime(long millis) {
        long hours = millis / 3_600_000;
        long minutes = (millis % 3_600_000) / 60_000;
        long seconds = (millis % 60_000) / 1000;
        long ms = millis % 1000;
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, ms);
    }

    /** Сравнение сначала по имени, потом по времени (чтобы избежать потери записей). */
    @Override
    public int compareTo(TmDat other) {
        int cmp = this.name.compareTo(other.name);
        if (cmp == 0) {
            return Long.compare(this.time, other.time);
        }
        return cmp;
    }
}
