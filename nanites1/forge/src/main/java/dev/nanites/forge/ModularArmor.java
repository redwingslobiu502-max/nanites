package dev.nanites.forge;

import dev.nanites.game.GameService;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Three consumed-install module slots on the worn chest. No client-authoritative effects. */
final class ModularArmor {
    static final Set<String> MODULES=dev.nanites.game.ArmorModules.IDS;
    private final Map<UUID,Long> window=new HashMap<>();
    private final Map<UUID,Float> blocked=new HashMap<>();
    static Set<String> installed(ItemStack chest){
        if(!(chest.getItem() instanceof ArmorItem)||!chest.is(NanitesForge.CHEST.get()))return Set.of();
        CompoundTag tag=chest.getTag();if(tag==null||!tag.contains("NanitesModules",Tag.TAG_LIST))return Set.of();
        ListTag list=tag.getList("NanitesModules",Tag.TAG_STRING);if(list.size()>3)return Set.of();List<String> result=new ArrayList<>();
        for(int i=0;i<list.size();i++)result.add(list.getString(i));try{return dev.nanites.game.ArmorModules.validate(result);}catch(IllegalArgumentException e){return Set.of();}
    }
    static void command(ServerPlayer p,String arg){ItemStack chest=p.getItemBySlot(EquipmentSlot.CHEST);if(!chest.is(NanitesForge.CHEST.get()))throw new IllegalArgumentException("Wear nanites:modular_chestplate");Set<String> slots=installed(chest);if(arg.equals("list")){p.sendSystemMessage(Component.literal("Módulos="+slots));return;}String[] words=arg.split("\\s+");if(words.length!=2||!words[0].equals("install")||!MODULES.contains(words[1]))throw new IllegalArgumentException("armor list | armor install cushioning/impact/thermal (module held)");String module=words[1];ItemStack held=p.getMainHandItem();var item=NanitesForge.MODULE_ITEMS.get(module);String heldId=item!=null&&held.is(item.get())?module:"";Set<String> next=dev.nanites.game.ArmorModules.install(slots,module,heldId,held.getCount());ListTag list=new ListTag();new TreeSet<>(next).forEach(s->list.add(StringTag.valueOf(s)));held.shrink(1);chest.getOrCreateTag().put("NanitesModules",list);p.sendSystemMessage(Component.literal("Módulo instalado e consumido; sem devolução/duplicação. Proteção usa saldo por evento."));}
    private static final List<String> FAMILIES=List.of("impact","fall","fire","projectile");
    static int exposure(CompoundTag root,String family){if(!FAMILIES.contains(family)||!root.contains("NanitesLiving",Tag.TAG_COMPOUND))return 0;var t=root.getCompound("NanitesLiving");if(t.getInt("version")!=1)return 0;return Math.max(0,Math.min(8,t.getInt(family)));}
    static void learn(CompoundTag root,String family){if(!FAMILIES.contains(family))return;var t=root.getCompound("NanitesLiving").copy();if(!t.isEmpty()&&t.getInt("version")!=1)return;t.putInt("version",1);t.putInt(family,Math.min(8,exposure(root,family)+1));root.put("NanitesLiving",t);}
    static void decay(CompoundTag root){if(!root.contains("NanitesLiving",Tag.TAG_COMPOUND))return;var t=root.getCompound("NanitesLiving");if(t.getInt("version")!=1)return;for(String f:FAMILIES)t.putInt(f,Math.max(0,exposure(root,f)-1));}
    static boolean compatible(ItemStack equipment,ItemStack material){return !equipment.isEmpty()&&equipment.isDamageableItem()&&!material.isEmpty()&&equipment.getItem().isValidRepairItem(equipment,material);}
    private static EquipmentSlot slot(String name){return EquipmentSlot.byName(name);}
    private static boolean livingPiece(ItemStack s,EquipmentSlot slot){return switch(slot){case HEAD->s.is(NanitesForge.HELMET.get());case CHEST->s.is(NanitesForge.CHEST.get());case LEGS->s.is(NanitesForge.LEGS.get());case FEET->s.is(NanitesForge.BOOTS.get());default->false;};}
    com.google.gson.JsonObject status(ServerPlayer p){var o=new com.google.gson.JsonObject();if(p==null){o.addProperty("status","OFFLINE");return o;}var equipment=new com.google.gson.JsonArray();for(var slot:EquipmentSlot.values()){var s=p.getItemBySlot(slot);var row=new com.google.gson.JsonObject();row.addProperty("slot",slot.getName());row.addProperty("item",net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).toString());row.addProperty("damage",s.getDamageValue());row.addProperty("maxDamage",s.getMaxDamage());row.addProperty("living",livingPiece(s,slot));equipment.add(row);}o.add("equipment",equipment);var mods=new com.google.gson.JsonArray();installed(p.getItemBySlot(EquipmentSlot.CHEST)).stream().sorted().forEach(mods::add);o.add("modules",mods);var learning=new com.google.gson.JsonObject();var tag=p.getItemBySlot(EquipmentSlot.CHEST).getTag();for(String f:FAMILIES)learning.addProperty(f,tag==null?0:exposure(tag,f));o.add("exposure",learning);o.addProperty("status",p.isAlive()?"READY":"DEAD");return o;}
    com.google.gson.JsonObject repair(net.minecraft.server.MinecraftServer server,ServerPlayer p,com.google.gson.JsonObject a,GameService service){
        dev.nanites.core.MachineTools.validate("armor_repair",a);if(!server.isSameThread()||p==null||!p.isAlive()||p.isRemoved())throw new IllegalStateException("Repair requires live owner on server thread");String key=a.get("key").getAsString();if(!key.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Exact assimilated identity required");var target=p.getItemBySlot(slot(a.get("slot").getAsString()));int damage=target.getDamageValue();
        return dev.nanites.game.LivingArmor.repair(new dev.nanites.game.LivingArmor.RepairPort(){
            Runnable refund;ItemStack material;
            public int damage(){return damage;}public int maxDamage(){return target.isDamageableItem()?target.getMaxDamage():0;}
            public boolean compatible(){material=ForgeAssimilation.preview(server,p.getUUID(),key,1);return ModularArmor.compatible(target,material);}
            public String reserve(){return ForgeAssimilation.reserve(server,p.getUUID(),key,1,"living-armor:"+a.get("slot").getAsString());}
            public void release(String token){ForgeAssimilation.release(server,p.getUUID(),token);}
            public boolean pay(long cost){refund=service.repairPayment(p.getUUID(),cost);return refund!=null;}
            public void refund(long cost){refund.run();}
            public void commit(String token){if(p.getItemBySlot(slot(a.get("slot").getAsString()))!=target||target.getDamageValue()!=damage)throw new IllegalStateException("Equipment changed during repair preflight; no material commit");ForgeAssimilation.commit(server,p.getUUID(),token);}
            public void apply(int points){if(p.getItemBySlot(slot(a.get("slot").getAsString()))!=target||target.getDamageValue()!=damage)throw new IllegalStateException("Equipment changed after material commit");target.setDamageValue(damage-points);if(target.getDamageValue()!=damage-points)throw new IllegalStateException("Item did not expose requested durability change");p.getInventory().setChanged();p.containerMenu.broadcastChanges();}
        },a.get("preview").getAsBoolean());
    }
    float damage(ServerPlayer p,String kind,boolean fire,float amount,long now,GameService service){
        if(amount<=0||!Float.isFinite(amount))return amount;var profile=service.armorProfile(p.getUUID());if(!profile.enabled())return amount;
        ItemStack chest=p.getItemBySlot(EquipmentSlot.CHEST);Set<String> modules=installed(chest);long bucket=now/20;if(window.getOrDefault(p.getUUID(),-1L)!=bucket){window.put(p.getUUID(),bucket);blocked.put(p.getUUID(),0f);}
        if(fire&&!service.insulating(p.getUUID()))return amount;
        float reduction=dev.nanites.game.ArmorModules.reduction(modules,kind,fire,amount,blocked.getOrDefault(p.getUUID(),0f));
        if(profile.adaptive()&&chest.is(NanitesForge.CHEST.get())&&(!chest.hasTag()||!chest.getTag().contains("NanitesLiving")||chest.getTag().contains("NanitesLiving",Tag.TAG_COMPOUND)&&chest.getTag().getCompound("NanitesLiving").getInt("version")==1)&&(!fire||modules.contains("thermal"))){int pieces=0;for(var s:EquipmentSlot.values())if(livingPiece(p.getItemBySlot(s),s))pieces++;String family=fire?"fire":kind;var tag=chest.getOrCreateTag();reduction=dev.nanites.game.LivingArmor.response(reduction,pieces,exposure(tag,family),amount);learn(tag,family);}
        float paid=service.paidProtection(p.getUUID(),reduction);if(paid>0){blocked.merge(p.getUUID(),paid,Float::sum);return amount-paid;}return amount;
    }
    void tick(ServerPlayer p,long tick,GameService service){if(!p.isAlive()||p.isRemoved())return;var chest=p.getItemBySlot(EquipmentSlot.CHEST);if(tick%200==0&&chest.is(NanitesForge.CHEST.get())&&chest.hasTag())decay(chest.getTag());}
    void forget(UUID id){window.remove(id);blocked.remove(id);}
}
