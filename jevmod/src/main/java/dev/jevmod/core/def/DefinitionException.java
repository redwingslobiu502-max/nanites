package dev.jevmod.core.def;

/** A brain/power/sensor definition is invalid. The message is shown verbatim to the player and to agents. */
public class DefinitionException extends RuntimeException {
    public DefinitionException(String message) {
        super(message);
    }

    public DefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
