package dev.nanites.core;

import com.google.gson.JsonObject;
import java.util.Set;

/** Owner-only living armor; no paths, arbitrary entities or executable code. */
public final class LivingArmorTools {
    private LivingArmorTools() {}
    public static final Set<String> NAMES=Set.of("armor_status","armor_configure","armor_reload","armor_repair");
    public static JsonObject schema(String name){
        var p=new JsonObject();
        switch(name){
            case "armor_status","armor_reload" -> {}
            case "armor_configure" -> {p.add("version",MachineTools.number(1,1000000));p.add("enabled",MachineTools.scalar("boolean"));p.add("adaptive",MachineTools.scalar("boolean"));p.add("autoRepair",MachineTools.scalar("boolean"));p.add("repairKey",MachineTools.text());p.add("repairSlot",MachineTools.choices("head","chest","legs","feet","mainhand","offhand"));p.add("intervalTicks",MachineTools.number(20,72000));}
            case "armor_repair" -> {p.add("slot",MachineTools.choices("head","chest","legs","feet","mainhand","offhand"));p.add("key",MachineTools.text());p.add("preview",MachineTools.scalar("boolean"));}
            default -> throw new IllegalArgumentException("Unknown armor tool");
        }
        return MachineTools.object(p,p.keySet().toArray(String[]::new));
    }
    public static String description(String name){return switch(name){
        case "armor_status" -> "Live owner equipment/modules, persistent living armor configuration and adaptive exposure; actual repair receipt. No universal mod support.";
        case "armor_configure" -> "Persist owner living armor profile, increasing version; adaptive paid guard and bounded automatic repair of one specified equipment slot using one exact assimilated repairKey. Empty key disables automatic repair. No restart.";
        case "armor_reload" -> "Reload owner's saved armor profile, validate first; invalid/stale candidate retains last good configuration.";
        case "armor_repair" -> "Repair one owner equipment slot using ONE actual compatible assimilated item/NBT identity and 100000 nanites per durability restored. Max quarter durability per item. preview is inert; inspect stock key first. No private repair API or free durability.";
        default -> throw new IllegalArgumentException("Unknown armor tool");
    };}
}
