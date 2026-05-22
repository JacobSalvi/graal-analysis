package dag.usi.ch;

import java.util.List;

record SourceMapping(
        List<String> bytes,
        List<String> sourcePosition,
        int functionNameId,
        int beg,
        int end,
        int calleeId
) {
}
