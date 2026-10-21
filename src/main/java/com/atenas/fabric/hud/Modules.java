package com.atenas.fabric.hud;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lista de modulos del cliente (las mismas del 1.8.9) con seccion, estado por defecto y descripcion. Una sola fuente para el menu, los comandos y los paneles. */
public final class Modules {
    private Modules() {}

    /** Secciones (indice en ConfigScreen.CATS): 1 HUD, 2 PvP, 3 Animaciones, 4 Rendimiento, 5 Chat, 6 Utilidades. */
    public static final class M {
        public final String key, name, desc; public final int cat; public final boolean def;
        M(String key, String name, int cat, boolean def, String desc) { this.key = key; this.name = name; this.cat = cat; this.def = def; this.desc = desc; }
    }

    private static final Map<String, M> ALL = new LinkedHashMap<>();
    private static void m(String key, String name, int cat, boolean def, String desc) { ALL.put(key, new M(key, name, cat, def, desc)); }

    static {
        m("hud", "HUD", 1, true, "Muestra u oculta todos los paneles del HUD de Atenas.");
        m("fps", "FPS", 1, true, "Muestra tus fotogramas por segundo.");
        m("ping", "Ping", 1, true, "Tu latencia con el servidor.");
        m("coords", "Coordenadas", 1, true, "Tu posici\u00f3n y hacia d\u00f3nde miras.");
        m("clock", "Reloj", 1, false, "Muestra la hora real de tu PC.");
        m("memory", "Memoria", 1, false, "Muestra cu\u00e1nta RAM usa Minecraft.");
        m("speed", "Velocidad", 1, false, "Tu velocidad en bloques por segundo.");
        m("direction", "Direcci\u00f3n", 1, false, "Muestra hacia d\u00f3nde miras: punto cardinal y grados.");
        m("scoreboard", "Scoreboard", 1, true, "Quita el fondo de la tabla de la derecha y limpia sus n\u00fameros.");
        m("nobg", "Lista TAB", 1, true, "Quita el fondo de la lista de jugadores (TAB) para ver el juego detr\u00e1s.");
        m("crosshair", "Mira", 2, false, "Mira personalizada: estilo, tama\u00f1o, separaci\u00f3n, grosor, opacidad y color.");
        m("combo", "Combo", 2, false, "Golpes seguidos sin recibir da\u00f1o.");
        m("reach", "Alcance", 2, false, "Distancia de tu \u00faltimo golpe.");
        m("cps", "CPS", 2, true, "Clics por segundo (izquierdo | derecho).");
        m("keystrokes", "Teclas", 2, true, "Muestra WASD, clics y espacio (con CPS).");
        m("armor", "Armadura", 2, true, "Armadura y objeto en mano con su durabilidad.");
        m("potions", "Pociones", 2, true, "Efectos activos con su icono, nombre, nivel y tiempo restante.");
        m("movement", "Movimiento", 2, true, "Sprint autom\u00e1tico y agacharse con toggle.");
        m("lowfire", "Sin Fuego", 2, true, "Oculta el fuego que tapa la pantalla cuando te quemas.");
        m("blockoverlay", "Resalte de bloque", 2, false, "Cambia el contorno del bloque que miras: color, grosor y relleno.");
        m("team", "Equipo", 2, true, "Vida, distancia y un marcador sobre cada compa\u00f1ero. H abre tu lista de equipo.");
        m("anim17", "Animaciones 1.7", 3, true, "Animaciones 1.7: bloquear con la espada, comer y usar el arco mientras golpeas.");
        m("perf", "Rendimiento", 4, true, "M\u00e1s FPS: no dibuja los mobs ni los hologramas que est\u00e1n lejos.");
        m("chat", "Chat", 5, true, "Hora, historial largo, copiar con clic derecho, sin fondo y animaci\u00f3n suave.");
        m("autotext", "Autotext", 5, true, "Asigna una tecla o bot\u00f3n del mouse a un mensaje o comando.");
        m("perspective", "Perspective", 6, true, "Mant\u00e9n Alt izquierdo y mira alrededor sin girar al personaje.");
        m("fullbright", "Fullbright", 6, false, "M\u00e1ximo brillo: ves bien en cuevas y de noche.");
        m("worldtime", "Hora", 6, false, "Fija la hora del mundo solo para ti (0 amanecer, 6000 d\u00eda, 18000 noche).");
        m("waypoints", "Waypoints", 6, true, "Guarda puntos del mapa y m\u00edralos en pantalla. B abre el administrador, N guarda un punto aqu\u00ed.");
        m("zoom", "Zoom", 6, true, "Mant\u00e9n C para acercar la vista; la rueda cambia el nivel.");
        m("tablogo", "Logo Atenas", 6, true, "Pone el logo de Atenas en el TAB y sobre la cabeza de quienes usan el cliente.");
        m("voice", "Voice chat", 5, true, "Chat de voz por proximidad.");
        m("atenasmenu", "Men\u00fa Atenas", 6, true, "Usa el men\u00fa principal de Atenas.");
    }

    public static Map<String, M> all() { return ALL; }
    public static boolean def(String key) { M x = ALL.get(key); return x == null || x.def; }
    public static String name(String key) { M x = ALL.get(key); return x == null ? key : x.name; }
    public static boolean on(String key) { return com.atenas.fabric.shared.Config.get(key, def(key)); }
}
