package com.hurma.topol.mixin;

import com.hurma.topol.client.ViewportFx;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private float yaw;
    @Shadow private float pitch;
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Inject(method="update",at=@At("RETURN"))
    private void topol$shake(BlockView area,Entity entity,boolean thirdPerson,boolean inverse,float delta,CallbackInfo ci) {
        float y=ViewportFx.yaw(delta),p=ViewportFx.pitch(delta);
        if(y!=0||p!=0)setRotation(yaw+y,pitch+p);
    }
}
