package dev.jevmod.body;

import dev.jevmod.core.Json;
import dev.jevmod.core.def.BrainDef;
import dev.jevmod.core.def.PowerDef;
import dev.jevmod.core.script.ScriptHost;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;

/** Local editor IO: validation, confined paths, history and atomic replacement. */
public final class LocalContent {
    private final Path root;
    public LocalContent(Path root) {this.root=root.toAbsolutePath().normalize();}
    public List<String> files() throws Exception {
        if(!Files.exists(root))return List.of();
        try(var walk=Files.walk(root,2)){return walk.filter(Files::isRegularFile).map(root::relativize).map(p->p.toString().replace('\\','/'))
                .filter(p->p.matches("(brains|powers|sensors|bodies|minds|systems|apis)/[a-zA-Z0-9_.-]+\\.(json|js)")).sorted().toList();}
    }
    public Path path(String relative) throws Exception {
        if(!relative.matches("(brains|powers|sensors|bodies|minds|systems|apis)/[a-zA-Z0-9_.-]+\\.(json|js)"))throw new IllegalArgumentException("Caminho de conteúdo inválido");
        Path p=root.resolve(relative).normalize();
        if(!p.startsWith(root))throw new IllegalArgumentException("Fora da pasta do mod");
        for(Path check=p;check!=null&&check.startsWith(root);check=check.getParent())if(Files.isSymbolicLink(check))throw new IllegalArgumentException("Links simbólicos não permitidos");
        return p;
    }
    public String read(String relative)throws Exception {Path p=path(relative);if(Files.size(p)>262_144)throw new IllegalArgumentException("Arquivo maior que 256 KB");return Files.readString(p);}
    public void save(String relative,String text)throws Exception {
        Path p=path(relative);validate(relative,text);Files.createDirectories(p.getParent());
        if(Files.exists(p)){Path history=folder(".history");Files.copy(p,history.resolve(System.currentTimeMillis()+"-"+java.util.UUID.randomUUID()+"-"+p.getFileName()));}
        atomic(p,text.getBytes(StandardCharsets.UTF_8));
        Files.deleteIfExists(deletionMarker(relative));
    }
    public static boolean isDeleted(Path root,String relative){return Files.isRegularFile(root.resolve(".history").resolve(relative.replace('/','_')+".deleted"));}
    private Path deletionMarker(String relative)throws Exception{return folder(".history").resolve(relative.replace('/','_')+".deleted");}
    /** Explicit deletion keeps source history and suppresses the missing-default installer. */
    public void delete(String relative)throws Exception{
        Path p=path(relative);if(Files.exists(p))Files.copy(p,folder(".history").resolve(System.currentTimeMillis()+"-"+java.util.UUID.randomUUID()+"-DELETED-"+p.getFileName()));
        atomic(deletionMarker(relative),relative.getBytes(StandardCharsets.UTF_8));Files.deleteIfExists(p);
    }
    public static void validate(String relative,String text) {
        if(text.length()>262_144)throw new IllegalArgumentException("Limite: 256 KB");
        if(relative.endsWith(".js")){new ScriptHost(2_000_000).compile(text,relative);return;}
        var o=Json.parseObject(text);String name=Path.of(relative).getFileName().toString().replace(".json","");
        if(relative.startsWith("brains/"))BrainDef.parse(o,name);
        else if(relative.startsWith("powers/"))PowerDef.parse(o,name,Path.of("powers"));
        else if(relative.startsWith("bodies/"))BodySpec.parse(o);
        else if(relative.startsWith("minds/"))dev.jevmod.mind.MindDef.parse(o,name);
        else if(relative.startsWith("systems/"))dev.jevmod.mind.SystemDef.parse(o,name);
    }
    public String importAsset(Path source)throws Exception {
        String ext=source.getFileName().toString().toLowerCase().endsWith(".png")?"png":source.getFileName().toString().toLowerCase().endsWith(".obj")?"obj":"";
        if(ext.isEmpty())throw new IllegalArgumentException("Selecione PNG ou OBJ");
        if(Files.size(source)>4_000_000)throw new IllegalArgumentException("Arquivo até 4 MB");
        byte[] bytes=Files.readAllBytes(source);
        if(ext.equals("obj"))ObjMesh.parse(new String(bytes,StandardCharsets.UTF_8));
        else try(var input=ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("PNG inválido");
            var reader=readers.next();try{reader.setInput(input);if(!reader.getFormatName().equalsIgnoreCase("png")||reader.getWidth(0)>2048||reader.getHeight(0)>2048)throw new IllegalArgumentException("PNG até 2048x2048");reader.read(0);}finally{reader.dispose();}
        }
        String name=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))+"."+ext;
        Path folder=folder("assets");
        Path target=folder.resolve(name);if(!Files.exists(target))atomic(target,bytes);return name;
    }
    public static void atomic(Path target,byte[] bytes)throws Exception {
        Path staging=Files.createTempFile(target.getParent(),".jev-",".tmp");
        try{Files.write(staging,bytes);try{Files.move(staging,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(staging,target,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(staging);}
    }
    public Path folder(String name)throws Exception {
        if(!java.util.Set.of("assets",".history","generated").contains(name))throw new IllegalArgumentException("Pasta inválida");
        var path=root.resolve(name);if(Files.isSymbolicLink(root)||Files.isSymbolicLink(path))throw new IllegalArgumentException("Pasta de conteúdo inválida");Files.createDirectories(path);return path;
    }
}
