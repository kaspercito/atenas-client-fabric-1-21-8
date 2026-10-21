package com.atenas.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.atenas.fabric.hud.Mods;

import net.minecraft.client.render.GameRenderer;

/** Zoom: el FOV del mundo se divide entre el nivel de zoom (sin tocar tu opcion de FOV). require = 0: si no encuentra el metodo se salta. */
@Mixin(GameRenderer.class)
public abstract class ZoomFovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void atenas$zoom(CallbackInfoReturnable<Float> cir) {
        Mods.fovHooked = true;
        float m = Mods.zoomMul();
        if (m > 1.001f) cir.setReturnValue(cir.getReturnValueF() / m);
    }
}
