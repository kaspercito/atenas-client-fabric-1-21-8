package com.atenas.fabric.shared;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/** Ajustes simples (true/false) en <.minecraft>/atenas/fabric-config.properties. */
public final class Config {
    private Config() {}

    private static final Properties P = new Properties();
    private static boolean loaded;

    private static File file() { return new File(SharedDir.dir(), "fabric-config.properties"); }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (FileInputStream in = new FileInputStream(file())) { P.load(in); } catch (Exception ignored) { }
    }

    public static synchronized boolean get(String key, boolean def) {
        load();
        String v = P.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    public static synchronized void set(String key, boolean v) {
        load();
        P.setProperty(key, Boolean.toString(v));
        try (FileOutputStream out = new FileOutputStream(file())) { P.store(out, "Atenas Client 1.21.8"); } catch (Exception ignored) { }
    }

    public static synchronized boolean toggle(String key, boolean def) {
        boolean n = !get(key, def);
        P.setProperty(key, Boolean.toString(n));
        try (FileOutputStream out = new FileOutputStream(file())) { P.store(out, "Atenas Client 1.21.8"); } catch (Exception ignored) { }
        return n;
    }

    // ---- numeros y textos (posiciones y ajustes del HUD). put* no escribe a disco: llama a save() al terminar (por ejemplo al soltar el mouse).
    public static synchronized int getInt(String key, int def) {
        load();
        try { String v = P.getProperty(key); return v == null ? def : Integer.parseInt(v.trim()); } catch (Exception e) { return def; }
    }

    public static synchronized float getFloat(String key, float def) {
        load();
        try { String v = P.getProperty(key); return v == null ? def : Float.parseFloat(v.trim()); } catch (Exception e) { return def; }
    }

    public static synchronized void putInt(String key, int v) { load(); P.setProperty(key, Integer.toString(v)); }

    public static synchronized void putFloat(String key, float v) { load(); P.setProperty(key, String.format(java.util.Locale.ROOT, "%.2f", v)); }

    public static synchronized void putBool(String key, boolean v) { load(); P.setProperty(key, Boolean.toString(v)); }

    public static synchronized String getStr(String key, String def) { load(); String v = P.getProperty(key); return v == null ? def : v; }

    public static synchronized void putStr(String key, String v) { load(); P.setProperty(key, v == null ? "" : v); }

    public static synchronized void save() {
        load();
        try (FileOutputStream out = new FileOutputStream(file())) { P.store(out, "Atenas Client 1.21.8"); } catch (Exception ignored) { }
    }
}
