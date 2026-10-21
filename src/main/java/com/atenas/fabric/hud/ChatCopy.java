package com.atenas.fabric.hud;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.atenas.fabric.gui.Keys;
import com.atenas.fabric.shared.Config;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Copiar un mensaje del chat con clic derecho (igual que en 1.8.9, con o sin la hora). Se lee el boton directo de GLFW en cada
 * fotograma de la pantalla del chat. Las listas de lineas de ChatHud son privadas: se buscan por TIPO (no por nombre), asi que
 * funciona igual en desarrollo (Yarn) y en el juego real (intermediary). Si algo no cuadra, se desactiva sin romper nada.
 */
public final class ChatCopy {
    private ChatCopy() {}

    private static Field visibleF, messagesF, scrollF;
    private static boolean resolved, ok, wasDown;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen)) return;
            wasDown = Keys.mouseDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT); // el clic que abrio el chat no cuenta
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                try { frame(client, mx, my); } catch (Throwable t) { ok = false; resolved = true; }
            });
        });
    }

    private static void frame(MinecraftClient mc, int mx, int my) throws Exception {
        boolean down = Keys.mouseDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        boolean edge = down && !wasDown;
        wasDown = down;
        if (!edge || !Config.get("chat", true) || !Settings.b("chat", "copy")) return;
        String msg = messageAt(mc, mx, my);
        if (msg == null || msg.isEmpty()) return;
        if (Settings.b("chat", "copynotime")) msg = msg.replaceFirst("^\\[\\d{1,2}:\\d{2}(:\\d{2})?\\] ", "");
        mc.keyboard.setClipboard(msg);
        mc.inGameHud.setOverlayMessage(Text.literal("Mensaje copiado").formatted(Formatting.GREEN), false);
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            int ints = 0;
            for (Field f : ChatHud.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() == int.class) { scrollF = f; ints++; continue; }
                if (!List.class.isAssignableFrom(f.getType()) || !(f.getGenericType() instanceof ParameterizedType pt)) continue;
                if (!(pt.getActualTypeArguments()[0] instanceof Class<?> c) || !c.isRecord()) continue;
                boolean ordered = false, text = false;
                for (RecordComponent rc : c.getRecordComponents()) {
                    if (rc.getType() == OrderedText.class) ordered = true;
                    if (rc.getType() == Text.class) text = true;
                }
                f.setAccessible(true);
                if (ordered && visibleF == null) visibleF = f;     // List<ChatHudLine.Visible>: lineas ya partidas
                else if (text && messagesF == null) messagesF = f; // List<ChatHudLine>: mensajes completos
            }
            if (ints != 1) scrollF = null; else scrollF.setAccessible(true);
            ok = visibleF != null && messagesF != null && scrollF != null;
        } catch (Throwable t) { ok = false; }
        if (!ok) System.out.println("[Atenas] Chat: no se pudieron localizar las lineas del chat (copiar con clic derecho desactivado)");
    }

    /** Texto completo del mensaje que hay bajo el mouse, o null. Mismas cuentas que ChatHud (toChatLineX/Y + indice de linea). */
    private static String messageAt(MinecraftClient mc, int mx, int my) throws Exception {
        resolve();
        if (!ok) return null;
        ChatHud chat = mc.inGameHud.getChatHud();
        List<?> visible = (List<?>) visibleF.get(chat);
        double scale = mc.options.getChatScale().getValue(); // ChatHud.getChatScale() es privado: mismo valor, desde las opciones
        double spacing = mc.options.getChatLineSpacing().getValue();
        double cx = mx / scale - 4.0;
        double cy = (mc.getWindow().getScaledHeight() - my - 40.0) / (scale * (spacing + 1.0));
        if (cx < -4.0 || cx > Math.floor(Math.floor(mc.options.getChatWidth().getValue() * 280.0 + 40.0) / scale)) return null;
        int shown = Math.min(chat.getVisibleLineCount(), visible.size());
        double lines = cy / 9.0;
        if (lines < 0 || lines >= shown) return null;
        int idx = (int) lines + scrollF.getInt(chat);
        if (idx < 0 || idx >= visible.size()) return null;

        Object line = visible.get(idx);
        int tick = intOf(line);
        StringBuilder piece = new StringBuilder();
        OrderedText ot = (OrderedText) valueOf(line, OrderedText.class);
        if (ot != null) ot.accept((i, style, cp) -> { piece.appendCodePoint(cp); return true; });
        String part = piece.toString().trim();

        // el mensaje original (sin partir en lineas): mismo tick y que contenga este trozo
        for (Object full : (List<?>) messagesF.get(chat)) {
            if (intOf(full) != tick) continue;
            Text t = (Text) valueOf(full, Text.class);
            if (t == null) continue;
            String whole = t.getString();
            if (part.isEmpty() || whole.contains(part)) return whole;
        }
        return part;
    }

    private static int intOf(Object record) throws Exception {
        for (RecordComponent rc : record.getClass().getRecordComponents()) if (rc.getType() == int.class) return (int) rc.getAccessor().invoke(record);
        return -1;
    }

    private static Object valueOf(Object record, Class<?> type) throws Exception {
        for (RecordComponent rc : record.getClass().getRecordComponents()) if (rc.getType() == type) return rc.getAccessor().invoke(record);
        return null;
    }
}
