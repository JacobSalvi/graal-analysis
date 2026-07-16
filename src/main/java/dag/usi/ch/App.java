package dag.usi.ch;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.IntStream;


public class App {
    private static Map<Integer, List<SourceMapping>> loadGraalData(
            Path inputFile,
            Map<String, Integer> methodNameToId,
            int counter
    ) throws IOException {

        Map<Integer, List<SourceMapping>> functionToSourceMapping = new HashMap<>();

        BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(inputFile.toFile())),
                1 << 20
        );

        String line;

        while ((line = br.readLine()) != null) {

            if (line.isBlank()) continue;

            String[] parts = line.split(",", -1);
            if (parts.length < 4) continue;

            // range
            String[] range = parts[0].trim().split("-");
            int beg = Integer.parseInt(range[0].trim());
            int end = Integer.parseInt(range[1].trim());

            List<String> instrs = List.of(parts[1].trim().split(" "));

            // at... entries (everything except first 2 and last field)
            List<String> sourcePosition = new ArrayList<>();
            for (int i = 2; i < parts.length - 1; i++) {
                String s = parts[i].trim();
                if (!s.isEmpty()) {
                    sourcePosition.add(s);
                }
            }

            // method
            String name = parts[parts.length - 1].trim();

            methodNameToId.putIfAbsent(name, counter++);
            int nameId = methodNameToId.get(name);

            String lastAt = sourcePosition.getLast();
            String lastKey = lastAt.split(" ")[0];

            methodNameToId.putIfAbsent(lastKey, counter++);
            int calleeId = methodNameToId.get(lastKey);

            SourceMapping sm = new SourceMapping(
                    instrs,
                    sourcePosition,
                    nameId,
                    beg,
                    end,
                    calleeId
            );

            functionToSourceMapping
                    .computeIfAbsent(calleeId, k -> new ArrayList<>())
                    .add(sm);
        }

        return functionToSourceMapping;
    }

    private static List<CondInfo> loadConditionMapping(Path conditionMappingFile) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(conditionMappingFile.toFile()));
        String line;
        List<String> currentCond = new ArrayList<>();
        List<String> currentTrue = new ArrayList<>();
        List<String> currentFalse = new ArrayList<>();
        Integer currentEndBci = null;
        List<CondInfo> condInfos = new ArrayList<>();
//        cond: at sun.util.locale.provider.TimeZoneNameUtility$TimeZoneNameGetter.getName(TimeZoneNameUtility.java:269) [bci: 11]
//        true: at sun.util.locale.provider.TimeZoneNameUtility$TimeZoneNameGetter.getName(TimeZoneNameUtility.java:269) [bci: 11]
//        false: at sun.util.locale.provider.TimeZoneNameUtility$TimeZoneNameGetter.getName(TimeZoneNameUtility.java:269) [bci: 11]
//        --------------------------------------
        while ((line = br.readLine()) != null) {
            if(line.contains("--------------------------------------")){
                if(currentCond.isEmpty() || currentTrue.isEmpty() || currentFalse.isEmpty()){
                    currentCond = new ArrayList<>();
                    currentTrue = new ArrayList<>();
                    currentFalse = new ArrayList<>();
                    currentEndBci = null;
                    continue;
                }
                int bcitrue = Integer.parseInt(currentTrue.getFirst().substring(currentTrue.getFirst().lastIndexOf(" ")+1, currentTrue.getFirst().length()-1));
                int bcifalse = Integer.parseInt(currentFalse.getFirst().substring(currentFalse.getFirst().lastIndexOf(" ")+1, currentFalse.getFirst().length()-1));
                condInfos.add(new CondInfo(currentCond, currentTrue, bcitrue, bcifalse, currentFalse, currentEndBci, 1, 0));
                currentCond = new ArrayList<>();
                currentTrue = new ArrayList<>();
                currentFalse = new ArrayList<>();
                currentEndBci = null;
                continue;
            }
            if(line.startsWith("cond: ")){
                String subString = line.substring(6);
                Collections.addAll(currentCond, subString.split(","));
            } else if (line.startsWith("true: ")) {
                String subString = line.substring(6);
                Collections.addAll(currentTrue, subString.split(","));
            } else if(line.startsWith("false: ")){
                String subString = line.substring(7);
                Collections.addAll(currentFalse, subString.split(","));
            } else if(line.startsWith("end: ")){
                String subString = line.substring(5);
                currentEndBci = Integer.parseInt(subString);
            }
        }
        return condInfos.stream().distinct().toList();
    }

    private static void usage(){
        System.err.println("Use: ");
        System.err.println("    --objdump file, path ");
        System.err.println("    --sm, path ");
        System.err.println("    --perf, path ");
        System.err.println("    --condition mapping file, path ");
        System.exit(1);
    }


    public static void main(String[] args) throws Exception {
        if(args.length< 4){
            usage();
        }

        Path objFile = null;
        Path graal_output = null;
        Path perfFolder = null;
        Path conditionMappingFile = null;

        for (String arg : args) {
            String key = arg.split("=")[0];
            String value = arg.split("=")[1];

            Path path = Paths.get(value);
            switch (key) {
                case "--objdump":
                    objFile = path;
                    break;
                case "--sm":
                    graal_output = path;
                    break;
                case "--perf":
                    perfFolder = path;
                    break;
                case "--condition":
                    conditionMappingFile = path;
                    break;
                default:
                    usage();
            }
        }

        if (objFile == null || graal_output == null ||
              perfFolder == null || conditionMappingFile == null) {
            usage();
        }

        assert objFile != null;
        assert graal_output != null;
        assert perfFolder != null;
        assert conditionMappingFile != null;
        long start = System.currentTimeMillis();
        System.out.printf("Extracting objdump info took %d\n", (System.currentTimeMillis()-start)/ 1000);
        start = System.currentTimeMillis();
        Map<String, Integer> methodNameToId = new HashMap<>();
        Map<Integer, List<SourceMapping>> funcToSourceMapping = loadGraalData(graal_output, methodNameToId, 0);
        System.out.printf("Extracting graal data took %d\n", (System.currentTimeMillis()-start)/ 1000);
        System.out.println("Created method to source mapping");

        List<CondInfo> condInfos = loadConditionMapping(conditionMappingFile);
        ProgramNodeBuilder pnb = new ProgramNodeBuilder(condInfos);
        List<ProgramNode> nodes = buildNodeSequence(new PerfFileStream(perfFolder, methodNameToId), funcToSourceMapping, pnb);

        computeNodeCount(nodes);
        nodesToIprof(pnb.nodes());

    }

    private static void computeNodeCount(List<ProgramNode> nodes){
        IfNode lastCond = null;
        nodes = nodes.stream().filter(e->!(e instanceof EmptyNode)).toList();
        // avoid sequences of repeated values
        List<ProgramNode> finalNodes = nodes;
        nodes = IntStream.range(0, nodes.size()).filter(i -> i == 0 || !Objects.equals(finalNodes.get(i), finalNodes.get(i - 1))).mapToObj(nodes::get).toList();
        for(ProgramNode node: nodes){
            switch(node){
                // we are encountering a new condition.
                case IfNode in -> {
                    lastCond = in;
                    in.increment();
                }
                case BranchNode bn -> {
                    assert lastCond != null;
                    if(lastCond.equals(bn.predecessor())){
                        bn.increment();
                    }
//                     // cond -> trueBranch|falseBranch
//                     // we can increment the count for the branch
//                      if(lastBranch == null){
//                         lastBranch = bn;
// //                        bn.increment();
//                     }else{
//                         // cond -> trueBranch -> falseBranch
//                         // can only happen if the falseBranch is also the fall-though for the condition
//                         // do not increment the count for the branch.
//                         continue;
//                     }
                }
                case EmptyNode ignored -> {}
                default -> throw new IllegalStateException("Unexpected value: " + node);
            }
        }
        // adjust if counts.
        // Sometimes a successor node can be moved before the condition
        // preventing from correctly counting
        // example: bci 10 loop header -> bci 13 true branch -> bci 32 backedge|false branch
        // the compilation rewrote it as 13 10 32
        // then the true branch appears before the condition, and is not counted.
        for(IfNode n: nodes.stream().filter(e->e instanceof IfNode).map(e->(IfNode)e).toList()){
            if(n.trueBranch().count()==0){
                int m = n.children().stream().map(ProgramNode::count).max(Integer::compareTo).orElse(0);
                n.trueBranch().setCount(m);
            }
        }

        // adjust the count of a branch node
        // a branch node might also be the fall through case for the condition
        // in such cases it might be counted too many times.
        // to fix this situation, we ensure that the sum false and true branch should be the same as the condition count
        for(IfNode n: nodes.stream().filter(e->e instanceof IfNode).map(e->(IfNode)e).toList()){
            int cond_count = n.count();
            int true_count = n.trueBranch().count();
            int false_count= n.falseBranch().count();
            // cond executed 1000
            // true_branch executed 1000|999 times
            // false_branch executed n | 0 < n < 1000 times
            // then the true_branch count should be adjusted to true count - false count
            if(true_count == 0 || false_count == 0){
                continue;
            }
            if(Math.abs(cond_count-true_count)<= 1){
                n.trueBranch().setCount(n.trueBranch().count()-n.falseBranch().count());
            }
            // simmetrically the opposite might have happened
            if(Math.abs(cond_count-false_count)<= 1){
                n.falseBranch().setCount(n.falseBranch().count()-n.trueBranch().count());
            }
        }
    }

    record PerfMatch(List<PerfInfo> infos, SourceMapping  match){}
    record Pair<T, R> (T first, R second) {}


    private static List<ProgramNode> buildNodeSequence(AbstractPerfStream perfstream, Map<Integer, List<SourceMapping>> methodToSourceMappings, ProgramNodeBuilder pnb){
        // Build a List of nodes corresponding to the sequence of nodes executed by the program.
        SourceMapping lastMatch = null;
        Long firstOffset = null;
        List<PerfInfo> infos = new ArrayList<>();
        List<ProgramNode> nodes = new ArrayList<>();
        Map<Long, PerfMatch> pcToNode = new HashMap<>();
        
        while(perfstream.hasNext()){
            PerfInfo info = perfstream.next();
            if(info == null){
                continue;
            }
            if(info.nameId() == -1){
                continue;
            }
            // cache to speed up execution
            if(pcToNode.containsKey(info.pc())){
                PerfMatch pm = pcToNode.get(info.pc());
                ProgramNode pn = pnb.getNode(pm.match(), info.pc());
                nodes.add(pn);
                perfstream.skip(pm.infos().size()-1);
                lastMatch=null;
                continue;
            }


            int offset = Integer.valueOf(info.offset().substring(2), 16);
            SourceMapping match = null;
            for(SourceMapping sm: methodToSourceMappings.get(info.nameId())){
                if(sm.beg() == offset){
                    match = sm;
                    break;
                }
            }
            // if match is different to last match then we are mapping to a different line
            // These three instructions match to a single condition in the original java
            // aec9f:	90                   	nop
            // aeca0:	80 f8 04             	cmp    $0x4,%al
            // aeca3:	0f 84 a5 00 00 00    	je     aed4e <__svm_code_section@@Base+0x1ed4e>
            if(match == null){
                continue;
            }
            if(lastMatch == null){
                lastMatch = match;
                firstOffset = info.pc();
                infos = new ArrayList<>();
                infos.add(info);
                continue;
            }
            if(!match.equals(lastMatch)){
                ProgramNode pn = pnb.getNode(lastMatch, firstOffset);
                if(!pcToNode.containsKey(firstOffset)){
                    pcToNode.put(firstOffset, new PerfMatch(infos, lastMatch));
                }
                nodes.add(pn);
                lastMatch=match;
                firstOffset = info.pc();
                infos = new ArrayList<>();
                infos.add(info);
            } else{
                infos.add(info);
            }
        }

        // last block
        if(lastMatch==null){
            return nodes;
        }
        if(!pcToNode.containsKey(firstOffset)){
            ProgramNode pn = pnb.getNode(lastMatch, firstOffset);
            nodes.add(pn);
        }
        return nodes;
    }


    public record Block(List<String> lines, int p, int uf) {}


    private record BranchData(int bci, int id, long executions){}
    private record Triplet<A, B, C> (A a, B b, C c){}

    record ContextComponent(String name, int bci, String signature){}


    private static Pair<List<ContextComponent>, Long> getContext(IfNode in, IProfBuilder ipf){
        // matches the condition being executed
        long executions;
        List<ContextComponent> context;
        CondInfo ci = in.condInfo();

        long count = in.count();
        // compensate for peeling and unrolling.
        count = ci.p()+ci.uf()*count;
        executions = count;
        List<ContextComponent> contextComponents = new ArrayList<>();
        for (String c : ci.cond()) {
            String cName = c.substring(0, c.indexOf(" ["));
            int bciConditional = Integer.parseInt(c.substring(c.lastIndexOf(" ")+1, c.length()-1));
            // take this part [([Ljava/lang/String;)V]
            String signature = c.split(" ")[1];
            signature = signature.substring(1, signature.length()-1);
            ipf.addTypes(signature);
            contextComponents.add(new ContextComponent(cName, bciConditional, signature));
        }
        context = contextComponents;
        return new Pair<>(context, executions);
    }
    
    private static void nodesToIprof(List<ProgramNode> nodes) {
        List<Triplet<List<ContextComponent>, BranchData, BranchData>> ctxToRecords = new ArrayList<>();
        IProfBuilder ipf = new IProfBuilder();
        for(ProgramNode pn: nodes){
            List<ContextComponent> context;
            List<BranchData> trueBranches = new ArrayList<>();
            List<BranchData> falseBranches = new ArrayList<>();
            long executions;
            switch(pn){
                case IfNode in -> {
                    var ctxAndExecutions = getContext(in, ipf);
                    context = ctxAndExecutions.first();
                    executions = ctxAndExecutions.second();
                    ProgramNode trueBranchNode = in.trueBranch();
                    ProgramNode falseNode = in.falseBranch();
                    String trueSp = trueBranchNode.stack().getFirst();
                    int bci = Integer.parseInt(trueSp.substring(trueSp.lastIndexOf(" ") + 1, trueSp.length() - 1));
                    trueBranches.add(new BranchData(bci, 0, trueBranchNode.count()));
                    String falseSp= falseNode.stack().getFirst();
                    bci = Integer.parseInt(falseSp.substring(falseSp.lastIndexOf(" ") + 1, falseSp.length() - 1));
                    falseBranches.add(new BranchData(bci, 1, falseNode.count()));
                }
                default -> {
                    continue;
                }
            }
            
            // this is useful for debugging.
                
            if(context==null){
                continue;
            }
            // assert executions+1 == trueBranch.executions()+falseBranch.executions();
//            trueBranches = trueBranches.stream().filter(e -> e.executions()<= executions).toList();
//            falseBranches = falseBranches.stream().filter(e -> e.executions()<= executions).toList();
            long bestDiff = Long.MAX_VALUE;
            BranchData trueBranch = null;
            BranchData falseBranch = null;
            for (BranchData ba : trueBranches) {
                for (BranchData bb : falseBranches) {
                    long sum = ba.executions() + bb.executions();
                    long diff = Math.abs(executions- sum);

                    if (diff < bestDiff) {
                        bestDiff = diff;
                        trueBranch = ba;
                        falseBranch = bb;
                    }
                }
            }
            ctxToRecords.add(new Triplet<>(context, trueBranch, falseBranch));
        }

        List<IProfMethod> iprofMethods = ipf.createMethods(ctxToRecords.stream().map(Triplet::a).toList());

        Path path = Paths.get("profile_2.iprof");
        //  {
        //   "ctx": "28755:10<28754:19",
        //   "records": [
        //     13,
        //     0,
        //     1000000,
        //     35,
        //     1,
        //     1
        //   ]
        // },
        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
                writer.write("{");
                writer.write("\"version\": \"1.1.0\",");
                // write the "types" section
                writer.write("\"types\": [");
                List<String> records = new ArrayList<>();
                for(var e: ipf.getTypeToId().entrySet()){
                    String type = e.getKey();
                    long id = e.getValue();
                    StringBuilder sb = new StringBuilder();
                    sb.append("{");
                    sb.append("\"id\":");
                    sb.append(id);
                    sb.append(",");
                    sb.append("\"name\":");
                    sb.append("\"");
                    sb.append(type);
                    sb.append("\"");
                    sb.append("}");
                    records.add(sb.toString());
                }
                writer.write(String.join(",", records));
                writer.write("]");
                writer.write(",");

                // write methods section 
                writer.write("\"methods\": [");
                records = new ArrayList<>();
                for(IProfMethod ipm: iprofMethods){
                    // Pair<String, String> names = demangle(e.name());
                    StringBuilder sb = new StringBuilder();
                    sb.append("{");
                    sb.append("\"id\":");
                    sb.append(ipm.id());
                    sb.append(",");
                    sb.append("\"name\":");
                    sb.append("\""+ipm.name()+"\"");
                    sb.append(",");
                    sb.append("\"signature\":");
                    sb.append(ipm.signature());
                    sb.append("}");
                    records.add(sb.toString());
                }
                writer.write(String.join(",", records));
                writer.write("]");
                writer.write(",");
                

                // write conditional_profiles section
                writer.write("\"conditionalProfiles\": [");
                records = new ArrayList<>();
                for(var e: ctxToRecords){
                    BranchData trueBranch = e.b();
                    BranchData falseBranch = e.c();
                    // String context = String.join("<", e.a().stream().map(c -> c.name).toList());
                    String context = ipf.createContext(e.a());
                    if(trueBranch == null || falseBranch == null){
                        continue;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("{");
                    sb.append("\"ctx\":");
                    sb.append("\""+context+"\"");
                    sb.append(",");
                    sb.append("\"records\":[");
                    sb.append(String.format("%d,%d,%d,", trueBranch.bci, trueBranch.id, trueBranch.executions));
                    sb.append(String.format("%d,%d,%d", falseBranch.bci, falseBranch.id, falseBranch.executions));
                    sb.append("]");
                    sb.append("}");
                    records.add(sb.toString());
                }
                writer.write(String.join(",", records));
                writer.write("]");
                writer.write("}");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
