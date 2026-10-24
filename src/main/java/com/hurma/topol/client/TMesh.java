package com.hurma.topol.client;

import com.hurma.topol.Topol;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import java.io.*;
import java.util.*;

/** Checked, bounded reader for the original TMSH meshes. Rigid parts are animated without a model library. */
public final class TMesh {
    private static final Map<String,TMesh> CACHE=new HashMap<>();
    private final Map<String,Part> parts=new LinkedHashMap<>();
    private static final TMesh EMPTY=new TMesh();
    private record Part(float[] pos,float[] nrm,float[] uv,byte[] shade,int[] indices,boolean emissive) {}
    public static void clear(){CACHE.clear();}
    public static TMesh get(String name,int lod) {
        String key=name+"_"+lod;
        return CACHE.computeIfAbsent(key,k->{
            try(var in=MinecraftClient.getInstance().getResourceManager().getResource(Topol.id("models/tmsh/"+k+".tmsh")).orElseThrow().getInputStream()) {
                return read(in);
            }catch(Exception e){Topol.LOG.error("Cannot load Topol mesh "+k,e);return EMPTY;}
        });
    }
    private static int bound(int n,int max)throws IOException {if(n<0||n>max)throw new IOException("Invalid TMSH count");return n;}
    public static TMesh read(InputStream stream)throws IOException {
        TMesh mesh=new TMesh();
        try(DataInputStream d=new DataInputStream(new BufferedInputStream(stream))) {
            if(d.readInt()!=0x544d5348||d.readInt()!=1)throw new IOException("Not a TMSH v1 file");
            int count=bound(d.readInt(),128);
            for(int i=0;i<count;i++) {
                String name=d.readUTF();
                for(int k=0;k<3;k++)d.readFloat(); // pivots are also exported as constants
                boolean emissive=d.readBoolean();
                int n=bound(d.readInt(),300000);
                float[] p=floats(d,n*3),normal=floats(d,n*3),uv=floats(d,n*2);
                byte[] shade=new byte[n];d.readFully(shade);
                int quads=bound(d.readInt(),150000);
                int[] index=new int[quads*4];
                for(int k=0;k<index.length;k++){index[k]=d.readInt();if(index[k]<0||index[k]>=n)throw new IOException("Invalid mesh index");}
                mesh.parts.put(name,new Part(p,normal,uv,shade,index,emissive));
            }
            int anchors=bound(d.readInt(),128);
            for(int i=0;i<anchors;i++){d.readUTF();d.readFloat();d.readFloat();d.readFloat();}
        }
        return mesh;
    }
    private static float[] floats(DataInputStream d,int n)throws IOException {
        float[] a=new float[n];
        for(int i=0;i<n;i++){a[i]=d.readFloat();if(!Float.isFinite(a[i]))throw new IOException("Non-finite mesh vertex");}
        return a;
    }
    public static Identifier texture(String mesh,double distance) {
        String atlas=mesh.equals("launcher")?"launcher":"missile";
        int size=distance>500?512:distance>120?1024:2048;
        return Topol.id("textures/entity/"+atlas+"_"+size+".png");
    }
    public void emit(String name,MatrixStack matrices,VertexConsumerProvider consumers,Identifier texture,int light,float tint) {
        Part p=parts.get(name);if(p==null||tint<=0)return;
        MatrixStack.Entry e=matrices.peek();
        VertexConsumer v=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        int lt=p.emissive?0xf000f0:light;
        for(int i:p.indices) {
            int c=Math.min(255,(int)((p.shade[i]&255)*tint));
            v.vertex(e.getPositionMatrix(),p.pos[i*3],p.pos[i*3+1],p.pos[i*3+2])
                    .color(c,c,c,255).texture(p.uv[i*2],p.uv[i*2+1]).overlay(OverlayTexture.DEFAULT_UV)
                    .light(lt).normal(e,p.nrm[i*3],p.nrm[i*3+1],p.nrm[i*3+2]);
        }
    }
}
