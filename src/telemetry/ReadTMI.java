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
 * Читает двоичный ТМ-файл, создаёт объекты записей и собирает статистику.
 * Все записи сохраняются в списке и в отображении по имени параметра.
 */
public class ReadTMI {
    private final List<TmDat> allRecords = new ArrayList<>();
    private final Map<String, List<TmDat>> recordsByName = new TreeMap<>();

    // Статистика
    private int totalRecords = 0;
    private int serviceRecords = 0;
    private int usefulRecords = 0;
    private int unknownRecords = 0;
    private int pointLess4 = 0;
    private int pointGreater4 = 0;
    private int codeLess8 = 0;
    private int codeGreater8 = 0;

    // геттеры
    public int getPointLess4() { return pointLess4; }
    public int getPointGreater4() { return pointGreater4; }
    public int getCodeLess8() { return codeLess8; }
    public int getCodeGreater8() { return codeGreater8; }

    // Геттер для unknownRecords
    public int getUnknownRecords() { return unknownRecords; }
    private final int[] typeCounts = new int[4]; // Q0..Q3

    public void load(String tmFilename, Dim dim, DatXML datXML) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(tmFilename, "r")) {
            long fileLength = raf.length();
            long pos = 0;

            // Проверка минимального размера (первая запись 32 байта)
            if (fileLength < 32) {
                throw new IOException("Файл слишком мал: " + fileLength + " байт, требуется минимум 32");
            }

            // Чтение первой служебной записи (начало сеанса, 32 байта)
            byte[] header32 = new byte[32];
            raf.readFully(header32);
            pos += 32;
            processServiceRecord(header32);
            totalRecords++;
            serviceRecords++;

            // Основной цикл
            while (pos < fileLength) {
                // Проверяем, хватит ли места для заголовка 16 байт
                if (fileLength - pos < 16) {
                    System.err.println("Предупреждение: файл обрезан (осталось <16 байт), позиция " + pos);
                    break;
                }

                byte[] header = new byte[16];
                raf.readFully(header);
                pos += 16;

                int paramNumber = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);

                if (paramNumber == 0xFFFF) {
                    // Служебная запись (16 байт)
                    processServiceRecord(header);
                    serviceRecords++;
                    totalRecords++;
                    continue;
                }

                // Полезная запись
                int valueType = header[7] & 0x0F;
                int dataLength = 0;
                if (valueType == 3) { // Point
                    dataLength = ((header[10] & 0xFF) << 8) | (header[11] & 0xFF);
                }

                byte[] valueData;
                if (dataLength > 0) {
                    // Проверяем, достаточно ли байт для данных Point
                    if (fileLength - pos < dataLength) {
                        System.err.println("Предупреждение: файл обрезан, не хватает данных для Point-записи. Позиция " + pos);
                        break;
                    }
                    valueData = new byte[dataLength];
                    raf.readFully(valueData);
                    pos += dataLength;
                } else {
                    valueData = new byte[0];
                }

                TmDat record = createTmRecord(header, valueData, valueType, dim, datXML);
                allRecords.add(record);

                if (record instanceof TmUnknown) {
                    unknownRecords++;
                } else {
                    usefulRecords++;
                    typeCounts[record.getValueType()]++;
                }

                String name = record.getName();
                recordsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(record);
                totalRecords++;
            }
        }
    }
    /** Создание объекта соответствующего типа из заголовка и данных. */
    private TmDat createTmRecord(byte[] header, byte[] valueData, int valueType,
                                 Dim dim, DatXML datXML) {
        int number = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        long time = ((long)(header[2] & 0xFF) << 24) |
                ((long)(header[3] & 0xFF) << 16) |
                ((long)(header[4] & 0xFF) << 8)  |
                (header[5] & 0xFF);
        int dimensionCode = header[6] & 0xFF;
        int attribute = (header[7] & 0xF0) >> 4;

        String dimensionStr;
        if (dimensionCode >= 32) {
            dimensionStr = dim.getDimension(dimensionCode);
        } else {
            dimensionStr = "fmt" + dimensionCode;
        }

        String name = datXML.getName(number);

        // Если тип значения неизвестен, создаём TmUnknown
        if (valueType < 0 || valueType > 3) {
            TmUnknown unk = new TmUnknown();
            unk.setNumber(number);
            unk.setName(name + " [unknown type]");
            unk.setTime(time);
            unk.setDimension(dimensionStr);
            unk.setAttribute(attribute);
            unk.setValueType(valueType);
            unk.setRawHeader(header);
            unk.setRawData(valueData);
            unknownRecords++;
            return unk;
        }

        // Обработка известных типов (без изменений)
        ByteBuffer bb = ByteBuffer.wrap(header, 8, 8).order(ByteOrder.BIG_ENDIAN);
        TmDat record;
        switch (valueType) {
            case 0: // Long
                TmLong rl = new TmLong();
                rl.setValue(bb.getInt(4));
                record = rl;
                break;
            case 1: // Double
                TmDouble rd = new TmDouble();
                rd.setValue(bb.getDouble(0));
                record = rd;
                break;
            case 2: // Code
                TmCode rc = new TmCode();
                int codeLength = header[9] & 0xFF;
                rc.setCodeLength(codeLength);
                rc.setCodeValue(bb.getInt(4));
                record = rc;
                if (codeLength < 8) {
                    codeLess8++;
                } else if (codeLength >= 8) {
                    codeGreater8++;
                }
                break;
            case 3: // Point
                TmPoint rp = new TmPoint();
                int elemSize = header[8] & 0xFF;
                int dataLen = ((header[10] & 0xFF) << 8) | (header[11] & 0xFF);
                rp.setElementSize(elemSize);
                rp.setDataLength(dataLen);
                rp.setData(valueData);
                record = rp;
                if (dataLen < 4) {
                    pointLess4++;
                } else if (dataLen >= 4) {
                    pointGreater4++;
                }
                break;
            default:
                // Недостижимо, но оставим для безопасности
                throw new IllegalArgumentException("Неизвестный тип значения: " + valueType);
        }

        record.setNumber(number);
        record.setName(name);
        record.setTime(time);
        record.setDimension(dimensionStr);
        record.setAttribute(attribute);
        record.setValueType(valueType);

        return record;
    }
    /** Обработка служебной записи (можно собирать дополнительную статистику). */
    private void processServiceRecord(byte[] rec) {
        // Пока ничего не делаем, но можно при необходимости извлекать тип сообщения и т.д.
    }

    // Геттеры для данных и статистики
    public List<TmDat> getAllRecords() { return allRecords; }
    public Map<String, List<TmDat>> getRecordsByName() { return recordsByName; }
    public int getTotalRecords() { return totalRecords; }
    public int getServiceRecords() { return serviceRecords; }
    public int getUsefulRecords() { return usefulRecords; }
    public int[] getTypeCounts() { return typeCounts; }
}