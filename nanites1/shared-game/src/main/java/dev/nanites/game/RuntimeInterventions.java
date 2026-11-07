package dev.nanites.game;

import java.util.*;

/** Data-only leases. All methods must run on the server thread. No persistence/resume. */
public final class RuntimeInterventions {
    public record Profile(double speed, double attack, double armor, double knockback, double guard, double regeneration) {
        public Profile {
            cap(speed,0,.10); cap(attack,0,1); cap(armor,0,2); cap(knockback,0,.1); cap(guard,0,4); cap(regeneration,0,1);
        }
        private static void cap(double n,double lo,double hi){if(!Double.isFinite(n)||n<lo||n>hi)throw new IllegalArgumentException("Profile hard cap");}
        public boolean attributes(){return speed+attack+armor+knockback>0;}
    }
    public static final Profile EXTREMIS=new Profile(.10,1,0,0,0,1);
    public interface Backend {
        boolean available(UUID owner);
        void validate(UUID owner, Profile profile);
        void overlay(UUID owner, UUID modifier, Profile profile);
        void remove(UUID owner, UUID modifier);
        void heal(UUID owner, double hp);
        boolean pay(UUID owner,long cost);
        void refund(UUID owner,long cost);
        default void settled(UUID owner) {}
    }
    public record Lease(String profile, UUID modifier, long end, Profile definition, double remainingGuard) {}
    public record Grant(long serial,long end,int calls,long remaining) {}
    private final Backend backend;
    private final Map<UUID,Map<String,Profile>> profiles=new HashMap<>();
    private final Map<UUID,Lease> leases=new HashMap<>();
    private final Map<UUID,Long> pulses=new HashMap<>();
    private final Map<UUID,Grant> grants=new HashMap<>();
    private final Set<UUID> opted=new HashSet<>();
    private long serial;
    public RuntimeInterventions(Backend backend){this.backend=Objects.requireNonNull(backend);}
    public static long cost(Profile p,int seconds){if(seconds<1||seconds>60)throw new IllegalArgumentException("Lease 1..60 seconds");return Math.addExact(1_000_000L,Math.multiplyExact(seconds,Math.max(1,(long)Math.ceil((p.speed()*100+p.attack()+p.armor()+p.knockback()*100+p.guard()+p.regeneration())*1_000_000))));}
    public void define(UUID owner,String id,Profile profile){if(!id.matches("p[0-7]"))throw new IllegalArgumentException("Profile slots p0..p7");if(leases.containsKey(owner))throw new IllegalStateException("Cancel active lease before redefining");profiles.computeIfAbsent(owner,k->new HashMap<>()).put(id,profile);}
    public Profile profile(UUID owner,String id){if(id.equals("extremis")){if(!opted.contains(owner))throw new SecurityException("Extremis explicit opt-in required");return EXTREMIS;}Profile p=profiles.getOrDefault(owner,Map.of()).get(id);if(p==null)throw new IllegalArgumentException("Undefined runtime profile");return p;}
    public Map<String,Profile> profiles(UUID owner){return Map.copyOf(profiles.getOrDefault(owner,Map.of()));}
    public Lease lease(UUID owner){return leases.get(owner);}
    public void opt(UUID owner,boolean enable){if(enable)opted.add(owner);else{opted.remove(owner);cancel(owner);}}
    public void start(UUID owner,String id,int seconds,long now){
        Profile p=profile(owner,id);if(leases.containsKey(owner))throw new IllegalStateException("One lease per owner; no reemit/stacking");
        if(!backend.available(owner))throw new IllegalStateException("Target unavailable");backend.validate(owner,p);
        long end=Math.addExact(now,seconds*20L),price=cost(p,seconds);if(!backend.pay(owner,price))throw new IllegalStateException("Insufficient nanites");
        UUID modifier=UUID.randomUUID();
        try{backend.overlay(owner,modifier,p);leases.put(owner,new Lease(id,modifier,end,p,p.guard()*seconds));pulses.put(owner,now);backend.settled(owner);}
        catch(RuntimeException e){leases.remove(owner);pulses.remove(owner);try{backend.remove(owner,modifier);}catch(RuntimeException cleanup){e.addSuppressed(cleanup);}finally{backend.refund(owner,price);}throw e;} // Only failed activation is compensated; cancel never refunds.
    }
    public void tick(UUID owner,long now){Lease l=leases.get(owner);if(l==null)return;if(now>=l.end()||!backend.available(owner)){cancel(owner);return;}if(now-pulses.getOrDefault(owner,now)>=20&&l.definition().regeneration()>0){pulses.put(owner,now);backend.heal(owner,l.definition().regeneration());}}
    public float damage(UUID owner,float amount){Lease l=leases.get(owner);if(l==null||!Float.isFinite(amount)||amount<=0)return amount;double blocked=Math.min(amount,Math.min(l.definition().guard(),l.remainingGuard()));leases.put(owner,new Lease(l.profile(),l.modifier(),l.end(),l.definition(),l.remainingGuard()-blocked));return (float)Math.max(0,amount-blocked);}
    public void cancel(UUID owner){pulses.remove(owner);Lease l=leases.remove(owner);if(l!=null)backend.remove(owner,l.modifier());}
    public Grant grant(UUID owner,int seconds,int calls,long budget,long now){if(seconds<1||seconds>60||calls<1||calls>8||budget<1||budget>100_000_000L)throw new IllegalArgumentException("Grant bounded: 1..60s, 1..8 calls, 1..100000000 nanites");revoke(owner);Grant g=new Grant(++serial,now+seconds*20L,calls,budget);grants.put(owner,g);return g;}
    public Grant grant(UUID owner,long now){Grant g=grants.get(owner);return g!=null&&now<g.end()&&g.calls()>0?g:null;}
    public void authorize(UUID owner,long token,long now,long price){Grant g=grant(owner,now);if(g==null||g.serial()!=token||price<0||price>g.remaining())throw new SecurityException("Grant revoked/expired/exhausted");grants.put(owner,new Grant(g.serial(),g.end(),g.calls()-1,g.remaining()-price));}
    public void revoke(UUID owner){grants.remove(owner);cancel(owner);}
    public void forget(UUID owner){revoke(owner);profiles.remove(owner);opted.remove(owner);}
    public void close(){for(UUID owner:List.copyOf(leases.keySet()))cancel(owner);grants.clear();profiles.clear();opted.clear();}
}
