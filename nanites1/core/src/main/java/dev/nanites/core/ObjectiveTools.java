package dev.nanites.core;

import com.google.gson.*;
import java.util.*;

/** Owner authenticated by gateway, never by model supplied UUID. */
public final class ObjectiveTools {
    private ObjectiveTools() {}
    public static final Set<String> NAMES;
    static {var n=new HashSet<String>();for(String prefix:List.of("objective_","factory_"))for(String op:List.of("create","list","status","update","pause","resume","stop"))n.add(prefix+op);NAMES=Set.copyOf(n);}
    public static JsonObject schema(String name){
        if(!NAMES.contains(name))throw new IllegalArgumentException("Unknown objective tool");
        var p=new JsonObject();String op=name.substring(name.indexOf('_')+1);
        if(!op.equals("list"))p.add("name",MachineTools.text());
        if(Set.of("update","pause","resume","stop").contains(op))p.add("version",MachineTools.number(1,Integer.MAX_VALUE));
        if(Set.of("create","update").contains(op))p.add("source",MachineTools.text());
        return MachineTools.object(p,p.keySet().toArray(String[]::new));
    }
    public static String description(String name){return "Durable owner-bound background objective/factory. create/update source JSON: mode factory|language, text, scope {dimension,min:{x,y,z},max:{x,y,z},machines:[exact machine targets]}, intervalTicks 20..72000, budget 1..24, recipe {project,name} (empty strings for language without daemon), monitor {args:machine_inspect args,path:dot path,op:eq|gt|gte|lt|lte|exists,value:primitive}. Controls require current version. Continuous measured monitoring; model text never proves success. update can attach internal adapter as deterministic factory; interval is ticks. No force loading, arbitrary API or external adapters. list/status expose lifecycle, receipts, telemetry and errors.";}
}
