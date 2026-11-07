package dev.nanites.game;

import com.google.gson.*;
import dev.nanites.core.SafeData;
import java.util.*;

/** Registry state strings are resolved by the loader, never by a fixed material allowlist. */
public record Blueprint(Origin origin, List<Voxel> voxels) {
    public record Origin(String dimension, int x, int y, int z) {}
    public record Pos(int x, int y, int z) {}
    public record Voxel(Pos relative, String block) {}
    public Blueprint { voxels=List.copyOf(voxels); }
    public Pos absolute(Voxel v) {
        return new Pos(Math.addExact(origin.x(),v.relative().x()),Math.addExact(origin.y(),v.relative().y()),Math.addExact(origin.z(),v.relative().z()));
    }
    private static int integer(JsonObject o,String key) {try{return Math.toIntExact(SafeData.integer(o.get(key)));}catch(RuntimeException e){throw new IllegalArgumentException("Inteiro 32-bit exigido em "+key+": "+e.getMessage(),e);}}
    private static void keys(JsonObject o,String... names) {
        if(!o.keySet().equals(Set.of(names)))throw new IllegalArgumentException("Campos exatos exigidos: "+String.join(",",names));
    }
    private static Pos pos(JsonObject o) {keys(o,"x","y","z");return new Pos(integer(o,"x"),integer(o,"y"),integer(o,"z"));}
    private static String block(JsonObject o) {
        String s=SafeData.text(o,"block",512);
        if(!s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+(\\[[^\\[\\]{}\\s]+\\])?"))throw new IllegalArgumentException("Bloco deve ser namespace:id[prop=valor,...], sem NBT: "+s);
        return s;
    }
    public static Blueprint parse(String source,int maximum) {
        if(maximum<1)throw new IllegalArgumentException("max.voxels >= 1");
        JsonObject root=SafeData.object(source,4_000_000,(int)Math.min(8_000_000,maximum*8L+1024));
        if(!root.has("origin")||!root.keySet().stream().allMatch(Set.of("origin","voxels","cuboids","spheres","lines")::contains))throw new IllegalArgumentException("origin obrigatório; listas voxels/cuboids/spheres/lines opcionais");
        if(!root.get("origin").isJsonObject())throw new IllegalArgumentException("origin deve ser objeto {dimension,x,y,z}");
        for(String list:List.of("voxels","cuboids","spheres","lines"))if(root.has(list)&&!root.get(list).isJsonArray())throw new IllegalArgumentException(list+" deve ser lista JSON");
        JsonObject o=root.getAsJsonObject("origin");keys(o,"dimension","x","y","z");
        Origin origin=new Origin(SafeData.text(o,"dimension",240),integer(o,"x"),integer(o,"y"),integer(o,"z"));
        if(!origin.dimension().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("dimension deve ser namespace:id");
        Map<Pos,String> out=new LinkedHashMap<>();
        long[] work={0};
        java.util.function.BiConsumer<Pos,String> add=(p,b)->{
            if(++work[0]>maximum)throw new IllegalArgumentException("Plano excede build.max.voxels="+maximum+" (inclui sobreposições)");
            Math.addExact(origin.x(),p.x());Math.addExact(origin.y(),p.y());Math.addExact(origin.z(),p.z());out.put(p,b);
        };
        if(root.has("voxels"))for(JsonElement e:root.getAsJsonArray("voxels")) {
            JsonObject v=e.getAsJsonObject();keys(v,"x","y","z","block");add.accept(new Pos(integer(v,"x"),integer(v,"y"),integer(v,"z")),block(v));
        }
        if(root.has("cuboids"))for(JsonElement e:root.getAsJsonArray("cuboids")) {
            JsonObject v=e.getAsJsonObject();keys(v,"from","to","block");Pos a=pos(v.getAsJsonObject("from")),b=pos(v.getAsJsonObject("to"));String s=block(v);
            long nx=Math.abs((long)b.x()-a.x())+1,ny=Math.abs((long)b.y()-a.y())+1,nz=Math.abs((long)b.z()-a.z())+1;
            if(nx>maximum||ny>maximum||nz>maximum||Math.multiplyExact(Math.multiplyExact(nx,ny),nz)>maximum-work[0])throw new IllegalArgumentException("Cuboid excede tamanho máximo");
            for(long x=Math.min(a.x(),b.x());x<=Math.max(a.x(),b.x());x++)for(long y=Math.min(a.y(),b.y());y<=Math.max(a.y(),b.y());y++)for(long z=Math.min(a.z(),b.z());z<=Math.max(a.z(),b.z());z++)add.accept(new Pos((int)x,(int)y,(int)z),s);
        }
        if(root.has("spheres"))for(JsonElement e:root.getAsJsonArray("spheres")) {
            JsonObject v=e.getAsJsonObject();keys(v,"center","radius","block");Pos c=pos(v.getAsJsonObject("center"));int r=integer(v,"radius");String s=block(v);
            if(r<0||r>128||Math.pow(2L*r+1,3)>maximum*8L)throw new IllegalArgumentException("Sphere excede orçamento de geometria");
            for(int x=-r;x<=r;x++)for(int y=-r;y<=r;y++)for(int z=-r;z<=r;z++)if((long)x*x+(long)y*y+(long)z*z<=(long)r*r)add.accept(new Pos(Math.addExact(c.x(),x),Math.addExact(c.y(),y),Math.addExact(c.z(),z)),s);
        }
        if(root.has("lines"))for(JsonElement e:root.getAsJsonArray("lines")) {
            JsonObject v=e.getAsJsonObject();keys(v,"from","to","block");Pos a=pos(v.getAsJsonObject("from")),b=pos(v.getAsJsonObject("to"));String s=block(v);
            long n=Math.max(Math.abs((long)b.x()-a.x()),Math.max(Math.abs((long)b.y()-a.y()),Math.abs((long)b.z()-a.z())));
            if(n+1>maximum-work[0])throw new IllegalArgumentException("Line excede tamanho máximo");
            for(long i=0;i<=n;i++){double t=n==0?0:(double)i/n;add.accept(new Pos((int)Math.round(a.x()+((long)b.x()-a.x())*t),(int)Math.round(a.y()+((long)b.y()-a.y())*t),(int)Math.round(a.z()+((long)b.z()-a.z())*t)),s);}
        }
        if(out.isEmpty())throw new IllegalArgumentException("Plano vazio");
        return new Blueprint(origin,out.entrySet().stream().map(e->new Voxel(e.getKey(),e.getValue())).toList());
    }
}
