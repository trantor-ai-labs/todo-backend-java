package xyz.trantor.todo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON for the Todo-Backend contract, hand-rolled.
 *
 * This service has no dependencies on purpose: it is the fixed point of a migration case study,
 * so it has to build and run on whatever JDK is in front of it, years from now, with no network
 * and no resolvable artifacts. A JSON library is the one dependency it would otherwise need, and
 * the payloads here are flat objects of string/boolean/number — small enough to parse honestly.
 */
final class Json {

  static String escape(String s) {
    StringBuilder b = new StringBuilder();
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> b.append("\\\"");
        case '\\' -> b.append("\\\\");
        case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r");
        case '\t' -> b.append("\\t");
        default -> {
          if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
          else b.append(c);
        }
      }
    }
    return b.toString();
  }

  static String write(Object v) {
    if (v == null) return "null";
    if (v instanceof String s) return '"' + escape(s) + '"';
    if (v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof Double) {
      return String.valueOf(v);
    }
    if (v instanceof Map<?, ?> m) {
      StringBuilder b = new StringBuilder("{");
      boolean first = true;
      for (Map.Entry<?, ?> e : m.entrySet()) {
        if (!first) b.append(',');
        first = false;
        b.append('"').append(escape(String.valueOf(e.getKey()))).append("\":").append(write(e.getValue()));
      }
      return b.append('}').toString();
    }
    if (v instanceof List<?> l) {
      StringBuilder b = new StringBuilder("[");
      for (int i = 0; i < l.size(); i++) {
        if (i > 0) b.append(',');
        b.append(write(l.get(i)));
      }
      return b.append(']').toString();
    }
    throw new IllegalArgumentException("cannot serialise " + v.getClass());
  }

  /** Parses a flat JSON object. Nested objects and arrays are not part of this contract. */
  static Map<String, Object> readObject(String src) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (src == null) return out;
    P p = new P(src);
    p.ws();
    if (!p.eat('{')) return out;
    p.ws();
    if (p.eat('}')) return out;
    while (true) {
      p.ws();
      String key = p.string();
      p.ws();
      p.expect(':');
      p.ws();
      out.put(key, p.value());
      p.ws();
      if (p.eat(',')) continue;
      p.expect('}');
      return out;
    }
  }

  private static final class P {
    private final String s;
    private int i;

    P(String s) { this.s = s; }

    void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    boolean eat(char c) {
      if (i < s.length() && s.charAt(i) == c) { i++; return true; }
      return false;
    }

    void expect(char c) {
      if (!eat(c)) throw new IllegalArgumentException("expected '" + c + "' at " + i + " in " + s);
    }

    String string() {
      expect('"');
      StringBuilder b = new StringBuilder();
      while (i < s.length()) {
        char c = s.charAt(i++);
        if (c == '"') return b.toString();
        if (c == '\\') {
          char e = s.charAt(i++);
          switch (e) {
            case 'n' -> b.append('\n');
            case 'r' -> b.append('\r');
            case 't' -> b.append('\t');
            case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
            default -> b.append(e);
          }
        } else {
          b.append(c);
        }
      }
      throw new IllegalArgumentException("unterminated string in " + s);
    }

    Object value() {
      char c = s.charAt(i);
      if (c == '"') return string();
      if (c == '{') { int d = 0; int start = i; do { char x = s.charAt(i++); if (x=='{') d++; else if (x=='}') d--; } while (d > 0); return s.substring(start, i); }
      if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
      if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
      if (s.startsWith("null", i)) { i += 4; return null; }
      int start = i;
      while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
      String num = s.substring(start, i);
      if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
      return Integer.parseInt(num);
    }
  }

  private Json() {}
}
