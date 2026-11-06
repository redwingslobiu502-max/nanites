package dev.jevmod.core.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.WrapFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sandboxed JavaScript (Rhino, ES6 mode, interpreted) for powers and script sensors.
 * <ul>
 *   <li>No {@code java}/{@code Packages}: scripts only see the objects the mod hands them.</li>
 *   <li>A {@link ClassShutter} blocks every Java class except the mod's API package, so {@code x.getClass()} and
 *       reflection tricks fail.</li>
 *   <li>Each call has an instruction budget, so {@code while(true){}} throws instead of freezing the server.</li>
 * </ul>
 */
public final class ScriptHost {
    /** Java classes a script may touch (as wrapped objects). Everything else is invisible. */
    private static final String API_PREFIX = "dev.jevmod.api.";
    private static final Set<String> ALLOWED = Set.of("java.lang.String", "java.lang.Boolean", "java.lang.Double", "java.lang.Integer", "java.lang.Long");

    private final Factory factory;
    private final ScriptableObject shared;

    public ScriptHost(long instructionLimit) {
        this.factory = new Factory(instructionLimit);
        Context cx = factory.enterContext();
        try {
            this.shared = cx.initSafeStandardObjects(null, true);
        } finally {
            Context.exit();
        }
    }

    public static boolean visibleToScripts(String className) {
        return className.startsWith(API_PREFIX) || ALLOWED.contains(className);
    }

    /** A compiled script file. Compile once per reload, instantiate per brain. */
    public Script compile(String source, String name) {
        Context cx = factory.enterContext();
        try {
            return cx.compileString(source, name, 1, null);
        } catch (RhinoException e) {
            throw new ScriptError(describe(e), e);
        } finally {
            Context.exit();
        }
    }

    /** Runs the file's top level in a fresh scope; its globals persist for the life of the instance. */
    public Instance instantiate(Script script, Map<String, Object> globals) {
        Context cx = factory.enterContext();
        try {
            Scriptable scope = cx.newObject(shared);
            scope.setPrototype(shared);
            scope.setParentScope(null);
            for (Map.Entry<String, Object> g : globals.entrySet()) {
                ScriptableObject.putProperty(scope, g.getKey(), toJs(cx, scope, g.getValue()));
            }
            ((Limited) cx).reset();
            script.exec(cx, scope);
            return new Instance(scope);
        } catch (RhinoException e) {
            throw new ScriptError(describe(e), e);
        } catch (BudgetExceeded e) {
            throw new ScriptError(e.getMessage(), e);
        } finally {
            Context.exit();
        }
    }

    public final class Instance {
        private final Scriptable scope;

        private Instance(Scriptable scope) {
            this.scope = scope;
        }

        public boolean has(String fn) {
            return ScriptableObject.getProperty(scope, fn) instanceof Function;
        }

        /** Refreshes a global when runtime parameters change. */
        public void set(String name, Object value) {
            Context cx = factory.enterContext();
            try {
                ScriptableObject.putProperty(scope, name, toJs(cx, scope, value));
            } finally {
                Context.exit();
            }
        }

        /** Calls a top-level function if it exists. Returns the JS result converted to plain Java (maps/lists/doubles). */
        public Object call(String fn, Object... args) {
            Object f = ScriptableObject.getProperty(scope, fn);
            if (!(f instanceof Function func)) {
                return null;
            }
            Context cx = factory.enterContext();
            try {
                Object[] js = new Object[args.length];
                for (int i = 0; i < args.length; i++) js[i] = toJs(cx, scope, args[i]);
                ((Limited) cx).reset();
                return toJava(func.call(cx, scope, scope, js));
            } catch (RhinoException e) {
                throw new ScriptError(describe(e), e);
            } catch (BudgetExceeded e) {
                throw new ScriptError(e.getMessage(), e);
            } finally {
                Context.exit();
            }
        }
    }

    // ---- conversions -------------------------------------------------------------------------------------------

    /** Java -> JS: maps become objects, lists become arrays, API objects are wrapped, primitives stay primitive. */
    public static Object toJs(Context cx, Scriptable scope, Object v) {
        return toJs(cx, scope, v, new Conversion());
    }

    private static Object toJs(Context cx, Scriptable scope, Object v, Conversion budget) {
        budget.enter(v);
        try {
            if (v == null) return null;
            if (v instanceof String || v instanceof Boolean) return v;
            if (v instanceof Number n) return n.doubleValue();
            if (v instanceof Scriptable) return v;
            if (v instanceof JsonElement j) return toJs(cx, scope, fromJson(j), budget);
            if (v instanceof Map<?, ?> m) {
                budget.size(m.size());
                Scriptable o = cx.newObject(scope);
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    ScriptableObject.putProperty(o, String.valueOf(e.getKey()), toJs(cx, scope, e.getValue(), budget));
                }
                return o;
            }
            if (v instanceof List<?> l) {
                budget.size(l.size());
                Object[] arr = new Object[l.size()];
                for (int i = 0; i < arr.length; i++) arr[i] = toJs(cx, scope, l.get(i), budget);
                return cx.newArray(scope, arr);
            }
            return Context.javaToJS(v, scope);
        } finally {
            budget.leave(v);
        }
    }

    /** JS -> plain Java: objects -> LinkedHashMap, arrays -> ArrayList, numbers -> Double, undefined -> null. */
    public static Object toJava(Object v) {
        return toJava(v, new Conversion());
    }

    private static Object toJava(Object v, Conversion budget) {
        budget.enter(v);
        try {
            if (v == null || v instanceof Undefined || v == Scriptable.NOT_FOUND) return null;
            if (v instanceof NativeJavaObject w) {
                Object raw = w.unwrap();
                return raw instanceof Map<?, ?> || raw instanceof List<?> || raw instanceof Scriptable
                        ? toJava(raw, budget) : raw;
            }
            if (v instanceof JsonSerializableApi) return v;
            if (v instanceof CharSequence s) return s.toString();
            if (v instanceof Number n) return n.doubleValue();
            if (v instanceof Boolean || v instanceof JsonElement) return v;
            // Rhino arrays implement List and objects implement Map: handle Scriptable first,
            // while a Context is active, instead of leaking live scopes into Gson/API callers.
            if (v instanceof NativeArray a) {
                budget.size(a.getLength());
                List<Object> out = new ArrayList<>();
                for (Object x : a) out.add(toJava(x, budget));
                return out;
            }
            if (v instanceof Function) return null;
            if (v instanceof Scriptable s) {
                Map<String, Object> out = new LinkedHashMap<>();
                Object[] ids = s.getIds();
                budget.size(ids.length);
                for (Object id : ids) {
                    String k = String.valueOf(id);
                    Object x = id instanceof Number n ? s.get(n.intValue(), s) : s.get(k, s);
                    if (!(x instanceof Function)) out.put(k, toJava(x, budget));
                }
                return out;
            }
            if (v instanceof Map<?, ?> m) {
                budget.size(m.size());
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), toJava(e.getValue(), budget));
                return out;
            }
            if (v instanceof List<?> l) {
                budget.size(l.size());
                List<Object> out = new ArrayList<>();
                for (Object x : l) out.add(toJava(x, budget));
                return out;
            }
            return v.toString();
        } finally {
            budget.leave(v);
        }
    }

    /** Java-side traversal is not covered by Rhino's instruction counter. */
    private static final class Conversion {
        private static final int MAX_DEPTH = 64, MAX_VALUES = 16_384;
        private final IdentityHashMap<Object, Boolean> path = new IdentityHashMap<>();
        private int depth, values;

        void enter(Object value) {
            if (++values > MAX_VALUES || ++depth > MAX_DEPTH) {
                throw new ScriptError("script data exceeds conversion limits (64 levels / 16384 values)", null);
            }
            if (value != null && path.put(value, Boolean.TRUE) != null) {
                throw new ScriptError("cyclic script data cannot be converted to JSON", null);
            }
        }

        void size(long size) {
            if (size > MAX_VALUES - values) throw new ScriptError("script data exceeds conversion value limit (16384)", null);
        }

        void leave(Object value) {
            if (value != null) path.remove(value);
            depth--;
        }
    }

    public static Object fromJson(JsonElement j) {
        return fromJson(j, new Conversion());
    }

    private static Object fromJson(JsonElement j, Conversion budget) {
        budget.enter(j);
        try {
            if (j == null || j.isJsonNull()) return null;
            if (j.isJsonPrimitive()) {
                JsonPrimitive p = j.getAsJsonPrimitive();
                if (p.isBoolean()) return p.getAsBoolean();
                if (p.isNumber()) return p.getAsDouble();
                return p.getAsString();
            }
            if (j.isJsonArray()) {
                budget.size(j.getAsJsonArray().size());
                List<Object> out = new ArrayList<>();
                for (JsonElement x : j.getAsJsonArray()) out.add(fromJson(x, budget));
                return out;
            }
            budget.size(j.getAsJsonObject().size());
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : j.getAsJsonObject().entrySet()) out.put(e.getKey(), fromJson(e.getValue(), budget));
            return out;
        } finally {
            budget.leave(j);
        }
    }

    /** Plain Java (from {@link #toJava}) -> Gson tree, for script sensors. Unknown objects become their toString(). */
    public static JsonElement toJson(Object v) {
        return toJson(v, new Conversion());
    }

    private static JsonElement toJson(Object v, Conversion budget) {
        budget.enter(v);
        try {
            if (v == null || v instanceof JsonNull) return JsonNull.INSTANCE;
            if (v instanceof JsonPrimitive p) return p.deepCopy();
            if (v instanceof JsonObject json) {
                budget.size(json.size());
                JsonObject out = new JsonObject();
                for (Map.Entry<String, JsonElement> e : json.entrySet()) out.add(e.getKey(), toJson(e.getValue(), budget));
                return out;
            }
            if (v instanceof JsonArray json) {
                budget.size(json.size());
                JsonArray out = new JsonArray();
                for (JsonElement x : json) out.add(toJson(x, budget));
                return out;
            }
            if (v instanceof Boolean b) return new JsonPrimitive(b);
            if (v instanceof Number n) {
                double d = n.doubleValue();
                return d == Math.rint(d) && Math.abs(d) < 1e15 ? new JsonPrimitive((long) d) : new JsonPrimitive(d);
            }
            if (v instanceof Map<?, ?> m) {
                budget.size(m.size());
                JsonObject o = new JsonObject();
                for (Map.Entry<?, ?> e : m.entrySet()) o.add(String.valueOf(e.getKey()), toJson(e.getValue(), budget));
                return o;
            }
            if (v instanceof List<?> l) {
                budget.size(l.size());
                JsonArray a = new JsonArray();
                for (Object x : l) a.add(toJson(x, budget));
                return a;
            }
            if (v instanceof JsonSerializableApi s) return toJson(s.toJson(), budget);
            return new JsonPrimitive(v.toString());
        } finally {
            budget.leave(v);
        }
    }

    static String describe(RhinoException e) {
        String where = e.sourceName() == null ? "" : e.sourceName() + (e.lineNumber() > 0 ? ":" + e.lineNumber() : "") + ": ";
        return where + e.details();
    }

    // ---- sandbox plumbing --------------------------------------------------------------------------------------

    /** An Error (not an Exception) so JavaScript try/catch cannot swallow it. */
    private static final class BudgetExceeded extends Error {
        BudgetExceeded(String m) {
            super(m);
        }
    }

    private static final class Limited extends Context {
        private final long limit;
        private long used;

        Limited(ContextFactory f, long limit) {
            super(f);
            this.limit = limit;
        }

        void reset() {
            used = 0;
        }
    }

    private static final class Factory extends ContextFactory {
        private final long limit;

        Factory(long limit) {
            this.limit = limit;
        }

        @Override
        protected Context makeContext() {
            Limited cx = new Limited(this, limit);
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setInterpretedMode(true); // required for instruction counting
            cx.setInstructionObserverThreshold(10_000);
            cx.setClassShutter(ScriptHost::visibleToScripts);
            WrapFactory wf = new WrapFactory();
            wf.setJavaPrimitiveWrap(false);
            cx.setWrapFactory(wf);
            return cx;
        }

        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            if (featureIndex == Context.FEATURE_ENHANCED_JAVA_ACCESS) return false;
            return super.hasFeature(cx, featureIndex);
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            Limited l = (Limited) cx;
            l.used += instructionCount;
            if (l.used > l.limit) {
                throw new BudgetExceeded("script exceeded its instruction budget (" + l.limit + "); check for infinite loops");
            }
        }
    }
}
