package telemetry;

public class TmUnknown extends TmDat {
    private byte[] rawHeader;      // заголовок 16 байт
    private byte[] rawData;        // дополнительные данные (если есть)

    public void setRawHeader(byte[] rawHeader) {
        this.rawHeader = rawHeader.clone();
    }

    public void setRawData(byte[] rawData) {
        this.rawData = rawData.clone();
    }

    @Override
    public String getValueAsString() {
        int rawType = (rawHeader[7] & 0x0F);
        return String.format("Неизвестный тип значения (%d): %d байт данных",
                rawType, rawData.length);
    }
}
