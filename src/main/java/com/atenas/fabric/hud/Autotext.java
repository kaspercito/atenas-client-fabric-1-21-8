package com.atenas.fabric.hud;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.atenas.fabric.gui.Keys;
import com.atenas.fabric.shared.Config;
import com.atenas.fabric.shared.SharedDir;

import net.minecraft.client.MinecraftClient;

/**
 * Autotext como en 1.8.9 (AutotextModule): hasta 12 atajos que envian un mensaje o comando al chat. Cada atajo es una tecla del teclado
 * o un boton del mouse. Codigo: > 0 = tecla GLFW; < 0 = mouse (-100 + boton, 0 = clic izquierdo, igual que 1.8.9); 0 = sin tecla.
 * Se editan en {@link AutotextScreen}. Se guardan en fabric-config.properties como at.n, at.&lt;i&gt;.k y at.&lt;i&gt;.m.
 * Las teclas se leen directo de GLFW (flanco de subida), asi un atajo puede ser CUALQUIER tecla o boton sin registrar 12 KeyBinding.
 */
public final class Autotext {
    private Autotext() {}

    public static final int MAX = 12;

    public static final class Entry {
        public int key;
        public String msg;
        public Entry(int key, String msg) { this.key = key; this.msg = msg; }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static boolean loaded;
    private static final boolean[] WAS = new boolean[MAX];
    private static long lastSent;

    /** Lista viva (se puede editar; luego llama a {@link #save()}). */
    public static synchronized List<Entry> entries() { load(); return ENTRIES; }

    private static void load() {
        if (loaded) return;
        loaded = true;
        int n = Config.getInt("at.n", -1);
        if (n >= 0) {
            for (int i = 0; i < Math.min(n, MAX); i++) ENTRIES.add(new Entry(Config.getInt("at." + i + ".k", 0), Config.getStr("at." + i + ".m", "")));
            return;
        }
        // Migracion desde la version anterior: 4 lineas (autotext.txt o ajustes) con Numpad 1-4.
        List<String> lines = new ArrayList<>();
        try {
            File f = new File(SharedDir.dir(), "autotext.txt");
            if (f.exists()) lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
        for (int i = 0; i < 4; i++) {
            String t = Config.getStr("s.autotext.line" + (i + 1), i < lines.size() ? lines.get(i).trim() : "");
            if (t != null && !t.trim().isEmpty()) ENTRIES.add(new Entry(GLFW.GLFW_KEY_KP_1 + i, t.trim()));
        }
        save();
    }

    public static synchronized void save() {
        load();
        Config.putInt("at.n", ENTRIES.size());
        for (int i = 0; i < ENTRIES.size(); i++) {
            Config.putInt("at." + i + ".k", ENTRIES.get(i).key);
            Config.putStr("at." + i + ".m", ENTRIES.get(i).msg == null ? "" : ENTRIES.get(i).msg);
        }
        Config.save();
    }

    /** Nombre legible de una tecla o boton del mouse. */
    public static String keyName(int key) {
        if (key == 0) return "Sin tecla";
        if (key < 0) {
            int b = key + 100;
            if (b == 0) return "Clic izq.";
            if (b == 1) return "Clic der.";
            if (b == 2) return "Clic central";
            return "Mouse " + (b + 1);
        }
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) return String.valueOf((char) key);
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return String.valueOf((char) key);
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) return "Num " + (key - GLFW.GLFW_KEY_KP_0);
        switch (key) {
            case GLFW.GLFW_KEY_SPACE: return "Espacio";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_BACKSPACE: return "Retroceso";
            case GLFW.GLFW_KEY_INSERT: return "Insert";
            case GLFW.GLFW_KEY_DELETE: return "Supr";
            case GLFW.GLFW_KEY_HOME: return "Inicio";
            case GLFW.GLFW_KEY_END: return "Fin";
            case GLFW.GLFW_KEY_PAGE_UP: return "Re Pág";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "Av Pág";
            case GLFW.GLFW_KEY_UP: return "Flecha arriba";
            case GLFW.GLFW_KEY_DOWN: return "Flecha abajo";
            case GLFW.GLFW_KEY_LEFT: return "Flecha izq.";
            case GLFW.GLFW_KEY_RIGHT: return "Flecha der.";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "Shift izq.";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "Shift der.";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "Ctrl izq.";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "Ctrl der.";
            case GLFW.GLFW_KEY_LEFT_ALT: return "Alt izq.";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "Alt der.";
            case GLFW.GLFW_KEY_KP_ADD: return "Num +";
            case GLFW.GLFW_KEY_KP_SUBTRACT: return "Num -";
            case GLFW.GLFW_KEY_KP_MULTIPLY: return "Num *";
            case GLFW.GLFW_KEY_KP_DIVIDE: return "Num /";
            case GLFW.GLFW_KEY_KP_ENTER: return "Num Enter";
            case GLFW.GLFW_KEY_KP_DECIMAL: return "Num .";
            default: break;
        }
        try { String n = GLFW.glfwGetKeyName(key, 0); if (n != null && !n.isEmpty()) return n.toUpperCase(java.util.Locale.ROOT); } catch (Throwable ignored) { }
        return "Tecla " + key;
    }

    /** Cada tick: si se acaba de pulsar la tecla o boton de un atajo (y no hay pantalla abierta), se envia su mensaje. Cooldown de 250 ms. */
    public static void tick(MinecraftClient mc) {
        List<Entry> es = entries();
        boolean enabled = Modules.on("autotext") && mc.player != null && mc.currentScreen == null;
        for (int i = 0; i < es.size() && i < MAX; i++) {
            Entry e = es.get(i);
            boolean down = e.key > 0 ? Keys.down(e.key) : e.key < 0 && Keys.mouseDown(e.key + 100);
            boolean edge = down && !WAS[i];
            WAS[i] = down; // se actualiza siempre: una tecla pulsada con un menu abierto no dispara al cerrarlo
            if (!edge || !enabled || e.msg == null || e.msg.trim().isEmpty()) continue;
            long now = System.currentTimeMillis();
            if (now - lastSent < 250L) continue;
            lastSent = now;
            String t = e.msg.trim();
            if (t.startsWith("/")) mc.player.networkHandler.sendChatCommand(t.substring(1));
            else mc.player.networkHandler.sendChatMessage(t);
        }
        for (int i = es.size(); i < MAX; i++) WAS[i] = false;
    }
}
