package com.atenas.fabric.hud;

import com.atenas.fabric.shared.Config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Dibuja cada panel del HUD en su sitio (cada modulo por separado, como en 1.8.9), el resalte de bloque y los marcadores del mundo. */
public final class Hud {
    private Hud() {}

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        boolean editing = mc.currentScreen instanceof HudEditorScreen;
        if (Config.get("hud", true)) {
            for (HudPanels.Panel p : HudPanels.all()) {
                if (!p.enabled()) continue;
                try {
                    int[] pos = HudPos.pos(p, sw, sh, p.width(mc), p.height(mc));
                    HudPos.begin(ctx, pos[0], pos[1], HudPos.scale(p.id));
                    try { p.draw(ctx, mc, editing); } finally { HudPos.end(ctx); }
                } catch (RuntimeException e) {
                    com.atenas.fabric.AtenasFabricClient.reportOnce("hud-" + p.id, e);
                }
            }
        }
        BlockOverlay.draw(mc, ctx, tick.getTickProgress(false));
        Markers.draw(mc, ctx, tick.getTickProgress(false));
    }
}
