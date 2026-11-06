package dev.jevmod.body;

import java.util.*;
import java.util.function.ToDoubleFunction;

/** Clips one polygon and interpolates every attribute, including texture coordinates. */
public final class HeightClip {
    private HeightClip(){}
    public static List<float[]> below(List<float[]> vertices,double height,ToDoubleFunction<float[]> localY){
        if(vertices.isEmpty())return List.of();
        var result=new ArrayList<float[]>();float[] a=vertices.getLast();double ay=localY.applyAsDouble(a);
        for(float[] b:vertices){double by=localY.applyAsDouble(b);boolean ai=ay<=height,bi=by<=height;
            if(ai!=bi){float t=(float)((height-ay)/(by-ay));float[] edge=new float[a.length];for(int i=0;i<edge.length;i++)edge[i]=a[i]+(b[i]-a[i])*t;result.add(edge);}
            if(bi)result.add(b);a=b;ay=by;
        }
        return result;
    }
}
