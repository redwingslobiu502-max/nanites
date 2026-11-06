package dev.jevmod.body;

/** Generates the 64x64 humanoid texture in spatial body layers, including skin overlays. */
public final class SkinAssembly {
    private final int[] source, built=new int[4096];
    private final double[] height=new double[4096];
    public SkinAssembly(int[] pixels) {
        if(pixels.length!=4096)throw new IllegalArgumentException("Skin: PNG 64x64");source=pixels.clone();
        java.util.Arrays.fill(height,1.8);
        part(0,16,4,4,12,0,0.675);part(0,32,4,4,12,0,0.675);
        part(16,48,4,4,12,0,0.675);part(0,48,4,4,12,0,0.675);
        part(16,16,8,4,12,0.675,1.35);part(16,32,8,4,12,0.675,1.35);
        part(40,16,4,4,12,0.675,1.35);part(40,32,4,4,12,0.675,1.35);
        part(32,48,4,4,12,0.675,1.35);part(48,48,4,4,12,0.675,1.35);
        part(0,0,8,8,8,1.35,1.8);part(32,0,8,8,8,1.35,1.8);
    }
    private void part(int x,int y,int w,int d,int h,double bottom,double top){
        for(int row=0;row<d;row++)for(int col=0;col<w;col++){
            height[(y+row)*64+x+d+col]=top;
            height[(y+row)*64+x+d+w+col]=bottom;
        }
        for(int row=0;row<h;row++)for(int col=0;col<2*(w+d);col++)
            height[(y+d+row)*64+x+col]=bottom+(top-bottom)*(h-row-0.5)/h;
    }
    public void advance(AssemblyProgress progress){for(int i=0;i<built.length;i++)if(progress.finished()||height[i]<=progress.height())built[i]=source[i];}
    public int[] pixels(){return built.clone();}
    public static int[] defaultPixels(){int[] p=new int[4096];for(int y=0;y<64;y++)for(int x=0;x<64;x++)p[y*64+x]=((x+y)%8<2)?0xFF63CFFF:0xFFEDF8FF;return p;}
}
