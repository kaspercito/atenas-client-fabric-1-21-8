package com.atenas.fabric.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

/** Lista de jugadores (TAB) propia: sin fondo y sin celdas. Reemplaza a la de vanilla cuando "TAB/Score sin fondo" esta activo. */
public final class TabList {
    private TabList() {}

    public static void render(MinecraftClient mc, DrawContext g) {
        if (mc.getNetworkHandler() == null || !mc.options.playerListKey.isPressed()) return;
        List<PlayerListEntry> l = new ArrayList<>(mc.getNetworkHandler().getPlayerList());
        l.sort(Comparator.comparing((PlayerListEntry e) -> e.getProfile().name().toLowerCase()));
        if (l.size() > 80) l = l.subList(0, 80);
        int rows = Math.min(20, Math.max(1, (l.size() + 3) / 4)), cols = (l.size() + rows - 1) / rows;
        int colW = 40;
        List<Text> names = new ArrayList<>();
        for (PlayerListEntry e : l) {
            Text t = mc.inGameHud.getPlayerListHud().getPlayerName(e);
            names.add(t);
            colW = Math.max(colW, mc.textRenderer.getWidth(t) + 12 + 34);
        }
        int total = cols * colW, x0 = g.getScaledWindowWidth() / 2 - total / 2, y0 = 14;
        for (int i = 0; i < l.size(); i++) {
            PlayerListEntry e = l.get(i);
            int x = x0 + (i / rows) * colW, y = y0 + (i % rows) * 10;
            try { PlayerSkinDrawer.draw(g, e.getSkinTextures(), x, y, 8); } catch (Throwable ignored) { }
            g.drawTextWithShadow(mc.textRenderer, names.get(i), x + 11, y, 0xFFFFFFFF);
            int ms = e.getLatency();
            int col = ms < 0 ? 0xFF888888 : ms < 80 ? 0xFF55FF55 : ms < 160 ? 0xFFFFFF55 : 0xFFFF5555;
            String s = ms < 0 ? "?" : ms + "ms";
            g.drawTextWithShadow(mc.textRenderer, s, x + colW - 6 - mc.textRenderer.getWidth(s), y, col);
        }
    }
}
