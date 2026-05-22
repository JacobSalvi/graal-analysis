package dag.usi.ch;

public class PerfInfo {
    private final int nameId;
    private final long pc;
    private final String offset;

    public int nameId() {
        return nameId;
    }

    public long pc() {
        return pc;
    }

    public String offset() {
        return offset;
    }

    public PerfInfo(Integer nameId, long pc, String offset) {
        this.nameId = nameId;
        this.pc = pc;
        this.offset = offset;
        int h = nameId;
        h = 31 * h + Long.hashCode(pc);
        h = 31 * h + offset.hashCode();
        this.hash=h;
    }

    private final int hash;

    @Override
    public int hashCode() {
        return hash;
    }
}
