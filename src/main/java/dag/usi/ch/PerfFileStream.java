package dag.usi.ch;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;

public class PerfFileStream extends AbstractPerfStream {

    private final Map<String, Integer> methodNameToId;
    private BufferedReader reader;
    private String next;

    public PerfFileStream(Path folderPath, Map<String, Integer> methodNameToId) throws IOException {
        Path filePath = folderPath.resolve("perf_clean");
        this.methodNameToId = methodNameToId;
        this.reader = new BufferedReader(new FileReader(filePath.toFile()));
    }

    @Override
    public boolean hasNext() {
        if (next != null) {
            return true;
        }

        if (reader == null) {
            return false;
        }

        try {
            next = reader.readLine();
            if (next != null) {
                return true;
            }

            reader.close();
            reader = null;
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PerfInfo next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        String line = next;
        next = null;

        if (!line.contains("_ZN")) {
            return null;
        }

        int i = 0;
        int len = line.length();

        while (i < len && line.charAt(i) == ' ') {
            i++;
        }

        int pcStart = i;
        while (i < len && line.charAt(i) != ' ') {
            i++;
        }
        int pcEnd = i;

        while (i < len && line.charAt(i) == ' ') {
            i++;
        }

        int symStart = i;
        while (i < len && line.charAt(i) != '+') {
            i++;
        }
        int symEnd = i;

        int offsetStart = i + 1;
        while (i < len && line.charAt(i) != ' ') {
            i++;
        }
        int offsetEnd = i;

        long pc = Long.parseLong(line.substring(pcStart, pcEnd), 16);
        String symbol = line.substring(symStart, symEnd);
        String offset = line.substring(offsetStart, offsetEnd);

        Integer nameId = methodNameToId.get(symbol);
        if (nameId == null) {
            nameId = 100;
        }

        return new PerfInfo(nameId, pc, offset);
    }

    @Override
    public void skip(int n) {
        for (int i = 0; i < n; i++) {
            next();
        }
    }

    @Override
    public void close() throws IOException {
        if (reader != null) {
            reader.close();
            reader = null;
        }
    }
}
