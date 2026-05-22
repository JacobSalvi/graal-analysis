package dag.usi.ch;

import java.util.List;

public class Method{
    private final int nameId;
    private final List<Instr> instrs;
    private long baseOffset;

    public  Method(int nameId, List<Instr>instrs, long baseOffset){
        this.nameId = nameId;
        this.instrs = instrs;
        this.baseOffset = baseOffset;
    }

    public int nameId(){
        return nameId;
    }

    public List<Instr> instrs(){
        return instrs;
    }

    public long baseOffset(){
        return baseOffset;
    }

    public void setBaseOffset(long b){
        baseOffset=b;
    }
}
