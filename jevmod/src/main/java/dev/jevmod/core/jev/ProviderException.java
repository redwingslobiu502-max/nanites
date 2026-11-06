package dev.jevmod.core.jev;

/** The AI provider failed (HTTP error, timeout, unparseable answer). The brain skips that think and retries later. */
public class ProviderException extends RuntimeException {
    public ProviderException(String message) {
        super(message);
    }

    public ProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
