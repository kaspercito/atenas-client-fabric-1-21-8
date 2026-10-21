package com.atenas.fabric.hud;

import java.lang.reflect.Method;

import net.minecraft.client.MinecraftClient;

/**
 * Modulo "Hora": fija la hora del mundo solo para ti (0 amanecer, 6000 dia, 18000 noche), como TimeModule de 1.8.9 (setWorldTime cada tick).
 *
 * PENDIENTE DE VERIFICAR EN 1.21.11: la API del reloj del mundo cambio en esta version y aqui no hay forma de ver ClientWorld decompilado.
 * Por eso el metodo que fija la hora NO se llama por nombre (si el nombre no existe, el proyecto no compilaria): se busca por su FORMA,
 * (long gameTime, long timeOfDay, boolean tickTimeOfDay) -> void, que es la de ClientWorld.setTime hasta 1.21.x. Con tickTimeOfDay = false
 * el cliente no avanza la hora y los paquetes de hora del servidor no la mueven. Si esa forma no existe, se avisa UNA vez en el log y el
 * modulo no hace nada (el juego sigue normal). Cuando se tenga ClientWorld.java de 1.21.11 se cambia por la llamada directa.
 */
public final class WorldTime {
    private WorldTime() {}

    private static Method setter;
    private static boolean searched, warned;

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || !Modules.on("worldtime")) return;
        if (!searched) {
            searched = true;
            for (Method m : mc.world.getClass().getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[0] == long.class && p[1] == long.class && p[2] == boolean.class
                        && m.getReturnType() == void.class && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    m.setAccessible(true);
                    setter = m;
                    break;
                }
            }
        }
        if (setter == null) {
            if (!warned) { warned = true; System.out.println("[Atenas] Hora fija: no hay un metodo (long, long, boolean) en ClientWorld; falta portarla a 1.21.11 (se necesita ClientWorld.java)."); }
            return;
        }
        try {
            long ticks = Math.round(Settings.clamped("worldtime", "ticks"));
            setter.invoke(mc.world, mc.world.getTime(), ticks, Boolean.FALSE);
        } catch (Throwable t) {
            setter = null;
            if (!warned) { warned = true; System.out.println("[Atenas] Hora fija fallo y se desactiva: " + t); }
        }
    }
}
