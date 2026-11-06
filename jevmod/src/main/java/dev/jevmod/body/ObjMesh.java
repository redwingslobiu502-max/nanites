package dev.jevmod.body;

import java.util.ArrayList;
import java.util.List;

/** Bounded OBJ geometry importer. Material/texture paths are ignored: texture is imported separately. */
public record ObjMesh(List<Triangle> triangles) {
    public record Point(float x,float y,float z,float u,float v) {
        public Point mix(Point b,float t) { return new Point(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t,u+(b.u-u)*t,v+(b.v-v)*t); }
    }
    public record Triangle(Point a,Point b,Point c) {}

    public static ObjMesh parse(String text) {
        if (text.length()>4_000_000) throw new IllegalArgumentException("OBJ até 4 MB");
        List<float[]> positions=new ArrayList<>(),uvs=new ArrayList<>();
        List<Triangle> triangles=new ArrayList<>();
        for (String line : text.split("\\R")) {
            String clean=line.split("#",2)[0].trim(); if(clean.isEmpty()) continue;
            String[] words=clean.split("\\s+");
            switch(words[0]) {
                case "v" -> { if(words.length<4 || positions.size()>=30_000) throw new IllegalArgumentException("Vértices inválidos/excessivos"); positions.add(new float[]{number(words[1]),number(words[2]),number(words[3])}); }
                case "vt" -> { if(words.length<3 || uvs.size()>=60_000) throw new IllegalArgumentException("UV inválido/excessivo"); uvs.add(new float[]{number(words[1]),number(words[2])}); }
                case "f" -> {
                    if(words.length<4 || words.length>33) throw new IllegalArgumentException("Face inválida");
                    Point first=point(words[1],positions,uvs);
                    for(int i=2;i<words.length-1;i++) {
                        if(triangles.size()>=40_000) throw new IllegalArgumentException("Máximo: 40000 triângulos");
                        triangles.add(new Triangle(first,point(words[i],positions,uvs),point(words[i+1],positions,uvs)));
                    }
                }
                default -> { /* normals/groups/material names do not grant file access */ }
            }
        }
        if(triangles.isEmpty()) throw new IllegalArgumentException("OBJ sem faces");
        float minY=Float.MAX_VALUE,maxY=-Float.MAX_VALUE,minX=minY,maxX=maxY,minZ=minY,maxZ=maxY;
        for(Triangle t:triangles) for(Point p:List.of(t.a,t.b,t.c)) {
            minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minZ=Math.min(minZ,p.z);maxZ=Math.max(maxZ,p.z);
        }
        if(maxY-minY<0.00001f) throw new IllegalArgumentException("O modelo precisa ter altura");
        float scale=Math.min(1.8f/(maxY-minY),0.74f/Math.max(0.00001f,Math.max(maxX-minX,maxZ-minZ)));
        final float cx=(minX+maxX)/2,cz=(minZ+maxZ)/2,bottom=minY,s=scale;
        return new ObjMesh(triangles.stream().map(t->new Triangle(normalize(t.a,cx,bottom,cz,s),normalize(t.b,cx,bottom,cz,s),normalize(t.c,cx,bottom,cz,s))).toList());
    }
    private static float number(String s) {float f=Float.parseFloat(s);if(!Float.isFinite(f)||Math.abs(f)>1_000_000) throw new IllegalArgumentException("Coordenada inválida");return f;}
    private static Point normalize(Point p,float x,float y,float z,float s){return new Point((p.x-x)*s,(p.y-y)*s,(p.z-z)*s,p.u,p.v);}
    private static int index(String s,int size){int i=Integer.parseInt(s);i=i<0?size+i:i-1;if(i<0||i>=size)throw new IllegalArgumentException("Índice OBJ inválido");return i;}
    private static Point point(String word,List<float[]> ps,List<float[]> uvs){String[] parts=word.split("/",-1);float[] p=ps.get(index(parts[0],ps.size()));float[] uv=parts.length>1&&!parts[1].isEmpty()?uvs.get(index(parts[1],uvs.size())):new float[]{0,0};return new Point(p[0],p[1],p[2],uv[0],1-uv[1]);}
}
