package dag.usi.ch;
import java.util.List;

public class CondInfo {
 private final List<String> cond;
 private final List<String> trueBranch;
 private final int truebci;
 private final int falsebci;
 private final List<String> falseBranch;
 private int uf;
 private int p;

 public CondInfo(List<String> cond, List<String> trueBranch, int truebci, int falsebci, List<String> falseBranch, int uf, int p) {
  this.cond = cond;
  this.trueBranch = trueBranch;
  this.truebci = truebci;
  this.falsebci = falsebci;
  this.falseBranch = falseBranch;
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
}
