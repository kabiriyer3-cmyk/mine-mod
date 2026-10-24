package com.hurma.topol.client;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

/**
 * Zenitco Perst-4 mesh (CC BY 4.0, ikoval31) as a 3D item. Mesh: metres, lens towards -Z, top +Y.
 * Shown about 4x real size so it reads in a blocky world; per-view placement lives in models/item/designator.json.
 */
public final class DesignatorRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final float SCALE=8f;
    @Override public void render(ItemStack stack,ModelTransformationMode mode,MatrixStack m,VertexConsumerProvider b,int light,int overlay){
        m.push();
        m.translate(.5f,.5f,.5f);
        m.scale(SCALE,SCALE,SCALE);
        TMesh.get("designator").emit("body",m,b,mode==ModelTransformationMode.GROUND?1:0,light,overlay);
        m.pop();
    }
}
