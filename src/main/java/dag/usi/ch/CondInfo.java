package dag.usi.ch;
import java.util.List;
import java.util.Objects;

public class CondInfo {
 private final List<String> cond;
 private final List<String> trueBranch;
 private final int truebci;
 private final int falsebci;
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

 public Integer endbci(){
  return endbci;
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

}
