package dag.usi.ch;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.*;

public class ProgramNodeBuilder {

  private final Long2ObjectOpenHashMap<ProgramNode> pcToNode = new Long2ObjectOpenHashMap<>();
  private final List<ProgramNode> nodes = new ArrayList<>();

  public List<ProgramNode> nodes(){
    return nodes;
  }

  public ProgramNodeBuilder(List<CondInfo> condInfos) {
    List<IfNode> ifNodes = new ArrayList<>();
    for(CondInfo ci: condInfos){
      BranchNode trueBranch = new BranchNode(ci.trueBranch());
      BranchNode falseBranch = new BranchNode(ci.falseBranch());
      IfNode ifNode = new IfNode(ci, trueBranch, falseBranch);
      trueBranch.setPredecessor(ifNode);
      falseBranch.setPredecessor(ifNode);
      nodes.add(trueBranch);
      nodes.add(falseBranch);
      nodes.add(ifNode);
      ifNodes.add(ifNode);
    }
    // Maintain the nesting between the branches
    for (IfNode child : ifNodes) {
      IfNode parent = null;
      int smallestContainingRange = Integer.MAX_VALUE;
      CondInfo childCi = child.condInfo();
      for (IfNode candidate : ifNodes) {
        if (candidate == child) {
          continue;
        }
        // The candidate is valid only if it matches the same call chain
        // for child: main2f1 [Main.java 25] [bci: 17], main4main [Main.java 8] [bci: 19]
        // the candidate: main2f1 [Main.java 24] [bci: 10], main4main [Main.java 8] [bci: 19]
        // is a real candidate.
        // the candidate:
        // the candidate: main2f1 [Main.java 24] [bci: 10], main4main [Main.java 9] [bci: 19]
        // is not.
        int size = child.condInfo().cond().size();
        if(size != candidate.condInfo().cond().size()){
          continue;
        }
        if(!child.condInfo().cond().subList(1, size).equals(candidate.condInfo().cond().subList(1, size))){
          continue;
        }
        String childMethodName = child.condInfo().cond().getFirst().substring(0, child.condInfo().cond().getFirst().indexOf(" ["));
        String candidateMethodName = candidate.condInfo().cond().getFirst().substring(0, candidate.condInfo().cond().getFirst().indexOf(" ["));
        if(!childMethodName.equals(candidateMethodName)){
          continue;
        }

        if(candidate.condInfo().getLineNumber() >= child.condInfo().getLineNumber()){
          continue;
        }

        CondInfo candidateCi = candidate.condInfo();
        int childCondBci = childCi.getCondBci();
        int candidateCondBci = candidateCi.getCondBci();
        if(candidateCondBci > childCondBci){
          continue;
        }

        boolean contains = (candidateCi.truebci() <= childCondBci && childCondBci < candidateCi.truebciend()) ||
                (candidateCi.falsebci()<= childCondBci && childCondBci < candidateCi.falsebciend());
//        boolean contains =
//                candidateCi.truebci() <= childCi.truebci()
//                        && candidateCi.falsebci() >= childCi.falsebci();
        if (!contains) {
          continue;
        }
//        boolean same = candidateCi.truebci() == childCi.truebci() && candidateCi.falsebci() == childCi.falsebci();
//        if(same){
//          System.out.println("i am an idiot");
//        }
        int candidateRange = candidateCi.falsebci() - candidateCi.truebci();

        // Pick the closest enclosing conditional
        if (candidateRange < smallestContainingRange) {
          parent = candidate;
          smallestContainingRange = candidateRange;
        }
      }
      if(parent != null && parent.parent()==child){
        child.setParent(null);
        continue;
      }
      child.setParent(parent);
      if(parent != null){
        parent.addChild(child);
      }
    }
    detectCycles(ifNodes);
    System.out.println("aaaa");
  }


  public static void detectCycles(List<IfNode> roots) {
    Set<IfNode> visited = new HashSet<>();
    Set<IfNode> onStack = new HashSet<>();

    for (IfNode root : roots) {
      dfs(root, visited, onStack);
    }
  }

  private static void dfs(
          IfNode node,
          Set<IfNode> visited,
          Set<IfNode> onStack) {

    if (onStack.contains(node)) {
      throw new IllegalStateException("Cycle detected at " + node);
    }

    if (!visited.add(node)) {
      return;
    }

    onStack.add(node);

    for (ProgramNode child : node.children()) {
      if (child instanceof IfNode in) {
        dfs(in, visited, onStack);
      }
    }

    onStack.remove(node);
  }


  private static Map<ProgramNode, Integer> idk = new HashMap<>();
  private static ProgramNode findEncapsulatingConditional(IfNode ifNode, SourceMapping match){
//    if(idk.getOrDefault(ifNode, 0) >2){
//      System.out.println("robe");
//    }
//    idk.merge(ifNode, 1, Integer::sum);
    for(ProgramNode node: ifNode.children()) {
        if (Objects.requireNonNull(node) instanceof IfNode in) {
            // found a child node that better encapsulates the match.
            ProgramNode matchedNode = findEncapsulatingConditional(in, match);
            if(matchedNode != null){
              return matchedNode;
            }
        }
    }
    // no match in any of the children.
    // we might match with the ifnode itself
    String sp = match.sourcePosition().getFirst();
    CondInfo ci = ifNode.condInfo();
    int bci = Integer.parseInt(sp.substring(sp.lastIndexOf(" ") + 1, sp.length() - 1));
    // case 1: we match the condition itself.
    for (String condSp : ci.cond()) {
      if (condSp.equals(sp)) {
        return ifNode;
      }
    }

    // case 2: we match with the true branch
    BranchNode branchnode = (BranchNode) ifNode.trueBranch();
    for (String trueSp : ci.trueBranch()) {
      if (sp.substring(0, sp.indexOf(" [")).equals(trueSp.substring(0, trueSp.indexOf(" [")))) {
        // given that the compiler might remove portion of code
        // as long as the bytecode that was used to emit the binary code
        // comes from the true block, this should be correct.
        if(bci >= ci.truebci() && bci <= ci.truebciend()){
          return branchnode;
        }
//        if (bci < ci.truebci() || bci >= ci.falsebci()) {
//          continue;
//        }
//        return branchnode;
      }
    }
    // case 3: we match with the false branch
    String falseSp = ci.falseBranch().getFirst();
    if (sp.substring(0, sp.indexOf(" [")).equals(falseSp.substring(0, falseSp.indexOf(" [")))) {
       if(bci >= ci.falsebci() && bci <= ci.falsebciend()){
        return ifNode.falseBranch();
      }
    }
    return null;
  }

  public ProgramNode getNode(SourceMapping match, long pc) {
    if(pcToNode.containsKey(pc)){
      return pcToNode.get(pc);
    }
    List<IfNode> ifNodes = nodes.stream().filter(node -> node instanceof IfNode).map(node -> (IfNode) node).toList();
    List<BranchNode> branchNodes = nodes.stream().filter(node -> node instanceof BranchNode).map(node -> (BranchNode) node).toList();
    for (IfNode ifnode : ifNodes) {
          String sp = match.sourcePosition().getFirst();
          CondInfo ci = ifnode.condInfo();
          // case 1: we match the condition itself.
          for (String condSp : ci.cond()) {
            if (condSp.equals(sp)) {
              pcToNode.put(pc, ifnode);
              return ifnode;
            }
          }
    }
    for (BranchNode branchnode : branchNodes) {
        ProgramNode candidate = findEncapsulatingConditional(branchnode.predecessor(), match);
        if (candidate != null) {
          pcToNode.put(pc, candidate);
          return candidate;
        }
    }
    // case 4: we don't match any of the conditions
    // there was a node source position, but we couldn't match to a conditional
    pcToNode.put(pc, EmptyNode.getInstance());
    return EmptyNode.getInstance();
  }
}
