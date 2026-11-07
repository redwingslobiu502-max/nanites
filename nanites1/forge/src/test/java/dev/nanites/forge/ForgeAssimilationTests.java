package dev.nanites.forge;

import com.google.gson.*;
import dev.nanites.game.AssimilationLedger;
import net.minecraft.nbt.*;
import java.io.*;
import java.util.*;

/** Actual Minecraft NBT and SavedData encoding; no server/client/world launch. */
public final class ForgeAssimilationTests {
    static int passed;static void check(boolean b,String why){if(!b)throw new AssertionError(why);passed++;}
    static void denied(Runnable r,String why){try{r.run();throw new AssertionError(why);}catch(RuntimeException expected){passed++;}}
    static JsonObject json(String s){return JsonParser.parseString(s).getAsJsonObject();}
    public static void main(String[] args)throws Exception {
        var first=TagParser.parseTag("{id:\"example:metal\",Count:1b,tag:{Damage:12,display:{Name:\"original 🧪\"},ench:[{lvl:3s,id:\"minecraft:unbreaking\"}],bytes:[B;1b,2b],ints:[I;4,5],longs:[L;8L,9L]},ForgeCaps:{example:{stored:900L}}}");
        var reordered=TagParser.parseTag("{ForgeCaps:{example:{stored:900L}},tag:{longs:[L;8L,9L],bytes:[B;1b,2b],ints:[I;4,5],ench:[{id:\"minecraft:unbreaking\",lvl:3s}],display:{Name:\"original 🧪\"},Damage:12},Count:1b,id:\"example:metal\"}");
        check(ForgeAssimilation.canonical(first).equals(ForgeAssimilation.canonical(reordered)),"compound order has stable identity including nested compounds");check(first.equals(TagParser.parseTag(ForgeAssimilation.canonical(first))),"full actual SNBT roundtrip preserves numeric types, arrays, enchantments, custom name and ForgeCaps");var changed=first.copy();changed.getCompound("tag").putInt("Damage",13);check(!ForgeAssimilation.canonical(first).equals(ForgeAssimilation.canonical(changed)),"durability NBT identity distinct");
        var data=new ForgeAssimilation.Data();UUID owner=UUID.fromString("00000000-0000-0000-0000-000000000001");var profile=json("{\"traits\":{\"tag_membership\":[\"forge:metals\"]},\"evidence\":[{\"trait\":\"tag_membership\",\"source\":\"observed-test-registry\"}]}");
        for(int i=0;i<12;i++){var payload=first.copy();payload.getCompound("tag").putInt("variant",i);payload.getCompound("tag").putString("large",("NBT-original-🧪"+i).repeat(400));String key=String.format("%064x",i+1);var sample=new AssimilationLedger.Sample("example:metal",key,ForgeAssimilation.canonical(payload),10,profile,json("{\"origin\":\"inventory\",\"mod_version\":\"test-observed\"}"),64);data.checked().deposit(owner,sample,10);}
        String token=data.checked().reserve(owner,String.format("%064x",1),3,"test:repair");var serialized=data.save(new CompoundTag());check(serialized.getList("ledger",Tag.TAG_STRING).size()>1,"SavedData chunks avoid NBT UTF string length limit");check(serialized.toString().length()>65535,"test actually exceeds single UTF payload limit");
        var bytes=new ByteArrayOutputStream();NbtIo.write(serialized,new DataOutputStream(bytes));var disk=NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));var loaded=ForgeAssimilation.Data.load(disk);check(data.checked().save().equals(loaded.checked().save()),"real NBT binary SavedData roundtrip all original payloads/profiles/stock");check(loaded.checked().reserved(owner,token).amount()==3,"SavedData restart retains reservations");loaded.checked().commit(owner,token);denied(()->loaded.checked().commit(owner,token),"persistent token debited exactly once");
        var corrupt=new CompoundTag();corrupt.putInt("format",1);var chunks=new ListTag();chunks.add(StringTag.valueOf("{\"version\":1,\"version\":2,\"owners\":{}}"));corrupt.put("ledger",chunks);var invalid=ForgeAssimilation.Data.load(corrupt);denied(invalid::checked,"corrupt ledger remains disabled instead of silent empty reset");denied(()->invalid.save(new CompoundTag()),"corrupt SavedData cannot overwrite original");denied(()->ForgeAssimilation.Data.load(new CompoundTag()).checked(),"missing SavedData format refuses reset");
        System.out.println("ForgeAssimilationTests: "+passed+" checks passed (actual NBT/SavedData objects; Minecraft NOT launched)");
    }
}
