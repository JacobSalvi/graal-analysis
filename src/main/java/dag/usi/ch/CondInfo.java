package dag.usi.ch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CondInfo {
 private static Map<String, Integer> nameToId = new HashMap<>();
 private static int currentId = 0;

 private final List<String> cond;
 private final List<String> trueBranch;
 private final int lineNumber;
 private final int condBci;
 private final String methodname;
 private final int methodid;
 private final int truebci;
 private final int truebciend;
 private final int falsebci;
 private final int falsebciend;
 private final List<String> falseBranch;
 private final Integer endbci;
 private int uf;
 private int p;

 public CondInfo(List<String> cond, List<String> trueBranch, int truebci, int falsebci, List<String> falseBranch, Integer end, int uf, int p) {
  this.cond = cond;
  this.trueBranch = trueBranch;
  this.truebci = truebci;
  this.falsebci = falsebci;
  this.falseBranch = falseBranch;
  this.endbci = end;
  this.uf = uf;
  this.p = p;
  this.condBci = Integer.parseInt(cond.getFirst().substring(cond.getFirst().lastIndexOf(" ") + 1, cond.getFirst().length() - 1));
  this.lineNumber = Integer.parseInt(cond.getFirst().split(" ")[3].substring(0,cond.getFirst().split(" ")[3].length()-1));
  this.methodname = cond.getFirst().substring(0, cond.getFirst().indexOf(" ["));
  this.methodid = nameToId.getOrDefault(this.methodname, currentId++);
  // special case: Sometimes the jvm emits a conditional for certain bytecodes
  // for example: getfield get some sort of null check. But in the source code there is no cond.
  if(this.condBci == truebci && this.condBci == falsebci){
   this.truebciend = this.falsebciend = this.condBci;
   return;
  }
  if(truebci <= falsebci){
    this.truebciend = falsebci-1>=truebci? falsebci-1: falsebci;
    this.falsebciend = end == null ? this.falsebci: end;
  }else{
    this.truebciend = end == null? this.truebci: end;
    this.falsebciend = truebci-1>=falsebci? truebci-1: truebci;
  }
 }

 public List<String> cond() {
  return cond;
 }

 public List<String> trueBranch() {
  return trueBranch;
 }

 public int truebci() {
  return truebci;
 }

 public int falsebci() {
  return falsebci;
 }

 public List<String> falseBranch() {
  return falseBranch;
 }

 public int uf() {
  return uf;
 }

 public int p() {
  return p;
 }

 public void setUf(int uf){
  this.uf=uf;
 }

 public void setP(int p){
  this.p=p;
 }

 public Integer _r(){
  return endbci;
 }

 public int truebciend(){
  return this.truebciend;
 }

 public int falsebciend(){
  return this.falsebciend;
 }

 public boolean equals(Object o) {
  if (this == o) return true;
  if (!(o instanceof CondInfo ci)) return false;

  return truebci == ci.truebci
          && falsebci == ci.falsebci
          && Objects.equals(cond, ci.cond)
          && Objects.equals(trueBranch, ci.trueBranch)
          && Objects.equals(falseBranch, ci.falseBranch)
          && endbci == ci.endbci;
 }

 @Override
 public int hashCode() {
  return Objects.hash(cond, trueBranch, truebci, falsebci, falseBranch);
 }

    public int getLineNumber() {
        return lineNumber;
    }
    public int getCondBci() {
        return condBci;
    }
    public String getMethodname() {
        return methodname;
    }

    public int getMethodid() {
        return methodid;
    }
}
