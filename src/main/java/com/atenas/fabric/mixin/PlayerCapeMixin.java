package com.atenas.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.atenas.fabric.cosmetics.CapeTextures;
import com.atenas.fabric.cosmetics.CosmeticSync;
import com.atenas.fabric.cosmetics.Cosmetics;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

/**
 * Capa: se cambia la textura de capa de las SkinTextures. ARREGLO DEL CRASH: antes habia UN solo inject con el nombre de Yarn
 * ("getSkinTextures") y, sin refmap en el jar, el juego no lo encontraba y moria ("Mixin transformation of class_742 failed").
 * Ahora hay dos inyecciones con require = 0 (si no encuentran el metodo se saltan, NUNCA tumban el juego): una con el nombre de Yarn
 * (se remapea si hay refmap) y otra con el nombre intermediary (remap = false, funciona aunque no haya refmap). Es idempotente.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class PlayerCapeMixin {
    @Inject(method = {"getSkin", "getSkinTextures"}, at = @At("RETURN"), cancellable = true, require = 0)
    private void atenas$cape(CallbackInfoReturnable<SkinTextures> cir) { apply(cir); }

    @Inject(method = "method_52814", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void atenas$capeI(CallbackInfoReturnable<SkinTextures> cir) { apply(cir); }

    private void apply(CallbackInfoReturnable<SkinTextures> cir) {
        String id = CosmeticSync.capeOf((AbstractClientPlayerEntity) (Object) this);
        if (id == null) return;
        Identifier tex = CapeTextures.get(Cosmetics.find(id));
        if (tex == null) return;
        SkinTextures s = cir.getReturnValue();
        cir.setReturnValue(new SkinTextures(s.texture(), s.textureUrl(), tex, s.elytraTexture(), s.model(), s.secure()));
    }
}
