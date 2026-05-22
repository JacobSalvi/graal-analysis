package dag.usi.ch;

import java.util.List;

public record Instr(String offset, List<String> instr, String mnemonic){}
