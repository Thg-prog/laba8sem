package telemetry;

public class TmLong extends TmDat {
    private int value; // 32-разрядное целое

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }

    @Override
    public String getValueAsString() {
        return value + " " + dimension;
    }
}