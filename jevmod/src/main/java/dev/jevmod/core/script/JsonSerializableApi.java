package dev.jevmod.core.script;

import com.google.gson.JsonElement;

/** API objects (vectors, entity handles) that a script sensor may return directly; they serialise themselves. */
public interface JsonSerializableApi {
    JsonElement toJson();
}
