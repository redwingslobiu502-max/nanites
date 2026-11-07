package dev.nanites.game;

import java.util.*;

/** Pure module policy shared by server NBT adapter and deterministic tests. */
public final class ArmorModules {
    private ArmorModules(){}
    public static final Set<String> IDS=Set.of("cushioning","impact","thermal");
    public static Set<String> validate(List<String> names){if(names.size()>3||!IDS.containsAll(names)||new HashSet<>(names).size()!=names.size())throw new IllegalArgumentException("Invalid module slots");return Set.copyOf(names);}
    public static Set<String> install(Set<String> installed,String id,String held,int count){validate(List.copyOf(installed));if(!IDS.contains(id)||!id.equals(held)||count<1||installed.contains(id)||installed.size()>=3)throw new IllegalArgumentException("Matching consumed module and unique free slot required");var next=new HashSet<>(installed);next.add(id);return Set.copyOf(next);}
    public static float limit(Set<String> installed,String damage,boolean fire){if(fire)return installed.contains("thermal")?2:0;if(damage.equals("fall"))return installed.contains("cushioning")?4:0;return installed.contains("impact")?2:0;}
    public static float reduction(Set<String> installed,String damage,boolean fire,float amount,float alreadyBlocked){if(!Float.isFinite(amount)||!Float.isFinite(alreadyBlocked)||amount<=0||alreadyBlocked<0)return 0;return Math.max(0,Math.min(amount,Math.min(limit(installed,damage,fire),8-alreadyBlocked)));}
}
