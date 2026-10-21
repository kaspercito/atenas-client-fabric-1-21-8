package com.atenas.fabric.shared;

import java.io.File;

/**
 * Carpeta compartida por TODAS las versiones del cliente: <.minecraft real>/atenas/.
 * Cada instancia del launcher tiene su propio mcDataDir, asi que los datos que deben ser comunes (waypoints) van aqui.
 * Windows: %APPDATA%\.minecraft  |  macOS: ~/Library/Application Support/minecraft  |  Linux: ~/.minecraft
 * Se puede cambiar con la propiedad -Datenas.shared=RUTA o la variable ATENAS_SHARED.
 */
public final class SharedDir {
    private SharedDir() {}

    public static File dir() {
        String o = System.getProperty("atenas.shared");
        if (o == null || o.trim().isEmpty()) o = System.getenv("ATENAS_SHARED");
        File base;
        if (o != null && !o.trim().isEmpty()) {
            base = new File(o.trim());
        } else {
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            String home = System.getProperty("user.home", ".");
            if (os.contains("win")) {
                String ad = System.getenv("APPDATA");
                base = new File(ad != null && !ad.isEmpty() ? ad : new File(home, "AppData/Roaming").getPath(), ".minecraft");
            } else if (os.contains("mac")) {
                base = new File(home, "Library/Application Support/minecraft");
            } else {
                base = new File(home, ".minecraft");
            }
            base = new File(base, "atenas");
        }
        base.mkdirs();
        return base;
    }
}
