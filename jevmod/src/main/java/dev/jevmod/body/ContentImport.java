package dev.jevmod.body;

import dev.jevmod.core.Json;
import java.nio.file.*;
import java.util.*;

/** Plans drag/drop without writing. Ambiguous scripts need an explicit folder; batches validate before saving. */
public final class ContentImport {
    public static final List<String> FOLDERS=List.of("systems","apis","minds","brains","powers","sensors","bodies");
    public record File(Path source,String text,String inferredFolder){
        public String relative(String folder){
            if(!FOLDERS.contains(folder))throw new IllegalArgumentException("Escolha a pasta de conteúdo");
            return folder+"/"+source.getFileName();
        }
    }
    public record Plan(String relative,String text){}
    private ContentImport(){}
    public static File read(Path source,LocalContent content)throws Exception{
        String name=source.getFileName().toString();if(!name.matches("[a-zA-Z0-9_.-]+\\.(json|js)"))throw new IllegalArgumentException("Arquivo: JSON/JS com nome simples");
        if(!Files.isRegularFile(source)||Files.size(source)>262144)throw new IllegalArgumentException("Conteúdo: até 256 KB");
        String text=Files.readString(source),folder=null;
        if(name.endsWith(".json")){
            var o=Json.parseObject(text);String hint=Json.str(o,"content_type","");
            if(!hint.isBlank())folder=switch(hint){case "system","systems"->"systems";case "api","apis"->"apis";case "mind","minds"->"minds";case "brain","brains"->"brains";case "power","powers"->"powers";case "sensor","sensors"->"sensors";case "body","bodies"->"bodies";default->throw new IllegalArgumentException("content_type desconhecido");};
            else if(o.has("engine")||o.has("rules")||o.has("module"))folder="systems";
            else if(o.has("systems"))folder="minds";
            else if(o.has("powers")||o.has("sensors")||o.has("think_every_ticks"))folder="brains";
            else if(Set.of("player","mob","obj").contains(Json.str(o,"kind","")))folder="bodies";
            else if(o.has("kind")||o.has("params")||o.has("cooldown_ticks"))folder="powers";
        }else{
            var directive=java.util.regex.Pattern.compile("(?m)^\\s*//\\s*jevmod:\\s*(systems|apis|powers|sensors)\\s*$").matcher(text);
            if(directive.find())folder=directive.group(1);
        }
        if(folder==null&&source.getParent()!=null&&FOLDERS.contains(source.getParent().getFileName().toString()))folder=source.getParent().getFileName().toString();
        if(folder==null&&name.endsWith(".js")){
            var candidates=new LinkedHashSet<String>();
            for(String relative:content.files())if(relative.endsWith(".json"))try{var o=Json.parseObject(content.read(relative));if(name.equals(Json.str(o,"script","")))candidates.add(relative.substring(0,relative.indexOf('/')));}catch(RuntimeException ignored){}
            if(candidates.size()==1)folder=candidates.iterator().next();
            if(folder==null&&java.util.regex.Pattern.compile("\\bfunction\\s+sense\\s*\\(").matcher(text).find())folder="sensors";
            if(folder==null&&java.util.regex.Pattern.compile("\\bfunction\\s+onEvent\\s*\\(").matcher(text).find())folder="systems";
        }
        return new File(source,text,folder);
    }
    public static void save(LocalContent content,List<Plan> plans,boolean overwrite)throws Exception{
        var names=new HashSet<String>();
        for(var plan:plans){var path=content.path(plan.relative());if(!names.add(plan.relative()))throw new IllegalArgumentException("Destino repetido: "+plan.relative());LocalContent.validate(plan.relative(),plan.text());if(!overwrite&&Files.exists(path))throw new IllegalArgumentException("Confirme substituição: "+plan.relative());}
        for(var plan:plans)content.save(plan.relative(),plan.text());
    }
}
