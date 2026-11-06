package dev.jevmod.api;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;

import java.util.List;

/** Builds real JS arrays from API methods (so scripts can use .forEach/.filter/.map). */
final class Js {
    private Js() {
    }

    static Object array(List<?> items) {
        Context cx = Context.getCurrentContext();
        if (cx == null) return items.toArray();
        Scriptable scope = ScriptRuntime.getTopCallScope(cx);
        Object[] arr = new Object[items.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = Context.javaToJS(items.get(i), scope);
        return cx.newArray(scope, arr);
    }
}
