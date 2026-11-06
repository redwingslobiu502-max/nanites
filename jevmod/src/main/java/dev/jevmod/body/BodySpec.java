package dev.jevmod.body;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.util.List;
import dev.jevmod.core.Json;

/** Portable body recipe. Uploaded assets are content-addressed local files, never arbitrary paths. */
public record BodySpec(String kind, String model, String texture, String brain, int durationTicks,
                       List<String> resistances, JsonObject metadata) {
    /** Existing Java recipes and old saved worlds retain their original behavior. */
    public BodySpec(String kind,String model,String texture,String brain,int durationTicks){
        this(kind,model,texture,brain,durationTicks,List.of(),new JsonObject());
    }
    public BodySpec {
        if (!java.util.Set.of("player", "mob", "obj").contains(kind)) throw new IllegalArgumentException("Tipo: player, mob ou obj");
        if (model == null || model.length() > 160) throw new IllegalArgumentException("Modelo inválido");
        if (kind.equals("player") && !model.equals("classic")) throw new IllegalArgumentException("Base humanoide: classic");
        texture = texture == null ? "" : texture;
        brain = brain == null ? "" : brain;
        if (!texture.isEmpty() && !texture.matches("[a-f0-9]{64}\\.png")) throw new IllegalArgumentException("Textura não importada");
        if (kind.equals("obj") && !model.matches("[a-f0-9]{64}\\.obj")) throw new IllegalArgumentException("Importe o OBJ primeiro");
        if (kind.equals("mob") && !model.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Use namespace:mob");
        if (!brain.isEmpty() && !brain.matches("[a-z][a-z0-9_]{0,47}")) throw new IllegalArgumentException("Cérebro inválido");
        if (durationTicks < 20 || durationTicks > 72_000) throw new IllegalArgumentException("Tempo: 1 a 3600 segundos");
        resistances=List.copyOf(resistances==null?List.of():resistances);
        if(resistances.size()>64||resistances.stream().anyMatch(s->!s.matches("[a-zA-Z0-9_.:/-]{1,96}")||s.contains("..")||s.startsWith("/")))throw new IllegalArgumentException("Resistências: até 64 tipos de dano");
        metadata=metadata==null?new JsonObject():metadata.deepCopy();
        if(metadata.toString().length()>8192)throw new IllegalArgumentException("Metadados: até 8 KB");
    }
    @Override public JsonObject metadata(){return metadata.deepCopy();}
    public float damageMultiplier(String type,String registryId){return resistances.contains(type)||resistances.contains(registryId)?.1f:1f;}
    public static BodySpec defaults() { return new BodySpec("player", "classic", "", "", 200); }
    public static BodySpec parse(JsonObject o) {
        return new BodySpec(Json.str(o,"kind","player"), Json.str(o,"model","classic"),
                Json.str(o,"texture",""), Json.str(o,"brain",""), Json.integer(o,"duration_ticks",200),
                Json.strings(o,"resistances"),o.has("metadata")?o.getAsJsonObject("metadata"):new JsonObject());
    }
    public JsonObject json() {
        JsonObject o = new JsonObject();
        o.addProperty("kind",kind); o.addProperty("model",model); o.addProperty("texture",texture);
        o.addProperty("brain",brain); o.addProperty("duration_ticks",durationTicks);
        var r=new JsonArray();resistances.forEach(r::add);o.add("resistances",r);o.add("metadata",metadata());return o;
    }
}
