package tollbooth.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ============================================================================
 *  FILE    : Json.java
 *  PACKAGE : tollbooth.web
 * ----------------------------------------------------------------------------
 *  A very small JSON writer and parser written with the JDK only.
 *
 *  WHY IS THIS CLASS NEEDED ?
 *  The web front end talks to the server in JSON. Libraries like Jackson or
 *  Gson would have to be downloaded from the internet, and this project must
 *  stay "Core Java only, no external framework". So this class provides the
 *  four things the application needs :
 *
 *      Json.obj().put("ok", true)            -> { "ok" : true }
 *      Json.arr().add("A").add("B")          -> [ "A", "B" ]
 *      Json.parse(text)                      -> Map / List / String / Double
 *      Json.escape(text)                     -> safe text inside JSON
 *
 *  The parser is a small recursive descent parser, which is the classic way of
 *  writing a parser : every method reads one part of the grammar and calls the
 *  next one.
 * ============================================================================
 */
public final class Json {

    /** Maximum nesting depth, so a bomb style input cannot hang the server. */
    private static final int MAX_DEPTH = 30;

    /** Utility class : no objects are needed. */
    private Json() {
    }

    // ========================================================================
    //  WRITING JSON
    // ========================================================================

    /** A JSON object builder : the map keeps the insertion order. */
    public static class JsonObject {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public JsonObject put(String key, Object value) {
            values.put(key, value);
            return this;
        }

        /** Adds the value only when it is not null (keeps the JSON small). */
        public JsonObject putIfNotNull(String key, Object value) {
            if (value != null) {
                values.put(key, value);
            }
            return this;
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        @Override
        public String toString() {
            StringBuilder text = new StringBuilder();
            writeValue(values, text, 0);
            return text.toString();
        }
    }

    /** A JSON array builder. */
    public static class JsonArray {
        private final List<Object> values = new ArrayList<>();

        public JsonArray add(Object value) {
            values.add(value);
            return this;
        }

        public int size() {
            return values.size();
        }

        @Override
        public String toString() {
            StringBuilder text = new StringBuilder();
            writeValue(values, text, 0);
            return text.toString();
        }
    }

    public static JsonObject obj() {
        return new JsonObject();
    }

    public static JsonArray arr() {
        return new JsonArray();
    }

    /** Writes any supported value as JSON text. */
    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder out, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            escapeInto((String) value, out);
        } else if (value instanceof Boolean) {
            out.append(value.toString());
        } else if (value instanceof Number) {
            out.append(formatNumber((Number) value));
        } else if (value instanceof JsonObject) {
            writeValue(((JsonObject) value).values, out, depth);
        } else if (value instanceof JsonArray) {
            writeValue(((JsonArray) value).values, out, depth);
        } else if (value instanceof Map) {
            writeObject((Map<String, Object>) value, out, depth);
        } else if (value instanceof Iterable) {
            writeArray((Iterable<Object>) value, out, depth);
        } else {
            escapeInto(value.toString(), out);      // last resort : print as text
        }
    }

    private static void writeObject(Map<String, Object> map, StringBuilder out, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("JSON is nested too deeply");
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            escapeInto(entry.getKey(), out);
            out.append(':');
            writeValue(entry.getValue(), out, depth + 1);
        }
        out.append('}');
    }

    private static void writeArray(Iterable<Object> list, StringBuilder out, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("JSON is nested too deeply");
        }
        out.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeValue(item, out, depth + 1);
        }
        out.append(']');
    }

    /** Money friendly number format : 45.0 is written as 45, 45.5 as 45.5 */
    private static String formatNumber(Number number) {
        if (number instanceof Double || number instanceof Float) {
            double value = number.doubleValue();
            if (!Double.isFinite(value)) {
                return "0";                       // JSON has no NaN / Infinity
            }
            if (value == Math.rint(value) && Math.abs(value) < 1e15) {
                return String.valueOf((long) value);
            }
            return String.format(Locale.US, "%.2f", value);
        }
        return number.toString();
    }

    /** Escapes a text value and appends the quotes. */
    public static String escape(String text) {
        StringBuilder out = new StringBuilder();
        escapeInto(text, out);
        return out.toString();
    }

    private static void escapeInto(String text, StringBuilder out) {
        out.append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                case '\b': out.append("\\b");  break;
                case '\f': out.append("\\f");  break;
                default:
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
            }
        }
        out.append('"');
    }

    // ========================================================================
    //  READING JSON
    // ========================================================================

    /**
     * Parses JSON text into plain Java objects :
     * object -> LinkedHashMap, array -> ArrayList, number -> Double / Long,
     * text -> String, true/false -> Boolean, null -> null.
     */
    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("JSON text is null");
        }
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Unexpected text after the JSON value");
        }
        return value;
    }

    /** Convenience : parse an object body, or throw a clear error. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("A JSON object was expected");
        }
        return (Map<String, Object>) value;
    }

    /** Reads a text field of a parsed object (null safe). */
    public static String text(Map<String, Object> object, String key) {
        Object value = object.get(key);
        return (value == null) ? null : value.toString();
    }

    /** Reads a number field of a parsed object (null safe). */
    public static double number(Map<String, Object> object, String key, double defaultValue) {
        Object value = object.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /** Reads a boolean field of a parsed object (null safe). */
    public static boolean flag(Map<String, Object> object, String key, boolean defaultValue) {
        Object value = object.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    /** The internal recursive descent parser. */
    private static final class Parser {
        private final String text;
        private int position;

        Parser(String text) {
            this.text = text;
            this.position = 0;
        }

        boolean atEnd() {
            return position >= text.length();
        }

        void skipWhitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw new IllegalArgumentException("JSON is nested too deeply");
            }
            skipWhitespace();
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char character = text.charAt(position);
            switch (character) {
                case '{': return readObject(depth);
                case '[': return readArray(depth);
                case '"': return readString();
                case 't': expect("true");  return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null");  return null;
                default:  return readNumber();
            }
        }

        private Map<String, Object> readObject(int depth) {
            Map<String, Object> map = new LinkedHashMap<>();
            position++;                                     // skip '{'
            skipWhitespace();
            if (!atEnd() && text.charAt(position) == '}') {
                position++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                if (atEnd() || text.charAt(position) != ':') {
                    throw new IllegalArgumentException("':' expected in the JSON object");
                }
                position++;
                map.put(key, readValue(depth + 1));
                skipWhitespace();
                if (atEnd()) {
                    throw new IllegalArgumentException("Unclosed JSON object");
                }
                char next = text.charAt(position++);
                if (next == '}') {
                    return map;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("',' expected in the JSON object");
                }
            }
        }

        private List<Object> readArray(int depth) {
            List<Object> list = new ArrayList<>();
            position++;                                     // skip '['
            skipWhitespace();
            if (!atEnd() && text.charAt(position) == ']') {
                position++;
                return list;
            }
            while (true) {
                list.add(readValue(depth + 1));
                skipWhitespace();
                if (atEnd()) {
                    throw new IllegalArgumentException("Unclosed JSON array");
                }
                char next = text.charAt(position++);
                if (next == ']') {
                    return list;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("',' expected in the JSON array");
                }
            }
        }

        private String readString() {
            if (atEnd() || text.charAt(position) != '"') {
                throw new IllegalArgumentException("A JSON text value was expected");
            }
            position++;
            StringBuilder value = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new IllegalArgumentException("Unclosed JSON text value");
                }
                char character = text.charAt(position++);
                if (character == '"') {
                    return value.toString();
                }
                if (character != '\\') {
                    value.append(character);
                    continue;
                }
                if (atEnd()) {
                    throw new IllegalArgumentException("Broken escape sequence");
                }
                char escaped = text.charAt(position++);
                switch (escaped) {
                    case '"':  value.append('"');  break;
                    case '\\': value.append('\\'); break;
                    case '/':  value.append('/');  break;
                    case 'b':  value.append('\b'); break;
                    case 'f':  value.append('\f'); break;
                    case 'n':  value.append('\n'); break;
                    case 'r':  value.append('\r'); break;
                    case 't':  value.append('\t'); break;
                    case 'u':
                        if (position + 4 > text.length()) {
                            throw new IllegalArgumentException("Broken \\u escape");
                        }
                        value.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown escape : \\" + escaped);
                }
            }
        }

        private Object readNumber() {
            int start = position;
            if (!atEnd() && (text.charAt(position) == '-' || text.charAt(position) == '+')) {
                position++;
            }
            boolean isDecimal = false;
            while (!atEnd()) {
                char character = text.charAt(position);
                if (character >= '0' && character <= '9') {
                    position++;
                } else if (character == '.' || character == 'e' || character == 'E'
                        || character == '-' || character == '+') {
                    isDecimal = isDecimal || character == '.' || character == 'e' || character == 'E';
                    position++;
                } else {
                    break;
                }
            }
            String numberText = text.substring(start, position);
            if (numberText.isEmpty()) {
                throw new IllegalArgumentException("A JSON value was expected");
            }
            if (isDecimal) {
                return Double.valueOf(numberText);
            }
            try {
                return Long.valueOf(numberText);
            } catch (NumberFormatException e) {
                return Double.valueOf(numberText);
            }
        }

        private void expect(String word) {
            if (!text.startsWith(word, position)) {
                throw new IllegalArgumentException("'" + word + "' expected");
            }
            position += word.length();
        }
    }
}
