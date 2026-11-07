package dev.nanites.core;

import com.google.gson.*;
import java.util.*;

/** Shared public tool contracts; no loader types or arbitrary execution. */
public final class MachineTools {
    private MachineTools() {}
    public static final Set<String> OPERATIONS=Set.of("machine_inspect","scan_machines","transfer_items","transfer_energy","transfer_fluid","machine_control","machine_recover");
    public static final Set<String> ADAPTERS=Set.of("adapter_create","adapter_load","adapter_execute","adapter_list","adapter_status","adapter_cancel");
    public static final Set<String> NAMES;
    static {var n=new HashSet<>(OPERATIONS);n.addAll(ADAPTERS);n.addAll(AssimilationTools.NAMES);n.addAll(ObjectiveTools.NAMES);n.addAll(LivingArmorTools.NAMES);NAMES=Set.copyOf(n);}
    static JsonObject scalar(String type){var j=new JsonObject();j.addProperty("type",type);return j;}
    static JsonObject number(int min,int max){var j=scalar("integer");j.addProperty("minimum",min);j.addProperty("maximum",max);return j;}
    static JsonObject text(){var j=scalar("string");j.addProperty("maxLength",65536);return j;}
    static JsonObject choices(String... values){var j=text();var a=new JsonArray();for(String s:values)a.add(s);j.add("enum",a);return j;}
    static JsonObject object(JsonObject props,String... required){var j=scalar("object");j.add("properties",props);j.addProperty("additionalProperties",false);var r=new JsonArray();for(String s:required)r.add(s);j.add("required",r);return j;}
    public static JsonObject target(){var p=new JsonObject();p.add("type",choices("block","player"));p.add("dimension",text());for(String a:List.of("x","y","z"))p.add(a,number(-30000000,30000000));p.add("side",choices("auto","none","down","up","north","south","west","east"));return object(p,"type","dimension","x","y","z","side");}
    public static JsonObject schema(String name){
        if(LivingArmorTools.NAMES.contains(name))return LivingArmorTools.schema(name);
        if(ObjectiveTools.NAMES.contains(name))return ObjectiveTools.schema(name);
        if(AssimilationTools.NAMES.contains(name))return AssimilationTools.schema(name);
        var p=new JsonObject();String[] required;
        switch(name){
            case "machine_inspect" -> {p.add("target",target());p.add("offset",number(0,4095));p.add("limit",number(1,16));required=new String[]{"target","offset","limit"};}
            case "scan_machines" -> {p.add("target",target());p.add("radius",number(0,16));p.add("offset",number(0,35936));p.add("limit",number(1,128));required=new String[]{"target","radius","offset","limit"};}
            case "transfer_items","transfer_energy","transfer_fluid" -> {p.add("from",target());p.add("to",target());p.add("amount",number(1,1000000));p.add("preview",scalar("boolean"));if(!name.equals("transfer_energy")){p.add("from_slot",number(-1,4095));p.add("to_slot",number(-1,4095));}required=p.keySet().toArray(String[]::new);}
            case "machine_control" -> {p.add("target",target());p.add("property",choices("powered","enabled"));p.add("value",choices("true","false"));p.add("preview",scalar("boolean"));required=p.keySet().toArray(String[]::new);}
            case "machine_recover" -> {p.add("to",target());p.add("index",number(0,127));p.add("slot",number(-1,4095));p.add("preview",scalar("boolean"));required=p.keySet().toArray(String[]::new);}
            case "adapter_create" -> {p.add("project",text());p.add("name",text());p.add("source",text());required=p.keySet().toArray(String[]::new);}
            case "adapter_load" -> {p.add("project",text());p.add("name",text());required=p.keySet().toArray(String[]::new);}
            case "adapter_execute" -> {p.add("project",text());p.add("name",text());p.add("preview",scalar("boolean"));required=p.keySet().toArray(String[]::new);}
            case "adapter_list","adapter_status","adapter_cancel" -> {p.add("project",text());required=p.keySet().toArray(String[]::new);}
            default -> throw new IllegalArgumentException("Unknown machine tool: "+name);
        }
        return object(p,required);
    }
    public static void validate(String name,JsonObject args){validateValue(schema(name),args);}
    private static void validateValue(JsonObject s,JsonElement v){
        String type=s.get("type").getAsString();
        if(s.has("enum")&&!s.getAsJsonArray("enum").contains(v))throw new IllegalArgumentException("Expected one of "+s.get("enum"));
        switch(type){
            case "object" -> {if(!v.isJsonObject())throw new IllegalArgumentException("Expected object");var a=v.getAsJsonObject();var p=s.getAsJsonObject("properties");if(!p.keySet().equals(a.keySet()))throw new IllegalArgumentException("Exact parameters required: "+p.keySet());for(String k:p.keySet())validateValue(p.getAsJsonObject(k),a.get(k));}
            case "string" -> {if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString()||v.getAsString().length()>65536)throw new IllegalArgumentException("Expected bounded string");}
            case "boolean" -> {if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected boolean");}
            case "integer" -> {long n=SafeData.integer(v);if(n<s.get("minimum").getAsLong()||n>s.get("maximum").getAsLong())throw new IllegalArgumentException("Integer out of bounds");}
            default -> throw new IllegalArgumentException("Invalid schema");
        }
    }
    public static String description(String name){if(LivingArmorTools.NAMES.contains(name))return LivingArmorTools.description(name);if(ObjectiveTools.NAMES.contains(name))return ObjectiveTools.description(name);if(AssimilationTools.NAMES.contains(name))return AssimilationTools.description(name);return switch(name){
        case "machine_inspect" -> "Live loaded block/player-owner inventory, sided Forge capabilities, BlockState and escrow. auto enumerates none and six directions; auto effective_limit<=2 per face; explicit face<=16. Never force-load. See catalog.";
        case "scan_machines" -> "Loaded capability discovery. Stable cube cell cursor x then y then z; radius<=16, <=128 cells/8 hits. Continue next_offset; partial unloaded cells skipped.";
        case "transfer_items" -> "Simulate then transfer one item/NBT identity. Slots -1 first 256 auto. Supports block to block or owner inventory. Compensates or retains escrow; no grant. preview never writes.";
        case "transfer_energy" -> "Simulate then transfer FE respecting extract/receive throughput; compensation or escrow if producer refuses refund. Block endpoints only. preview never writes.";
        case "transfer_fluid" -> "Simulate then transfer mB preserving fluid tags. from_slot selects tank identity, to_slot validates compatibility only; Forge drain/fill selects actual tanks. Compensation/escrow. preview never writes.";
        case "machine_control" -> "Set only actual powered/enabled BlockState boolean property, e.g. wired lever. Returns observed state; mod may revert next tick. No universal start; preview never writes.";
        case "machine_recover" -> "Deliver owner's retained escrow resource to compatible target. Only actual received amount removed; preview inert. Inspect escrow indices first.";
        case "adapter_create" -> "Validate recipe source JSON, persist and activate last-known-good replacement. Changed content needs increasing version. Read catalog adapter_recipe for full steps/repeat/when schema. No restart or user file needed.";
        case "adapter_load" -> "Validate saved world/UUID/project/name recipe then replace active version. Invalid candidate retains last good. No restart.";
        case "adapter_execute" -> "Queue finite validated pipeline; preview forces write steps to simulate. QUEUED is not completion; poll adapter_status. No grant. One active job/owner.";
        case "adapter_list" -> "List active versioned recipes and saved names for owner's project (default).";
        case "adapter_status" -> "Live queued pipeline step, repeat, observed last receipt, skipped/executed and errors. WAITING_CHUNK does not force-load.";
        case "adapter_cancel" -> "Cancel remaining pipeline steps, preserving already observed effects.";
        default -> throw new IllegalArgumentException("Unknown machine tool");
    };}
}
