package com.atenas.fabric.shared;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


/**
 * Waypoints guardados en <.minecraft real>/atenas/waypoints.json (el MISMO archivo para todas las versiones del cliente, aunque cada
 * una corra en su propia instancia del launcher), separados por mundo: cada servidor (por su IP) y cada mundo de
 * un jugador tiene su propia lista. Un waypoint tambien recuerda su dimension y solo se ve en ella.
 */
public final class WaypointStore {
    private WaypointStore() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<Waypoint>> WORLDS = new LinkedHashMap<String, List<Waypoint>>();
    private static boolean loaded;
    public static final int MAX_PER_WORLD = 100;

    private static File file() { return new File(SharedDir.dir(), "waypoints.json"); }

    private static long loadedStamp = -1L;
    private static long lastCheck;

    private static volatile String worldKey;
    private static volatile int dim;

    /** El cliente avisa en que mundo esta (null = sin mundo). Claves: "mp:ip" o "sp:carpeta", igual que en 1.8.9. */
    public static void setContext(String key, int dimension) { worldKey = key; dim = dimension; }
    public static String worldKey() { return worldKey; }
    public static int currentDim() { return dim; }

    private static synchronized void ensureLoaded() {
        long t = System.currentTimeMillis();
        if (loaded && t - lastCheck < 2000L) return;     // no consultar el disco en cada frame
        lastCheck = t;
        File f = file();
        if (!f.exists()) {
            loaded = true;
            return;
        }
        long stamp = f.lastModified() * 31L + f.length();
        if (loaded && stamp == loadedStamp) return;      // otra version del cliente pudo cambiarlo: se recarga solo si cambio
        loaded = true;
        readFrom(f);
        loadedStamp = stamp;
    }

    private static void readFrom(File f) {
        Reader r = null;
        try {
            r = new InputStreamReader(new FileInputStream(f), "UTF-8");
            JsonObject root = new JsonParser().parse(r).getAsJsonObject();
            JsonObject worlds = root.has("worlds") ? root.getAsJsonObject("worlds") : new JsonObject();
            Map<String, List<Waypoint>> fresh = new LinkedHashMap<String, List<Waypoint>>();
            for (Map.Entry<String, JsonElement> e : worlds.entrySet()) {
                List<Waypoint> list = new ArrayList<Waypoint>();
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    Waypoint w = new Waypoint(
                            o.has("id") ? o.get("id").getAsString() : newId(),
                            o.has("name") ? o.get("name").getAsString() : "Punto",
                            o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(),
                            o.has("dim") ? o.get("dim").getAsInt() : 0,
                            o.has("color") ? o.get("color").getAsInt() : 0);
                    if (o.has("visible")) w.visible = o.get("visible").getAsBoolean();
                    if (o.has("death")) w.death = o.get("death").getAsBoolean();
                    list.add(w);
                }
                fresh.put(e.getKey(), list);
            }
            WORLDS.clear();
            WORLDS.putAll(fresh);
        } catch (Exception ex) {
            System.err.println("[AtenasClient] No se pudieron leer los waypoints: " + ex);
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
    }

    public static synchronized void save() {
        Writer w = null;
        try {
            JsonObject worlds = new JsonObject();
            for (Map.Entry<String, List<Waypoint>> e : WORLDS.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                JsonArray arr = new JsonArray();
                for (Waypoint p : e.getValue()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("id", p.id);
                    o.addProperty("name", p.name);
                    o.addProperty("x", p.x);
                    o.addProperty("y", p.y);
                    o.addProperty("z", p.z);
                    o.addProperty("dim", p.dim);
                    o.addProperty("color", p.color);
                    o.addProperty("visible", p.visible);
                    if (p.death) o.addProperty("death", true);
                    arr.add(o);
                }
                worlds.add(e.getKey(), arr);
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.add("worlds", worlds);
            File f = file();
            f.getParentFile().mkdirs();
            File tmp = new File(f.getPath() + ".tmp");
            w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
            GSON.toJson(root, w);
            w.close();
            w = null;
            try {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFailed) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            loadedStamp = f.lastModified() * 31L + f.length();
        } catch (Exception ex) {
            System.err.println("[AtenasClient] No se pudieron guardar los waypoints: " + ex);
        } finally {
            if (w != null) try { w.close(); } catch (Exception ignored) { }
        }
    }

    private static String newId() { return Long.toString(System.nanoTime(), 36); }

    /** Lista del mundo actual (la misma instancia: se puede editar y luego llamar a save()). Vacia si no hay mundo. */
    public static synchronized List<Waypoint> current() {
        ensureLoaded();
        String key = worldKey();
        if (key == null) return new ArrayList<Waypoint>();
        List<Waypoint> l = WORLDS.get(key);
        if (l == null) { l = new ArrayList<Waypoint>(); WORLDS.put(key, l); }
        return l;
    }

    public static synchronized Waypoint add(String name, double x, double y, double z, int dim, int color) {
        List<Waypoint> l = current();
        if (l.size() >= MAX_PER_WORLD) return null;
        Waypoint w = new Waypoint(newId(), name, x, y, z, dim, color);
        l.add(w);
        save();
        return w;
    }

    public static synchronized void remove(Waypoint w) {
        current().remove(w);
        save();
    }

    /** Reemplaza el punto de muerte del mundo actual (solo se guarda el ultimo). */
    public static synchronized void setDeath(String name, double x, double y, double z, int dim) {
        List<Waypoint> l = current();
        Waypoint death = null;
        for (Waypoint w : l) if (w.death) { death = w; break; }
        if (death == null) {
            death = new Waypoint(newId(), name, x, y, z, dim, 0);
            death.death = true;
            l.add(death);
        } else {
            death.name = name; death.x = x; death.y = y; death.z = z; death.dim = dim; death.visible = true;
        }
        save();
    }

    /** Nombre libre para un punto nuevo: "Punto 1", "Punto 2"... */
    public static synchronized String nextName() {
        List<Waypoint> l = current();
        for (int i = 1; i < 1000; i++) {
            String n = "Punto " + i;
            boolean used = false;
            for (Waypoint w : l) if (w.name.equalsIgnoreCase(n)) { used = true; break; }
            if (!used) return n;
        }
        return "Punto";
    }
}
