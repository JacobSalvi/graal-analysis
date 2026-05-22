package dag.usi.ch;

import javax.annotation.processing.Filer;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

class PerfStream implements Iterator<PerfInfo>, AutoCloseable {

    private final List<Path> perfFiles;
    private int currentFileIndex = 0;

    private Process process;
    private BufferedReader reader;

    private String next;
    private final Map<String, Integer> methodNameToId;

    public PerfStream(Path perfFolder, Map<String, Integer> methodNameToId) throws IOException {
        this.perfFiles = Files.list(perfFolder).toList();
        this.methodNameToId = methodNameToId;
        startNextProcess();
    }

    private void startNextProcess() throws IOException {
        if (currentFileIndex >= perfFiles.size()) {
            reader = null;
            process = null;
            return;
        }

        Path file = perfFiles.get(currentFileIndex++);

//        ProcessBuilder pb = new ProcessBuilder(
//                "perf", "script",
//                "--insn-trace",
//                "--no-demangle",
//                "--fields", "ip,sym,symoff",
//                "-i", file.toString()
//        );
//
//        pb.redirectErrorStream(true);
//        process = pb.start();

//        reader = new BufferedReader(
//                new InputStreamReader(process.getInputStream()),
//                1 << 20
//        );

        reader = new BufferedReader(new FileReader("/home/jacob/PHD/graal-ws/experiment/Control/ccc.txt"));
    }

//    @Override
//    public boolean hasNext() {
//        if (next != null) return true;
//
//        try {
//            while (true) {
//
//                if (reader == null) return false;
//
//                next = reader.readLine();
//
//                if (next != null) return true;
//
//                process.waitFor();
//                startNextProcess();
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }

    @Override
    public boolean hasNext() {
        if (next != null) return true;

        try {
            while (true) {

                if (reader == null) return false;

                next = reader.readLine();

                if (next != null) return true;
                if(next == null){
                    return false;
                }

//                process.waitFor();
//                startNextProcess();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
//    int robe = 0;
//    @Override
//    public boolean hasNext() {
//        String a = "56412592e688 _ZN15dag.usi.ch.Main4mainEJvP18java.lang.String[]+0xa8";
//        String b = "56412592e68a _ZN15dag.usi.ch.Main4mainEJvP18java.lang.String[]+0xaa";
//        if(next!=null){
//            return true;
//        }
//        if(robe == 0){
//            next = a;
//            robe++;
//            return true;
//        }
//        if(robe == 1){
//            next = b;
//            robe++;
//            return true;
//        }
//        return false;
//    }

    @Override
    public PerfInfo next() {
        if (!hasNext()) throw new NoSuchElementException();

        String line = next;
        next = null;

        int idx = 0;
        if(!line.contains("_ZN")){
            return null;
        }
        int i = idx + 1;
        int len = line.length();

        // skip whitespace
        while (i < len && line.charAt(i) == ' ') i++;

        // --- pc ---
        int pcStart = i;
        while (i < len && line.charAt(i) != ' ') i++;
        int pcEnd = i;

        // skip whitespace
        while (i < len && line.charAt(i) == ' ') i++;

        // --- symbol+offset ---
        int symStart = i;
        while (i < len && line.charAt(i) != '+') i++;
        int symEnd = i;

        int offsetStart = i+1;
        while(i<len && line.charAt(i) != ' ') i++;
        int offsetEnd = i;

        long pc = Long.parseLong( line.substring(pcStart, pcEnd), 16);
        String symbol = line.substring(symStart, symEnd);
        String offset = line.substring(offsetStart, offsetEnd);
        Integer name_id = methodNameToId.get(symbol);
        if(name_id==null){
            // this is mostly for debugging
            name_id= 100;
        }
        return new PerfInfo(name_id, pc, offset);
    }

    public void skip(int n){
        for(int i = 0; i <n; i++){
            next();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (process != null) process.destroy();
        } finally {
            if (reader != null) reader.close();
        }
    }
}
