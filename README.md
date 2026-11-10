# RecipeStudio & KitchenSync

Cocina colaborativa: la PC es la pantalla grande y el celular (Android) es el control remoto.

| Parte | Dónde vive | Qué hace |
|---|---|---|
| App Android (Kotlin + Compose) | `app/`, `shared/` | Escanea el QR, tacha ingredientes, temporizador, chat, sustitución con foto (IA), reportes, panel de soporte |
| Servidor Python | `backend/server.py` (Render) | QR de la sesión, sincronización de la receta y el temporizador, chat |
| Dashboard de la estación | `backend/index.html` | Pantalla de la PC: QR, receta y temporizador en vivo, invitar a otro celular |
| Firebase | `firebase/`, `FIREBASE.md` | Reportes de soporte, recetas terminadas con su foto, login de admin/agente |
| Panel de soporte web | `backend/admin.html` | Admin y agentes gestionan reportes (Firebase Auth) |

## Servidor
```
cd backend
python server.py                    # Python 3.9+, sin dependencias
python -m unittest test_server -v   # pruebas de caja negra
```
En Render el comando de inicio es `python server.py`. Detalles y límites del plan gratuito en `FIREBASE.md` (sección 6).

## App Android
Abrir la carpeta en Android Studio, agregar `GEMINI_API_KEY=...` en `local.properties` (ver `FIREBASE.md`) y correr.
Flujo: Receta (escanear el QR) → Sustitutos / Chat / Reportar. El botón **Soy soporte** de la pantalla del QR abre el panel de admin/agente.

Pruebas unitarias: `./gradlew :app:testDebugUnitTest`

## Soporte (Práctica 7, 9 y 10)
Los reportes están en Firebase. Admin: ve todo, asigna y elimina (con huella o rostro). Agente: ve solo los casos que le asignan y cambia estado y notas.
Configuración paso a paso: `FIREBASE.md`.
