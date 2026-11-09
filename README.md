# RecipeStudio & KitchenSync

## Servidor (PC de la cocina)
```
cd backend
py server.py                 # Python 3.9+, sin dependencias
py -m unittest test_server -v   # 25 pruebas de caja negra
```
Abre `http://localhost:8080` en la PC: muestra el QR (lo genera el servidor, caduca en 10 min).
Botón **🔧 Soporte** (arriba a la derecha): solo admin/agente ven los reportes.

| Usuario | Rol | Contraseña demo |
|---|---|---|
| admin | Administrador (ve todo, asigna casos) | Admin2026! |
| agente | Agente (atiende los casos asignados) | Agente2026! |

**Solo admin y agente entran por la web.** Los celulares no tienen cuenta: entran únicamente con el QR.

Cambia las contraseñas con `KITCHENSYNC_ADMIN_PASSWORD`, `KITCHENSYNC_AGENTE_PASSWORD`.

## App Android
Abrir la carpeta en Android Studio. En el emulador la PC es `http://10.0.2.2:8080`; en un celular real usa la IP que imprime el servidor.
Flujo: abrir la app → Receta (escanear el QR) → Sustitutos / Chat / Reportar.

### Sin escribir IPs
El servidor responde a un aviso UDP (puerto 48555). Si el celular y la PC están en la misma red Wi-Fi,
la app encuentra la estación sola aunque la IP de la PC cambie. En Windows permite Python en
"redes privadas" cuando lo pregunte (TCP 8080 y UDP 48555). En el emulador funciona con 10.0.2.2.
Ojo: algunas redes (Wi-Fi de escuela o invitados) bloquean la comunicación entre dispositivos.

Pruebas unitarias: `./gradlew :app:testDebugUnitTest`
