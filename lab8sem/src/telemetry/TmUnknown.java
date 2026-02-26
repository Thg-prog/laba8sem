package telemetry;

public class TmUnknown extends TmDat {
    private byte[] rawData;

    public void setRawData(byte[] rawData) {
        this.rawData = rawData;
    }

    @Override
    public String getValueAsString() {
        return String.format("Неизвестный тип (%d), данных: %d байт",
                valueType, rawData == null ? 0 : rawData.length);
    }
}