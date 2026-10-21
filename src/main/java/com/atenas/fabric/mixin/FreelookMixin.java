package com.atenas.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.atenas.fabric.hud.Freelook;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

/**
 * Perspectiva libre. (1) El mouse mueve la camara y no al jugador. (2) Mientras Camera.update calcula la vista, el jugador "mira"
 * hacia donde apunta la camara libre; antes y despues conserva su rotacion real (por eso no gira de verdad).
 * require = 0: si Yarn cambia un nombre, la inyeccion se salta y NUNCA tumba el juego (Freelook se apaga solo).
 */
@Mixin(Entity.class)
public abstract class FreelookMixin {
    private boolean atenas$isMe() { return (Object) this == MinecraftClient.getInstance().player; }

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
    private void atenas$look(double dx, double dy, CallbackInfo ci) {
        if (Freelook.swallowMouse() && atenas$isMe()) {
            Freelook.look(dx, dy);
            ci.cancel();
        }
    }

    @Inject(method = "getYaw(F)F", at = @At("RETURN"), cancellable = true, require = 0)
    private void atenas$yaw(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (Freelook.active && Freelook.inCamera && atenas$isMe()) {
            Freelook.yawHooked = true;
            cir.setReturnValue(Freelook.yaw);
        }
    }

    @Inject(method = "getPitch(F)F", at = @At("RETURN"), cancellable = true, require = 0)
    private void atenas$pitch(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (Freelook.active && Freelook.inCamera && atenas$isMe()) cir.setReturnValue(Freelook.pitch);
    }
}
