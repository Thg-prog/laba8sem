package telemetry;

public class TmCode extends TmDat {
    private int codeLength; // длина кода в битах (байт 9)
    private int codeValue;  // 32-разрядное целое (код)

    public int getCodeLength() { return codeLength; }
    public void setCodeLength(int codeLength) { this.codeLength = codeLength; }

    public int getCodeValue() { return codeValue; }
    public void setCodeValue(int codeValue) { this.codeValue = codeValue; }

    @Override
    public String getValueAsString() {
        return String.format("Code(len=%d): %d %s", codeLength, codeValue, dimension);
    }
}
