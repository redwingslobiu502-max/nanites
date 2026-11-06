package dev.jevmod.mind;

import com.google.gson.*;
import dev.jevmod.api.mind.MindContext;
import java.util.*;
import java.util.function.Function;

/** Stable extension seam: Java mods supply systems, services or entirely new language engines. */
public final class MindExtensions {
    @FunctionalInterface public interface Service {JsonElement call(MindContext context,String operation,JsonObject arguments)throws Exception;}
    private static final Map<String,Function<SystemDef,MindModule>> MODULES=new LinkedHashMap<>(),ENGINES=new LinkedHashMap<>();
    private static final Map<String,Service> SERVICES=new LinkedHashMap<>();
    private MindExtensions(){}
    private static void requireName(String name){if(!name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Extensão: namespace:id");}
    public static synchronized void registerModule(String name,Function<SystemDef,MindModule> factory){requireName(name);if(MODULES.putIfAbsent(name,Objects.requireNonNull(factory))!=null)throw new IllegalArgumentException("Módulo já registrado: "+name);}
    public static synchronized void registerService(String name,Service service){requireName(name);if(SERVICES.putIfAbsent(name,Objects.requireNonNull(service))!=null)throw new IllegalArgumentException("API já registrada: "+name);}
    public static synchronized void registerEngine(String name,Function<SystemDef,MindModule> factory){requireName(name);if(ENGINES.putIfAbsent(name,Objects.requireNonNull(factory))!=null)throw new IllegalArgumentException("Engine já registrada: "+name);}
    static MindModule create(SystemDef def){var factory=def.engine().equals("java")?MODULES.get(def.module()):ENGINES.get(def.engine());if(factory==null)throw new IllegalArgumentException("Extensão não instalada: "+(def.engine().equals("java")?def.module():def.engine()));return Objects.requireNonNull(factory.apply(def));}
    public static JsonElement call(String name,MindContext context,String operation,JsonObject args)throws Exception{var service=SERVICES.get(name);if(service==null)throw new IllegalArgumentException("API não instalada: "+name);return service.call(context,operation,args);}
    public static Set<String> services(){return Set.copyOf(SERVICES.keySet());}
}
