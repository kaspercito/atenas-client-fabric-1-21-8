package com.atenas.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.atenas.fabric.hud.Mods;

import net.minecraft.client.Mouse;

/** Con la tecla de zoom pulsada, la rueda cambia el nivel de zoom en vez de cambiar la ranura de la barra de objetos. */
@Mixin(Mouse.class)
public abstract class ZoomScrollMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void atenas$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Mods.zoomWheel(vertical)) ci.cancel();
    }
}
