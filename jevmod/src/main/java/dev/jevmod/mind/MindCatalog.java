package dev.jevmod.mind;

import dev.jevmod.core.Json;
import java.nio.file.*;
import java.util.*;

/** Invalid files are reported individually, preserving other systems. */
public final class MindCatalog {
    public final Map<String,MindDef> minds=new LinkedHashMap<>();
    public final Map<String,SystemDef> systems=new LinkedHashMap<>();
    public final List<String> errors=new ArrayList<>();
    public static void requireId(String id){if(id==null||!id.matches("[a-z][a-z0-9_]{0,47}"))throw new IllegalArgumentException("ID: letras minúsculas, números e _ (até 48)");}
    public static MindCatalog load(Path root){
        var out=new MindCatalog();
        for(String dir:List.of("minds","systems")){
            Path folder=root.resolve(dir);if(!Files.isDirectory(folder))continue;
            try(var files=Files.list(folder)){
                for(var file:files.filter(p->p.getFileName().toString().endsWith(".json")).sorted().toList())try{
                    if(Files.isSymbolicLink(folder)||Files.isSymbolicLink(file)||Files.size(file)>262144)throw new IllegalArgumentException("Arquivo inválido/excessivo");
                    var json=Json.parseObject(Files.readString(file));String fallback=file.getFileName().toString().replace(".json","");
                    if(dir.equals("minds")){var def=MindDef.parse(json,fallback);if(out.minds.putIfAbsent(def.id(),def)!=null)throw new IllegalArgumentException("Mente duplicada");}
                    else {var def=SystemDef.parse(json,fallback);if(out.systems.putIfAbsent(def.id(),def)!=null)throw new IllegalArgumentException("Sistema duplicado");}
                }catch(Exception e){out.errors.add(dir+"/"+file.getFileName()+": "+e.getMessage());}
            }catch(Exception e){out.errors.add(dir+": "+e.getMessage());}
        }
        for(var mind:out.minds.values())for(var system:mind.systems())if(!out.systems.containsKey(system))out.errors.add("Mente "+mind.id()+": sistema ausente "+system);
        return out;
    }
}
