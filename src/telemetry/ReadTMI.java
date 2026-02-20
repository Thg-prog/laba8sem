package telemetry;

import java.io.*;
import java.util.*;

public class ReadTMI {
    // Константы
    private static final int SYSTEM_MESSAGE_PARAM = 0xFFFF;

    // Входные данные
    private InputStream inputStream;
    private Dim dim;
    private DatXML datXML;

    // Состояние чтения
    private int currentByte = 0;
    private String currentByteHex = "";
    private int bytesNum = 0;
    private int position = -1;
    private String tmpString = "";

    // Поля текущей записи
    private int paramNumber = 0;
    private long milliseconds = 0;
    private int dimensionCode = 0;
    private int attribute = 0;
    private int valueType = 0;
    private int messageType = 0;
    private int codeLength = 0;
    private int dataLength = 0;
    private byte[] pointData = null;
    private int pointDataIndex = 0;
    private boolean isPointReading = false;

    // Статистика
    private int totalRecords = 0;
    private int serviceRecords = 0;
    private int usefulRecords = 0;
    private int unknownRecords = 0;
    private int[] typeCounts = new int[4];

    // Новые счётчики
    private int pointLess4 = 0;
    private int pointGreater4 = 0;
    private int codeLess8 = 0;
    private int codeGreater8 = 0;

    // Результаты
    private List<TmDat> allRecords = new ArrayList<>();
    private Map<String, List<TmDat>> recordsByName = new TreeMap<>();

    // Вспомогательные переменные для определения длины Point
    private boolean readingPointData = false;
    private int remainingPointBytes = 0;

    /**
     * Загружает данные из ТМ-файла
     * @param filename путь к файлу
     * @param dim объект с размерностями
     * @param datXML объект с данными из XML
     * @throws IOException при ошибках чтения
     */
    public void load(String filename, Dim dim, DatXML datXML) throws IOException {
        this.dim = dim;
        this.datXML = datXML;
        this.inputStream = new FileInputStream(filename);

        // Читаем первый заголовок (32 байта) - служебная запись "начало сеанса"
        for (int i = 0; i < 32; i++) {
            readByte();
        }
        // Первая запись уже обработана как служебная
        serviceRecords++;
        totalRecords++;

        // Читаем остальные записи
        while (readByte() != -1) {
            // Цикл продолжается, пока есть байты
        }

        inputStream.close();
        System.out.println("File size: " + bytesNum + " bytes");
        System.out.println("Total records: " + totalRecords);
        System.out.println("Useful records: " + usefulRecords);
    }

    /**
     * Читает один байт из файла и обрабатывает его
     * @return прочитанный байт или -1 при достижении конца файла
     * @throws IOException при ошибках чтения
     */
    private int readByte() throws IOException {
        this.currentByte = this.inputStream.read();
        if (this.currentByte == -1) {
            return -1;
        }

        this.currentByteHex = String.format("%02x", this.currentByte);
        this.bytesNum++;

        // Если читаем данные Point, обрабатываем отдельно
        if (readingPointData) {
            processPointDataByte();
            return this.currentByte;
        }

        this.position++;

        switch (this.position) {
            case 0:
            case 1:
                getParamNumber();
                break;
            case 2:
            case 3:
            case 4:
            case 5:
                getMilliseconds();
                break;
            case 6:
                getDimensionAndMessageType();
                break;
            case 7:
                getAttributeAndValueType();
                break;
            default:
                getValue();
                break;
        }

        return this.currentByte;
    }

    /**
     * Обрабатывает байты данных Point
     */
    private void processPointDataByte() {
        if (pointData != null && pointDataIndex < pointData.length) {
            pointData[pointDataIndex++] = (byte) currentByte;
        }

        remainingPointBytes--;
        if (remainingPointBytes <= 0) {
            // Закончили чтение данных Point
            readingPointData = false;
            finishPointRecord();
        }
    }

    /**
     * Обрабатывает номер параметра (байты 0-1)
     */
    private void getParamNumber() {
        if (this.position == 0) {
            this.tmpString = this.currentByteHex;
        } else {
            this.tmpString = this.tmpString + this.currentByteHex;
            this.paramNumber = Integer.parseInt(this.tmpString, 16);

            // Сброс временных переменных
            this.codeLength = 0;
            this.dataLength = 0;
            this.pointData = null;
            this.pointDataIndex = 0;
            this.readingPointData = false;
        }
    }

    /**
     * Обрабатывает время (байты 2-5)
     */
    private void getMilliseconds() {
        if (this.position == 2) {
            this.tmpString = this.currentByteHex;
        } else {
            this.tmpString = this.tmpString + this.currentByteHex;
            if (this.position == 5) {
                this.milliseconds = Long.parseLong(this.tmpString, 16);
            }
        }
    }

    /**
     * Обрабатывает размерность/тип сообщения (байт 6)
     */
    private void getDimensionAndMessageType() {
        if (isSystemMessage()) {
            this.messageType = this.currentByte;
        } else {
            this.dimensionCode = this.currentByte & 0xFF;
        }
    }

    /**
     * Обрабатывает атрибут и тип значения (байт 7)
     */
    private void getAttributeAndValueType() {
        if (isSystemMessage()) {
            this.valueType = this.currentByte;
        } else {
            this.attribute = (this.currentByte & 0xF0) >> 4;
            this.valueType = this.currentByte & 0x0F;
        }
    }

    /**
     * Обрабатывает значение параметра (байты 8+)
     */
    private void getValue() throws IOException {
        if (this.position == 8) {
            this.tmpString = "";
            this.codeLength = 0;
            this.dataLength = 0;
        }

        if (isSystemMessage()) {
            processServiceMessage();
        } else {
            processUsefulRecord();
        }
    }

    /**
     * Обрабатывает служебное сообщение
     */
    private void processServiceMessage() throws IOException {
        // Служебные сообщения всегда 16 байт
        if (this.position == 15) {
            serviceRecords++;
            totalRecords++;
            this.position = -1;
        }
    }

    /**
     * Обрабатывает полезную запись в зависимости от типа значения
     */
    private void processUsefulRecord() throws IOException {
        switch (this.valueType) {
            case 0: // Long
                processLong();
                break;
            case 1: // Double
                processDouble();
                break;
            case 2: // Code
                processCode();
                break;
            case 3: // Point
                processPoint();
                break;
            default: // Неизвестный тип
                processUnknown();
                break;
        }
    }

    /**
     * Обрабатывает запись типа Long
     */
    private void processLong() throws IOException {
        // Байты 8-11 не используются
        if (this.position >= 8 && this.position <= 11) return;

        this.tmpString = this.tmpString + this.currentByteHex;
        if (this.position == 15) {
            createLongRecord();
            resetForNextRecord();
        }
    }

    /**
     * Обрабатывает запись типа Double
     */
    private void processDouble() throws IOException {
        this.tmpString = this.tmpString + this.currentByteHex;
        if (this.position == 15) {
            createDoubleRecord();
            resetForNextRecord();
        }
    }

    /**
     * Обрабатывает запись типа Code
     */
    private void processCode() throws IOException {
        // Байт 8 - не используется
        if (this.position == 8) return;

        // Байт 9 - длина кода
        if (this.position == 9) {
            this.codeLength = this.currentByte & 0xFF;
            return;
        }

        // Байты 10-11 не используются
        if (this.position == 10 || this.position == 11) return;

        // Байты 12-15 - значение
        this.tmpString = this.tmpString + this.currentByteHex;
        if (this.position == 15) {
            createCodeRecord();
            resetForNextRecord();
        }
    }

    /**
     * Обрабатывает запись типа Point
     */
    private void processPoint() throws IOException {
        // Байт 8 - размер элемента
        if (this.position == 8) {
            // Не сохраняем, но можем использовать при необходимости
            return;
        }

        // Байт 9 - не используется
        if (this.position == 9) return;

        // Байты 10-11 - длина массива
        if (this.position == 10 || this.position == 11) {
            this.tmpString = this.tmpString + this.currentByteHex;
            if (this.position == 11) {
                this.dataLength = Integer.parseInt(this.tmpString, 16);
                // Создаём буфер для данных Point
                this.pointData = new byte[this.dataLength];
                this.pointDataIndex = 0;
                this.remainingPointBytes = this.dataLength;
                this.readingPointData = true;

                // Сброс позиции - дальше читаем данные Point отдельно
                this.position = 15; // Чтобы не сбить счётчик
            }
            return;
        }
    }

    /**
     * Завершает создание записи Point после чтения всех данных
     */
    private void finishPointRecord() {
        createPointRecord();

        // Статистика для Point
        if (this.dataLength < 4) {
            pointLess4++;
        } else if (this.dataLength >= 4) {
            pointGreater4++;
        }

        resetForNextRecord();
    }

    /**
     * Обрабатывает запись с неизвестным типом
     */
    private void processUnknown() throws IOException {
        // Просто пропускаем байты до конца записи
        if (this.position == 15) {
            createUnknownRecord();
            resetForNextRecord();
        }
    }

    /**
     * Создаёт объект Long-записи
     */
    private void createLongRecord() {
        TmLong record = new TmLong();
        record.setNumber(paramNumber);
        record.setName(getParamName());
        record.setTime(milliseconds);
        record.setDimension(getDimensionString());
        record.setAttribute(attribute);
        record.setValueType(valueType);

        try {
            long value = Long.parseLong(tmpString, 16);
            record.setValue((int) value);
        } catch (NumberFormatException e) {
            record.setValue(0);
        }

        addRecord(record);
        typeCounts[0]++;
        usefulRecords++;
    }

    /**
     * Создаёт объект Double-записи
     */
    private void createDoubleRecord() {
        TmDouble record = new TmDouble();
        record.setNumber(paramNumber);
        record.setName(getParamName());
        record.setTime(milliseconds);
        record.setDimension(getDimensionString());
        record.setAttribute(attribute);
        record.setValueType(valueType);

        try {
            long bits = Long.parseLong(tmpString, 16);
            record.setValue(Double.longBitsToDouble(bits));
        } catch (NumberFormatException e) {
            record.setValue(0.0);
        }

        addRecord(record);
        typeCounts[1]++;
        usefulRecords++;
    }

    /**
     * Создаёт объект Code-записи
     */
    private void createCodeRecord() {
        TmCode record = new TmCode();
        record.setNumber(paramNumber);
        record.setName(getParamName());
        record.setTime(milliseconds);
        record.setDimension(getDimensionString());
        record.setAttribute(attribute);
        record.setValueType(valueType);
        record.setCodeLength(codeLength);

        try {
            int value = (int) Long.parseLong(tmpString, 16);
            record.setCodeValue(value);
        } catch (NumberFormatException e) {
            record.setCodeValue(0);
        }

        addRecord(record);
        typeCounts[2]++;
        usefulRecords++;

        // Статистика для Code
        if (codeLength < 8) {
            codeLess8++;
        } else if (codeLength >= 8) {
            codeGreater8++;
        }
    }

    /**
     * Создаёт объект Point-записи
     */
    private void createPointRecord() {
        TmPoint record = new TmPoint();
        record.setNumber(paramNumber);
        record.setName(getParamName());
        record.setTime(milliseconds);
        record.setDimension(getDimensionString());
        record.setAttribute(attribute);
        record.setValueType(valueType);
        record.setDataLength(dataLength);
        record.setData(pointData);

        addRecord(record);
        typeCounts[3]++;
        usefulRecords++;
    }

    /**
     * Создаёт объект записи с неизвестным типом
     */
    private void createUnknownRecord() {
        TmUnknown record = new TmUnknown();
        record.setNumber(paramNumber);
        record.setName(getParamName() + " [unknown type]");
        record.setTime(milliseconds);
        record.setDimension(getDimensionString());
        record.setAttribute(attribute);
        record.setValueType(valueType);

        addRecord(record);
        unknownRecords++;
    }

    /**
     * Добавляет запись в коллекции
     */
    private void addRecord(TmDat record) {
        allRecords.add(record);
        String name = record.getName();
        recordsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(record);
    }

    /**
     * Сбрасывает состояние для следующей записи
     */
    private void resetForNextRecord() {
        this.position = -1;
        totalRecords++;
        // Не увеличиваем usefulRecords здесь, они увеличиваются в конкретных методах
    }

    /**
     * Возвращает имя параметра по номеру
     */
    private String getParamName() {
        return datXML.getName(paramNumber);
    }

    /**
     * Возвращает строку размерности
     */
    private String getDimensionString() {
        if (dimensionCode >= 32) {
            return dim.getDimension(dimensionCode);
        } else {
            return "fmt" + dimensionCode;
        }
    }

    /**
     * Проверяет, является ли текущая запись служебной
     */
    private boolean isSystemMessage() {
        return paramNumber == SYSTEM_MESSAGE_PARAM;
    }

    // Геттеры для статистики

    public List<TmDat> getAllRecords() {
        return allRecords;
    }

    public Map<String, List<TmDat>> getRecordsByName() {
        return recordsByName;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public int getServiceRecords() {
        return serviceRecords;
    }

    public int getUsefulRecords() {
        return usefulRecords;
    }

    public int getUnknownRecords() {
        return unknownRecords;
    }

    public int[] getTypeCounts() {
        return typeCounts;
    }

    public int getPointLess4() {
        return pointLess4;
    }

    public int getPointGreater4() {
        return pointGreater4;
    }

    public int getCodeLess8() {
        return codeLess8;
    }

    public int getCodeGreater8() {
        return codeGreater8;
    }
}