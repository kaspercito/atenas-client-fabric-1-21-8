package com.atenas.fabric.net;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.MinecraftClient;

/**
 * Cuentas del launcher dentro del juego (la misma Accounts de 1.8.9, mismos archivos y mismo protocolo).
 *
 * - Lee la LISTA de cuentas que el launcher guarda en config/accounts.json (solo tipo, nick y uuid; los tokens van cifrados
 *   y este codigo nunca los lee ni los muestra).
 * - Cuenta sin conexion: se cambia en caliente, sin reiniciar (se reemplaza la Session del juego).
 * - Cuenta de Microsoft: el token solo lo puede descifrar el launcher, asi que el juego deja config/switch-request.json y se
 *   cierra; el launcher activa esa cuenta y vuelve a abrir el mismo modpack solo.
 * - Agregar premium / guardar un nick / quitar una cuenta: pedidos en config/account-request.json que el launcher contesta en
 *   config/account-result.json.
 */
public final class Accounts {
    private Accounts() {}

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    public static final class Entry {
        public String id = "", kind = "microsoft", name = "", uuid = "";
        public boolean offline() { return "offline".equals(kind); }
    }

    /** Respuesta del launcher a un pedido. */
    public static final class Result {
        public boolean ok;
        public String message = "";
    }

    // ------------------------------------------------------------ rutas

    /** Carpeta del launcher: el launcher la pasa con -Datenas.home; si se abre sin launcher, ~/.atenasclient. */
    public static File root() {
        String h = System.getProperty("atenas.home");
        if (h != null && h.trim().length() > 0) return new File(h.trim());
        return new File(System.getProperty("user.home"), ".atenasclient");
    }

    private static File configDir() { return new File(root(), "config"); }

    /** true si el juego lo abrio el launcher (entonces puede reabrirlo con otra cuenta). */
    public static boolean launchedByLauncher() {
        String h = System.getProperty("atenas.home");
        return h != null && h.trim().length() > 0;
    }

    // ------------------------------------------------------------ lectura

    public static boolean validName(String n) { return n != null && NAME.matcher(n).matches(); }

    /** Misma formula que el launcher (OfflinePlayer:<nick>): UUID v3 de Java = MD5 con los bits de version. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static String dashless(String u) { return u == null ? "" : u.replace("-", "").toLowerCase(Locale.ROOT); }

    private static String sessionName() {
        try { return MinecraftClient.getInstance().getSession().getUsername(); } catch (Throwable t) { return ""; }
    }

    private static String sessionUuid() {
        try {
            UUID u = MinecraftClient.getInstance().getSession().getUuidOrNull();
            return u == null ? "" : dashless(u.toString());
        } catch (Throwable t) { return ""; }
    }

    /** Nick con el que se juega ahora. */
    public static String currentName() { return sessionName(); }

    /** Cuentas guardadas en el launcher (la mas reciente primero). Nunca lanza: si no hay archivo, lista vacia. */
    public static List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        File f = new File(configDir(), "accounts.json");
        if (!f.isFile()) return out;
        try {
            JsonElement root = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            if (!root.isJsonArray()) return out;
            for (JsonElement el : root.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String kind = str(o, "kind"), name = str(o, "name"), uuid = str(o, "uuid");
                if (!"offline".equals(kind) && !"microsoft".equals(kind)) continue;
                if (!validName(name) || uuid.isEmpty()) continue;
                Entry e = new Entry();
                e.kind = kind;
                e.name = name;
                e.uuid = uuid;
                e.id = kind + ":" + uuid.toLowerCase(Locale.ROOT); // igual que accounts.js del launcher
                boolean dup = false;
                for (Entry x : out) if (x.id.equals(e.id)) { dup = true; break; }
                if (!dup) out.add(e);
            }
        } catch (Exception ignored) { /* archivo a medio escribir: se muestra vacio */ }
        return out;
    }

    /** true si la cuenta con la que se juega ahora es SIN CONEXION: su UUID es el de la formula OfflinePlayer:<nick> (las premium lo tienen aleatorio). */
    public static boolean currentIsOffline() {
        String pid = sessionUuid();
        return pid.isEmpty() || pid.equals(dashless(offlineUuid(sessionName()).toString()));
    }

    /** true si esta cuenta es la que esta jugando ahora. */
    public static boolean isActive(Entry e) {
        if (e == null) return false;
        String pid = sessionUuid();
        if (!pid.isEmpty() && pid.equals(dashless(e.uuid))) return true;
        return pid.isEmpty() && e.name.equalsIgnoreCase(sessionName());
    }

    // ------------------------------------------------------------ cambio de cuenta

    /**
     * Cambia a una cuenta SIN CONEXION al instante. Devuelve null si salio bien o el mensaje de error.
     * Ojo: en servidores con online-mode=true esa cuenta no puede entrar (igual que en el launcher).
     */
    public static String switchToOffline(String name) {
        if (!validName(name)) return "El nombre debe tener de 3 a 16 caracteres: letras, numeros o _";
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) return "Sal del mundo o del servidor antes de cambiar de cuenta";
        try {
            replaceSession(mc, name, offlineUuid(name));
        } catch (Throwable t) {
            System.err.println("[AtenasClient] no se pudo reemplazar la sesion: " + t);
            return "No se pudo cambiar la cuenta dentro del juego. Cambiala en el launcher.";
        }
        afterChange();
        try { if (launchedByLauncher()) request("add-offline", null, name); } catch (IOException ignored) { /* solo para que aparezca en la lista */ }
        return null;
    }

    /**
     * Reemplaza la Session del juego SIN nombrar clases ni campos de Yarn: se busca el campo de instancia cuyo tipo es el de la sesion
     * actual y se arma la nueva con el constructor que tenga (nick, UUID, token, xuid/clientId opcionales, tipo de cuenta si existe).
     */
    private static void replaceSession(MinecraftClient mc, String name, UUID uuid) throws Exception {
        Object cur = mc.getSession();
        Class<?> type = cur.getClass();
        Object next = null;
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            Object[] a = new Object[p.length];
            boolean ok = true, gotName = false, gotUuid = false;
            for (int i = 0; i < p.length && ok; i++) {
                if (p[i] == String.class) { if (!gotName) { a[i] = name; gotName = true; } else a[i] = ""; }
                else if (p[i] == UUID.class) { a[i] = uuid; gotUuid = true; }
                else if (p[i] == Optional.class) a[i] = Optional.empty();
                else if (p[i].isEnum() && p[i].getEnumConstants().length > 0) a[i] = legacyConstant(p[i]);
                else ok = false;
            }
            if (!ok || !gotName || !gotUuid) continue;
            c.setAccessible(true);
            next = c.newInstance(a);
            break;
        }
        if (next == null) throw new IllegalStateException("sin constructor de Session compatible");
        for (Field f : MinecraftClient.class.getDeclaredFields()) {
            if (f.getType() != type || Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            f.set(mc, next); // funciona aunque el campo sea final (es de instancia)
            return;
        }
        throw new IllegalStateException("no se encontro el campo de la sesion");
    }

    /** Del tipo de cuenta (MSA / LEGACY / MOJANG) prefiere LEGACY; si no existe, el primero. */
    private static Object legacyConstant(Class<?> en) {
        Object[] all = en.getEnumConstants();
        for (Object o : all) if ("LEGACY".equalsIgnoreCase(o.toString())) return o;
        return all[0];
    }

    /** Cuenta de Microsoft: pide al launcher que la active y reabra el juego. Devuelve null si salio bien o el error. */
    public static String requestLauncherSwitch(Entry e) {
        if (e == null) return "Cuenta no valida";
        if (!launchedByLauncher()) return "Abre el juego desde el launcher para cambiar a una cuenta de Microsoft";
        try {
            File dir = configDir();
            if (!dir.isDirectory() && !dir.mkdirs()) return "No se pudo escribir en la carpeta del launcher";
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id);
            o.addProperty("name", e.name);
            o.addProperty("at", System.currentTimeMillis());
            Files.writeString(new File(dir, "switch-request.json").toPath(), o.toString(), StandardCharsets.UTF_8);
            return null;
        } catch (IOException ex) {
            return "No se pudo avisar al launcher: " + ex.getMessage();
        }
    }

    // ------------------------------------------------------------ pedidos al launcher

    private static File requestFile() { return new File(configDir(), "account-request.json"); }
    private static File resultFile() { return new File(configDir(), "account-result.json"); }

    /** Marca de tiempo de accounts.json: cambia cuando el launcher agrega o quita una cuenta. */
    public static long listStamp() {
        File f = new File(configDir(), "accounts.json");
        return f.isFile() ? f.lastModified() : 0L;
    }

    /**
     * Deja un pedido para el launcher. action: add-microsoft | add-offline | remove.
     * Devuelve el id del pedido (para pedir la respuesta con {@link #result}); lanza IOException con un mensaje listo para mostrar.
     */
    public static String request(String action, String target, String name) throws IOException {
        if (!launchedByLauncher()) throw new IOException("Abre el juego desde el launcher para agregar o quitar cuentas.");
        File dir = configDir();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("No se pudo escribir en la carpeta del launcher.");
        String id = Long.toHexString(System.nanoTime()) + Long.toHexString(System.currentTimeMillis());
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("action", action);
        if (target != null) o.addProperty("target", target);
        if (name != null) o.addProperty("name", name);
        o.addProperty("at", System.currentTimeMillis());
        // se escribe en un archivo aparte y se renombra: el launcher nunca ve un archivo a medias
        File tmp = new File(dir, "account-request.tmp");
        Files.writeString(tmp.toPath(), o.toString(), StandardCharsets.UTF_8);
        File dst = requestFile();
        if (dst.exists() && !dst.delete()) throw new IOException("Hay otro pedido en curso. Espera un momento.");
        try { Files.move(tmp.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException ex) { throw new IOException("No se pudo avisar al launcher."); }
        return id;
    }

    /** true mientras el launcher todavia no tomo el pedido (si pasan unos segundos asi, el launcher no esta abierto). */
    public static boolean requestQueued() { return requestFile().exists(); }

    /** Retira el pedido si el launcher no llego a tomarlo. */
    public static void cancelRequest() { requestFile().delete(); }

    /** Respuesta del launcher a ese pedido, o null si todavia no hay. Nunca lanza. */
    public static Result result(String id) {
        File f = resultFile();
        if (id == null || !f.isFile()) return null;
        try {
            JsonElement el = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            if (!el.isJsonObject()) return null;
            JsonObject o = el.getAsJsonObject();
            if (!id.equals(str(o, "id"))) return null;
            Result r = new Result();
            r.ok = o.has("ok") && o.get("ok").isJsonPrimitive() && o.get("ok").getAsBoolean();
            r.message = str(o, "message");
            return r;
        } catch (Exception ignored) {
            return null; // a medio escribir: se vuelve a mirar en el siguiente tick
        }
    }

    /** La lista de amigos, el chat, la verificacion y los cosmeticos eran de la otra cuenta: se vuelve a pedir todo. */
    private static void afterChange() {
        Friends.onAccountChanged();
    }

    // ------------------------------------------------------------ utilidades

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }
}
