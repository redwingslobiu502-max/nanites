package dev.jevmod.body;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BodyTest {
    @TempDir Path root;
    @Test void timerResumesAndCompletesOnExactlyTheConfiguredTick(){
        var progress=new AssemblyProgress(0,37);
        for(int i=1;i<=37;i++){progress=progress.advance();assertEquals(i,progress.elapsed());assertEquals(i==37,progress.finished());}
        assertEquals(1.8,progress.height());assertEquals(128,progress.completedLayers(128));assertEquals(progress,progress.advance());
        assertEquals(new AssemblyProgress(18,37),new AssemblyProgress(17,37).advance());
    }
    @Test void skinBuildsFeetBeforeHeadAndFinishesWithTheExactUploadedPixels(){
        int[] pixels=new int[4096];Arrays.fill(pixels,0xFFAB4567);var skin=new SkinAssembly(pixels);
        skin.advance(new AssemblyProgress(5,20));var early=skin.pixels();assertEquals(pixels[31*64+4],early[31*64+4]);assertEquals(0,early[8*64+8]);assertEquals(0,early[20*64+20]);
        var resumed=new SkinAssembly(pixels);resumed.advance(new AssemblyProgress(5,20));assertArrayEquals(early,resumed.pixels());
        skin.advance(new AssemblyProgress(20,20));assertArrayEquals(pixels,skin.pixels());
    }
    @Test void clippingPreservesUvAtTheActualHeightAndRejectsUpperPolygons(){
        var face=List.of(new float[]{0,0,0,0},new float[]{1,0,1,0},new float[]{1,2,1,1},new float[]{0,2,0,1});
        var cut=HeightClip.below(face,1,p->p[1]);assertEquals(4,cut.size());
        for(var vertex:cut){assertTrue(vertex[1]<=1);if(vertex[1]==1)assertEquals(0.5f,vertex[3]);}
        assertTrue(HeightClip.below(face,-1,p->p[1]).isEmpty());assertEquals(4,HeightClip.below(face,2,p->p[1]).size());
        var translated=HeightClip.below(face,0,p->p[1]-1);assertEquals(4,translated.size());
    }
    @Test void objSupportsRelativeFacesUvAndNormalizesWithoutReadingMaterialFiles(){
        var mesh=ObjMesh.parse("mtllib ../../secret\nv 0 0 0\nv 2 0 0\nv 2 4 0\nv 0 4 0\nvt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nf -4/-4 -3/-3 -2/-2 -1/-1\n");
        assertEquals(2,mesh.triangles().size());assertEquals(0,mesh.triangles().getFirst().a().y());assertTrue(mesh.triangles().getFirst().c().y()<=1.8f);assertEquals(1,mesh.triangles().getFirst().a().v());
        assertThrows(IllegalArgumentException.class,()->ObjMesh.parse("v NaN 0 0\nv 0 1 0\nv 1 0 0\nf 1 2 3"));
        assertThrows(IllegalArgumentException.class,()->ObjMesh.parse("v 0 0 0\nv 0 1 0\nv 1 0 0\nf 0 2 3"));
    }
    @Test void recipesRejectArbitraryAssetPathsAndInvalidDurations(){
        assertEquals(BodySpec.defaults(),BodySpec.parse(BodySpec.defaults().json()));
        assertThrows(IllegalArgumentException.class,()->new BodySpec("obj","../model.obj","","",200));
        assertThrows(IllegalArgumentException.class,()->new BodySpec("mob","minecraft:zombie","../skin.png","",200));
        assertThrows(IllegalArgumentException.class,()->new BodySpec("player","classic","","",19));
        assertThrows(IllegalArgumentException.class,()->new BodySpec("player","missing","","",200));
    }
    @Test void invalidEditsNeverReplaceWorkingScriptsAndSuccessfulEditsKeepHistory()throws Exception{
        var local=new LocalContent(root);local.save("powers/test.js","function run(ctx) {}\n");
        assertThrows(Exception.class,()->local.save("powers/test.js","function broken( {"));
        assertEquals("function run(ctx) {}\n",local.read("powers/test.js"));
        local.save("powers/test.js","function run(ctx) { ctx.say('ok'); }\n");
        try(var files=Files.list(root.resolve(".history"))){assertEquals(1,files.count());}
        assertThrows(IllegalArgumentException.class,()->local.path("powers/../../outside.js"));
    }
    @Test void importedAssetsAreValidatedAndAddressedByTheirContent()throws Exception{
        var image=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);var source=root.resolve("skin.png");javax.imageio.ImageIO.write(image,"png",source.toFile());
        var local=new LocalContent(root.resolve("content"));var name=local.importAsset(source);assertTrue(name.matches("[a-f0-9]{64}\\.png"));assertEquals(name,local.importAsset(source));
        Files.writeString(source,"not a png");assertThrows(Exception.class,()->local.importAsset(source));
    }
}
