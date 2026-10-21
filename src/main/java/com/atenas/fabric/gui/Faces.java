package com.atenas.fabric.gui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import com.atenas.fabric.net.Social;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Caras (cabezas) de los jugadores, como en 1.8.9: se bajan una sola vez por nick, en un hilo aparte, de la misma pagina que usa el launcher
 * (mc-heads.net; si esta caida se prueban minotar y crafatar). Mientras llegan, o si no hay conexion, quien dibuja pone la inicial del nick;
 * si la descarga falla o la cuenta es sin conexion se dibuja la cabeza de Steve. Solo el hilo principal llama a {@link #get} y {@link #draw}.
 */
public final class Faces {
    private Faces() {}

    private static final int SIZE = 40;
    private static final Identifier STEVE = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    private static final Map<String, Identifier> READY = new HashMap<>();
    private static final Set<String> BUSY = new HashSet<>();
    /** Nicks cuya cara no se pudo bajar (no existe, sin conexion...): se dibujan como Steve. Lo escribe el hilo de descarga. */
    private static final Set<String> FAILED = Collections.synchronizedSet(new HashSet<String>());
    private static int counter;

    /** La textura de la cara, o null si todavia no llego (o no se pudo bajar). */
    public static Identifier get(String name) {
        if (name == null || !Social.validName(name)) return null;
        final String key = name.toLowerCase(Locale.ROOT);
        Identifier id = READY.get(key);
        if (id != null || BUSY.contains(key)) return id;
        BUSY.add(key); // se intenta una vez por sesion: si falla, queda la inicial / Steve
        final String shown = name;
        Thread t = new Thread(() -> {
            try {
                String[] urls = {"https://mc-heads.net/avatar/" + shown + "/" + SIZE, "https://minotar.net/avatar/" + shown + "/" + SIZE,
                        "https://crafatar.com/avatars/" + shown + "?size=" + SIZE + "&overlay"};
                BufferedImage got = null;
                for (String u : urls) {
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                        c.setConnectTimeout(4000);
                        c.setReadTimeout(5000);
                        c.setRequestProperty("User-Agent", "AtenasClient");
                        try (InputStream in = c.getInputStream()) { got = ImageIO.read(in); }
                        if (got != null) break;
                    } catch (Exception ignoredUrl) { /* siguiente fuente */ }
                }
                if (got == null) { FAILED.add(key); return; }
                BufferedImage sq = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
                sq.createGraphics().drawImage(got, 0, 0, SIZE, SIZE, null);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(sq, "png", bos);
                final byte[] png = bos.toByteArray();
                MinecraftClient.getInstance().execute(() -> {
                    try {
                        NativeImage ni = NativeImage.read(new ByteArrayInputStream(png));
                        Identifier tex = Identifier.of("atenasclientfabric", "dyn/face_" + (counter++));
                        MinecraftClient.getInstance().getTextureManager().registerTexture(tex, new NativeImageBackedTexture(() -> "atenas face", ni));
                        READY.put(key, tex);
                    } catch (Throwable e) { FAILED.add(key); }
                });
            } catch (Exception ignored) { FAILED.add(key); }
        }, "Atenas-Face");
        t.setDaemon(true);
        t.start();
        return null;
    }

    /** true si ya se sabe que no hay cara para este nick (entonces se usa Steve). */
    public static boolean failed(String name) { return name != null && FAILED.contains(name.toLowerCase(Locale.ROOT)); }

    /** Cabeza de Steve (con la capa de encima), a "size" pixeles de GUI. */
    public static void drawSteve(DrawContext g, int x, int y, int size, float alpha) {
        int col = Ui.a(0xFFFFFF, alpha);
        g.drawTexture(RenderPipelines.GUI_TEXTURED, STEVE, x, y, 8f, 8f, size, size, 8, 8, 64, 64, col);
        g.drawTexture(RenderPipelines.GUI_TEXTURED, STEVE, x, y, 40f, 8f, size, size, 8, 8, 64, 64, col);
    }

    /**
     * Dibuja la cabeza del jugador. "steve" = true fuerza a Steve (cuenta sin conexion: no tiene skin que bajar). Devuelve false
     * mientras la cara todavia esta llegando; entonces quien llama pone la inicial. Si la descarga falla, se dibuja Steve.
     */
    public static boolean draw(DrawContext g, String name, int x, int y, int size, float alpha, boolean steve) {
        if (steve || name == null || !Social.validName(name)) { drawSteve(g, x, y, size, alpha); return true; }
        Identifier face = get(name);
        if (face != null) {
            g.drawTexture(RenderPipelines.GUI_TEXTURED, face, x, y, 0f, 0f, size, size, SIZE, SIZE, SIZE, SIZE, Ui.a(0xFFFFFF, alpha));
            return true;
        }
        if (failed(name)) { drawSteve(g, x, y, size, alpha); return true; }
        return false;
    }
}
