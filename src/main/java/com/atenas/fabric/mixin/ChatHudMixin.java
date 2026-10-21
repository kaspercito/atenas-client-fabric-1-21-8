package com.atenas.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.atenas.fabric.hud.Mods;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;

/**
 * Chat de Atenas: (1) hora [HH:mm] delante de TODOS los mensajes (Fabric API 1.21.11 no trae un evento para modificar el chat de
 * jugadores, asi que se hace aqui, en el unico metodo por donde pasan todos); (2) historial largo: vanilla guarda 100 mensajes
 * y 100 lineas, con "Chat largo" el tope sube a {@link Mods#CHAT_MAX}. require = 0: si un nombre cambia, se salta sin tumbar el juego.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void atenas$stamp(Text msg, MessageSignatureData sig, MessageIndicator ind, CallbackInfo ci) {
        Text stamped = Mods.stamp(msg);
        if (stamped == msg) return;
        ci.cancel();
        ((ChatHud) (Object) this).addMessage(stamped, sig, ind); // Mods.stamp reconoce su propio resultado: no se estampa dos veces
    }

    /** Los dos topes de 100 (mensajes y lineas ya partidas) de ChatHud. */
    @ModifyConstant(method = {"addVisibleMessage", "addMessage"}, constant = @Constant(intValue = 100), require = 0)
    private int atenas$historyLimit(int vanilla) { return Mods.chatLimit(vanilla); }
}
