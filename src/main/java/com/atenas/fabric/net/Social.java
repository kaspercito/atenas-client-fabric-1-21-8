package com.atenas.fabric.net;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Duration;

import com.atenas.fabric.shared.SharedDir;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Servicio social de Atenas (el Worker de amigos). Identidad: nick de Minecraft + token de esta instalacion,
 * guardado en <.minecraft>/atenas/identity.json (igual para todas las versiones). Los nicks premium ya verificados piden verificar cada
 * instalacion nueva (verify_required): se hace solo con la sesion de Minecraft.
 */
public final class Social {
    private Social() {}

    public static final String URL = System.getProperty("atenas.social", "https://atenas-presence.misafi282.workers.dev");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static volatile String token;

    public static String token() {
        if (token != null) return token;
        synchronized (Social.class) {
            if (token != null) return token;
            File f = new File(SharedDir.dir(), "identity.json");
            try {
                if (f.exists()) {
                    JsonObject o = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
                    if (o.has("token") && o.get("token").getAsString().length() >= 16) return token = o.get("token").getAsString();
                }
            } catch (Exception ignored) { }
            byte[] b = new byte[24];
            new SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            token = sb.toString();
            try {
                JsonObject o = new JsonObject();
                o.addProperty("token", token);
                Files.writeString(f.toPath(), o.toString(), StandardCharsets.UTF_8);
            } catch (Exception ignored) { }
            return token;
        }
    }

    public static boolean validName(String n) { return n != null && n.matches("[A-Za-z0-9_]{3,16}"); }

    /** Error devuelto por el servicio (code = campo "error": verify_required, not_verified, not_found...). */
    public static final class ApiError extends Exception {
        public final String code;
        public ApiError(String code, String msg) { super(msg); this.code = code; }
    }

    private static volatile String verifiedFor = "", failedFor = "";
    private static volatile long failedAt;
    private static volatile boolean failedHard;

    public static boolean verified(String name) { return name != null && name.equalsIgnoreCase(verifiedFor); }

    public static void retryVerify() { failedFor = ""; failedAt = 0; }

    /** Cambio de cuenta: la verificacion de la anterior ya no vale. */
    public static synchronized void resetVerification() { verifiedFor = ""; failedFor = ""; failedAt = 0; }

    /** POST al servicio. Si el nick premium ya esta verificado desde otra instalacion, se verifica esta con la sesion de Minecraft y se reintenta. */
    public static JsonObject post(String name, String route, JsonObject body) throws Exception {
        ensureVerified(name, false);
        try {
            return raw(name, route, body);
        } catch (ApiError e) {
            if (!"verify_required".equals(e.code)) throw e;
            synchronized (Social.class) { verifiedFor = ""; failedFor = ""; }
            ensureVerified(name, true);
            if (!verified(name)) throw e;
            return raw(name, route, body);
        }
    }

    /** Verificacion premium: desafio del servicio + joinServer en Mojang con TU sesion + comprobacion del servicio (hasJoined). Nunca lanza. */
    private static synchronized void ensureVerified(String name, boolean force) {
        String low = name.toLowerCase(java.util.Locale.ROOT);
        if (!force && low.equals(verifiedFor)) return;
        if (!force && low.equals(failedFor) && System.currentTimeMillis() - failedAt < (failedHard ? 300000L : 20000L)) return;
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            for (int attempt = 1; attempt <= 3; attempt++) {
                JsonObject ch = raw(name, "/auth/challenge", new JsonObject());
                String sid = ch.get("serverId").getAsString();
                try {
                    sessionService().joinServer(mc.getSession().getUuidOrNull(), mc.getSession().getAccessToken(), sid);
                } catch (Exception e) { // cuenta sin conexion o sesion caducada: todo funciona menos lo privado
                    failedFor = low; failedAt = System.currentTimeMillis(); failedHard = true;
                    System.err.println("[AtenasClient] verificacion: Mojang rechazo la sesion (" + e.getMessage() + ")");
                    return;
                }
                try { Thread.sleep(300L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                try {
                    raw(name, "/auth/verify", new JsonObject());
                    verifiedFor = low; failedFor = "";
                    return;
                } catch (ApiError e) {
                    if ("not_verified".equals(e.code) && attempt < 3) continue; // Mojang a veces tarda en ver el joinServer
                    throw e;
                }
            }
        } catch (Exception e) {
            System.err.println("[AtenasClient] verificacion: " + e.getMessage());
            failedFor = low; failedAt = System.currentTimeMillis(); failedHard = false;
        }
    }

    private static volatile com.mojang.authlib.minecraft.MinecraftSessionService sessions;

    /** Servicio de sesion de Mojang (authlib directo: no depende del nombre del getter en Yarn). */
    private static com.mojang.authlib.minecraft.MinecraftSessionService sessionService() {
        if (sessions == null) {
            sessions = new com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService(java.net.Proxy.NO_PROXY).createMinecraftSessionService();
        }
        return sessions;
    }

    private static JsonObject raw(String name, String route, JsonObject body) throws Exception {
        if (!validName(name)) throw new IllegalStateException("Tu nick no es valido para el servicio de Atenas.");
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL + route))
            .timeout(Duration.ofSeconds("/auth/verify".equals(route) ? 25 : 8))
            .header("Content-Type", "application/json")
            .header("User-Agent", "AtenasClientFabric/0.2")
            .header("x-name", name).header("x-token", token())
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject o = null;
        try { o = JsonParser.parseString(res.body()).getAsJsonObject(); } catch (Exception ignored) { }
        if (o == null || res.statusCode() >= 400 || (o.has("ok") && !o.get("ok").getAsBoolean())) {
            String msg = o != null && o.has("message") ? o.get("message").getAsString() : "HTTP " + res.statusCode();
            if (o != null && o.has("error")) throw new ApiError(o.get("error").getAsString(), msg);
            throw new IllegalStateException(msg);
        }
        return o;
    }

    public static HttpClient http() { return HTTP; }
}
