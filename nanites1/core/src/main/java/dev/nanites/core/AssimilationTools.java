package dev.nanites.core;

import com.google.gson.*;
import java.util.*;

/** Stage 2 contracts. Inventory slots refer exclusively to the owner's main inventory. */
public final class AssimilationTools {
    private AssimilationTools() {}
    public static final Set<String> NAMES=Set.of("material_inspect","assimilate_material","material_stock","recover_material","technology_scan","technology_assimilate","technology_list","recipe_search");
    public static JsonObject schema(String name){
        var p=new JsonObject();
        switch(name){
            case "material_inspect" -> {p.add("query",MachineTools.text());p.add("registry",MachineTools.scalar("boolean"));page(p);}
            case "assimilate_material" -> {p.add("slot",MachineTools.number(0,35));p.add("item",MachineTools.text());p.add("identity",MachineTools.text());p.add("amount",MachineTools.number(1,64));}
            case "material_stock","technology_list" -> page(p);
            case "recover_material" -> {p.add("key",MachineTools.text());p.add("amount",MachineTools.number(1,2304));}
            case "technology_scan" -> {p.add("target",MachineTools.target());page(p);}
            case "technology_assimilate" -> {p.add("target",MachineTools.target());p.add("project",MachineTools.text());p.add("name",MachineTools.text());p.add("source",MachineTools.text());}
            case "recipe_search" -> {p.add("query",MachineTools.text());p.add("type",MachineTools.text());page(p);}
            default -> throw new IllegalArgumentException("Unknown assimilation tool");
        }
        return MachineTools.object(p,p.keySet().toArray(String[]::new));
    }
    private static void page(JsonObject p){p.add("offset",MachineTools.number(0,Integer.MAX_VALUE));p.add("limit",MachineTools.number(1,8));}
    public static String description(String name){return switch(name){
        case "material_inspect" -> "Observe real owner inventory (registry=false) or installed item registry (registry=true, default stacks). query matches item ID/tag. Paginated 1..8. Evidence/traits are API observations, not inferred physics. Inventory returns slot/item/identity for exact assimilation.";
        case "assimilate_material" -> "Consume exact amount 1..64 from owner's main slot only after item ID and full identity hash validation. Adds durable per-world/UUID stock with original NBT, evidence, provenance. No energy/armor granted. Use inventory material_inspect first.";
        case "material_stock" -> "Durable material stock and observed traits, quantities available/reserved, recovery key. Paginated 1..8; pool usable by future repair/protection API, not current armor bonuses.";
        case "recover_material" -> "Withdraw exact available amount and restore original item NBT to main inventory. Validates whole capacity before debit; full inventory/reservation rejects without mutation.";
        case "technology_scan" -> "Observe loaded block state/tags, sided capabilities, installed mod version, paginated RecipeManager metadata. Recipe presence does NOT prove this block executes it. No force-load/private machine API invented.";
        case "technology_assimilate" -> "Register observed technology plus validated operational declarative machine adapter linked by project/name. source is stage1 adapter JSON; empty source creates version1 inspect-only integration, NOT manufacturing. No copied mod code. Explicit errors for unavailable APIs; recipes require increasing version for edits.";
        case "technology_list" -> "Paginated durable observed technologies, adapter link and recipe blueprint. Integration remains executable using adapter_execute/load without game reload. Capability/recipe observations may become stale; scan again.";
        case "recipe_search" -> "Search all accessible RecipeManager recipes by ID, input item ID/tag, output ID/tag; type empty means any. Paginated 1..8. Generic inputs are public Ingredient.toJson; output uses registry access. Private conditions/semantics explicitly unknown; never universal manufacturing execution.";
        default -> throw new IllegalArgumentException("Unknown assimilation tool");
    };}
}
