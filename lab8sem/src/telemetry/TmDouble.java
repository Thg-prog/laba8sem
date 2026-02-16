package telemetry;

public class TmDouble extends TmDat {
    private double value; // 64-разрядное вещественное

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    @Override
    public String getValueAsString() {
        // Ограничим количество знаков для читаемости
        return String.format("%.6f %s", value, dimension);
    }
}