package dev.jevmod.mind;

import com.google.gson.JsonObject;
import dev.jevmod.api.mind.MindContext;

/** Public Java addon contract. Register a factory with MindExtensions during mod setup. */
public interface MindModule {
    default void start(MindContext context)throws Exception {}
    default void tick(MindContext context)throws Exception {}
    default void event(MindContext context,String event,JsonObject payload)throws Exception {}
    default void stop(MindContext context)throws Exception {}
}
