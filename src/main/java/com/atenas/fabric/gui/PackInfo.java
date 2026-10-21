package com.atenas.fabric.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;

/**
 * Puerto de PackInfo + FeaturedServer de 1.8.9: datos del modpack que manda el launcher (-Datenas.pack / -Datenas.kind / -Datenas.server /
 * -Datenas.servername), el icono de 10x10 del pie del menu principal y el servidor destacado (arefy.net por defecto).
 */
public final class PackInfo {
    private PackInfo() {}

    public static final String DEFAULT_IP = "arefy.net", DEFAULT_NAME = "Arefy";
    private static final String STAR = "\u00A7e\u2605 \u00A7f";

    private static String prop(String k) { String v = System.getProperty(k); return v == null ? "" : v.trim(); }

    public static String packName() { String n = prop("atenas.pack"); return n.isEmpty() ? "Atenas Client 1.21.8" : n; }

    public static String kind() { String k = prop("atenas.kind"); return k.isEmpty() ? kindOfName(packName()) : k; }

    public static String kindOfName(String name) {
        String t = name == null ? "" : name.toLowerCase();
        if (t.contains("pvp") || t.contains("client") || t.contains("cliente") || t.contains("1.8")) return "pvp";
        if (t.contains("smp") || t.contains("survival") || t.contains("supervivencia")) return "smp";
        if (t.contains("1.20") || t.contains("forge") || t.contains("fabric") || t.contains("mod")) return "mods";
        return "cube";
    }

    public static String serverIp() { String v = prop("atenas.server"); return v.isEmpty() ? DEFAULT_IP : v; }
    public static String serverName() { String n = prop("atenas.servername"); return n.isEmpty() ? DEFAULT_NAME : n; }

    /** Icono de 10x10 dibujado con rectangulos (el mismo de 1.8.9). */
    public static void drawIcon(DrawContext g, int x, int y, String kind) {
        if ("pvp".equals(kind)) {
            for (int i = 0; i < 9; i++) { Ui.rect(g, x + i, y + i, 2, 1, 0xFFE8F1FB); Ui.rect(g, x + 8 - i, y + i, 2, 1, 0xFFA9BDD3); }
            Ui.rect(g, x, y + 8, 3, 2, 0xFFB45309); Ui.rect(g, x + 7, y + 8, 3, 2, 0xFFB45309);
        } else if ("smp".equals(kind)) {
            Ui.rect(g, x + 4, y + 6, 2, 4, 0xFF6B4423); Ui.rect(g, x + 1, y + 2, 8, 5, 0xFF2F8F46); Ui.rect(g, x + 3, y, 4, 3, 0xFF3BB55A);
        } else if ("mods".equals(kind)) {
            Ui.rect(g, x + 1, y + 3, 8, 6, 0xFF8B5CF6); Ui.rect(g, x + 3, y + 1, 4, 2, 0xFFA78BFA); Ui.rect(g, x + 4, y + 5, 2, 2, 0xFF22D3EE);
        } else {
            Ui.rect(g, x + 1, y + 1, 8, 8, 0xFF3F9A4B); Ui.rect(g, x + 1, y + 1, 8, 2, 0xFF67C877); Ui.rect(g, x + 1, y + 3, 8, 6, 0xFF7A5230);
        }
    }

    /** Deja el servidor destacado primero en la lista de Multijugador, con estrella (como FeaturedServer.ensure de 1.8.9). */
    public static void ensureFeatured() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            ServerList list = new ServerList(mc);
            list.loadFile();
            String ip = serverIp(), title = STAR + serverName();
            int found = -1;
            for (int i = 0; i < list.size(); i++) if (list.get(i).address.equalsIgnoreCase(ip)) { found = i; break; }
            boolean changed = false;
            if (found < 0) { list.add(new ServerInfo(title, ip, ServerInfo.ServerType.OTHER), false); found = list.size() - 1; changed = true; }
            else if (!title.equals(list.get(found).name)) { list.get(found).name = title; changed = true; }
            for (int i = found; i > 0; i--) { list.swapEntries(i, i - 1); changed = true; }
            if (changed) list.saveFile();
        } catch (Throwable t) { System.err.println("[Atenas] servidor destacado: " + t); }
    }

    /** Entra directo al servidor destacado; si algo falla, abre Multijugador. */
    public static void join(Screen parent) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String ip = serverIp();
        try { ConnectScreen.connect(parent, mc, ServerAddress.parse(ip), new ServerInfo(serverName(), ip, ServerInfo.ServerType.OTHER), false, null); }
        catch (Throwable t) { System.err.println("[Atenas] no se pudo entrar a " + ip + ": " + t); ensureFeatured(); mc.setScreen(new net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen(parent)); }
    }
}
