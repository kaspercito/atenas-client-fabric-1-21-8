package com.atenas.fabric.hud;

import com.atenas.fabric.shared.Config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;

/**
 * Perspectiva libre (igual que Perspective de 1.8.9): mantienes la tecla (Y por defecto) y la camara gira alrededor de tu
 * personaje en tercera persona SIN que el personaje gire ni cambie hacia donde caminas o pegas. Al soltarla vuelve tu vista.
 * Aqui vive el estado; el mouse y la camara los engancha {@code mixin/FreelookMixin} y {@code mixin/CameraMixin}.
 */
public final class Freelook {
    private Freelook() {}

    /** Camara suelta del personaje. */
    public static boolean active;
    /** true mientras Camera.update esta corriendo (los mixins de getYaw/getPitch solo responden entonces). */
    public static boolean inCamera;
    /** Cada mixin marca que se aplico de verdad. Si falta alguno, la perspectiva libre se desactiva sola en vez de dejarte sin poder mirar. */
    public static boolean camHooked, yawHooked;
    public static float yaw, pitch;

    private static boolean toggled, prevDown, broken;
    private static int activeTicks;
    private static Perspective prev = Perspective.FIRST_PERSON;

    /** El mouse solo se roba si los dos mixins de camara estan puestos y funcionando. */
    public static boolean swallowMouse() { return active && camHooked && !broken; }

    /** Movimiento del mouse que normalmente giraria al jugador (mismo factor 0.15 que Entity.changeLookDirection). */
    public static void look(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch += (float) (Settings.b("perspective", "invert") ? -dy : dy) * 0.15f;
        if (pitch > 90f) pitch = 90f;
        if (pitch < -90f) pitch = -90f;
    }

    public static void tick(MinecraftClient mc, KeyBinding key) {
        if (mc.player == null) { if (active) active = false; toggled = false; return; }
        boolean enabled = Config.get("perspective", true);
        boolean down = enabled && mc.currentScreen == null && key.isPressed();
        boolean want;
        if (Settings.c("perspective", "mode") == 1) { // Alternar
            if (down && !prevDown) toggled = !toggled;
            want = enabled && toggled;
        } else {
            want = down;
        }
        prevDown = down;
        if (want && !active) start(mc);
        else if (!want && active) stop(mc);
        if (active) {
            // si la camara no usa nuestros angulos tras unos ticks, algun mixin no se aplico: no te quedes sin mouse
            if (++activeTicks == 6 && !yawHooked) broken = true;
        }
    }

    private static void start(MinecraftClient mc) {
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        prev = mc.options.getPerspective();
        if (prev == Perspective.FIRST_PERSON) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK); // si ya estabas en 3a persona se respeta
        activeTicks = 0;
        active = true;
    }

    private static void stop(MinecraftClient mc) {
        active = false;
        inCamera = false;
        mc.options.setPerspective(prev);
    }

    public static void reset() { active = false; inCamera = false; toggled = false; }
}
