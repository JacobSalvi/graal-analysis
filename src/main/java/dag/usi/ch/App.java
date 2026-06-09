package dag.usi.ch;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;


public class App {

    private static long computeBaseOffset(PerfInfo info, Method method) {
//        String baseAddress;
        int offsetInt = Integer.parseInt(info.offset().substring(2), 16);
        int count = 0;
        String instrOffset = null;

        for (Instr instr : method.instrs()) {
            if (count == offsetInt) {
                instrOffset = instr.offset();
                break;
            }
            count += instr.instr().size();
        }

        if (instrOffset == null) {
            throw new RuntimeException("no base_address could be found");
        }
        return info.pc() - Long.parseLong(instrOffset, 16);
    }


    private record Quad<A, B, C, D> (A a, B b, C c, D d){}

    private static Quad<List<Method>, Map<String, Integer>, Map<Integer, Method>, Integer> extractMethodsFromObjdump(Path objFile) throws IOException {
        List<Method> methods = new ArrayList<>();

        String currentMethod = "";
        List<Instr> mLines = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(objFile.toFile())), 1<<20);
        String line;

        Map<String, Integer> methodNameToId = new HashMap<>();
        Map<Integer, Method> idToMethod = new HashMap<>();
        int counter=0;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("_ZN")) {
                if (!currentMethod.isEmpty()) {
                    // remove (): from method name
                    String methodName = currentMethod.substring(0, currentMethod.length()-3);
                    if(!methodNameToId.containsKey(methodName)){
                        methodNameToId.put(methodName, counter++);
                    }
                    Method method = new Method(methodNameToId.get(methodName), mLines, 0);
                    idToMethod.put(methodNameToId.get(methodName), method);
                    methods.add(method);
                }
                currentMethod = line;
                mLines = new ArrayList<>();
                continue;
            }

            if(line.startsWith("  ")){
                String[] splitLine = line.split("\t");
                String instructionOffset = splitLine[0].substring(3, splitLine[0].length()-1);
                String ops = splitLine[1].trim();
                List<String> opsList = new ArrayList<>();
                String mnemonic = splitLine.length == 3 ? splitLine[2]: null;
                Collections.addAll(opsList, ops.split(" "));
                mLines.add(new Instr(instructionOffset, opsList, mnemonic));
            }

        }

        if (!currentMethod.isEmpty()) {
            String methodName = currentMethod.substring(0, currentMethod.length()-3);
            if(!methodNameToId.containsKey(methodName)){
                methodNameToId.put(methodName, counter++);
            }
            Method method = new Method(methodNameToId.get(methodName), mLines, 0);
            methods.add(method);
            idToMethod.put(methodNameToId.get(methodName), method);
        }
        return new Quad<>(methods, methodNameToId, idToMethod, counter);
    }

    private static int[] mapToObjdump(Method method, SourceMapping sourceMapping) {
        List<String> targetInstr = sourceMapping.bytes();

        int beg = -1;
        int end = -1;

        for (int i = 0; i < method.instrs().size(); i++) {
            List<String> currentInstrs = new ArrayList<>(method.instrs().get(i).instr());

            if (targetInstr.subList(0, Math.min(targetInstr.size(), currentInstrs.size()))
                    .equals(currentInstrs)) {

                int j = i + 1;

                while (currentInstrs.size() < targetInstr.size()) {
                    if (j >= method.instrs().size()) break;

                    currentInstrs.addAll(method.instrs().get(j).instr());
                    if(currentInstrs.size() > targetInstr.size()){
                        break;
                    }

                    if (!targetInstr.subList(0, currentInstrs.size()).equals(currentInstrs)) break;
                    j++;
                }

                if (targetInstr.equals(currentInstrs)) {
                    beg = i;
                    end = j - 1;
                    break;
                }
            }
        }

        return new int[]{beg, end};
    }

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
            String lastKey = lastAt.split(" ")[1];

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

    record Match(SourceMapping sm, int[] beg_end, Method m) {
    }

    private static Match perfToSourceMapping(int i, List<Match> matches) {
        for(Match match: matches){
            if(i >= match.beg_end()[0] && i <= match.beg_end()[1]){
                return match;
            }
        }
        return null;
    }

    private static List<CondInfo> adjustCondInfos(List<CondInfo> condInfos, List<Block> blocks){
        for(CondInfo ci: condInfos){
            for(Block b: blocks){
                if(ci.cond().get(0).equals(b.lines.get(0))){
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
        int i = 0;
        while ((line = br.readLine()) != null) {
            i++;
            if(line.contains("--------------------------------------")){
                if(currentCond.isEmpty() || currentTrue.isEmpty() || currentFalse.isEmpty()){
                    currentCond = new ArrayList<>();
                    currentTrue = new ArrayList<>();
                    currentFalse = new ArrayList<>();
                    continue;
                }
                int bcitrue = Integer.parseInt(currentTrue.get(0).substring(currentTrue.get(0).lastIndexOf(" ")+1, currentTrue.get(0).length()-1));
                int bcifalse = Integer.parseInt(currentFalse.get(0).substring(currentFalse.get(0).lastIndexOf(" ")+1, currentFalse.get(0).length()-1));
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
                currentTrue.add(line.substring(6));
                String subString = line.substring(6);
                Collections.addAll(currentTrue, subString.split(","));
            } else if(line.startsWith("false: ")){
                currentFalse.add(line.substring(7));
                String subString = line.substring(7);
                Collections.addAll(currentFalse, subString.split(","));
            }
        }
//        int bcitrue = Integer.parseInt(currentTrue.get(0).substring(currentTrue.get(0).lastIndexOf(" ")+1, currentTrue.get(0).length()-1));
//        int bcifalse = Integer.parseInt(currentFalse.get(0).substring(currentFalse.get(0).lastIndexOf(" ")+1, currentFalse.get(0).length()-1));
//        condInfos.add(new CondInfo(currentCond, currentTrue, bcitrue, bcifalse, currentFalse));
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

        long start = System.currentTimeMillis();
        var res = extractMethodsFromObjdump(objFile);
        System.out.printf("Extracting objdump info took %d\n", (System.currentTimeMillis()-start)/ 1000);
        List<Method> methods = res.a();
        start = System.currentTimeMillis();
        Map<Integer, List<SourceMapping>> funcToSourceMapping = loadGraalData(graal_output, res.b(), res.d());
        System.out.printf("Extracting graal data took %d\n", (System.currentTimeMillis()-start)/ 1000);


        Map<Method, List<Match>> methodToMatches = new HashMap<>();
        for(Method method: methods){
            methodToMatches.put(method, new ArrayList<>());
        }
        // matches between source mapping and objdump
        for (var sourceMapping : funcToSourceMapping.entrySet()) {
            List<Method> filteredMethods = methods.stream().filter(m -> m.nameId()  == sourceMapping.getKey()).toList();
            if (filteredMethods.isEmpty()) {
                continue;
            }
            Method method = filteredMethods.getFirst();
            for (SourceMapping sm : sourceMapping.getValue()) {
                int[] beg_end = mapToObjdump(method, sm);
                methodToMatches.get(method).add(new Match(sm, beg_end, method));
            }
        }
        System.out.println("Created method to source mapping");



//        AbstractPerfStream perfStream = new PerfStream(perfFolder, res.b());
        AbstractPerfStream perfStream = new PerfFileStream(perfFolder, res.b());
        Pair<Map<Long, PerfMatch>, Map<Long, LongCounter>> p = extracted(perfStream, methodToMatches, res.c());
        System.out.println("\n");
        System.out.println("Extracted perf data");
        List<CondInfo> condInfos = loadConditionMapping(conditionMappingFile);
        System.out.println("Loaded condition mapping");
//        List<Block> blocks = extractBlock(conditionMappingFile.getParent().resolve("loop_begin.txt"));
//        condInfos = adjustCondInfos(condInfos, blocks);
        printResult(condInfos, p.first(), p.second());
    }

    record PerfMatch(List<PerfInfo> infos, Match match){}
    record Pair<T, R> (T first, R second) {}

    private static Pair<Map<Long,PerfMatch>, Map<Long, LongCounter>> extracted(AbstractPerfStream perfstream,
                                                                            Map<Method, List<Match>> methodToMatches,
                                                                               Map<Integer, Method> idToMethod) {
        Map<PerfInfo, Match> perfLineToMatch = new HashMap<>();

        Match lastMatch = null;
        Long firstOffset = null;
        List<PerfInfo> infos = new ArrayList<>();
        Map<Long, PerfMatch> offsetToMatch = new HashMap<>();
        Map<Long, LongCounter> offsetToCount = new HashMap<>();
//        Map<PerfInfo, Method> infoToMethod = new HashMap<>();
        int counter = 0;
        while(perfstream.hasNext()){
            PerfInfo info = perfstream.next();
            if(info == null){
                continue;
            }
            if(info.pc() == 94520975156916L){
                counter++;
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

            Method method = idToMethod.get(info.nameId());
            if(method==null){
                continue;
            }
            if(method.baseOffset() == 0){
                method.setBaseOffset(computeBaseOffset(info, method));
            }
            String relativePc = Long.toHexString(info.pc() - method.baseOffset());
            Match match = null;
//            if(!perfLineToMatch.containsKey(info)){
                for (int i = 0; i < method.instrs().size(); i++) {
                    if (method.instrs().get(i).offset().equals(relativePc)) {
                        match = perfToSourceMapping(i, methodToMatches.get(method));
//                        if(match!=null){
//                            perfLineToMatch.put(info, match);
//                        }
                        break;
                    }
                }
//            }
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

                int a = 0;
                for (CondInfo ci : condInfos) {
                    String sp = e.getValue().match().sm().sourcePosition().getFirst();

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
}
