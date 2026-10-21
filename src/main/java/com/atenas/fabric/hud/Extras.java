package com.atenas.fabric.hud;

import java.util.Locale;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Combo, Reach, Mira propia (cruz, T, punto, circulo y cuadrado; opacidad y separacion dinamica), TAB / scoreboard / chat sin fondo
 * y animacion de entrada del chat. El TAB ademas lo retoca PlayerListHudMixin y los numeros rojos del scoreboard ScoreboardObjectiveMixin.
 */
public final class Extras {
    private Extras() {}

    private static int combo, prevHurt;
    private static long lastHit, reachTime;
    private static double reach = -1;
    private static float dynGap;        // separacion extra de la mira: se abre al moverte, correr o saltar
    private static long chatAnimStart;  // 0 = sin animacion en curso

    public static String comboText() { return "Combo: " + combo; }

    public static String reachText() {
        if (reach < 0 || System.currentTimeMillis() - reachTime > 5000L) return "Alcance: --";
        return String.format(Locale.ROOT, "Alcance: %.2f", reach);
    }

    /** Lo llama Mods.stamp con cada mensaje nuevo del chat: arranca la animacion de entrada. */
    public static void onChatMessage() { chatAnimStart = System.currentTimeMillis(); }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (world.isClient() && player == mc.player) {
                long now = System.currentTimeMillis();
                if (entity instanceof LivingEntity le && le.hurtTime <= 0) { combo++; lastHit = now; }
                if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() == entity) { reach = ehr.getPos().distanceTo(player.getEyePos()); reachTime = now; }
            }
            return ActionResult.PASS;
        });

        // Mira propia (reemplaza la de vanilla)
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, orig -> (HudElement) (ctx, tick) -> {
            if (!Modules.on("crosshair")) { orig.render(ctx, tick); return; }
            drawCrosshair(ctx);
        });
        // TAB (el de vanilla; PlayerListHudMixin quita fondo/cuadros y pone el logo), scoreboard y chat sin fondo: se pone a 0 la opacidad del
        // fondo de texto solo mientras se dibujan (asi no hace falta tocar sus clases).
        HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, orig -> (HudElement) (ctx, tick) -> noBg(orig, ctx, tick, Modules.on("nobg")));
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, orig -> (HudElement) (ctx, tick) ->
            noBg(orig, ctx, tick, Modules.on("scoreboard") && Settings.b("scoreboard", "nobg")));
        HudElementRegistry.replaceElement(VanillaHudElements.CHAT, orig -> (HudElement) (ctx, tick) -> chat(orig, ctx, tick));
    }

    private static void noBg(HudElement orig, DrawContext ctx, RenderTickCounter tick, boolean remove) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!remove) { orig.render(ctx, tick); return; }
        double op = mc.options.getTextBackgroundOpacity().getValue();
        boolean only = mc.options.getBackgroundForChatOnly().getValue();
        mc.options.getTextBackgroundOpacity().setValue(0.0);
        mc.options.getBackgroundForChatOnly().setValue(false);
        try { orig.render(ctx, tick); }
        finally { mc.options.getTextBackgroundOpacity().setValue(op); mc.options.getBackgroundForChatOnly().setValue(only); }
    }

    /** Chat: sin fondo (ajuste "Sin fondo") y con una entrada suave: al llegar un mensaje todo el chat sube desde abajo (ajuste "Animacion suave"). */
    private static void chat(HudElement orig, DrawContext ctx, RenderTickCounter tick) {
        boolean on = Modules.on("chat");
        float off = on ? chatOffset() : 0f;
        if (off != 0f) { ctx.getMatrices().pushMatrix(); ctx.getMatrices().translate(0f, off); }
        try { noBg(orig, ctx, tick, on && Settings.b("chat", "nobg")); }
        finally { if (off != 0f) ctx.getMatrices().popMatrix(); }
    }

    /** Desplazamiento vertical actual del chat en pixeles de GUI (0 = quieto). Una linea de chat mide 9. */
    private static float chatOffset() {
        if (chatAnimStart == 0L || !Settings.b("chat", "anim")) return 0f;
        float ms = Math.max(100f, Settings.clamped("chat", "animms"));
        float t = (System.currentTimeMillis() - chatAnimStart) / ms;
        if (t >= 1f) { chatAnimStart = 0L; return 0f; }
        float inv = 1f - t;
        return inv * inv * inv * 9f;
    }

    // ------------------------------------------------------------------ mira

    private static void drawCrosshair(DrawContext g) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.getPerspective() != net.minecraft.client.option.Perspective.FIRST_PERSON || mc.options.hudHidden) return;
        int cx = g.getScaledWindowWidth() / 2, cy = g.getScaledWindowHeight() / 2;
        boolean aim = mc.crosshairTarget instanceof EntityHitResult && Settings.b("crosshair", "aim");
        int rgb = Settings.color("crosshair", aim ? "aimcolor" : "color") & 0xFFFFFF;
        int al = Math.round(255f * Math.max(0.2f, Math.min(1f, Settings.clamped("crosshair", "opacity"))));
        int c = (al << 24) | rgb, o = al << 24; // contorno: negro con la misma opacidad
        int s = Math.max(1, Math.round(Settings.clamped("crosshair", "size")));
        int t = Math.max(1, Math.round(Settings.clamped("crosshair", "thick")));
        int gap = Math.max(0, Math.round(Settings.clamped("crosshair", "gap") + (Settings.b("crosshair", "dynamic") ? dynGap : 0f)));
        int style = Settings.c("crosshair", "style"); // 0 cruz, 1 T, 2 punto, 3 circulo, 4 cuadrado
        boolean out = Settings.b("crosshair", "outline");
        int h = t / 2;
        if (style == 3 || style == 4) {
            shape(g, cx, cy, Math.max(3, gap + s / 2), t, style == 3, c, o, out);
        } else if (style != 2) {
            if (out) {
                g.fill(cx - gap - s - 1, cy - h - 1, cx - gap + 1, cy - h + t + 1, o);
                g.fill(cx + gap - 1, cy - h - 1, cx + gap + s + 1, cy - h + t + 1, o);
                g.fill(cx - h - 1, cy + gap - 1, cx - h + t + 1, cy + gap + s + 1, o);
                if (style == 0) g.fill(cx - h - 1, cy - gap - s - 1, cx - h + t + 1, cy - gap + 1, o);
            }
            g.fill(cx - gap - s, cy - h, cx - gap, cy - h + t, c);
            g.fill(cx + gap, cy - h, cx + gap + s, cy - h + t, c);
            g.fill(cx - h, cy + gap, cx - h + t, cy + gap + s, c);
            if (style == 0) g.fill(cx - h, cy - gap - s, cx - h + t, cy - gap, c);
        }
        if (style == 2 || Settings.b("crosshair", "dot")) { // punto central (estilo "Punto" o el ajuste "Punto central")
            int d = Math.max(t, 2), dh = d / 2;
            if (out) g.fill(cx - dh - 1, cy - dh - 1, cx - dh + d + 1, cy - dh + d + 1, o);
            g.fill(cx - dh, cy - dh, cx - dh + d, cy - dh + d, c);
        }
    }

    /**
     * Circulo o cuadrado hueco de radio r y grosor t, centrado en (cx, cy). Se calcula una mascara de pixeles y se dibuja cada pixel UNA vez
     * (asi la opacidad menor a 100% no se acumula donde se solapan los trazos); el contorno es la franja de 1 pixel alrededor de la forma.
     */
    private static void shape(DrawContext g, int cx, int cy, int r, int t, boolean ring, int col, int outCol, boolean out) {
        int R = r + t + 2, n = 2 * R + 1;
        boolean[][] f = new boolean[n][n];
        for (int dy = -R; dy <= R; dy++) {
            for (int dx = -R; dx <= R; dx++) {
                boolean in;
                if (ring) in = Math.abs(Math.sqrt((double) dx * dx + (double) dy * dy) - r) <= t / 2.0;
                else { int m = Math.max(Math.abs(dx), Math.abs(dy)); in = m <= r && m > r - t; }
                f[dy + R][dx + R] = in;
            }
        }
        if (out) {
            boolean[][] edge = new boolean[n][n];
            for (int y = 1; y < n - 1; y++) {
                for (int x = 1; x < n - 1; x++) {
                    if (f[y][x]) continue;
                    boolean near = false;
                    for (int j = -1; j <= 1 && !near; j++) for (int i = -1; i <= 1; i++) if (f[y + j][x + i]) { near = true; break; }
                    edge[y][x] = near;
                }
            }
            paintMask(g, cx - R, cy - R, edge, outCol);
        }
        paintMask(g, cx - R, cy - R, f, col);
    }

    /** Pinta la mascara con una sola llamada por tramo horizontal continuo. */
    private static void paintMask(DrawContext g, int x0, int y0, boolean[][] m, int col) {
        for (int y = 0; y < m.length; y++) {
            int start = -1;
            for (int x = 0; x <= m[y].length; x++) {
                boolean on = x < m[y].length && m[y][x];
                if (on && start < 0) start = x;
                else if (!on && start >= 0) { g.fill(x0 + start, y0 + y, x0 + x, y0 + y + 1, col); start = -1; }
            }
        }
    }

    public static void tick(MinecraftClient mc) {
        if (mc.getOverlay() == null && mc.currentScreen != null && mc.currentScreen.getClass() == net.minecraft.client.gui.screen.TitleScreen.class && Modules.on("atenasmenu"))
            { System.out.println("[Atenas] menu principal de Atenas activado"); mc.setScreen(new com.atenas.fabric.AtenasTitleScreen()); }
        if (mc.player == null) { combo = 0; dynGap = 0f; return; }
        int h = mc.player.hurtTime;
        if (h > 0 && prevHurt == 0) combo = 0;
        prevHurt = h;
        if (combo > 0 && System.currentTimeMillis() - lastHit > 3000L) combo = 0;

        // separacion dinamica de la mira: se abre al moverte, correr y saltar, y se cierra suave al quedarte quieto
        float target = 0f;
        if (Settings.b("crosshair", "dynamic")) {
            net.minecraft.util.math.Vec3d v = mc.player.getVelocity();
            if (v.x * v.x + v.z * v.z > 0.0004) target += 2f;
            if (mc.player.isSprinting()) target += 1f;
            if (!mc.player.isOnGround()) target += 3f;
            if (mc.player.isSneaking()) target = Math.max(0f, target - 1f);
        }
        dynGap += (target - dynGap) * 0.3f;
        if (Math.abs(dynGap - target) < 0.02f) dynGap = target;
    }
}
