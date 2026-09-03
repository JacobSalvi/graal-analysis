package dag.usi.ch;

import java.util.List;
import java.util.Objects;

public final class SourceMapping {
    private final List<String> bytes;
    private List<String> sourcePosition;
    private final int functionNameId;
    private final int beg;
    private final int end;
    private final int calleeId;

    public SourceMapping(
            List<String> bytes,
            List<String> sourcePosition,
            int functionNameId,
            int beg,
            int end,
            int calleeId
    ) {
        this.bytes = bytes;
        this.sourcePosition = sourcePosition;
        this.functionNameId = functionNameId;
        this.beg = beg;
        this.end = end;
        this.calleeId = calleeId;
    }

    void sanitize() {
        this.sourcePosition = this.sourcePosition.stream().filter(e -> !e.contains("bci: -1")).collect(java.util.stream.Collectors.toList());
    }

    public List<String> bytes() {
        return bytes;
    }

    public List<String> sourcePosition() {
        return sourcePosition;
    }

    public int functionNameId() {
        return functionNameId;
    }

    public int beg() {
        return beg;
    }

    public int end() {
        return end;
    }

    public int calleeId() {
        return calleeId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (SourceMapping) obj;
        return Objects.equals(this.bytes, that.bytes) &&
                Objects.equals(this.sourcePosition, that.sourcePosition) &&
                this.functionNameId == that.functionNameId &&
                this.beg == that.beg &&
                this.end == that.end &&
                this.calleeId == that.calleeId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bytes, sourcePosition, functionNameId, beg, end, calleeId);
    }

    @Override
    public String toString() {
        return "SourceMapping[" +
                "bytes=" + bytes + ", " +
                "sourcePosition=" + sourcePosition + ", " +
                "functionNameId=" + functionNameId + ", " +
                "beg=" + beg + ", " +
                "end=" + end + ", " +
                "calleeId=" + calleeId + ']';
    }

}
