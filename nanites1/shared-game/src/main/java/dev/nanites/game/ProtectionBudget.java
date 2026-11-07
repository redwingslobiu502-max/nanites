package dev.nanites.game;

import java.util.*;

/** One server-tick bucket per owner shared by script shields, modules and runtime guard. */
public final class ProtectionBudget {
    private record Window(long bucket,float used) {}
    private final Map<UUID,Window> windows=new HashMap<>();
    public float available(UUID owner,long tick){Window w=windows.get(owner);return w==null||w.bucket()!=Math.floorDiv(tick,20)?8:Math.max(0,8-w.used());}
    public float limit(UUID owner,long tick,float requested){return !Float.isFinite(requested)||requested<=0?0:Math.min(requested,available(owner,tick));}
    public void consume(UUID owner,long tick,float amount){float n=limit(owner,tick,amount);windows.put(owner,new Window(Math.floorDiv(tick,20),8-available(owner,tick)+n));}
    public void forget(UUID owner){windows.remove(owner);}
}
