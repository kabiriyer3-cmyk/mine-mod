package com.hurma.topol.client;

import com.hurma.topol.Topol;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import java.io.*;
import java.util.*;

/**
 * Bounded reader for TMSH v2 meshes exported by tools/gen_models.py from the CC BY artist models.
 * Each part keeps its own texture key, baked per-vertex ambient occlusion colour and an animation pivot/axis.
 */
public final class TMesh {
    private static final Map<String,TMesh> CACHE=new HashMap<>();
    private static final TMesh EMPTY=new TMesh();
    public record Part(String texture,float[] pivot,float[] axis,float[] pos,byte[] nrm,float[] uv,byte[] col,int[] tri) {}
    private final Map<String,Part> parts=new LinkedHashMap<>();
    private TMesh(){}
    public static void clear(){CACHE.clear();}
    public static TMesh get(String name){
        return CACHE.computeIfAbsent(name,k->{
            try(InputStream in=MinecraftClient.getInstance().getResourceManager().getResource(Topol.id("models/tmsh/"+k+".tmsh")).orElseThrow().getInputStream()){
                return read(in);
            }catch(Exception e){Topol.LOG.error("Cannot load Solo Silo mesh "+k,e);return EMPTY;}
        });
    }
    public Set<String> names(){return parts.keySet();}
    public Part part(String name){return parts.get(name);}
    private static int bound(int n,int max)throws IOException{if(n<0||n>max)throw new IOException("Invalid TMSH count "+n);return n;}
    public static TMesh read(InputStream stream)throws IOException{
        TMesh mesh=new TMesh();
        try(DataInputStream d=new DataInputStream(new BufferedInputStream(stream))){
            if(d.readInt()!=0x544d5348||d.readInt()!=2)throw new IOException("Not a TMSH v2 file");
            int count=bound(d.readInt(),64);
            for(int i=0;i<count;i++){
                String name=d.readUTF(),texture=d.readUTF();
                if(!texture.matches("[a-z0-9_]{1,32}"))throw new IOException("Invalid texture key");
                d.readByte();
                float[] pivot=floats(d,3),axis=floats(d,3);
                int n=bound(d.readInt(),200000);
                float[] pos=new float[n*3],uv=new float[n*2];byte[] nrm=new byte[n*3],col=new byte[n*3];
                for(int v=0;v<n;v++){
                    for(int k=0;k<3;k++)pos[v*3+k]=finite(d.readFloat());
                    for(int k=0;k<3;k++)nrm[v*3+k]=d.readByte();
                    for(int k=0;k<2;k++)uv[v*2+k]=finite(d.readFloat());
                    for(int k=0;k<3;k++)col[v*3+k]=d.readByte();
                }
                int t=bound(d.readInt(),200000);
                int[] tri=new int[t*3];
                for(int k=0;k<tri.length;k++){tri[k]=d.readInt();if(tri[k]<0||tri[k]>=n)throw new IOException("Invalid mesh index");}
                mesh.parts.put(name,new Part(texture,pivot,axis,pos,nrm,uv,col,tri));
            }
            if(d.read()!=-1)throw new IOException("Trailing mesh bytes");
        }
        return mesh;
    }
    private static float finite(float f)throws IOException{if(!Float.isFinite(f))throw new IOException("Non-finite mesh value");return f;}
    private static float[] floats(DataInputStream d,int n)throws IOException{float[] a=new float[n];for(int i=0;i<n;i++)a[i]=finite(d.readFloat());return a;}

    /** LOD 0 = full resolution texture, 1 = half, 2 = quarter. Entity textures have no mipmaps, so distance picks the level. */
    public static int lod(double distance){return distance<20?0:distance<64?1:2;}
    public static Identifier texture(String key,int lod){return Topol.id("textures/entity/"+key+"_"+Math.max(0,Math.min(2,lod))+".png");}

    public void emit(String name,MatrixStack matrices,VertexConsumerProvider consumers,int lod,int light,int overlay){
        Part p=parts.get(name);if(p==null)return;
        MatrixStack.Entry e=matrices.peek();
        VertexConsumer v=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture(p.texture,lod)));
        int[] t=p.tri;
        for(int i=0;i<t.length;i+=3){
            // Vanilla entity layers are quads: emit the triangle with its last corner repeated.
            vertex(v,e,p,t[i],light,overlay);vertex(v,e,p,t[i+1],light,overlay);
            vertex(v,e,p,t[i+2],light,overlay);vertex(v,e,p,t[i+2],light,overlay);
        }
    }
    private static void vertex(VertexConsumer v,MatrixStack.Entry e,Part p,int i,int light,int overlay){
        v.vertex(e.getPositionMatrix(),p.pos[i*3],p.pos[i*3+1],p.pos[i*3+2])
                .color(p.col[i*3]&255,p.col[i*3+1]&255,p.col[i*3+2]&255,255)
                .texture(p.uv[i*2],p.uv[i*2+1]).overlay(overlay).light(light)
                .normal(e,p.nrm[i*3]/127f,p.nrm[i*3+1]/127f,p.nrm[i*3+2]/127f);
    }
}
