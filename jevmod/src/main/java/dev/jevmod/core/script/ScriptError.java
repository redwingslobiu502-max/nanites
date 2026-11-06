package dev.jevmod.core.script;

/** A power or sensor script failed to compile or threw at runtime. The message includes file:line. */
public class ScriptError extends RuntimeException {
    public ScriptError(String message, Throwable cause) {
        super(message, cause);
    }
}
