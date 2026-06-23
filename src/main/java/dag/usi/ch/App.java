package dag.usi.ch;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;


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

    private static List<CondInfo> adjustCondInfos(List<CondInfo> condInfos, List<Block> blocks){
        for(CondInfo ci: condInfos){
            for(Block b: blocks){
                if(ci.cond().getFirst().equals(b.lines.getFirst())){
                    ci.setP(b.p());
                    ci.setUf(b.uf());
                }
            }
        }
        return condInfos;
    }

    private static List<CondInfo> loadConditionMapping(Path conditionMappingFile) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(conditionMappingFile.toFile()));
        String line;
        List<String> currentCond = new ArrayList<>();
        List<String> currentTrue = new ArrayList<>();
        List<String> currentFalse = new ArrayList<>();
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
                    continue;
                }
                int bcitrue = Integer.parseInt(currentTrue.getFirst().substring(currentTrue.getFirst().lastIndexOf(" ")+1, currentTrue.getFirst().length()-1));
                int bcifalse = Integer.parseInt(currentFalse.getFirst().substring(currentFalse.getFirst().lastIndexOf(" ")+1, currentFalse.getFirst().length()-1));
                condInfos.add(new CondInfo(currentCond, currentTrue, bcitrue, bcifalse, currentFalse, 1, 0));
                currentCond = new ArrayList<>();
                currentTrue = new ArrayList<>();
                currentFalse = new ArrayList<>();
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
            }
        }
        return condInfos;
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

//        AbstractPerfStream perfStream = new PerfStream(perfFolder, res.b());
        AbstractPerfStream perfStream = new PerfFileStream(perfFolder, methodNameToId);
        Pair<Map<Long, PerfMatch>, Map<Long, LongCounter>> p = extracted(perfStream, funcToSourceMapping);
        System.out.println("\n");
        System.out.println("Extracted perf data");
        List<CondInfo> condInfos = loadConditionMapping(conditionMappingFile);
        System.out.println("Loaded condition mapping");
//        List<Block> blocks = extractBlock(conditionMappingFile.getParent().resolve("loop_begin.txt"));
//        condInfos = adjustCondInfos(condInfos, blocks);
        printResult(condInfos, p.first(), p.second());
        outputIprof(condInfos, p.first(), p.second());
    }

    record PerfMatch(List<PerfInfo> infos, SourceMapping  match){}
    record Pair<T, R> (T first, R second) {}

    private static Pair<Map<Long,PerfMatch>, Map<Long, LongCounter>> extracted(AbstractPerfStream perfstream, Map<Integer, List<SourceMapping>> methodToSourceMappings) {

        SourceMapping lastMatch = null;
        Long firstOffset = null;
        List<PerfInfo> infos = new ArrayList<>();
        Map<Long, PerfMatch> offsetToMatch = new HashMap<>();
        Map<Long, LongCounter> offsetToCount = new HashMap<>();
//        Map<PerfInfo, Method> infoToMethod = new HashMap<>();
        while(perfstream.hasNext()){
            PerfInfo info = perfstream.next();
            if(info == null){
                continue;
            }
            // if we already have matched this sequence of instructions
            // increment the match counter
            if(offsetToMatch.containsKey(info.pc())){
                PerfMatch match = offsetToMatch.get(info.pc());
                offsetToCount.get(info.pc()).increment();
                // skip the following N lines in the block of instructions
                perfstream.skip(match.infos().size()-1);
                // should be correct to set last match to null
                lastMatch = null;
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
//            Match match = perfLineToMatch.get(info);
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
                PerfMatch m = new PerfMatch(infos, lastMatch);
                if(offsetToMatch.containsKey(firstOffset)){
                    offsetToCount.get(firstOffset).increment();
                }else{
                    offsetToMatch.put(firstOffset, m);
                    offsetToCount.put(firstOffset, new LongCounter(1L));
                }
                lastMatch=match;
                firstOffset = info.pc();
                infos = new ArrayList<>();
                infos.add(info);
            } else{
                infos.add(info);
            }
        }

        // last block of perf lines
        if(lastMatch==null){
            return new Pair<>(offsetToMatch, offsetToCount);
        }
        PerfMatch m = new PerfMatch(infos, lastMatch);
        if(offsetToMatch.containsKey(firstOffset)){
            offsetToCount.get(firstOffset).increment();
        }else{
            offsetToMatch.put(firstOffset, m);
            offsetToCount.put(firstOffset, new LongCounter(1L));
        }
        return new Pair<>(offsetToMatch, offsetToCount);
    }


    public record Block(List<String> lines, int p, int uf) {}


    public static List<Block> extractBlock(Path file) throws IOException {
        final Pattern END_PATTERN = Pattern.compile(".*\\[(\\d+),\\s*(\\d+)\\]\\s*$");
        List<Block> blocks = new ArrayList<>();


        try (BufferedReader reader = Files.newBufferedReader(file)) {

            List<String> currentBlock = new ArrayList<>();
            String line;

            while ((line = reader.readLine()) != null) {
                currentBlock.add(line);

                Matcher matcher = END_PATTERN.matcher(line);
                if (matcher.matches()) {
                    int uf = Integer.parseInt(matcher.group(1));
                    int p = Integer.parseInt(matcher.group(2));

                    blocks.add(
                        new Block(
                            new ArrayList<>(currentBlock),
                            uf,
                            p
                        )
                    );

                    currentBlock.clear();
                }
            }

            if (!currentBlock.isEmpty()) {
                throw new IllegalStateException(
                        "File ended with an incomplete block");
            }
        }
        return blocks;
    }

    private static void printResult(List<CondInfo> condInfos,
                                    Map<Long, PerfMatch> pcToMatch,
                                    Map<Long, LongCounter> pcToCount) {

        Path path = Paths.get("output.txt");

        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {

            writer.write("Mappings:");
            writer.newLine();

            for (var e : pcToMatch.entrySet()) {
                for (CondInfo ci : condInfos) {
                    String sp = e.getValue().match().sourcePosition().getFirst();

                    for (String condSp : ci.cond()) {
                        if (condSp.equals(sp)) {
                            long count = pcToCount.get(e.getKey()).count();
                            // compensate for peeling and unrolling.
                            count = ci.p()+ci.uf()*count;
                            writer.write(String.format(
                                    "Condition from %s executed %d times",
                                    condSp,
                                    count
                            ));
                            writer.newLine();
                            break;
                        }
                    }

                    for (String trueSp : ci.trueBranch()) {
                        if (sp.substring(0, sp.indexOf(" [")).equals(trueSp.substring(0, trueSp.indexOf(" [")))) {
                            int bci = Integer.parseInt(sp.substring(sp.lastIndexOf(" ")+1, sp.length()-1));
                            // given that the compiler might remove portion of code
                            // as long as the bytecode that was used to emit the binary code
                            // comes from the true block this should be correct.
                            if(bci < ci.truebci() || bci >= ci.falsebci()){
                                continue;
                            }
                            writer.write("Cond:");
                            writer.newLine();
                            for (String c : ci.cond()) {
                                writer.write("  ");
                                writer.write(c);
                                writer.newLine();
                            }

                            writer.write("  ");
                            writer.write("True branch taken:");
                            writer.newLine();
                            for (String c : ci.trueBranch()) {
                                writer.write("  ");
                                writer.write(c);
                                writer.newLine();
                            }

                            writer.write("  ");
                            writer.write(String.format(
                                    "It executed %d times",
                                    pcToCount.get(e.getKey()).count()
                            ));
                            writer.newLine();
                            break;
                        }
                    }

                    for (String falseSp : ci.falseBranch()) {
                        if (falseSp.equals(sp)) {
                            writer.write("Cond:");
                            writer.newLine();
                            for (String c : ci.cond()) {
                                writer.write("  ");
                                writer.write(c);
                                writer.newLine();
                            }

                            writer.write("  ");
                            writer.write("False branch taken:");
                            writer.newLine();
                            for (String c : ci.falseBranch()) {
                                writer.write("  ");
                                writer.write(c);
                                writer.newLine();
                            }

                            writer.write("  ");
                            writer.write(String.format(
                                    "It executed %d times",
                                    pcToCount.get(e.getKey()).count()
                            ));
                            writer.newLine();
                            break;
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private record BranchData(int bci, int id, long executions){}
    private record Triplet<A, B, C> (A a, B b, C c){}

    record ContextComponent(String name, int bci, String signature){}

    private static Pair<List<ContextComponent>, Long> getContext(Map<Long, PerfMatch> pcToMatch,  Map<Long, LongCounter> pcToCount, CondInfo ci, IProfBuilder ipf){
        // matches the condition being executed
        long executions = 0;
        List<ContextComponent> context = null;
    
        for (var e : pcToMatch.entrySet()) {
            String sp = e.getValue().match().sourcePosition().getFirst();

            for (String condSp : ci.cond()) {
                if (condSp.equals(sp)) {
                    long count = pcToCount.get(e.getKey()).count();
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
                    break;
                }
            }
        }
        return new Pair<>(context, executions);
    }

    private static void outputIprof(List<CondInfo> condInfos,
                                    Map<Long, PerfMatch> pcToMatch,
                                    Map<Long, LongCounter> pcToCount) {
        List<Triplet<List<ContextComponent>, BranchData, BranchData>> ctxToRecords = new ArrayList<>();
        IProfBuilder ipf = new IProfBuilder();

        for(CondInfo ci: condInfos){
            List<BranchData> trueBranches = new ArrayList<>();
            List<BranchData> falseBranches = new ArrayList<>();
            // this is useful for debugging.
                
            var ctxAndExecutions = getContext(pcToMatch, pcToCount, ci, ipf);
            List<ContextComponent> context = ctxAndExecutions.first();
            long executions = ctxAndExecutions.second();
            // matches the condition being executed
            for (var e : pcToMatch.entrySet()) {
                String sp = e.getValue().match().sourcePosition().getFirst();
                // matches a true successor being executed
                String trueSp = ci.trueBranch().getFirst();
                int bci = Integer.parseInt(sp.substring(sp.lastIndexOf(" ") + 1, sp.length() - 1));
                if (sp.substring(0, sp.indexOf(" [")).equals(trueSp.substring(0, trueSp.indexOf(" [")))) {
                    // given that the compiler might remove portion of code
                    // as long as the bytecode that was used to emit the binary code
                    // comes from the true block this should be correct.
                    if(bci >= ci.truebci() && bci < ci.falsebci()){
                        long count = pcToCount.get(e.getKey()).count();
                        trueBranches.add(new BranchData(bci, 0, count));
                        continue;
                    }
                }

                // matches a false successor being executed
                String falseSp = ci.falseBranch().getFirst();
                if (sp.substring(0, sp.indexOf(" [")).equals(falseSp.substring(0, falseSp.indexOf(" [")))) {
                    if(bci == ci.falsebci()){
                        long count = pcToCount.get(e.getKey()).count();
                        falseBranches.add(new BranchData(bci, 0, count));
                    }
                }
            }
            if(context==null){
                continue;
            }
            // assert executions+1 == trueBranch.executions()+falseBranch.executions();
            trueBranches = trueBranches.stream().filter(e -> e.executions()<= executions).toList();
            falseBranches = falseBranches.stream().filter(e -> e.executions()<= executions).toList();
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

        Path path = Paths.get("profile.iprof");
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
