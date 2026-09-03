package dag.usi.ch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SourceMapping {
    private final List<String> bytes;
    private List<SourcePosition> sourcePositions;
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
        List<SourcePosition> sps = new ArrayList<>();
        for(String sp: sourcePosition){
            int bci = Integer.parseInt(sp.substring(sp.lastIndexOf(" ") + 1, sp.length() - 1));
            String symbol = sp.substring(0, sp.lastIndexOf(" ["));
            sps.add(new SourcePosition(symbol, bci));
        }
        this.sourcePositions = sps;
        this.functionNameId = functionNameId;
        this.beg = beg;
        this.end = end;
        this.calleeId = calleeId;
    }

    void sanitize() {
        this.sourcePositions = this.sourcePositions.stream().filter(e -> e.bci() != -1).collect(java.util.stream.Collectors.toList());
    }

    public List<String> bytes() {
        return bytes;
    }

    public List<SourcePosition> sourcePositions() {
        return sourcePositions;
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
                Objects.equals(this.sourcePositions, that.sourcePositions) &&
                this.functionNameId == that.functionNameId &&
                this.beg == that.beg &&
                this.end == that.end &&
                this.calleeId == that.calleeId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bytes, sourcePositions, functionNameId, beg, end, calleeId);
    }

    @Override
    public String toString() {
        return "SourceMapping[" +
                "bytes=" + bytes + ", " +
                "sourcePosition=" + sourcePositions + ", " +
                "functionNameId=" + functionNameId + ", " +
                "beg=" + beg + ", " +
                "end=" + end + ", " +
                "calleeId=" + calleeId + ']';
    }

}
