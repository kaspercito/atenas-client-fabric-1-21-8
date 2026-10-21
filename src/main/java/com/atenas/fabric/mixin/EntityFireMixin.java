package com.atenas.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.atenas.fabric.hud.Modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

/**
 * Modulo "Sin Fuego": el fuego que tapa la pantalla se dibuja solo si el jugador local "esta en llamas" (Entity.isOnFire), asi que para
 * TU jugador en el cliente se responde false. Se engancha en Entity.isOnFire (metodo de instancia con firma estable) y no en el renderizador
 * de overlays (cuya firma cambio entre versiones y tumbaria el arranque). Efecto secundario: tampoco ves las llamas sobre tu propio modelo
 * en tercera persona. Solo toca al jugador local (nunca a otras entidades ni al servidor integrado). require = 0: si el nombre cambia, se salta.
 */
@Mixin(Entity.class)
public abstract class EntityFireMixin {
    @Inject(method = "isOnFire", at = @At("RETURN"), cancellable = true, require = 0)
    private void atenas$noFire(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && (Object) this == mc.player && Modules.on("lowfire")) cir.setReturnValue(false);
    }
}
