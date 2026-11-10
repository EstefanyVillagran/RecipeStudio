# Firebase en RecipeStudio

## Qué hace cada cosa
| Parte | Tecnología | Quién la usa |
|---|---|---|
| Reportes en tiempo real | Firestore `reportes` | Celular (los suyos) y soporte (todos) |
| Celular | Auth **anónimo** (se entra con el QR) | App Android |
| Soporte (admin/agente) | Auth **correo + contraseña** + documento `usuarios/{uid}` con `rol` | App (botón **Soy soporte** en la pantalla del QR) y `backend/admin.html` |
| Recetas terminadas + foto final | Firestore `progreso/{uid}/recetas/{receta}` (+ copia en el celular) | App |
| Sustitución con foto | IA Gemini (clave en `local.properties`) | App, pestaña Sustitutos |
| QR, chat, temporizador | Servidor Python local (por ahora) | PC + celulares |

## 1. Consola de Firebase (una sola vez)
1. Authentication → Método de acceso: activa **Anónimo** y **Correo/contraseña**.
2. Authentication → Users: crea el admin y el agente y copia sus UID.
3. Firestore Database (modo producción) → colección `usuarios`: un documento por UID con el campo `rol` = `admin` o `agente`.
4. Firestore → Reglas: pega `firebase/firestore.rules` y **Publica** (hay que volver a publicarlas: cambiaron).
5. `google-services.json` en `app/` y la configuración web en `backend/admin.html` (ya van con tu proyecto `recipestudio-e9730`).

## 2. Clave de la IA (sustitución con foto)
1. Entra a https://aistudio.google.com/apikey y crea una clave (tiene cupo gratuito).
2. En `local.properties` (en la raíz del proyecto) agrega una línea: `GEMINI_API_KEY=tu_clave`
3. Opcional: `GEMINI_MODEL=gemini-flash-latest` (es el valor por defecto; cámbialo si Google renombra modelos).
4. Sync y vuelve a compilar. `local.properties` no se sube a Git; no pegues la clave en el código.

## 3. Cómo se usa
- **Soporte desde el celular:** en la pantalla "Escanear QR" toca **Soy soporte**, entra con el correo y contraseña de Firebase y verás todos los reportes, con filtros, estado, notas, asignación y eliminar (estas dos últimas solo el admin).
- **Soporte desde la PC:** `py server.py` y abre `http://localhost:8080/admin.html`.
- **Invitar a otro celular:** en la pantalla de la PC (dashboard) toca **➕ Invitar otro celular** y se muestra el QR de la misma sesión.
- **Receta terminada:** al tomar la foto final se guarda en el celular y en la nube; al volver a vincular o cambiar de estación sigue marcada como hecha, con su foto.

## 4. Matriz de pruebas sugerida
| ID | Prueba | Resultado esperado |
|---|---|---|
| DB-01 | Celular A envía un reporte | Aparece en Firestore y en su lista |
| DB-02 | Soporte abre admin.html o el panel del celular | Lo ve sin recargar |
| DB-03 | Soporte cambia estado y agrega una nota | El celular A los ve cambiar solo |
| DB-04 | Título vacío | Se rechaza (validación local y reglas) |
| DB-05 | Modo avión, enviar y reconectar | Se sincroniza al volver la red |
| DB-06 | Terminar receta, foto final, desvincular y volver a escanear | Sigue "RECETA HECHA" con la foto |
| SEG-01 | Celular B abre Reportar | No ve los reportes de A |
| SEG-02 | Login de soporte con contraseña incorrecta | Mensaje de error, sin acceso |
| SEG-03 | Cuenta sin documento en `usuarios` | Se rechaza y cierra sesión |
| SEG-04 | Agente intenta asignar o eliminar | Permiso denegado |
| SEG-05 | Celular intenta crear con `estado: "Resuelto"` | Rechazado por reglas |
| SEG-06 | Celular intenta leer `progreso` de otro uid | Rechazado por reglas |
| SEG-07 | Sin sesión leer `reportes` | Rechazado por reglas |

SEG-04 a SEG-07 se prueban con el **Simulador de reglas** de la consola.

## 5. Sobre "ya no tenerlo en local"
Hoy el servidor Python sigue manejando el QR, el chat y la sincronización de ingredientes y temporizador (red local). Para quitarlo se pasaría todo a Firestore: la sesión sería un código de sala (el QR lo contiene), y el estado de la receta, el temporizador y el chat serían documentos que los celulares y la PC escuchan en tiempo real, sin IP ni servidor. Es un cambio grande; se hace en un paso aparte.

## 6. Servidor en Render (QR, chat, sincronización)
- **Comando de inicio:** `python server.py` (usa la variable `PORT` que pone Render; no tiene dependencias).
- **URL pública:** se toma sola de `RENDER_EXTERNAL_URL`. Si usas otra, pon la variable `KITCHENSYNC_PUBLIC_URL=https://tu-servicio.onrender.com`.
- **Plan gratuito:** el servicio se duerme tras ~15 min sin uso y la primera petición tarda hasta ~1 min. La app ya espera más tiempo al escanear, pero conviene "despertarlo" abriendo la página de la estación antes de la demo.
- **Memoria:** la sesión, el chat y el estado de la receta viven en la memoria del servidor; si Render lo reinicia o se duerme se pierden y hay que volver a vincular. Lo que está en Firebase (reportes, recetas terminadas, fotos) no se pierde.
- **Una sola cocina:** la dirección pública es una sola estación para todos. Quien abra la página ve la misma sesión. Para separar cocinas haría falta un código de sala por sesión (parte del paso a Firestore).
- **Invitar a otro celular:** en la página de la estación, botón **➕ Invitar otro celular** (con Render funciona gracias a una llave que recibe la página antes de vincular; si recargas la página ya vinculada, desvincula y vuelve a empezar la sesión).
- **Seguridad:** `local.properties` lleva tu clave de Gemini; no subas ese archivo ni lo compartas (el `.gitignore` ya lo excluye). Si lo enviaste en un zip, crea una clave nueva en aistudio.google.com/apikey.
