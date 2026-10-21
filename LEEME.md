# Atenas Client 1.21.8 (Fabric)

> Port del 1.21.11 a 1.21.8 hecho SIN compilar (sin red/Gradle). Cambios: eventos de raton/teclado con firmas antiguas, SkinTextures (capas), render de cosmeticos con VertexConsumerProvider, categoria de teclas como String. Confirma versiones en gradle.properties y pasa `gradle build`; los errores seran pocos y puntuales (NativeImageBackedTexture, WorldTime, mixins con require=0 que se saltan solos).


Estado: **primer corte, sin compilar todavia.** Lo hice sin poder ejecutar Gradle, asi que el primer paso es compilar y pasar los errores.

## Que trae (todo SIN compilar todavia)
- Proyecto Loom + Yarn `1.21.11+build.4` + Fabric API `0.141.1+1.21.11` (confirma versiones en fabricmc.net/develop).
- Waypoints compartidos con el 1.8.9 (`<.minecraft>/atenas/waypoints.json`). `/atenaswp add [nombre]`, `/atenaswp list`.
- HUD: FPS, coordenadas, direccion, hora del mundo, estado de voz, y arriba al centro waypoints y companeros con flecha y distancia.
- **Right Shift** abre el menu de Atenas Client (activar/desactivar cada modulo, se guarda al instante).
- Teclas (cambiables en Controles > Atenas Client): zoom C (mantener), fullbright B, perspectiva Y, voz V (pulsar para hablar), M silenciar mic, U silenciar audio, Numpad 1-4 autotext.
- Autotext: edita `<.minecraft>/atenas/autotext.txt` (una linea por tecla; lo que empiece con `/` es comando).
- `/atenas <modulo>` activa o desactiva: hud, fps, coords, direction, time, waypoints, team, team_share, team_friends, voice, fullbright.
- Team View: usa `/team/sync` del Worker social.
- Voice chat de proximidad SOLO entre usuarios del cliente: audio por WebSocket al Worker social (`/voice`), alcance 48 bloques, volumen segun distancia.
  Requiere `wrangler deploy` del social-worker (trae los Durable Objects TEAM y VOICE).

## Modulos del 1.8.9 portados (y los que no)
- Portados: CPS, Ping, Teclas (WASD), Armadura, Pociones, Velocidad, Memoria, Reloj real (ademas de FPS, coords, direccion, hora, waypoints, Team View, voz, zoom, fullbright, perspectiva, autotext). Se activan en Right Shift.
- No se portan por inutiles en 1.21.11: Performance, Old Animations, Low Fire (Sodium/vanilla ya lo cubren). Pendientes: Combo, Reach, Crosshair, Block Overlay, logo en tab/nametag y scoreboard/tab sin fondo (necesitan mixins).
- OptiFine NO existe aqui: el launcher instala Fabric API, Sodium, Sodium Extra, Lithium e Iris. AppleSkin se pone a mano en la carpeta mods.
- Voz de proximidad entre usuarios del cliente: V para hablar, M silenciar mic, U silenciar audio.

## Cosmeticos (capas)
- Mismo servicio y mismas rutas que 1.8.9 y 1.20.1: lo que equipas aqui se ve alla y al reves. Menu: Right Shift > Cosmeticos.
- Se sube solo el campo `cape`: tus alas/sombrero/mascota elegidos en otras versiones no se tocan.
- Verificacion premium portada (Social.java): ya no da verify_required con nicks premium.
- Catalogo cada 45 s con cache en `<.minecraft>/atenas/cosmetics/`.

## Limitaciones conocidas
- Sin balizas de waypoints ni siluetas de companeros dentro del mundo (solo en el HUD): falta el dibujo 3D de 1.21.11.
- Zoom: baja el FOV a 30 (sin zoom suave con la rueda). Perspectiva: alterna 1a/3a persona (no es freelook). Fullbright: vision nocturna visual.
- Nicks premium ya verificados con Mojang piden verificar cada instalacion nueva: ese paso no esta portado, asi que Team View y voz funcionan con nicks no verificados.
- Voz: sin cancelacion de eco ni supresion de ruido; usa audio mu-law 16 kHz (calidad de telefono).
- Cosmeticos: SOLO capas por ahora (32 del mod + las del catalogo). Alas, sombreros y mascotas: pendientes (necesitan un renderer propio).
- La capa se engancha en `mixin/PlayerCapeMixin.java` (SkinTextures). Si el compilador protesta por un nombre de Yarn, es ahi.

## Publicar
```
git init && git add . && git commit -m "Atenas Client 1.21.11"
git tag v0.1.0 && git push origin main v0.1.0
```
Repo esperado por el launcher: `kaspercito/atenas-client-fabric` (cambialo en `launcher.config.json` si usas otro nombre).


## v0.3.0 - cambios
- **Crash arreglado** (`Mixin transformation of class_742 failed`): PlayerCapeMixin usa dos injects (Yarn + intermediary `method_52814`) con `require = 0`; si no encuentra el metodo se salta y el juego arranca. `defaultRequire` = 0 y refmap declarado.
- **Pantalla de carga de Atenas** (`AtenasLoading.java`), la del mod 1.20.1.
- **Alas y sombreros** (`cosmetics/CosmeticLayer.java` + mallas del 1.8.9/1.20.1), sincronizados por el servicio (campos `wings` y `hat`).
- **Menu de cosmeticos** con pestanas y vista previa con tu personaje.
- **Logo de Atenas** en el TAB (glifo en `assets/minecraft/font/default.json`) y en el nametag (`NametagMixin`).
- **TAB/scoreboard sin fondo, Combo, Reach y Mira propia** (`hud/Extras.java`), activables con Right Shift.
- **v0.3.1**: Resalte de bloque (`hud/BlockOverlay.java`, proyectado en pantalla, sin tocar el render del mundo), TAB propio sin fondo ni celdas (`hud/TabList.java`), mascotas simplificadas (`cosmetics/PetRender.java`: te siguen, sin animacion de patas) y menu principal de Atenas (`AtenasTitleScreen.java`, boton estrella al servidor del pack).
- Nunca se compilo aqui: si Gradle protesta por un nombre de Yarn, mandame el error.

## Arreglo: pantalla negra al arrancar
- CAUSA REAL: desde 1.21.9 `Overlay.tick()` es el que termina la carga y abre el menu; `AtenasLoading` no lo reenviaba y el juego se quedaba en una MessageScreen (class_424) vacia. Ahora reenvia `tick()` y `pausesGame()` al overlay original. Ademas Ahora `AtenasLoading` quita el overlay al terminar su animacion y `AtenasLoading.guard` abre el menu principal si tras la carga no hay menu.
- Notas de version: el workflow usa `RELEASE_NOTES.md` (o los mensajes de commit, sin duplicados) en vez de `generate_release_notes`, asi no aparece "**Full Changelog**: ...". Edita `RELEASE_NOTES.md` antes de cada tag.

## Amigos y Cuenta igual que 1.8.9 (sin compilar)
- `gui/FriendsScreen.java`: reescrita sobre `SocialScreen` de 1.8.9 (mismas medidas y colores): campo "Agregar amigo", lista agrupada (Solicitudes, Jugando ahora, En linea, Desconectados, Enviadas) con caras, insignia de no leidos, botones aceptar/rechazar/cancelar, chat en burbujas con hora, "Quitar" con confirmacion, pie con tu nick y verificacion. Atajo **Shift+Tab** (juego, pausa y menu principal) en `Mods.friendsHotkey`.
- `net/Accounts.java` + `gui/AccountScreen.java`: cambio de cuenta real. Sin conexion: se cambia en caliente (reemplaza la Session por reflexion, sin nombres de Yarn). Microsoft: escribe `config/switch-request.json`, cierra el juego y el launcher lo reabre con esa cuenta. Agregar premium, guardar nick, quitar y cerrar sesion van por `account-request.json` / `account-result.json` (el launcher ya lo atiende). Requiere abrir el juego desde el launcher (`-Datenas.home`).
- Si Gradle protesta: lo mas probable es el nombre de algun metodo de input de 1.21.11 (`Click`, `KeyInput`, `CharInput`) o `Session`; mandame el error.

## Panel de ajustes, Editor HUD y modulos nuevos (sin compilar)
- Tarjetas con engranaje en Right Shift: `hud/Settings.java` define los ajustes (HUD: color/sombra/fondo; CPS; Armadura en %; Pociones; Teclas; Mira: estilo/tamano/separacion/grosor/color/contorno; Zoom: FOV y suave; Movimiento; Chat; Autotext editable) y `hud/ModSettingsScreen.java` los muestra.
- Editor HUD (`hud/HudEditorScreen.java`, `hud/HudPos.java`): arrastrar y rueda para tamano en los 5 paneles (info, armadura, pociones, teclas, waypoints).
- Modulos nuevos: Zoom suave (minimo FOV 30 = limite del juego), Movimiento (sprint automatico, agacharse toggle), Chat con hora (usa `ClientReceiveMessageEvents.MODIFY_GAME/MODIFY_CHAT` de Fabric API: si el compilador protesta, es ahi), Autotext editable en el menu.
- NO se portan (decision): Animaciones 1.7, Rendimiento, Sin Fuego. Pendiente: chat largo, copiar mensaje con clic derecho, perspectiva libre y zoom con la rueda (los tres necesitan mixin).
