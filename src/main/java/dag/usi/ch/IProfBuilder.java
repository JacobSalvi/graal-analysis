package dag.usi.ch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dag.usi.ch.App.ContextComponent;
import dag.usi.ch.App.Pair;

public class IProfBuilder {
  private long nextId = 0;
  private final Map<String, Long> typeToId = new HashMap<>();
  private final Map<String, Long> methodToId= new HashMap<>();

  public Map<String, Long> getTypeToId() {
    return typeToId;
  }

  public void addTypes(String signature) {
    List<String> parameters = standardizeSignature(signature);
    for(String p: parameters){
      typeToId.computeIfAbsent(p, k -> nextId++);
    }
  }

  private static Pair<String, String> demangle(String mangledName) {
    // _ZN15dag.usi.ch.Main4mainEJvP18java.lang.String[]
    int classLength = 0;
    int start = 0;
    for (int i = 3; i < mangledName.length(); i++) {
      char c = mangledName.charAt(i);
      if (Character.isDigit(c)) {
        classLength = classLength * 10 + c - '0';
      } else {
        start = i;
        break;
      }
    }
    String qualifiedName = mangledName.substring(start, start + classLength);
    int nameLength = 0;
    for (int i = start + classLength; i < mangledName.length(); i++) {
      char c = mangledName.charAt(i);
      if (Character.isDigit(c)) {
        nameLength = nameLength * 10 + c - '0';
      } else {
        start = i;
        break;
      }
    }
    String name = mangledName.substring(start, start + nameLength);

    return new Pair<>(qualifiedName, name);
  }

  private List<String> standardizeSignature(String signature){
    List<String> result = new ArrayList<>();
    int i = 0;
    while (i < signature.length()) {
      char c = signature.charAt(i);

      if (c == '(' || c == ')') {
        i++;
        continue;
      }

      int start = i;

      // Consume array dimensions
      int arr_size = 0;
      while (i < signature.length() && signature.charAt(i) == '[') {
        arr_size++;
        i++;
      }

      if (i >= signature.length()) {
        break;
      }

      c = signature.charAt(i);

      if (c == 'L') {
        int end = signature.indexOf(';', i);
        if (end < 0) {
          throw new IllegalArgumentException(
              "Invalid descriptor: " + signature);
        }
        i = end + 1;
      } else {
        // Primitive or void
        i++;
      }

      String type = signature.substring(start, i);
      switch (type) {
        case "V" -> type = "void";
        case "Z" -> type = "boolean";
        case "B" -> type = "byte";
        case "C" -> type = "char";
        case "S" -> type = "short";
        case "I" -> type = "int";
        case "J" -> type = "long";
        case "F" -> type = "float";
        case "D" -> type = "double";
      }

      // adjust Ljava.lang.String; -> java.lang.String
      if(type.contains("L")){
        type = type.replace("/", ".");
      }
      if (arr_size != 0 && type.startsWith("L")) {
        type = type.substring(1, type.length() - 1);
      }

      result.add(type);
    }
    return result;
  }

  public List<IProfMethod> createMethods(List<List<ContextComponent>> ctxs) {
    List<IProfMethod> result = new ArrayList<>();
    long i = 0;
    for (var l : ctxs) {
      for (ContextComponent component : l) {
        Pair<String, String> names = demangle(component.name());
        String rawSignature = component.signature();
        List<String> parameters = standardizeSignature(rawSignature);
        List<Long> signature = new ArrayList<>();
        long id = typeToId.computeIfAbsent(names.first(), k -> nextId++);
        // the format is [class, return type, arguments...]
        signature.add(id);
        signature.add(typeToId.computeIfAbsent(parameters.getLast(), k -> nextId++));
        for(int j = 0; j<parameters.size()-1;j++){
          String p = parameters.get(j);
          signature.add(typeToId.computeIfAbsent(p, k -> nextId++));
        }
        if(methodToId.containsKey(names.second()+rawSignature)){
          continue;
        }
        result.add(new IProfMethod(i, names.second(), signature));
        methodToId.put(names.second()+rawSignature, i);
        i++;
      }
    }
    return result;
  }

  public String createContext(List<ContextComponent> components){
    return components.stream().map(component -> {
        Pair<String, String> names = demangle(component.name());
        long id = methodToId.get(names.second()+component.signature());
        return String.format("%d:%d", id, component.bci());
    }).collect(Collectors.joining("<"));
  }
}
