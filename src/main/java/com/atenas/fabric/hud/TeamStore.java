package com.atenas.fabric.hud;

import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.atenas.fabric.shared.SharedDir;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/** Lista manual de companeros (nicks). Mismo archivo y formato que 1.8.9: <.minecraft>/config/atenas-team.json {"members": [...]}. */
public final class TeamStore {
    private TeamStore() {}

    public static final int MAX = 24;
    private static final List<String> NAMES = new ArrayList<>();
    private static boolean loaded;

    private static File file() {
        File base = SharedDir.dir().getParentFile();
        return new File(new File(base == null ? new File(".") : base, "config"), "atenas-team.json");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File f = file();
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(Files.newInputStream(f.toPath()), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root.has("members")) for (JsonElement el : root.getAsJsonArray("members")) {
                String n = el.getAsString();
                if (valid(n) && !has(n)) NAMES.add(n);
            }
        } catch (Exception e) { System.out.println("[Atenas] No se pudo leer el equipo: " + e); }
    }

    public static boolean valid(String n) { return n != null && n.matches("^[A-Za-z0-9_]{3,16}$"); }
    public static synchronized List<String> names() { ensureLoaded(); return new ArrayList<>(NAMES); }
    public static synchronized boolean has(String name) { ensureLoaded(); for (String n : NAMES) if (n.equalsIgnoreCase(name)) return true; return false; }

    public static synchronized boolean add(String name) {
        ensureLoaded();
        if (!valid(name) || has(name) || NAMES.size() >= MAX) return false;
        NAMES.add(name); save();
        return true;
    }

    public static synchronized void remove(String name) {
        ensureLoaded();
        for (int i = 0; i < NAMES.size(); i++) if (NAMES.get(i).equalsIgnoreCase(name)) { NAMES.remove(i); break; }
        save();
    }

    private static void save() {
        try {
            JsonArray arr = new JsonArray();
            for (String n : NAMES) arr.add(new JsonPrimitive(n));
            JsonObject root = new JsonObject();
            root.add("members", arr);
            File f = file();
            f.getParentFile().mkdirs();
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(f.toPath()), StandardCharsets.UTF_8)) { new Gson().toJson(root, w); }
        } catch (Exception e) { System.out.println("[Atenas] No se pudo guardar el equipo: " + e); }
    }
}
