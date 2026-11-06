package dev.jevmod.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Colour names the AI can pick from (Jev is unreliable with hex codes, so choice params use names) plus #rrggbb.
 */
public final class Colors {
    public static final Map<String, Integer> NAMED = new LinkedHashMap<>();

    static {
        NAMED.put("red", 0xFF2A1F);
        NAMED.put("orange", 0xFF8A1F);
        NAMED.put("yellow", 0xFFE81F);
        NAMED.put("gold", 0xFFC233);
        NAMED.put("lime", 0x9BFF2A);
        NAMED.put("green", 0x2AFF5A);
        NAMED.put("cyan", 0x2AF4FF);
        NAMED.put("blue", 0x2A6BFF);
        NAMED.put("purple", 0x9B3BFF);
        NAMED.put("magenta", 0xFF2AE8);
        NAMED.put("pink", 0xFF7AC8);
        NAMED.put("white", 0xFFFFFF);
    }

    private Colors() {
    }

    /** Accepts a colour name, "#rrggbb", "rrggbb" or a number. Unknown values fall back to white. */
    public static int parse(Object v) {
        if (v instanceof Number n) {
            return n.intValue() & 0xFFFFFF;
        }
        if (v == null) {
            return 0xFFFFFF;
        }
        String s = v.toString().trim().toLowerCase(Locale.ROOT);
        Integer named = NAMED.get(s);
        if (named != null) {
            return named;
        }
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.startsWith("0x")) {
            s = s.substring(2);
        }
        try {
            if (s.length() == 6) {
                return Integer.parseInt(s, 16);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return 0xFFFFFF;
    }
}
