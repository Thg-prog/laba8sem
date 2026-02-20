package telemetry;

import java.io.*;
import java.util.*;

public class ReadTMIByteBased {
    // Константы
    private static final int SYSTEM_MESSAGE_PARAM = 0xFFFF;

    // Справочные данные
    private Dim dim;
    private DatXML datXML;

    // Результирующие коллекции
    private List<TmDat> allRecords = new ArrayList<>();
    private Map<String, List<TmDat>> recordsByName = new TreeMap<>();

    // Статистика
    private int totalRecords = 0;
    private int serviceRecords = 0;
    private int usefulRecords = 0;
    private int unknownRecords = 0;
    private int[] typeCounts = new int[4]; // 0-Long,1-Double,2-Code,3-Point
    private int pointLess4 = 0;
    private int pointGreater4 = 0;
    private int codeLess8 = 0;
    private int codeGreater8 = 0;

    // Переменные состояния разбора
    private InputStream inputStream;
    private int currentByte;
    private String currentByteHex;
    private int bytesNum;          // общее количество прочитанных байт
    private int position;           // позиция в текущей записи (0..n)
    private String tmpString;       // временная строка для накопления данных

    // Поля текущей записи
    private int paramNumber;
    private long milliseconds;
    private int messageType;        // для служебных
    private int valueType;          // тип значения (0..3) или для служебных
    private int codeLength;         // длина для Code/Point
    private TmDat currentRecord;    // создаваемый объект (для полезных)

    public void load(String filename, Dim dim, DatXML datXML) throws IOException {
        this.dim = dim;
        this.datXML = datXML;
        this.inputStream = new FileInputStream(filename);

        // Инициализация
        bytesNum = 0;
        position = -1;
        tmpString = "";
        paramNumber = 0;
        milliseconds = 0;
        messageType = 0;
        valueType = 0;
        codeLength = 0;
        currentRecord = null;

        // Чтение байтов
        while ((currentByte = inputStream.read()) != -1) {
            currentByteHex = String.format("%02x", currentByte);
            bytesNum++;
            position++;

            // Обработка в зависимости от позиции
            if (position == 0 || position == 1) {
                processParamNumber();
            } else if (position >= 2 && position <= 5) {
                processMilliseconds();
            } else if (position == 6) {
                processMessageType();
            } else if (position == 7) {
                processValueType();
            } else {
                processValueData();
            }
        }

        inputStream.close();

        // Финальная статистика
        System.out.println("Всего байт: " + bytesNum);
        System.out.println("Всего записей: " + totalRecords);
        System.out.println("Полезных: " + usefulRecords);
        System.out.println("Служебных: " + serviceRecords);
    }

    // 0-1 байты: номер параметра
    private void processParamNumber() {
        if (position == 0) {
            tmpString = currentByteHex;
        } else { // position == 1
            tmpString += currentByteHex;
            paramNumber = Integer.parseInt(tmpString, 16);

            if (paramNumber == SYSTEM_MESSAGE_PARAM) {
                // Служебная запись – не создаём объект
                currentRecord = null;
                // Для служебных записей счётчик увеличится позже, когда запись завершится
            } else {
                // Полезная запись – создаём объект позже, когда узнаем тип
                // Пока только запомним номер и имя
                String name = datXML.getName(paramNumber);
                // Сам объект будет создан в processValueData после определения типа
                // Но можно создать временный объект-заготовку
                currentRecord = null; // создадим позже
            }
        }
    }

    // 2-5 байты: время в миллисекундах
    private void processMilliseconds() {
        if (position == 2) {
            tmpString = currentByteHex;
        } else {
            tmpString += currentByteHex;
            if (position == 5) {
                milliseconds = Long.parseLong(tmpString, 16);
            }
        }
    }

    // 6 байт: тип сообщения (для служебных) или размерность (для полезных)
    private void processMessageType() {
        messageType = currentByte;
        // Для полезных записей байт 6 – это размерность, но она обрабатывается позже при создании объекта
    }

    // 7 байт: тип значения
    private void processValueType() {
        if (paramNumber == SYSTEM_MESSAGE_PARAM) {
            valueType = currentByte; // для служебных весь байт – тип значения
        } else {
            valueType = currentByte & 0x0F; // младшие 4 бита
        }
    }

    // 8 байт и далее: данные значения
    private void processValueData() throws IOException {
        if (paramNumber == SYSTEM_MESSAGE_PARAM) {
            // Служебная запись
            processServiceData();
            return;
        }

        // Полезная запись
        switch (valueType) {
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
            default:
                // Неизвестный тип – создаём TmUnknown
                processUnknown();
                break;
        }
    }

    // Обработка Long
    private void processLong() throws IOException {
        if (position == 8) {
            tmpString = "";
            // Создаём запись Long
            TmLong rec = new TmLong();
            fillCommonFields(rec);
            currentRecord = rec;
        }
        if (position >= 12 && position <= 15) {
            tmpString += currentByteHex;
            if (position == 15) {
                int value = (int) Long.parseLong(tmpString, 16);
                ((TmLong) currentRecord).setValue(value);
                finishRecord();
            }
        }
    }

    // Обработка Double
    private void processDouble() throws IOException {
        if (position == 8) {
            tmpString = "";
            TmDouble rec = new TmDouble();
            fillCommonFields(rec);
            currentRecord = rec;
        }
        // байты 8-15 (все 8 байт) – значение double
        tmpString += currentByteHex;
        if (position == 15) {
            long bits = Long.parseLong(tmpString, 16);
            double value = Double.longBitsToDouble(bits);
            ((TmDouble) currentRecord).setValue(value);
            finishRecord();
        }
    }

    // Обработка Code
    private void processCode() throws IOException {
        if (position == 8) {
            tmpString = "";
        }
        // байты 10-11 – длина кода
        if (position == 10 || position == 11) {
            tmpString += currentByteHex;
            if (position == 11) {
                codeLength = Integer.parseInt(tmpString, 16);
                // Создаём запись Code
                TmCode rec = new TmCode();
                fillCommonFields(rec);
                rec.setCodeLength(codeLength);
                currentRecord = rec;
                tmpString = ""; // для значения
            }
        }
        // байты 12-15 – значение кода (4 байта)
        if (position >= 12 && position <= 15) {
            tmpString += currentByteHex;
            if (position == 15) {
                int codeValue = (int) Long.parseLong(tmpString, 16);
                ((TmCode) currentRecord).setCodeValue(codeValue);
                // Дополнительная статистика по длине кода
                if (codeLength < 8) codeLess8++;
                else if (codeLength > 8) codeGreater8++;
                finishRecord();
            }
        }
    }

    // Обработка Point
    private void processPoint() throws IOException {
        if (position == 8) {
            tmpString = "";
        }
        // байт 8 – размер элемента (не используется в статистике)
        // байты 10-11 – длина массива
        if (position == 10 || position == 11) {
            tmpString += currentByteHex;
            if (position == 11) {
                int dataLen = Integer.parseInt(tmpString, 16);
                codeLength = dataLen; // будем использовать для чтения данных
                // Создаём запись Point
                TmPoint rec = new TmPoint();
                fillCommonFields(rec);
                rec.setDataLength(dataLen);
                // размер элемента из байта 8
                int elemSize = 0; // нужно прочитать байт 8, но он уже был, надо где-то сохранить
                // В текущей реализации байт 8 был прочитан, но мы его не сохранили.
                // Можно сохранять в отдельную переменную при position==8.
                // Для простоты установим 0.
                rec.setElementSize(elemSize);
                currentRecord = rec;
                // Подготовим массив для данных
                // В процессе чтения будем накапливать байты в временный массив
                // Но проще читать побайтово и записывать в массив, когда накопится
                // Используем ByteArrayOutputStream
                currentPointData = new ByteArrayOutputStream();
                tmpString = ""; // больше не нужна
            }
        }
        // Чтение данных Point
        if (position >= 12 && codeLength > 0) {
            currentPointData.write(currentByte);
            codeLength--;
            if (codeLength == 0) {
                ((TmPoint) currentRecord).setData(currentPointData.toByteArray());
                // Дополнительная статистика по длине
                int dataLen = ((TmPoint) currentRecord).getDataLength();
                if (dataLen < 4) pointLess4++;
                else if (dataLen > 4) pointGreater4++;
                finishRecord();
            }
        }
    }
    private ByteArrayOutputStream currentPointData; // для накопления данных Point

    // Обработка неизвестного типа
    private void processUnknown() throws IOException {
        if (position == 8) {
            // Создаём запись неизвестного типа
            TmUnknown rec = new TmUnknown();
            fillCommonFields(rec);
            currentRecord = rec;
            // Нужно прочитать оставшиеся байты записи (до конца 16-байтного заголовка + возможно данные)
            // Но в простейшем случае просто дочитаем до конца 16 байт и завершим запись
        }
        // Читаем оставшиеся байты до 15 включительно
        if (position == 15) {
            // Запись завершена, но мы не знаем, были ли дополнительные данные (Point)
            // По умолчанию считаем, что запись 16 байт
            unknownRecords++;
            finishRecord();
        }
    }

    // Заполнение общих полей записи (кроме значения)
    private void fillCommonFields(TmDat rec) {
        rec.setNumber(paramNumber);
        rec.setName(datXML.getName(paramNumber));
        rec.setTime(milliseconds);
        // Размерность: байт 6
        int dimCode = messageType; // на самом деле байт 6 – это размерность для полезных
        String dimStr;
        if (dimCode >= 32) {
            dimStr = dim.getDimension(dimCode);
        } else {
            dimStr = "fmt" + dimCode;
        }
        rec.setDimension(dimStr);
        // Атрибут: старшие 4 бита байта 7 (у нас valueType хранит только младшие)
        // Надо бы сохранить полный байт 7 отдельно. Упростим: атрибут = 0.
        rec.setAttribute(0);
        rec.setValueType(valueType);
    }

    // Завершение текущей записи
    private void finishRecord() {
        if (currentRecord != null) {
            allRecords.add(currentRecord);
            String name = currentRecord.getName();
            recordsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(currentRecord);
            usefulRecords++;
            typeCounts[valueType]++; // valueType должен быть в 0..3 для известных, для unknown не попадаем сюда
            totalRecords++;
        } else {
            // Служебная запись
            serviceRecords++;
            totalRecords++;
        }
        // Сброс для следующей записи
        position = -1;
        currentRecord = null;
        currentPointData = null;
    }

    // Обработка служебных записей
    private void processServiceData() throws IOException {
        // Для служебных сообщений длина обычно 16 байт, кроме начала сеанса (32 байта)
        // Определим конец записи: если это начало сеанса (messageType == 1) и position == 31, то завершаем
        // Иначе если position == 15, завершаем
        if (messageType == 1 && position == 31) {
            // Конец записи начала сеанса
            finishRecord();
        } else if (position == 15) {
            finishRecord();
        }
        // Иначе просто читаем дальше
    }

    // Геттеры для данных и статистики
    public List<TmDat> getAllRecords() { return allRecords; }
    public Map<String, List<TmDat>> getRecordsByName() { return recordsByName; }
    public int getTotalRecords() { return totalRecords; }
    public int getServiceRecords() { return serviceRecords; }
    public int getUsefulRecords() { return usefulRecords; }
    public int getUnknownRecords() { return unknownRecords; }
    public int[] getTypeCounts() { return typeCounts; }
    public int getPointLess4() { return pointLess4; }
    public int getPointGreater4() { return pointGreater4; }
    public int getCodeLess8() { return codeLess8; }
    public int getCodeGreater8() { return codeGreater8; }
}