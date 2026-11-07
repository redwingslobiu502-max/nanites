package dev.nanites.forge;

import dev.nanites.game.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import java.io.*;

/** Actual NBT, ItemStack and vanilla repair predicates. Bootstrap registries only; never launch Minecraft. */
public final class ForgeLivingArmorTests {
    static int checks;static void check(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
    public static void main(String[] args)throws Exception {
        var root=TagParser.parseTag("{NanitesModules:[\"impact\",\"thermal\"],display:{Name:\"preserve me\"},Damage:18,ForgeCaps:{example:{charge:200L}}}");var original=root.copy();
        ModularArmor.learn(root,"impact");check(ModularArmor.exposure(root,"impact")==1&&ModularArmor.exposure(root,"fire")==0,"family-specific learning");
        float first=LivingArmor.reduction(4,ModularArmor.exposure(root,"impact"),4);for(int i=0;i<20;i++)ModularArmor.learn(root,"impact");check(ModularArmor.exposure(root,"impact")==8&&LivingArmor.reduction(4,8,4)>first,"adaptation increases bounded damage response");
        check(root.getList("NanitesModules",Tag.TAG_STRING).equals(original.getList("NanitesModules",Tag.TAG_STRING))&&root.getCompound("display").equals(original.getCompound("display"))&&root.getCompound("ForgeCaps").equals(original.getCompound("ForgeCaps"))&&root.getInt("Damage")==18,"modules, name, durability and capabilities retained");
        var bytes=new ByteArrayOutputStream();NbtIo.write(root,new DataOutputStream(bytes));var restored=NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));check(ModularArmor.exposure(restored,"impact")==8,"binary NBT restart persists exposure");
        ModularArmor.decay(restored);check(ModularArmor.exposure(restored,"impact")==7,"wear tick decay");for(int i=0;i<30;i++)ModularArmor.decay(restored);check(ModularArmor.exposure(restored,"impact")==0,"decay clamps zero");
        root.getCompound("NanitesLiving").putInt("fire",9999);check(ModularArmor.exposure(root,"fire")==8,"malformed count bounded");root.getCompound("NanitesLiving").putInt("version",9);var unknown=root.copy();ModularArmor.learn(root,"fire");ModularArmor.decay(root);check(root.equals(unknown)&&ModularArmor.exposure(root,"fire")==0,"unknown future NBT not overwritten/activated");
        // Isolated JavaExec fixture: bypass only the bootstrap guard, not item behavior.
        // Full Bootstrap invokes Forge NetworkHooks, which require launcher event transforms.
        // No launcher, network, world, client or server is started here.
        net.minecraft.SharedConstants.tryDetectVersion();
        var guard=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");guard.setAccessible(true);guard.setBoolean(null,true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        var armor=new ItemStack(Items.IRON_CHESTPLATE);armor.setDamageValue(30);armor.getOrCreateTag().putString("custom","preserved");
        check(ModularArmor.compatible(armor,new ItemStack(Items.IRON_INGOT)),"actual iron repair predicate");check(!ModularArmor.compatible(armor,new ItemStack(Items.DIAMOND)),"actual incompatible repair predicate");check(!ModularArmor.compatible(new ItemStack(Items.STICK),new ItemStack(Items.IRON_INGOT)),"nondamageable refused");
        var encoded=armor.save(new CompoundTag());var equipment=ItemStack.of(encoded);check(equipment.getDamageValue()==30&&equipment.getTag().getString("custom").equals("preserved"),"equipment NBT roundtrip");
        int points=LivingArmor.repairPoints(equipment.getDamageValue(),equipment.getMaxDamage());equipment.setDamageValue(equipment.getDamageValue()-points);check(equipment.getDamageValue()==0&&equipment.getTag().getString("custom").equals("preserved"),"real durability setter preserves unrelated NBT");
        System.out.println("ForgeLivingArmorTests: "+checks+" checks passed; actual NBT/items, registry bootstrap only; Minecraft NOT launched");
    }
}
