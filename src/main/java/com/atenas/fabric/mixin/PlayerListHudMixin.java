package com.atenas.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.atenas.fabric.cosmetics.CosmeticSync;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

/**
 * TAB de Atenas, igual que MixinGuiPlayerTabOverlay del 1.8.9: se usa el TAB de vanilla (layout, columnas, cabecera, pie,
 * puntuaciones, ping, cabezas, orden por equipo) y solo se (1) quitan el fondo general y los cuadros por jugador y
 * (2) se antepone el logo de Atenas al nombre de quienes usan el cliente/mod. require = 0: si un nombre cambia, se salta sin tumbar el juego.
 */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {

    /** Todos los fill() de PlayerListHud.render son fondos: el general, los de cabecera/pie y los cuadros de cada jugador. */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"), require = 0)
    private void atenas$noBackground(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        if (com.atenas.fabric.hud.Modules.on("nobg")) return;
        ctx.fill(x1, y1, x2, y2, color);
    }

    /** Logo al principio del nombre; el ancho de columna lo calcula vanilla con este mismo texto, asi que la fila se ensancha sola. */
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void atenas$logo(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (entry == null || entry.getProfile() == null || !com.atenas.fabric.hud.Modules.on("tablogo")) return;
        String n = entry.getProfile().name();
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean user = n.equalsIgnoreCase(mc.getSession().getUsername()) || CosmeticSync.isUserName(n);
        if (user) cir.setReturnValue(Text.literal(CosmeticSync.LOGO + " ").append(cir.getReturnValue()));
    }
}
