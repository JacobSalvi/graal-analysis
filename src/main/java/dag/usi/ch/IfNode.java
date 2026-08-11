package dag.usi.ch;

public class IfNode extends ProgramNode{
    private final CondInfo condInfo;
    private final ProgramNode trueBranch;
    private final ProgramNode falseBranch;
    public final int condsize;

    public IfNode(CondInfo condInfo, ProgramNode trueBranch, ProgramNode falseBranch){
        super(condInfo.cond());
        this.condInfo = condInfo;
        this.trueBranch = trueBranch;
        this.falseBranch = falseBranch;
        this.condsize = condInfo.cond().size();
    }

    public CondInfo condInfo(){
        return condInfo;
    }

    public ProgramNode trueBranch(){
        return trueBranch;
    }

    public ProgramNode falseBranch(){
        return falseBranch;
    }
}
