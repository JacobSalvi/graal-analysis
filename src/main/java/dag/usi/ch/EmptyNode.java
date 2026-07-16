package dag.usi.ch;

public class EmptyNode extends ProgramNode{
    private EmptyNode(){
        super(null);
    }

    private static final EmptyNode INSTANCE = new EmptyNode();

    public static EmptyNode getInstance(){
        return INSTANCE;
    }
}
