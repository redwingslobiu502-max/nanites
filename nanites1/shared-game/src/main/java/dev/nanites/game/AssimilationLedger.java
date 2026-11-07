package dev.nanites.game;

import com.google.gson.*;
import dev.nanites.core.SafeData;
import java.util.*;

/** Copy-on-write per-world stock ledger. Call only on the host/server thread.
 * Inventory.set must be an infallible main-inventory assignment after validation (NOT a mod capability).
 * Normal exceptions never replace last-known-good state. SavedData/inventory disk writes are NOT crash ACID.
 */
public final class AssimilationLedger {
    public record Sample(String item,String key,String payload,int amount,JsonObject profile,JsonObject provenance,int maxStack) {
        public Sample {Objects.requireNonNull(item);Objects.requireNonNull(key);Objects.requireNonNull(payload);profile=profile.deepCopy();provenance=provenance.deepCopy();if(amount<0||maxStack<1)throw new IllegalArgumentException("Invalid sample");}
        public Sample sized(int count){return new Sample(item,key,payload,count,profile,provenance,maxStack);}
        @Override public JsonObject profile(){return profile.deepCopy();}
        @Override public JsonObject provenance(){return provenance.deepCopy();}
    }
    public interface Inventory {int size();Sample get(int slot);default void prepare(Map<Integer,Sample> assignments){}void set(int slot,Sample sample);}
    private JsonObject state=new JsonObject();
    private final Runnable dirty;
    public AssimilationLedger(Runnable dirty){this.dirty=Objects.requireNonNull(dirty);state.addProperty("version",1);state.add("owners",new JsonObject());}
    public JsonObject save(){return state.deepCopy();}
    public static AssimilationLedger load(JsonObject input,Runnable dirty){var ledger=new AssimilationLedger(dirty);validate(input);ledger.state=input.deepCopy();return ledger;}
    private static void count(long n){if(n<1||n>1_000_000_000L)throw new IllegalArgumentException("Quantity 1..1000000000 required");}
    private static void profile(JsonObject p){if(!p.has("traits")||!p.get("traits").isJsonObject()||!p.has("evidence")||!p.get("evidence").isJsonArray()||p.getAsJsonArray("evidence").isEmpty()||p.toString().length()>8192)throw new IllegalArgumentException("Bounded traits and actual evidence required");}
    private static void validate(JsonObject root){
        if(SafeData.integer(root.get("version"))!=1)throw new IllegalArgumentException("Unknown ledger version");
        var owners=root.getAsJsonObject("owners");if(owners.size()>4096||root.toString().length()>16_777_216)throw new IllegalArgumentException("World ledger quota");
        for(var owner:owners.entrySet()){UUID.fromString(owner.getKey());var o=owner.getValue().getAsJsonObject();var materials=o.getAsJsonObject("materials");if(materials.size()>256||o.getAsJsonObject("technologies").size()>128||o.getAsJsonObject("reservations").size()>128)throw new IllegalArgumentException("Owner ledger quota");
            for(var e:materials.entrySet()){var row=e.getValue().getAsJsonObject();if(!e.getKey().matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Identity SHA256 required");long q=SafeData.integer(row.get("quantity")),in=SafeData.integer(row.get("deposited")),out=SafeData.integer(row.get("withdrawn"));if(q<0||q>1_000_000_000L||in<0||out<0||in-out!=q)throw new IllegalArgumentException("Stock accounting invalid");profile(row.getAsJsonObject("profile"));if(row.get("payload").getAsString().length()>16384||row.get("item").getAsString().isBlank()||row.get("max_stack").getAsInt()<1)throw new IllegalArgumentException("Invalid payload");}
            var held=new HashMap<String,Long>();for(var e:o.getAsJsonObject("reservations").entrySet()){UUID.fromString(e.getKey());var r=e.getValue().getAsJsonObject();long n=SafeData.integer(r.get("amount"));count(n);String key=r.get("key").getAsString();if(!materials.has(key))throw new IllegalArgumentException("Missing reserved material");held.merge(key,n,Math::addExact);}
            for(var e:held.entrySet())if(e.getValue()>materials.getAsJsonObject(e.getKey()).get("quantity").getAsLong())throw new IllegalArgumentException("Over-reservation");
            for(var e:o.getAsJsonObject("technologies").entrySet())technologyValid(e.getValue().getAsJsonObject());
        }
    }
    private JsonObject owner(UUID id){var all=state.getAsJsonObject("owners");return all.has(id.toString())?all.getAsJsonObject(id.toString()):empty();}
    private static JsonObject empty(){var o=new JsonObject();for(String k:List.of("materials","reservations","technologies"))o.add(k,new JsonObject());o.addProperty("revision",0);return o;}
    private void publish(UUID id,JsonObject candidate){var next=state.deepCopy();candidate.addProperty("revision",Math.addExact(owner(id).get("revision").getAsLong(),1));next.getAsJsonObject("owners").add(id.toString(),candidate);validate(next);state=next;dirty.run();}
    private static long held(JsonObject o,String key){long n=0;for(var e:o.getAsJsonObject("reservations").entrySet()){var r=e.getValue().getAsJsonObject();if(r.get("key").getAsString().equals(key))n+=r.get("amount").getAsLong();}return n;}
    private static JsonObject row(JsonObject o,String key){var rows=o.getAsJsonObject("materials");if(!rows.has(key))throw new IllegalArgumentException("Unknown stock key");return rows.getAsJsonObject(key);}
    public long available(UUID id,String key){var o=owner(id);return row(o,key).get("quantity").getAsLong()-held(o,key);}
    public JsonObject traits(UUID id,String key){return row(owner(id),key).getAsJsonObject("profile").deepCopy();}
    private static JsonObject credit(JsonObject o,Sample s,int amount){
        count(amount);profile(s.profile);if(s.amount<amount||s.payload.length()>16384||s.provenance.toString().length()>1024||!s.key.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid item sample");
        var rows=o.getAsJsonObject("materials");var r=rows.has(s.key)?rows.getAsJsonObject(s.key):new JsonObject();
        if(rows.has(s.key)&&(!r.get("item").getAsString().equals(s.item)||!r.get("payload").getAsString().equals(s.payload)))throw new IllegalArgumentException("Identity collision/payload mismatch");
        long q=rows.has(s.key)?r.get("quantity").getAsLong():0,in=rows.has(s.key)?r.get("deposited").getAsLong():0,out=rows.has(s.key)?r.get("withdrawn").getAsLong():0;
        r.addProperty("item",s.item);r.addProperty("payload",s.payload);r.addProperty("max_stack",s.maxStack);r.addProperty("quantity",Math.addExact(q,amount));r.addProperty("deposited",Math.addExact(in,amount));r.addProperty("withdrawn",out);r.add("profile",s.profile());
        var history=r.has("provenance")?r.getAsJsonArray("provenance"):new JsonArray();var receipt=s.provenance();receipt.addProperty("amount",amount);history.add(receipt);while(history.size()>8)history.remove(0);r.add("provenance",history);rows.add(s.key,r);return r;
    }
    /** Caller transfers ownership of actual resources, not a mint endpoint. Forge wrapper shrinks stack. */
    public void deposit(UUID id,Sample actual,int amount){var o=owner(id).deepCopy();credit(o,actual,amount);publish(id,o);}
    public void consume(UUID id,Inventory inv,int slot,String item,String identity,int amount){
        count(amount);if(slot<0||slot>=inv.size())throw new IllegalArgumentException("Slot outside main inventory");var s=inv.get(slot);if(s==null||!s.item.equals(item)||!s.key.equals(identity)||s.amount<amount)throw new IllegalArgumentException("Inventory identity/quantity changed; inspect again");
        var candidate=owner(id).deepCopy();credit(candidate,s,amount);prevalidate(id,candidate);var assignment=Map.of(slot,s.sized(s.amount-amount));inv.prepare(assignment);inv.set(slot,assignment.get(slot));publish(id,candidate);
    }
    private void prevalidate(UUID id,JsonObject o){Math.addExact(owner(id).get("revision").getAsLong(),1);var next=state.deepCopy();next.getAsJsonObject("owners").add(id.toString(),o);validate(next);}
    private static Sample sample(JsonObject r,int amount){var history=r.getAsJsonArray("provenance");return new Sample(r.get("item").getAsString(),r.get("key").getAsString(),r.get("payload").getAsString(),amount,r.getAsJsonObject("profile"),history.isEmpty()?new JsonObject():history.get(history.size()-1).getAsJsonObject(),r.get("max_stack").getAsInt());}
    private static Sample debit(JsonObject o,String key,int amount,boolean reserved){count(amount);var r=row(o,key);long q=r.get("quantity").getAsLong();if(amount>q-(reserved?0:held(o,key)))throw new IllegalArgumentException("Insufficient available stock");var copy=r.deepCopy();copy.addProperty("key",key);var s=sample(copy,amount);r.addProperty("quantity",q-amount);r.addProperty("withdrawn",Math.addExact(r.get("withdrawn").getAsLong(),amount));return s;}
    public Sample peek(UUID id,String key,int amount){var o=owner(id).deepCopy();return debit(o,key,amount,false);}
    public Sample reserved(UUID id,String token){var o=owner(id);var e=o.getAsJsonObject("reservations").get(token);if(e==null)throw new IllegalArgumentException("Unknown owner reservation");var r=e.getAsJsonObject();var material=row(o,r.get("key").getAsString()).deepCopy();material.addProperty("key",r.get("key").getAsString());return sample(material,Math.toIntExact(SafeData.integer(r.get("amount"))));}
    public Sample withdraw(UUID id,String key,int amount){var o=owner(id).deepCopy();var s=debit(o,key,amount,false);publish(id,o);return s;}
    public String reserve(UUID id,String key,int amount,String purpose){count(amount);if(purpose==null||purpose.isBlank()||purpose.length()>128)throw new IllegalArgumentException("Purpose 1..128");if(available(id,key)<amount)throw new IllegalArgumentException("Insufficient available stock");var o=owner(id).deepCopy();String token=UUID.randomUUID().toString();var r=new JsonObject();r.addProperty("key",key);r.addProperty("amount",amount);r.addProperty("purpose",purpose);o.getAsJsonObject("reservations").add(token,r);publish(id,o);return token;}
    public JsonObject reservations(UUID id){return owner(id).getAsJsonObject("reservations").deepCopy();}
    public void release(UUID id,String token){var o=owner(id).deepCopy();if(o.getAsJsonObject("reservations").remove(token)==null)throw new IllegalArgumentException("Unknown owner reservation");publish(id,o);}
    public Sample commit(UUID id,String token){var o=owner(id).deepCopy();var e=o.getAsJsonObject("reservations").remove(token);if(e==null)throw new IllegalArgumentException("Unknown owner reservation");var r=e.getAsJsonObject();var s=debit(o,r.get("key").getAsString(),Math.toIntExact(SafeData.integer(r.get("amount"))),false);publish(id,o);return s;}
    public void recover(UUID id,Inventory inv,String key,int amount){
        var candidate=owner(id).deepCopy();var s=debit(candidate,key,amount,false);int remaining=amount;var assignments=new LinkedHashMap<Integer,Sample>();
        for(int pass=0;pass<2;pass++)for(int slot=0;slot<inv.size()&&remaining>0;slot++){var old=inv.get(slot);boolean empty=old==null||old.amount==0;if(pass==0?(empty||!old.key.equals(key)):!empty)continue;int before=empty?0:old.amount;int n=Math.min(remaining,Math.max(0,s.maxStack-before));if(n>0){assignments.put(slot,s.sized(before+n));remaining-=n;}}
        if(remaining>0)throw new IllegalArgumentException("Main inventory capacity insufficient; no debit");prevalidate(id,candidate);inv.prepare(assignments);for(var e:assignments.entrySet())inv.set(e.getKey(),e.getValue());publish(id,candidate);
    }
    private static JsonObject page(JsonObject entries,int offset,int limit,boolean materials,JsonObject owner){
        if(offset<0||limit<1||limit>8)throw new IllegalArgumentException("Page 0+, limit 1..8");var keys=entries.keySet().stream().sorted().toList();var a=new JsonArray();int cursor=Math.min(offset,keys.size()),chars=0;
        while(cursor<keys.size()&&a.size()<limit){String key=keys.get(cursor);var r=entries.getAsJsonObject(key).deepCopy();r.addProperty("key",key);
            if(materials){r.remove("payload");long reserved=held(owner,key);r.addProperty("reserved",reserved);r.addProperty("available",r.get("quantity").getAsLong()-reserved);var history=r.getAsJsonArray("provenance");r.addProperty("retained_provenance_count",history.size());r.add("provenance",history.isEmpty()?new JsonObject():history.get(history.size()-1).deepCopy());}
            else {var observation=r.getAsJsonObject("observation");observation.remove("recipes");var blueprint=r.getAsJsonObject("blueprint");r.addProperty("blueprint_version",blueprint.get("version").getAsInt());r.addProperty("blueprint_steps",blueprint.getAsJsonArray("steps").size());if(r.toString().length()>12000){r.remove("blueprint");r.addProperty("blueprint_omitted",true);r.addProperty("blueprint_access","Persistent adapter: adapter_load/adapter_execute by project/name; not a private manufacturing recipe");}}
            int size=r.toString().length();if(size>12000)throw new IllegalStateException("Record exceeds tool page bound; use loader API");if(chars+size>12000)break;a.add(r);chars+=size;cursor++;
        }
        var o=new JsonObject();o.add("entries",a);o.addProperty("total",keys.size());o.addProperty("next_offset",cursor);o.addProperty("complete",cursor>=keys.size());o.addProperty("revision",owner.get("revision").getAsLong());return o;
    }
    public JsonObject stock(UUID id,int offset,int limit){var o=owner(id);return page(o.getAsJsonObject("materials"),offset,limit,true,o);}
    public JsonObject technologies(UUID id,int offset,int limit){var o=owner(id);return page(o.getAsJsonObject("technologies"),offset,limit,false,o);}
    private static void technologyValid(JsonObject record){if(record.toString().length()>65536||!record.has("observation")||!record.has("blueprint")||!record.has("project")||!record.has("name"))throw new IllegalArgumentException("Invalid bounded technology record");MachineAdapters.validate(record.getAsJsonObject("blueprint").toString());}
    public void validateTechnologyCandidate(UUID id,String key,JsonObject record){technologyValid(record);var candidate=owner(id).deepCopy();candidate.getAsJsonObject("technologies").add(key,record.deepCopy());prevalidate(id,candidate);}
    public void technology(UUID id,String key,JsonObject record){technologyValid(record);var o=owner(id).deepCopy();o.getAsJsonObject("technologies").add(key,record.deepCopy());publish(id,o);}
    public JsonObject live(UUID id){var o=owner(id);var result=new JsonObject();result.addProperty("revision",o.get("revision").getAsLong());result.addProperty("material_identities",o.getAsJsonObject("materials").size());result.addProperty("technologies",o.getAsJsonObject("technologies").size());result.addProperty("reservation_count",o.getAsJsonObject("reservations").size());long total=0,reserved=0;for(var e:o.getAsJsonObject("materials").entrySet()){total+=e.getValue().getAsJsonObject().get("quantity").getAsLong();reserved+=held(o,e.getKey());}result.addProperty("repair_protection_stock_units",total-reserved);result.addProperty("reserved_units",reserved);result.addProperty("energy_granted",0);return result;}
}
