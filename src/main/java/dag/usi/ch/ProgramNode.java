package dag.usi.ch;


import java.util.ArrayList;
import java.util.List;

public class ProgramNode {
  private ProgramNode parent;
  private List<ProgramNode> children = new ArrayList<>();
  private final List<String> stack;
  private int count = 0;

  ProgramNode(List<String> stack) {
    this.stack = stack;
  }

  public void addChild(ProgramNode child){
    children.add(child);
  }

  public List<ProgramNode> children(){
    return children;
  }

  public ProgramNode parent(){
    return parent;
  }

  public void setParent(ProgramNode parent){
    this.parent = parent;
  }

  public void increment(){
    count++;
  }

  public int count(){
    return count;
  }

  public void setCount(int c){
    count=c;
  }

  public List<String> stack(){
    return stack;
  }
}
