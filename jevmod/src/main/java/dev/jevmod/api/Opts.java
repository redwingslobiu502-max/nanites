package dev.jevmod.api;

import dev.jevmod.core.Colors;
import dev.jevmod.core.script.ScriptHost;
import org.mozilla.javascript.Context;

import java.util.List;
import java.util.Map;

/** Reads a JS options object ({@code {from: v, color: "red", width: 0.2}}) passed to an API method. */
final class Opts {
    private final Map<?, ?> m;

    private Opts(Map<?, ?> m) {
        this.m = m;
    }

    static Opts of(Object jsOrMap) {
        Object j = jsOrMap instanceof Map<?, ?> ? jsOrMap : ScriptHost.toJava(jsOrMap);
        return new Opts(j instanceof Map<?, ?> mm ? mm : Map.of());
    }

    boolean has(String k) {
        return m.get(k) != null;
    }

    Object raw(String k) {
        return m.get(k);
    }

    double num(String k, double def) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    int integer(String k, int def) {
        return (int) Math.round(num(k, def));
    }

    String str(String k, String def) {
        Object v = m.get(k);
        return v == null ? def : v.toString();
    }

    boolean bool(String k, boolean def) {
        Object v = m.get(k);
        return v instanceof Boolean b ? b : def;
    }

    int color(String k, int def) {
        return m.get(k) == null ? def : Colors.parse(m.get(k));
    }

    Vec vec(String k, Vec def) {
        Vec v = toVec(m.get(k));
        return v == null ? def : v;
    }

    EntityRef entity(String k) {
        return m.get(k) instanceof EntityRef e ? e : null;
    }

    /** Accepts a Vec, an EntityRef (its position), {x,y,z} or [x,y,z]. */
    static Vec toVec(Object v) {
        if (v instanceof Vec vec) return vec;
        if (v instanceof EntityRef e) return e.pos();
        if (v instanceof Map<?, ?> mm && mm.get("x") instanceof Number x && mm.get("y") instanceof Number y && mm.get("z") instanceof Number z) {
            return new Vec(x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
        if (v instanceof List<?> l && l.size() == 3 && l.get(0) instanceof Number x && l.get(1) instanceof Number y && l.get(2) instanceof Number z) {
            return new Vec(x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
        return null;
    }

    static Vec requireVec(Object v, String what) {
        Vec r = toVec(ScriptHost.toJava(v));
        if (r == null) throw Context.reportRuntimeError(what + " must be a vector (ctx.vec(x,y,z), an entity, {x,y,z} or [x,y,z])");
        return r;
    }
}
