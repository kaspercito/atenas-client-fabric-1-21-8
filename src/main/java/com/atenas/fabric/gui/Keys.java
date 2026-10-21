package com.atenas.fabric.gui;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;

/** Estado del teclado y del mouse leido directo de GLFW (Shift, Tab, boton izquierdo...). Un solo sitio: si Yarn cambia el nombre de la ventana, se arregla aqui. */
public final class Keys {
    private Keys() {}

    private static long handle() { return MinecraftClient.getInstance().getWindow().getHandle(); }

    public static boolean down(int key) {
        try { return GLFW.glfwGetKey(handle(), key) == GLFW.GLFW_PRESS; } catch (Throwable t) { return false; }
    }

    public static boolean shift() { return down(GLFW.GLFW_KEY_LEFT_SHIFT) || down(GLFW.GLFW_KEY_RIGHT_SHIFT); }

    public static boolean mouseDown(int button) {
        try { return GLFW.glfwGetMouseButton(handle(), button) == GLFW.GLFW_PRESS; } catch (Throwable t) { return false; }
    }
}
