package dag.usi.ch;
import java.util.List;

 record CondInfo(List<String> cond, List<String> trueBranch, int truebci, int falsebci, List<String> falseBranch) {
}
