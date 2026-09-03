package dag.usi.ch;

import java.util.List;

public class BranchNode extends ProgramNode{
    private IfNode predecessor;

    public BranchNode(List<SourcePosition> stack){
        super(stack);
    }

    public void setPredecessor(IfNode predecessor){
        this.predecessor = predecessor;
    }

    public IfNode predecessor(){
        return predecessor;
    }
}
