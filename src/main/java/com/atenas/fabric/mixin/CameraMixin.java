package com.atenas.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.atenas.fabric.hud.Freelook;

import net.minecraft.client.render.Camera;

/** Marca el tramo en que la camara calcula su orientacion (ahi FreelookMixin cambia getYaw/getPitch del jugador). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void atenas$camStart(CallbackInfo ci) {
        Freelook.camHooked = true;
        Freelook.inCamera = Freelook.active;
    }

    @Inject(method = "update", at = @At("RETURN"), require = 0)
    private void atenas$camEnd(CallbackInfo ci) { Freelook.inCamera = false; }
}
