package dev.jevmod.body;

import dev.jevmod.core.Json;
import dev.jevmod.mind.SystemDef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QolContentTest {
    @TempDir Path root;
    @Test void oldRecipesAndJavaConstructorsKeepDefaultsWhileNewDataIsImmutable(){
        assertEquals(BodySpec.defaults(),BodySpec.parse(Json.parseObject("{}")));
        var input=Json.parseObject("{\"resistances\":[\"onFire\",\"minecraft:lava\"],\"metadata\":{\"team\":\"fire\"}}");var spec=BodySpec.parse(input);
        input.getAsJsonObject("metadata").addProperty("team","edited");spec.metadata().addProperty("team","edited");assertEquals("fire",spec.metadata().get("team").getAsString());assertThrows(UnsupportedOperationException.class,()->spec.resistances().add("fall"));
        assertEquals(.1f,spec.damageMultiplier("lava","minecraft:lava"));assertEquals(1f,spec.damageMultiplier("fall","minecraft:fall"));assertEquals(spec,BodySpec.parse(spec.json()));
        assertThrows(IllegalArgumentException.class,()->BodySpec.parse(Json.parseObject("{\"resistances\":[\"../../x\"]}")));
    }
    @Test void deleteRetainsHistoryAndExplicitResaveClearsDefaultSuppression()throws Exception{
        var content=new LocalContent(root);content.save("systems/sample.js","function onTick() {}\n");content.delete("systems/sample.js");assertFalse(Files.exists(content.path("systems/sample.js")));assertTrue(LocalContent.isDeleted(root,"systems/sample.js"));
        try(var files=Files.list(root.resolve(".history"))){assertTrue(files.anyMatch(p->p.getFileName().toString().contains("DELETED")&&read(p).equals("function onTick() {}\n")));}
        assertThrows(IllegalArgumentException.class,()->content.delete("systems/../../outside.js"));content.save("systems/sample.js","function onTick() {}\n");assertFalse(LocalContent.isDeleted(root,"systems/sample.js"));
    }
    private static String read(Path p){try{return Files.readString(p);}catch(Exception e){throw new RuntimeException(e);}}
    @Test void importRoutesJsonAndRequiresExplicitChoiceForAmbiguousScript()throws Exception{
        var content=new LocalContent(root.resolve("content"));var file=root.resolve("my_system.json");Files.writeString(file,"{\"engine\":\"javascript\",\"script\":\"my_system.js\"}");assertEquals("systems",ContentImport.read(file,content).inferredFolder());
        var js=root.resolve("shared.js");Files.writeString(js,"function onTick(ctx) {}\n");assertNull(ContentImport.read(js,content).inferredFolder());Files.writeString(js,"// jevmod: apis\nvar lib = {};\n");assertEquals("apis",ContentImport.read(js,content).inferredFolder());
        var body=root.resolve("body.json");Files.writeString(body,"{\"kind\":\"mob\",\"model\":\"minecraft:blaze\"}");assertEquals("bodies",ContentImport.read(body,content).inferredFolder());
    }
    @Test void invalidImportBatchAndUnconfirmedOverwritePreserveExistingFiles()throws Exception{
        var content=new LocalContent(root);content.save("systems/working.js","function onTick() {}\n");var plans=List.of(new ContentImport.Plan("systems/working.js","function onTick(){return 1;}"),new ContentImport.Plan("systems/broken.js","function broken( {"));assertThrows(Exception.class,()->ContentImport.save(content,plans,true));assertEquals("function onTick() {}\n",content.read("systems/working.js"));
        var valid=List.of(plans.getFirst());assertThrows(IllegalArgumentException.class,()->ContentImport.save(content,valid,false));ContentImport.save(content,valid,true);assertEquals(valid.getFirst().text(),content.read("systems/working.js"));
    }
    @Test void enabledDefaultsTrueAndDisabledFlagSurvivesParsing(){assertTrue(SystemDef.parse(Json.parseObject("{}"),"sample").enabled());assertFalse(SystemDef.parse(Json.parseObject("{\"enabled\":false}"),"sample").enabled());}
}
