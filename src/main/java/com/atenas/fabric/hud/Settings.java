package com.atenas.fabric.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.atenas.fabric.shared.Config;

/**
 * Ajustes por modulo (los del engranaje del menu). Mismos nombres, rangos y valores por defecto que el cliente 1.8.9.
 * Claves: "s.<modulo>.<nombre>" en fabric-config.properties.
 */
public final class Settings {
    private Settings() {}

    public enum T { BOOL, NUM, CHOICE, COLOR, TEXT }

    public static final class Def {
        public final String name, label;
        public final T type;
        public final double def, min, max, step;
        public final String[] opts;
        Def(String name, String label, T type, double def, double min, double max, double step, String[] opts) {
            this.name = name; this.label = label; this.type = type; this.def = def; this.min = min; this.max = max; this.step = step; this.opts = opts;
        }
    }

    /** Paleta de 1.8.9 (Setting.PALETTE), en el mismo orden: el indice guardado significa lo mismo. */
    public static final String[] COLOR_NAMES = {"Blanco", "Dorado", "Cian", "Verde", "Rojo", "Magenta", "Azul", "Gris", "Violeta", "Rosa", "Naranja", "Turquesa", "Rosa claro", "Negro"};
    public static final int[] COLORS = {
        0xFFFFFFFF, 0xFFF5C542, 0xFF55FFFF, 0xFF55FF55, 0xFFFF5555, 0xFFFF55FF, 0xFF5577FF, 0xFFAAAAAA,
        0xFFA78BFA, 0xFFEC4899, 0xFFFF9F1C, 0xFF2DD4BF, 0xFFFFB3D9, 0xFF111111
    };

    /** Modulos que son un panel en pantalla (se mueven y escalan en el Editor HUD y tienen los 5 ajustes comunes de HudModule). */
    public static final String[] HUD_MODULES = {"fps", "cps", "ping", "coords", "combo", "reach", "direction", "clock", "memory", "speed", "keystrokes", "armor", "potions", "team", "voice"};

    private static final Map<String, List<Def>> MAP = new LinkedHashMap<>();

    private static void add(String mod, Def d) { MAP.computeIfAbsent(mod, k -> new ArrayList<>()).add(d); }
    private static void bool(String mod, String n, String l, boolean def) { add(mod, new Def(n, l, T.BOOL, def ? 1 : 0, 0, 1, 1, null)); }
    private static void num(String mod, String n, String l, double def, double min, double max, double step) { add(mod, new Def(n, l, T.NUM, def, min, max, step, null)); }
    private static void choice(String mod, String n, String l, int def, String... o) { add(mod, new Def(n, l, T.CHOICE, def, 0, o.length - 1, 1, o)); }
    private static void color(String mod, String n, String l, int def) { add(mod, new Def(n, l, T.COLOR, def, 0, COLORS.length - 1, 1, COLOR_NAMES)); }
    private static void text(String mod, String n, String l) { add(mod, new Def(n, l, T.TEXT, 0, 0, 0, 0, null)); }

    /** Los 5 ajustes que trae todo HudModule en 1.8.9: Escala, Fondo, Sombra de texto, Barra de acento y Color. */
    private static void hud(String mod) {
        num(mod, "scale", "Escala", 1.0, 0.5, 2.5, 0.1);
        bool(mod, "bg", "Fondo", true);
        bool(mod, "shadow", "Sombra de texto", true);
        bool(mod, "accent", "Barra de acento", true);
        color(mod, "color", "Color", 0);
    }

    static {
        // ---- paneles HUD: primero los propios de cada modulo y luego los comunes (como en 1.8.9: propios, Escala, Fondo...) ----
        bool("cps", "right", "Mostrar clic derecho", true);
        hud("fps"); hud("cps"); hud("ping"); hud("coords"); hud("combo"); hud("reach"); hud("direction"); hud("clock"); hud("memory"); hud("speed");

        bool("keystrokes", "cps", "Mostrar CPS", true);
        bool("keystrokes", "space", "Barra espaciadora", true);
        bool("keystrokes", "mouse", "Mostrar clics del mouse", true);
        hud("keystrokes");

        choice("armor", "layout", "Disposici\u00f3n", 0, "Vertical", "Horizontal");
        choice("armor", "text", "Texto", 0, "Durabilidad", "Porcentaje", "Ninguno");
        bool("armor", "bar", "Barra de durabilidad", true);
        bool("armor", "held", "Objeto en la mano", true);
        bool("armor", "arrows", "Contar flechas con el arco", true);
        bool("armor", "fixed", "Ranuras fijas (el panel no cambia de tama\u00f1o)", true);
        bool("armor", "warn", "Parpadear con poca durabilidad", true);
        hud("armor");

        choice("potions", "style", "Estilo", 0, "Completo", "Compacto");
        bool("potions", "icons", "Mostrar icono del efecto", true);
        bool("potions", "tint", "Color seg\u00fan el tiempo que queda", true);
        bool("potions", "blink", "Parpadear cuando se est\u00e1 acabando", true);
        bool("potions", "sort", "Ordenar por tiempo restante", false);
        hud("potions");

        bool("team", "markers", "Marcadores en el mundo", true);
        bool("team", "list", "Mostrar lista", true);
        bool("team", "useteams", "Usar equipos del servidor", true);
        bool("team", "friends", "Incluir amigos de Atenas", true);
        bool("team", "share", "Compartir mi posici\u00f3n", true);
        bool("team", "hp", "Mostrar vida", true);
        num("team", "maxdist", "Distancia m\u00e1xima (0 = sin l\u00edmite)", 0, 0, 1000, 25);
        num("team", "marksize", "Tama\u00f1o del marcador", 1.0, 0.6, 2.0, 0.1);
        bool("team", "dist", "Mostrar distancia", true);
        bool("team", "status", "Mostrar estado de la conexi\u00f3n", true);
        color("team", "color", "Color de compa\u00f1eros", 3);
        hud("team");
        hud("voice");

        // ---- PvP ----
        choice("crosshair", "style", "Estilo", 0, "Cruz", "T", "Punto", "C\u00edrculo", "Cuadrado");
        num("crosshair", "size", "Tama\u00f1o", 6, 1, 20, 1);
        num("crosshair", "gap", "Separaci\u00f3n", 3, 0, 12, 1);
        num("crosshair", "thick", "Grosor", 1, 1, 5, 1);
        num("crosshair", "opacity", "Opacidad", 1.0, 0.2, 1.0, 0.1);
        bool("crosshair", "dot", "Punto central", false);
        bool("crosshair", "outline", "Contorno", true);
        bool("crosshair", "dynamic", "Separaci\u00f3n din\u00e1mica (se abre al moverte/saltar)", false);
        color("crosshair", "color", "Color", 1);
        bool("crosshair", "aim", "Cambiar de color al apuntar a una entidad", true);
        color("crosshair", "aimcolor", "Color al apuntar", 4);

        bool("movement", "sprint", "Sprint autom\u00e1tico", true);
        bool("movement", "sneak", "Agacharse (toggle)", false);

        color("blockoverlay", "color", "Color", 8);
        num("blockoverlay", "width", "Grosor", 2.0, 1.0, 6.0, 0.5);
        bool("blockoverlay", "fill", "Relleno", true);
        num("blockoverlay", "fillalpha", "Opacidad del relleno", 0.2, 0.05, 0.6, 0.05);

        // ---- Animaciones ----
        bool("anim17", "block", "Espada: bloquear + golpear", true);
        bool("anim17", "eat", "Comer/beber: golpear mientras tanto", true);
        bool("anim17", "bow", "Arco: golpear mientras tensas", true);
        bool("anim17", "hold", "Mantener clic = balanceo continuo", true);
        bool("anim17", "breakblocks", "Picar el bloque de verdad (puede dar problemas con anticheat)", false);
        bool("anim17", "usewhilehit", "Clic derecho aunque mantengas el izquierdo (bloquear/comer mientras picas)", true);
        num("anim17", "strength", "Intensidad (modo de respaldo)", 1.0, 0.3, 1.5, 0.1);
        bool("anim17", "poson", "Mover/cambiar tama\u00f1o del objeto en mano", false);
        num("anim17", "posx", "Objeto: horizontal", 0.0, -0.5, 0.5, 0.02);
        num("anim17", "posy", "Objeto: vertical", 0.0, -0.5, 0.5, 0.02);
        num("anim17", "posz", "Objeto: distancia", 0.0, -0.5, 0.5, 0.02);
        num("anim17", "possize", "Objeto: tama\u00f1o", 1.0, 0.4, 1.4, 0.05);

        // ---- Rendimiento ----
        num("perf", "cull", "Ocultar mobs lejanos (bloques, 0 = no)", 48, 0, 128, 8);
        num("perf", "holocull", "Ocultar hologramas lejanos (bloques, 0 = no)", 24, 0, 64, 4);

        // ---- Chat ----
        bool("chat", "time", "Hora en cada mensaje", true);
        bool("chat", "h24", "Formato 24 horas", true);
        bool("chat", "seconds", "Incluir segundos", false);
        bool("chat", "long", "Chat infinito", true);
        bool("chat", "copy", "Copiar con clic derecho", true);
        bool("chat", "copynotime", "Copiar sin la hora", true);
        bool("chat", "nobg", "Sin fondo", true);
        bool("chat", "anim", "Animaci\u00f3n suave", true);
        num("chat", "animms", "Duraci\u00f3n de la animaci\u00f3n (ms)", 400, 100, 1500, 50);
        // Autotext: los atajos (tecla o boton del mouse -> mensaje) se editan en AutotextScreen; el engranaje abre esa pantalla.

        // ---- Utilidades ----
        num("zoom", "level", "Nivel", 4, 1.5, 10, 0.5);
        bool("zoom", "smooth", "C\u00e1mara suave al hacer zoom", true);
        bool("zoom", "wheel", "Rueda ajusta el zoom", true);
        choice("perspective", "mode", "Modo", 0, "Mantener", "Alternar");
        bool("perspective", "invert", "Invertir eje vertical", false);
        num("worldtime", "ticks", "Hora (ticks)", 6000, 0, 24000, 500);

        bool("waypoints", "dist", "Mostrar distancia", true);
        bool("waypoints", "beam", "Rayo vertical", true);
        bool("waypoints", "edge", "Flechas en el borde", true);
        bool("waypoints", "fade", "Atenuar cerca de la mira", true);
        bool("waypoints", "death", "Marcar d\u00f3nde moriste", true);
        num("waypoints", "maxdist", "Distancia m\u00e1xima (0 = sin l\u00edmite)", 0, 0, 5000, 50);
        num("waypoints", "hidenear", "Ocultar a menos de (bloques)", 3, 0, 30, 1);
        num("waypoints", "size", "Tama\u00f1o del marcador", 1.0, 0.6, 2.0, 0.1);

        bool("scoreboard", "nobg", "Sin fondo", true);
        bool("scoreboard", "nonumbers", "Ocultar n\u00fameros rojos", true);
    }

    public static boolean has(String mod) { return MAP.containsKey(mod); }
    public static List<Def> of(String mod) { return MAP.getOrDefault(mod, new ArrayList<>()); }

    private static Def def(String mod, String name) {
        for (Def d : of(mod)) if (d.name.equals(name)) return d;
        return null;
    }

    private static String key(String mod, String name) { return "s." + mod + "." + name; }

    public static boolean b(String mod, String name) { Def d = def(mod, name); return Config.get(key(mod, name), d != null && d.def != 0); }
    public static float n(String mod, String name) { Def d = def(mod, name); return Config.getFloat(key(mod, name), d == null ? 0f : (float) d.def); }
    public static int i(String mod, String name) { return Math.round(n(mod, name)); }
    public static int c(String mod, String name) { Def d = def(mod, name); return Config.getInt(key(mod, name), d == null ? 0 : (int) d.def); }
    public static int color(String mod, String name) { return COLORS[Math.floorMod(c(mod, name), COLORS.length)]; }
    public static String str(String mod, String name, String def) { return Config.getStr(key(mod, name), def); }

    public static void setBool(String mod, String name, boolean v) { Config.putBool(key(mod, name), v); Config.save(); }
    public static void setNum(String mod, String name, float v) { Config.putFloat(key(mod, name), v); }
    public static void setInt(String mod, String name, int v) { Config.putInt(key(mod, name), v); Config.save(); }
    public static void setStr(String mod, String name, String v) { Config.putStr(key(mod, name), v); }

    /** Valor fuera de rango (por un archivo viejo o editado a mano) -> se lleva al rango del ajuste. */
    public static float clamped(String mod, String name) {
        Def d = def(mod, name);
        float v = n(mod, name);
        return d == null || d.type != T.NUM ? v : (float) Math.max(d.min, Math.min(d.max, v));
    }
}
