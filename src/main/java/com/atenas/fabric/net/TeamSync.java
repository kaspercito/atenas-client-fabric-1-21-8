package com.atenas.fabric.net;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Posiciones de companeros (ruta /team/sync del Worker). En segundo plano; cada ~2.5 s. */
public final class TeamSync {
    private TeamSync() {}

    public static final class Mate { public String name; public double x, y, z; public int dim; public float hp, max; }

    private static volatile Map<String, Mate> mates = Collections.emptyMap();
    private static volatile String error = "";
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static long last;

    public static Map<String, Mate> mates() { return mates; }
    public static String error() { return error; }
    public static void clear() { mates = Collections.emptyMap(); error = ""; last = 0; }
    /** true si hubo una sincronizacion reciente (el cliente esta en un servidor y Team View corre). */
    public static boolean active() { return System.currentTimeMillis() - last < 8000L; }

    public static void tick(String me, String server, boolean share, boolean friends, int dim, double x, double y, double z, float hp, float max) {
        long now = System.currentTimeMillis();
        if (now - last < 2500L || !BUSY.compareAndSet(false, true)) return;
        last = now;
        JsonObject b = new JsonObject();
        b.addProperty("server", server); b.addProperty("share", share); b.addProperty("friends", friends);
        b.addProperty("dim", dim); b.addProperty("x", x); b.addProperty("y", y); b.addProperty("z", z);
        b.addProperty("hp", hp); b.addProperty("max", max);
        // lista manual de equipo (H): el servicio la usa para devolver tambien a estos jugadores aunque esten lejos
        com.google.gson.JsonArray manual = new com.google.gson.JsonArray();
        for (String n : com.atenas.fabric.hud.TeamStore.names()) manual.add(n);
        b.add("manual", manual);
        Thread t = new Thread(() -> {
            try {
                JsonObject res = Social.post(me, "/team/sync", b);
                Map<String, Mate> next = new HashMap<>();
                if (res.has("mates")) for (JsonElement el : res.getAsJsonArray("mates")) {
                    JsonObject o = el.getAsJsonObject();
                    Mate m = new Mate();
                    m.name = o.get("name").getAsString(); m.x = o.get("x").getAsDouble(); m.y = o.get("y").getAsDouble(); m.z = o.get("z").getAsDouble();
                    m.dim = o.has("dim") ? o.get("dim").getAsInt() : 0;
                    m.hp = o.has("hp") ? o.get("hp").getAsFloat() : 20f; m.max = o.has("max") ? o.get("max").getAsFloat() : 20f;
                    next.put(m.name.toLowerCase(Locale.ROOT), m);
                }
                mates = next; error = "";
            } catch (Exception e) { error = String.valueOf(e.getMessage()); }
            finally { BUSY.set(false); }
        }, "Atenas-Team");
        t.setDaemon(true);
        t.start();
    }
}
