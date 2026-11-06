package dev.jevmod.body;

import com.google.gson.JsonObject;
import dev.jevmod.core.Json;
import java.util.UUID;

/** Last accepted hit. Lives with the body and bound Mind, rather than an unbounded global UUID cache. */
public record DamageRecord(String type,String damageType,String attackerType,UUID attackerId,
                           float amount,float requestedAmount,long tick,double x,double y,double z,boolean lethal) {
    public JsonObject json(){var o=new JsonObject();o.addProperty("type",type);o.addProperty("damage_type",damageType);
        o.addProperty("attacker_type",attackerType);if(attackerId==null)o.add("attacker_id",com.google.gson.JsonNull.INSTANCE);else o.addProperty("attacker_id",attackerId.toString());
        o.addProperty("amount",amount);o.addProperty("requested_amount",requestedAmount);o.addProperty("tick",tick);
        o.addProperty("x",x);o.addProperty("y",y);o.addProperty("z",z);o.addProperty("lethal",lethal);return o;}
    public static DamageRecord parse(JsonObject o){String id=Json.str(o,"attacker_id","");return new DamageRecord(Json.str(o,"type","unknown"),Json.str(o,"damage_type",""),Json.str(o,"attacker_type","environment"),id.isBlank()?null:UUID.fromString(id),(float)Json.num(o,"amount",0),(float)Json.num(o,"requested_amount",0),o.get("tick").getAsLong(),Json.num(o,"x",0),Json.num(o,"y",0),Json.num(o,"z",0),Json.bool(o,"lethal",false));}
}
