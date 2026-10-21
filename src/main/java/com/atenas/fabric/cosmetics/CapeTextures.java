package com.atenas.fabric.cosmetics;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.atenas.fabric.net.Social;
import com.atenas.fabric.shared.SharedDir;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Texturas de capa. Las normales del mod son recursos del jar (1024x512 = hoja 64x32 a 16x). Las animadas (tira vertical de N fotogramas)
 * y las del catalogo se decodifican en un hilo aparte, se cortan en fotogramas y se registran como texturas dinamicas (solo eso va en el hilo principal).
 */
public final class CapeTextures {
    private CapeTextures() {}

    private static final Map<String, Identifier[]> READY = new ConcurrentHashMap<>();
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Atenas-CapeTextures");
        t.setDaemon(true);
        return t;
    });

    /** Textura actual de la capa, o null si aun se esta cargando (mientras tanto no se cambia nada). */
    public static Identifier get(Cosmetics.Item it) {
        if (it == null) return null;
        if (!it.custom && it.frames <= 1) return Identifier.of(Cosmetics.NS, "textures/cosmetics/capes/" + it.id + ".png");
        String key = it.id + "#" + it.ver;
        Identifier[] f = READY.get(key);
        if (f == null) {
            if (BUSY.add(key)) POOL.execute(() -> load(it, key));
            return null;
        }
        if (f.length == 0) return null; // fallo: no se reintenta hasta el proximo catalogo con otra version
        int i = f.length > 1 && Cosmetics.animate ? (int) ((System.currentTimeMillis() / Cosmetics.FRAME_MS) % f.length) : 0;
        return f[i];
    }

    private static void load(Cosmetics.Item it, String key) {
        try {
            byte[] data = it.custom ? fromService(it) : fromJar(it.id);
            NativeImage img = NativeImage.read(new ByteArrayInputStream(data));
            int n = Math.max(1, it.frames), w = img.getWidth(), fh = img.getHeight() / n;
            if (w < 1 || fh < 1 || w > 4096 || img.getHeight() > 16384) { img.close(); throw new IllegalStateException("imagen no valida"); }
            NativeImage[] parts = new NativeImage[n];
            for (int i = 0; i < n; i++) {
                parts[i] = new NativeImage(w, fh, false);
                img.copyRect(parts[i], 0, i * fh, 0, 0, w, fh, false, false);
            }
            img.close();
            MinecraftClient.getInstance().execute(() -> {
                Identifier[] ids = new Identifier[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    ids[i] = Identifier.of(Cosmetics.NS, "dyn/cape_" + it.id + "_v" + it.ver + "_" + i);
                    MinecraftClient.getInstance().getTextureManager().registerTexture(ids[i], new NativeImageBackedTexture(() -> "atenas cape", parts[i]));
                }
                READY.put(key, ids);
                BUSY.remove(key);
            });
        } catch (Exception e) {
            System.err.println("[AtenasClient] textura de capa " + it.id + ": " + e);
            READY.put(key, new Identifier[0]);
            BUSY.remove(key);
        }
    }

    private static byte[] fromJar(String id) throws Exception {
        try (InputStream in = CapeTextures.class.getResourceAsStream("/assets/" + Cosmetics.NS + "/textures/cosmetics/capes/" + id + ".png")) {
            if (in == null) throw new IllegalStateException("falta la textura");
            return in.readAllBytes();
        }
    }

    /** Del disco (carpeta atenas/cosmetics, con la version en el nombre) o del servicio (/cosmetics/asset). */
    private static byte[] fromService(Cosmetics.Item it) throws Exception {
        File dir = new File(SharedDir.dir(), "cosmetics");
        dir.mkdirs();
        File f = new File(dir, "cape_" + it.id + "_tex_v" + it.ver + ".png");
        if (f.isFile() && f.length() > 0 && f.length() <= 1100000) return Files.readAllBytes(f.toPath());
        HttpRequest req = HttpRequest.newBuilder(URI.create(Social.URL + "/cosmetics/asset?key=cape:" + it.id + "&kind=tex&v=" + it.ver))
            .timeout(Duration.ofSeconds(12)).header("User-Agent", "AtenasClientFabric/0.2").GET().build();
        HttpResponse<byte[]> res = Social.http().send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
        byte[] b = res.body();
        if (b.length > 1100000) throw new IllegalStateException("textura demasiado grande");
        Files.write(f.toPath(), b);
        return b;
    }
}
