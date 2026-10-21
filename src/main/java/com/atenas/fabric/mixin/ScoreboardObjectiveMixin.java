package com.atenas.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.atenas.fabric.hud.Modules;
import com.atenas.fabric.hud.Settings;

import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;

/**
 * Scoreboard sin los numeros rojos de la derecha. El marcador lateral pide el formato con getNumberFormatOr(RED): cuando el servidor no
 * definio uno propio devuelve ese RED, y aqui se cambia por el formato "en blanco" (no escribe nada). Si el servidor SI trae su propio
 * formato, se respeta. require = 0: si el nombre cambia, se salta sin tumbar el juego.
 */
@Mixin(ScoreboardObjective.class)
public abstract class ScoreboardObjectiveMixin {
    @Inject(method = "getNumberFormatOr", at = @At("RETURN"), cancellable = true, require = 0)
    private void atenas$noRedNumbers(NumberFormat fallback, CallbackInfoReturnable<NumberFormat> cir) {
        if (fallback == StyledNumberFormat.RED && cir.getReturnValue() == fallback
                && Modules.on("scoreboard") && Settings.b("scoreboard", "nonumbers")) {
            cir.setReturnValue(BlankNumberFormat.INSTANCE);
        }
    }
}
