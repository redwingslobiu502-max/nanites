package dev.jevmod.mind;

import com.google.gson.JsonObject;
import dev.jevmod.core.Json;
import java.util.List;

/** Personality and system composition; independent from an entity's physical appearance. */
public record MindDef(String id,String name,String instructions,List<String> systems) {
    public static MindDef parse(JsonObject json,String fallback){
        String id=Json.str(json,"id",fallback);MindCatalog.requireId(id);
        var systems=Json.strings(json,"systems");if(systems.size()>32)throw new IllegalArgumentException("Máximo: 32 sistemas por Mente");systems.forEach(MindCatalog::requireId);
        return new MindDef(id,Json.str(json,"name",id),Json.str(json,"instructions","Ajude o jogador."),List.copyOf(systems));
    }
}
