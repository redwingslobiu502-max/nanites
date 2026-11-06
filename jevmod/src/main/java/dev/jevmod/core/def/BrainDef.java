package dev.jevmod.core.def;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.jevmod.core.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A brain: the "network" that wires sensors to powers. Every think cycle it turns the sensor readings into a state,
 * asks one batched set of typed questions (one per power plus one per AI-controlled parameter), and gates the answers
 * by probability/confidence before any power runs.
 */
public final class BrainDef {
    public final String id;
    public final String name;
    public final String description;
    /** Rules/personality handed to the AI with every question (string or structured object). */
    public final JsonElement instructions;
    /** Power ids this brain controls; "*" means every loaded power with ai=true. */
    public final List<String> powers;
    public final List<SensorDef> sensors;
    public final int thinkEveryTicks;
    /** A toggle power turns on when P(yes) >= this ... */
    public final double activateThreshold;
    /** ... and off when P(yes) <= this. In between it keeps its current state (hysteresis). */
    public final double deactivateThreshold;
    /** Default minimum confidence for choice/score answers before a parameter changes. */
    public final double minConfidence;
    /** Feed the host's chat messages into the state and think immediately when one arrives. */
    public final boolean listenChat;
    /** If set, only chat messages starting with this prefix (e.g. "@jarvis") reach the brain. */
    public final String chatPrefix;
    /** Optional provider/model override for this brain (otherwise the global config is used). */
    public final String provider;
    public final String model;

    private BrainDef(String id, String name, String description, JsonElement instructions, List<String> powers,
                     List<SensorDef> sensors, int thinkEveryTicks, double activateThreshold, double deactivateThreshold,
                     double minConfidence, boolean listenChat, String chatPrefix, String provider, String model) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.instructions = instructions;
        this.powers = powers;
        this.sensors = sensors;
        this.thinkEveryTicks = thinkEveryTicks;
        this.activateThreshold = activateThreshold;
        this.deactivateThreshold = deactivateThreshold;
        this.minConfidence = minConfidence;
        this.listenChat = listenChat;
        this.chatPrefix = chatPrefix;
        this.provider = provider;
        this.model = model;
    }

    public static BrainDef parse(JsonObject o, String fallbackId) {
        String id = Json.str(o, "id", fallbackId);
        Defs.requireId(id, "brain");
        List<String> powers = Json.strings(o, "powers");
        if (powers.isEmpty()) {
            powers = List.of("*");
        }
        List<SensorDef> sensors = new ArrayList<>();
        JsonArray sa = Json.arr(o, "sensors");
        if (sa == null) {
            for (String t : SensorDef.DEFAULT_SET) {
                sensors.add(SensorDef.parse(Json.parseObject("{\"type\":\"" + t + "\"}")));
            }
        } else {
            for (JsonElement e : sa) {
                if (e.isJsonPrimitive()) {
                    sensors.add(SensorDef.parse(Json.parseObject("{\"type\":\"" + e.getAsString() + "\"}")));
                } else if (e.isJsonObject()) {
                    try {
                        sensors.add(SensorDef.parse(e.getAsJsonObject()));
                    } catch (DefinitionException ex) {
                        throw new DefinitionException("brain '" + id + "': " + ex.getMessage());
                    }
                } else {
                    throw new DefinitionException("brain '" + id + "': each sensor must be a string or an object");
                }
            }
        }
        double on = Json.num(o, "activate_threshold", 0.6);
        double off = Json.num(o, "deactivate_threshold", 0.4);
        if (!(on > off) || on > 1 || off < 0) {
            throw new DefinitionException("brain '" + id + "': need 0 <= deactivate_threshold < activate_threshold <= 1");
        }
        int every = Json.integer(o, "think_every_ticks", 40);
        if (every < 5) {
            throw new DefinitionException("brain '" + id + "': think_every_ticks must be >= 5 (each think is one API call)");
        }
        JsonElement instructions = Json.textOrTree(o, "instructions");
        return new BrainDef(id,
                Json.str(o, "name", id),
                Json.str(o, "description", ""),
                instructions == null ? Json.p("You are the brain of a Minecraft player's power suit. Help the player.") : instructions,
                Collections.unmodifiableList(powers),
                Collections.unmodifiableList(sensors),
                every, on, off,
                Json.num(o, "min_confidence", 0.35),
                Json.bool(o, "listen_chat", true),
                Json.str(o, "chat_prefix", null),
                Json.str(o, "provider", null),
                Json.str(o, "model", null));
    }
}
