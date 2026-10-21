package com.atenas.fabric.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.MinecraftClient;

/** Amigos y chat: mismo servicio y mismas rutas que SocialClient de 1.8.9 (/sync, /friends/*, /chat/*). Las respuestas llegan en un hilo aparte. */
public final class Friends {
    private Friends() {}

    public static final class Friend {
        public String name = "", key = "", state = "offline", server = "", pack = "", previewBody = "";
        public int unread;
        public long previewId;
        public boolean online() { return !"offline".equals(state); }
        public boolean playing() { return "playing".equals(state); }
    }
    public static final class Req { public String name = "", key = ""; }
    public static final class Snapshot {
        public final List<Friend> friends = new ArrayList<>();
        public final List<Req> incoming = new ArrayList<>(), outgoing = new ArrayList<>();
        public int unread;
    }
    public static final class Msg { public long id; public boolean mine; public String body = ""; public long ts; public boolean pending, failed; }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "Atenas-Friends"); t.setDaemon(true); return t; });
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static volatile Snapshot snap = new Snapshot();
    private static volatile String status = "", error = "";
    private static volatile long lastSync;

    public static Snapshot snapshot() { return snap; }
    public static String status() { return status; }
    public static String error() { return error; }
    public static long sinceLastSync() { return System.currentTimeMillis() - lastSync; }
    public static String me() { return MinecraftClient.getInstance().getSession().getUsername(); }
    public static boolean usable() { return "ok".equals(status); }
    public static boolean verified() { return Social.verified(me()); }

    /** Avisos en pantalla (solicitudes, mensajes, amigos en linea). Se puede apagar en el menu: Acerca de. */
    public static boolean toasts() { return com.atenas.fabric.shared.Config.get("toasts", true); }

    public static Friend find(String key) {
        if (key == null) return null;
        for (Friend f : snap.friends) if (f.key.equals(key)) return f;
        return null;
    }

    private static volatile boolean haveSnap;
    private static volatile long lastPollAt;
    private static String lastPollSig;
    private static final AtomicBoolean POLL_BUSY = new AtomicBoolean();

    public static long sinceLastPoll() { return System.currentTimeMillis() - lastPollAt; }

    /** Cambio de cuenta dentro del juego: la lista de amigos y el chat eran de la otra persona; se pide todo de nuevo. */
    public static void onAccountChanged() {
        Social.resetVerification();
        snap = new Snapshot();
        haveSnap = false;
        status = "";
        error = "";
        lastSync = 0;
        lastPollSig = null;
        com.atenas.fabric.cosmetics.CosmeticSync.markDirty();
        com.atenas.fabric.cosmetics.CosmeticSync.refreshNow();
    }

    /**
     * Consulta rapida (/poll): el servicio responde con 2 lecturas pequenas en vez de bajar todo el estado. Se hace cada pocos segundos y solo
     * si algo cambio (mensaje o solicitud nueva) se pide el estado completo con /sync: los avisos llegan casi al instante sin cargar al servicio.
     */
    public static void poll(boolean inGame, String server) {
        String who = me();
        if (!haveSnap || !Social.validName(who)) return;
        if (!POLL_BUSY.compareAndSet(false, true)) return;
        lastPollAt = System.currentTimeMillis();
        POOL.execute(() -> {
            try {
                JsonObject r = Social.post(who, "/poll", new JsonObject());
                if (!who.equals(me())) return;
                String sig = str(r, "unread") + ":" + str(r, "last") + ":" + str(r, "incoming");
                boolean changed = lastPollSig != null && !sig.equals(lastPollSig);
                lastPollSig = sig;
                if (changed) sync(inGame, server);
            } catch (Exception ignored) { /* sin conexion: el ciclo normal reintenta */ }
            finally { POLL_BUSY.set(false); }
        });
    }

    /** Texto de una etiqueta con el numero de novedades: "Amigos (2)". */
    public static String badge(String label) {
        Snapshot s = snap;
        int n = s.unread + s.incoming.size();
        return n > 0 ? label + " (" + n + ")" : label;
    }

    public static void sync(boolean inGame, String server) {
        String who = me();
        if (!Social.validName(who)) { status = "noaccount"; return; }
        if (!BUSY.compareAndSet(false, true)) return;
        lastSync = System.currentTimeMillis();
        POOL.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                if (inGame) {
                    JsonObject p = new JsonObject();
                    p.addProperty("source", "game");
                    p.addProperty("server", server == null ? "" : server);
                    p.addProperty("pack", "Atenas Client");
                    p.addProperty("playing", true);
                    body.add("presence", p);
                }
                Snapshot next = parse(Social.post(who, "/sync", body));
                if (!who.equals(me())) return;
                Snapshot prev = snap;
                snap = next; status = "ok"; error = "";
                if (haveSnap && toasts()) com.atenas.fabric.gui.Toasts.onSnapshot(prev, next);
                haveSnap = true;
            } catch (Exception e) { status = "error"; error = String.valueOf(e.getMessage()); }
            finally { BUSY.set(false); }
        });
    }

    private static void action(String route, JsonObject body, BiConsumer<String, String> cb) {
        String who = me();
        POOL.execute(() -> {
            try {
                JsonObject r = Social.post(who, route, body);
                try { snap = parse(Social.post(who, "/sync", new JsonObject())); status = "ok"; } catch (Exception ignored) { }
                if (cb != null) cb.accept(str(r, "result") + "|" + str(r, "name"), null);
            } catch (Exception e) { if (cb != null) cb.accept(null, String.valueOf(e.getMessage())); }
        });
    }

    public static void add(String to, BiConsumer<String, String> cb) { JsonObject b = new JsonObject(); b.addProperty("to", to); action("/friends/add", b, cb); }
    public static void accept(String key, BiConsumer<String, String> cb) { JsonObject b = new JsonObject(); b.addProperty("name", key); action("/friends/accept", b, cb); }
    public static void remove(String key, BiConsumer<String, String> cb) { JsonObject b = new JsonObject(); b.addProperty("name", key); action("/friends/remove", b, cb); }

    public static void history(String key, long after, BiConsumer<List<Msg>, String> cb) {
        String who = me();
        POOL.execute(() -> {
            try {
                JsonObject b = new JsonObject();
                b.addProperty("with", key); b.addProperty("after", after);
                JsonObject r = Social.post(who, "/chat/history", b);
                List<Msg> out = new ArrayList<>();
                for (JsonElement el : r.getAsJsonArray("messages")) {
                    JsonObject m = el.getAsJsonObject();
                    Msg x = new Msg();
                    x.id = m.get("id").getAsLong(); x.mine = m.get("mine").getAsBoolean(); x.body = str(m, "body"); x.ts = m.get("ts").getAsLong();
                    out.add(x);
                }
                cb.accept(out, null);
            } catch (Exception e) { cb.accept(Collections.emptyList(), String.valueOf(e.getMessage())); }
        });
    }

    public static void send(String key, String text, BiConsumer<Msg, String> cb) {
        String who = me();
        POOL.execute(() -> {
            try {
                JsonObject b = new JsonObject();
                b.addProperty("to", key); b.addProperty("body", text);
                JsonObject m = Social.post(who, "/chat/send", b).getAsJsonObject("message");
                Msg x = new Msg();
                x.id = m.get("id").getAsLong(); x.mine = true; x.body = str(m, "body"); x.ts = m.get("ts").getAsLong();
                cb.accept(x, null);
            } catch (Exception e) { cb.accept(null, String.valueOf(e.getMessage())); }
        });
    }

    private static Snapshot parse(JsonObject o) {
        Snapshot s = new Snapshot();
        s.unread = o.has("unread") ? o.get("unread").getAsInt() : 0;
        if (o.has("friends")) for (JsonElement el : o.getAsJsonArray("friends")) {
            JsonObject f = el.getAsJsonObject();
            Friend fr = new Friend();
            fr.name = nz(str(f, "name")); fr.key = nz(str(f, "key")); fr.state = nz(str(f, "state")); fr.server = nz(str(f, "server")); fr.pack = nz(str(f, "pack"));
            if (fr.state.isEmpty()) fr.state = "offline";
            fr.unread = f.has("unread") ? f.get("unread").getAsInt() : 0;
            if (f.has("preview") && f.get("preview").isJsonObject()) {
                JsonObject pv = f.getAsJsonObject("preview");
                fr.previewBody = nz(str(pv, "body"));
                fr.previewId = pv.has("id") && !pv.get("id").isJsonNull() ? pv.get("id").getAsLong() : 0;
            }
            s.friends.add(fr);
        }
        fill(s.incoming, o.has("incoming") ? o.getAsJsonArray("incoming") : null);
        fill(s.outgoing, o.has("outgoing") ? o.getAsJsonArray("outgoing") : null);
        return s;
    }

    private static void fill(List<Req> out, JsonArray arr) {
        if (arr == null) return;
        for (JsonElement el : arr) { JsonObject r = el.getAsJsonObject(); Req q = new Req(); q.name = nz(str(r, "name")); q.key = nz(str(r, "key")); out.add(q); }
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; }
}
