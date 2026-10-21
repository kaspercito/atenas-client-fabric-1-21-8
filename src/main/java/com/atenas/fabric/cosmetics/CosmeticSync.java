package com.atenas.fabric.cosmetics;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.atenas.fabric.net.Social;
import com.atenas.fabric.shared.SharedDir;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * Mismo servicio y mismas rutas que el cliente 1.8.9 y el mod 1.20.1: /cosmetics/set (sube tu capa), /cosmetics/get (la tuya y la de los
 * jugadores cercanos) y /cosmetics/catalog (capas nuevas, cada 45 s con cache en disco). Solo viaja tu nick y el id de la capa.
 * Se sube SOLO el campo "cape": el servicio conserva tus alas/sombrero/mascota elegidos desde 1.8.9 o 1.20.1.
 */
public final class CosmeticSync {
    private CosmeticSync() {}

    /** Logo (glifo de la fuente por defecto, ver assets/minecraft/font/default.json). */
    public static final String LOGO = "\ue0a7";
    /** nick -> {cape, wings, hat}; presente = el servicio lo conoce (usa el cliente/mod). */
    private static final Map<String, String[]> USERS = new ConcurrentHashMap<>();
    private static final Map<String, String> REMOTE = new ConcurrentHashMap<>();

    public static boolean isUser(AbstractClientPlayerEntity p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && p == mc.player) return true;
        return USERS.containsKey(p.getName().getString().toLowerCase(Locale.ROOT));
    }

    public static boolean isUserName(String n) { return n != null && USERS.containsKey(n.toLowerCase(Locale.ROOT)); }

    /** Id de alas / sombrero de este jugador (null = ninguno). */
    public static String partOf(AbstractClientPlayerEntity p, String type) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        if (p == mc.player) return Cosmetics.selectedId(type);
        if (p.squaredDistanceTo(mc.player) > Cosmetics.MAX_DIST_SQ) return null;
        String[] v = USERS.get(p.getName().getString().toLowerCase(Locale.ROOT));
        if (v == null) return null;
        String x = "wings".equals(type) ? v[1] : "pet".equals(type) ? v[3] : v[2];
        return x == null || "none".equals(x) ? null : x;
    }
    private static final AtomicBoolean PUSH = new AtomicBoolean(), SELF = new AtomicBoolean(), OTHERS = new AtomicBoolean(), CAT = new AtomicBoolean();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Atenas-Cosmetics");
        t.setDaemon(true);
        return t;
    });
    private static volatile boolean dirty;
    private static volatile int rev;
    private static volatile String problem;
    private static volatile String catRev = "";
    private static long lastPush, lastSelf, lastOthers, lastCat, ticks;
    private static String lastNames = "";
    private static boolean cacheLoaded;

    public static String problem() { return problem; }

    public static void markDirty() { dirty = true; rev++; lastPush = 0; }

    /** Pide ya lo tuyo al servicio (al abrir el menu: un cambio hecho en 1.8.9 aparece al instante). */
    public static void refreshNow() { lastSelf = 0; lastCat = 0; catRev = ""; }

    private static String me() { return MinecraftClient.getInstance().getSession().getUsername(); }

    private static void fail(String where, Exception e) {
        String code = e instanceof Social.ApiError ? ((Social.ApiError) e).code : "";
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if ("verify_required".equals(code) || "not_verified".equals(code)) m = "este nick es premium: entra con tu cuenta de Microsoft para verificar.";
        else if ("not_found".equals(code)) m = "el servicio no tiene los cosmeticos (hay que actualizar el Worker).";
        problem = "Sin sincronizar: " + m;
        System.err.println("[AtenasClient] cosmeticos " + where + ": " + m);
    }

    /** Id de capa que debe llevar este jugador (null = ninguna). La tuya sale de tu eleccion; la de los demas, del servicio. */
    public static String capeOf(AbstractClientPlayerEntity p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        if (p == mc.player) return Cosmetics.selectedId();
        if (p.squaredDistanceTo(mc.player) > Cosmetics.MAX_DIST_SQ) return null;
        String v = REMOTE.get(p.getName().getString().toLowerCase(Locale.ROOT));
        return v == null || "none".equals(v) ? null : v;
    }

    /** Una vez por tick del cliente; trabaja cada ~1 s. */
    public static void tick(MinecraftClient mc) {
        if (++ticks % 20 != 0) return;
        String me = me();
        if (!Social.validName(me)) return;
        long now = System.currentTimeMillis();

        if (!cacheLoaded) { cacheLoaded = true; loadCatalogCache(me); }

        // catalogo (cada 45 s)
        if (now - lastCat > 45000 && CAT.compareAndSet(false, true)) {
            lastCat = now;
            final String sent = catRev;
            POOL.execute(() -> {
                try {
                    JsonObject body = new JsonObject();
                    body.addProperty("rev", sent);
                    JsonObject res = Social.post(me, "/cosmetics/catalog", body);
                    if (res.has("unchanged") && res.get("unchanged").getAsBoolean()) return;
                    JsonArray items = res.has("items") && res.get("items").isJsonArray() ? res.getAsJsonArray("items") : new JsonArray();
                    String newRev = res.has("rev") && !res.get("rev").isJsonNull() ? res.get("rev").getAsString() : "";
                    List<Cosmetics.Def> defs = parse(items);
                    saveCatalogCache(newRev, items);
                    mc.execute(() -> { catRev = newRev; Cosmetics.applyCatalog(defs, me); });
                } catch (Exception e) { lastCat = System.currentTimeMillis() - 35000; System.err.println("[AtenasClient] catalogo: " + e.getMessage()); }
                finally { CAT.set(false); }
            });
        }

        // subir tu eleccion
        if (dirty && now - lastPush > 4000 && PUSH.compareAndSet(false, true)) {
            lastPush = now;
            final int sentRev = rev;
            final String id = Cosmetics.selectedId();
            POOL.execute(() -> {
                try {
                    JsonObject b = new JsonObject();
                    b.addProperty("cape", id == null ? "none" : id);
                    String wg = Cosmetics.selectedId("wings"), ht = Cosmetics.selectedId("hat");
                    b.addProperty("wings", wg == null ? "none" : wg);
                    b.addProperty("hat", ht == null ? "none" : ht);
                    String pt = Cosmetics.selectedId("pet");
                    b.addProperty("pet", pt == null ? "none" : pt);
                    Social.post(me, "/cosmetics/set", b);
                    if (sentRev == rev) dirty = false;
                    problem = null;
                } catch (Exception e) { fail("subir", e); lastPush = System.currentTimeMillis() + 11000; }
                finally { PUSH.set(false); }
            });
        }

        // bajar lo tuyo (asi un cambio hecho en 1.8.9 o 1.20.1 aparece aqui)
        if (!dirty && !PUSH.get() && now - lastSelf > 15000 && SELF.compareAndSet(false, true)) {
            lastSelf = now;
            final int seen = rev;
            POOL.execute(() -> {
                try {
                    JsonElement ve = fetch(List.of(me.toLowerCase(Locale.ROOT))).get(me.toLowerCase(Locale.ROOT));
                    JsonObject v = ve == null ? null : ve.getAsJsonObject();
                    problem = null;
                    if (v == null) { if (seen == rev) { dirty = true; } return; } // el servicio no te conoce: sube lo que tienes
                    String cape = str(v, "cape");
                    final String wg = str(v, "wings"), ht = str(v, "hat"), pe = str(v, "pet");
                    mc.execute(() -> { if (seen == rev && !dirty) { Cosmetics.applyRemote(cape); Cosmetics.applyRemote("wings", wg); Cosmetics.applyRemote("hat", ht); Cosmetics.applyRemote("pet", pe); } });
                } catch (Exception e) { fail("bajar lo tuyo", e); lastSelf = System.currentTimeMillis() - 10000; }
                finally { SELF.set(false); }
            });
        }

        // jugadores cercanos (los que se dibujan)
        if (mc.world == null || mc.player == null) return;
        List<String> names = new ArrayList<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            String n = p.getName().getString();
            if (Social.validName(n)) names.add(n.toLowerCase(Locale.ROOT));
        }
        if (names.isEmpty()) return;
        Collections.sort(names);
        if (names.size() > 40) names = new ArrayList<>(names.subList(0, 40));
        String joined = String.join(",", names);
        if ((joined.equals(lastNames) && now - lastOthers < 15000) || !OTHERS.compareAndSet(false, true)) return;
        lastNames = joined;
        lastOthers = now;
        final List<String> wanted = names;
        POOL.execute(() -> {
            try {
                JsonObject players = fetch(wanted);
                for (String n : wanted) {
                    JsonObject v = players.get(n) == null ? null : players.get(n).getAsJsonObject();
                    String cape = v == null ? null : str(v, "cape");
                    if (cape == null) REMOTE.remove(n); else REMOTE.put(n, cape);
                    if (v == null) USERS.remove(n); else USERS.put(n, new String[] {cape, str(v, "wings"), str(v, "hat"), str(v, "pet")});
                }
            } catch (Exception e) { fail("bajar los de otros", e); lastOthers = 0; }
            finally { OTHERS.set(false); }
        });
    }

    private static JsonObject fetch(List<String> names) throws Exception {
        JsonObject b = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String n : names) arr.add(n);
        b.add("names", arr);
        JsonObject res = Social.post(me(), "/cosmetics/get", b);
        JsonObject out = new JsonObject();
        if (res.has("players") && res.get("players").isJsonObject()) {
            for (Map.Entry<String, JsonElement> en : res.getAsJsonObject("players").entrySet()) if (en.getValue().isJsonObject()) out.add(en.getKey(), en.getValue());
        }
        return out;
    }

    // ------------------------------------------------------------------ catalogo

    private static List<Cosmetics.Def> parse(JsonArray arr) {
        List<Cosmetics.Def> out = new ArrayList<>();
        if (arr == null) return out;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            try {
                String type = str(o, "type");
                if (type == null || !(type.equals("cape") || type.equals("wings") || type.equals("hat") || type.equals("pet"))) continue;
                String id = str(o, "id");
                if (id == null || !id.matches("[a-z0-9_]{1,24}")) continue;
                // igual que 1.8.9: si trae lista de owners, es privado
                Set<String> owners = null;
                if (o.has("owners") && o.get("owners").isJsonArray()) {
                    owners = new HashSet<>();
                    for (JsonElement n : o.getAsJsonArray("owners")) owners.add(n.getAsString().toLowerCase(Locale.ROOT));
                }
                boolean priv = "private".equals(str(o, "scope")) || owners != null;
                String name = str(o, "name");
                out.add(new Cosmetics.Def(type, id, name == null ? id : name,
                    o.has("color") && !o.get("color").isJsonNull() ? (int) o.get("color").getAsLong() : 0xFF8B5CF6,
                    o.has("glow") && !o.get("glow").isJsonNull() && o.get("glow").getAsBoolean(),
                    o.has("frames") && !o.get("frames").isJsonNull() ? o.get("frames").getAsInt() : 1,
                    o.has("ver") && !o.get("ver").isJsonNull() ? o.get("ver").getAsInt() : 0, priv, owners));
            } catch (RuntimeException ex) { System.err.println("[AtenasClient] entrada del catalogo ignorada: " + ex); }
        }
        return out;
    }

    private static File cacheFile() { File d = new File(SharedDir.dir(), "cosmetics"); d.mkdirs(); return new File(d, "catalog.json"); }

    private static void loadCatalogCache(String me) {
        try {
            File f = cacheFile();
            if (!f.isFile()) return;
            JsonObject o = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            catRev = o.has("rev") && !o.get("rev").isJsonNull() ? o.get("rev").getAsString() : "";
            Cosmetics.applyCatalog(parse(o.getAsJsonArray("items")), me);
        } catch (Exception e) { catRev = ""; }
    }

    private static void saveCatalogCache(String r, JsonArray items) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("rev", r);
            o.add("items", items);
            Files.writeString(cacheFile().toPath(), o.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
    }

    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; }
}
