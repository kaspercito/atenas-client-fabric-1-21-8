package com.atenas.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.atenas.fabric.cosmetics.CosmeticSync;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

/** Logo de Atenas delante del nametag de quienes usan el cliente/mod. require = 0: si no encuentra el metodo, no pasa nada. */
@Mixin(Entity.class)
public abstract class NametagMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void atenas$tag(CallbackInfoReturnable<Text> cir) { tag(cir); }

    @Inject(method = "method_5476", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void atenas$tagI(CallbackInfoReturnable<Text> cir) { tag(cir); }

    private void tag(CallbackInfoReturnable<Text> cir) {
        Object self = this;
        if (!(self instanceof AbstractClientPlayerEntity p)) return;
        Text t = cir.getReturnValue();
        if (t == null || t.getString().startsWith(CosmeticSync.LOGO)) return;
        if (!CosmeticSync.isUser(p)) return;
        cir.setReturnValue(Text.literal(CosmeticSync.LOGO + " ").append(t));
    }
}
