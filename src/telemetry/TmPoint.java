package telemetry;

public class TmPoint extends TmDat {
    private int elementSize;   // размер элемента (байт 8)
    private int dataLength;    // длина массива в байтах (байты 10-11)
    private byte[] data;       // сами данные

    public int getElementSize() { return elementSize; }
    public void setElementSize(int elementSize) { this.elementSize = elementSize; }

    public int getDataLength() { return dataLength; }
    public void setDataLength(int dataLength) { this.dataLength = dataLength; }

    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }

    @Override
    public String getValueAsString() {
        // По заданию значение не выдаём, только информацию о длине
        return String.format("Point array: %d bytes (element size %d)", dataLength, elementSize);
    }
}
