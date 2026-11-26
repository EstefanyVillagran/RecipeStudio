# Práctica 11 (White Box) - guía paso a paso

> Qué es "White Box": probar la aplicación **mirando su código por dentro** (qué `if` hay, qué caminos tiene cada
> método) y **midiendo** (tiempos, códigos HTTP, cobertura). Con capturas de la app funcionando NO alcanza: hay que
> entregar **números reales y evidencia del código**. Aquí ya está casi todo preparado; tú ejecutas y pegas resultados.

## 0. Mapa: qué hay y qué haces tú
| Fase | Qué pide la práctica | Qué ya está hecho | Qué haces tú |
|---|---|---|---|
| 1 Análisis estático | Defectos en el código | `analisis_estatico.py` + `resultado_analisis_estatico.txt` + lint | Correr 2 comandos y pegar |
| 2 App móvil | 3 métodos, grafo, casos, cobertura | 4 archivos de pruebas Kotlin + JaCoCo | Correr 1 comando y sacar captura del % |
| 3 Servidor/API | 3 endpoints, códigos HTTP | `test_whitebox_servidor.py` + matriz de códigos | Correr y pegar |
| 4 Base de datos | CRUD, integridad, concurrencia | `medir_firestore.py` | Correr con tu cuenta admin y pegar |
| 5 Red y rendimiento | ping, latencia, Mbps, carga, corte | `medir_conectividad.py` | Correr desde TU equipo y hacer la prueba de modo avión |
| 6 Defectos | 3 o más con evidencia | `INFORME_WHITEBOX.md` ya trae 11 hallazgos reales | Revisar, comprobar y agregar tu evidencia |

**Regla de oro:** los números del informe tienen que salir de TU ejecución. No copies los de este documento: los que
puse son "de referencia" (los medí en un entorno aislado) y lo dice cada vez.

## 1. Preparación (10 min)
1. Tener **Python 3.9 o más** (`python --version`). En Windows también sirve `py`.
2. Tener **Android Studio** con el proyecto abierto (para la Fase 2) y **Git** (`git --version`).
3. Abrir una terminal **en la carpeta del proyecto** (la que tiene `README.md`).
4. Anotar para el informe: modelo del celular, versión de Android, versión de la app (`versionName` en `app/build.gradle.kts`),
   servidor (`https://recipestudio.onrender.com`, plan gratuito), fecha y el commit: `git rev-parse --short HEAD`.
5. Instalar (una vez) la herramienta de cobertura de Python: `pip install coverage`

## 2. Fase 1 - Análisis estático (30 min)
```
python pruebas_whitebox/analisis_estatico.py > resultado_analisis_estatico.txt
```
- Abre el archivo: trae la **complejidad ciclomática** de cada función, los patrones de riesgo (`!!`, `catch` genérico,
  claves en el código) y si `local.properties` está protegido por `.gitignore`. Lo que salió de mi corrida está en
  `INFORME_WHITEBOX.md` sección 1.
- **Lint de Android** (análisis estático de la herramienta oficial): `gradlew lintDebug` (Windows: `.\gradlew.bat lintDebug`).
  Se abre `app/build/reports/lint-results-debug.html`: pon captura del resumen y copia 3 advertencias con su explicación.
- En Android Studio: menú **Analyze > Inspect Code...** > "Whole project" > OK. Copia 3 hallazgos.
- Para Python (opcional, suma puntos): `pip install pyflakes bandit` y luego `python -m pyflakes backend/server.py` y `bandit -r backend/server.py`.
- **Qué entregar:** el .txt + las capturas de lint/Inspect Code + una tabla de defectos (ya viene armada en el informe).

## 3. Fase 2 - Pruebas White Box de la app (1 h)
Elegí 3 métodos (uno por tipo que pide la práctica). Ya tienen pruebas escritas:
| Tipo | Método | Archivo de pruebas |
|---|---|---|
| Validación | `InputValidator.validarReporte` | `shared/src/commonTest/.../security/ValidarReporteWhiteBoxTest.kt` |
| Cálculo | `SubstitutionCalculator.analizar` y `nivelDe` | `shared/src/commonTest/.../domain/SustitucionWhiteBoxTest.kt` |
| Comunicación | `KitchenApi.call` (vía `misReportes` y `reportar`) | `.../whitebox/KitchenApiWhiteBoxTest.kt` (levanta un servidor local de mentira) |
Extra: `ServerConfigWhiteBoxTest.kt` (el método que causaba el error del QR).

**Nota:** desde que la lógica de negocio pasó al módulo KMM `shared`, las pruebas de `InputValidator` y `SubstitutionCalculator` viven en
`shared/src/commonTest/...` y su cobertura sale de `gradlew :shared:createDebugUnitTestCoverageReport`
(reporte en `shared/build/reports/coverage/test/debug/index.html`). Las de `KitchenApi` y `ServerConfig` siguen en `app/`.

**Comandos** (Windows: `.\gradlew.bat`, Mac/Linux: `./gradlew`):
```
gradlew :shared:testDebugUnitTest :app:testDebugUnitTest     # corre todas las pruebas (núcleo KMM y app)
gradlew :shared:createDebugUnitTestCoverageReport :app:createDebugUnitTestCoverageReport   # cobertura JaCoCo de ambos
```
- Resultado de pruebas: `app/build/reports/tests/testDebugUnitTest/index.html` (captura: todas en verde).
- **Cobertura:** abre `app/build/reports/coverage/test/debug/index.html`. Entra al paquete `security`, `domain`, `network`
  y a las clases `InputValidator`, `SubstitutionCalculator`, `KitchenApi`. Anota **Instrucciones (Instruction)** y **Ramas (Branch)**
  de cada una. Meta: instrucciones >= 85 % y ramas >= 80 % en los módulos críticos.
- Si una rama no se cubre, **justifícala** (no la fuerces): por ejemplo `analizar` tiene un `?: return null` inalcanzable
  (hallazgo D-07), así que esa rama nunca debe llegar al 100 %.
- **Grafo de flujo de control (CFG):** ya están en `INFORME_WHITEBOX.md` (formato Mermaid). Pégalos en https://mermaid.live y
  exporta PNG para el informe. Revisa que coincidan con el código leyendo el método.
- **Cómo se cumple cada técnica:** instrucciones (cada línea ejecutada), decisiones (cada `if/when` en verdadero y falso),
  condiciones (cada parte de un `||` por separado: `titulo.isBlank()` y `descripcion.isBlank()`), valores límite (3, 80, 81...) y
  excepciones (`IllegalArgumentException`, `ApiException`). Los nombres de las pruebas dicen cuál cubre cuál.

## 4. Fase 3 - Servidor y endpoints (45 min)
```
cd backend
python -m unittest test_server test_whitebox_servidor -v
coverage run --branch -m unittest test_server test_whitebox_servidor
coverage report -m --include=server.py
coverage html --include=server.py            # abre htmlcov/index.html
```
- Captura el resumen de `coverage report` (columnas Stmts, Miss, Branch, BrPart, Cover). **Pega el % de instrucciones y de ramas.**
- Elige 3 endpoints para el informe, por ejemplo `GET /estado`, `POST /reportar` y `POST /evento`. Para cada uno explica
  qué función lo atiende (`Core.estado_publico`, `Core.reportar`, `Core.evento`), qué validaciones hace y qué código devuelve.
- La matriz de códigos HTTP (200, 400, 401, 403, 404, 405, 413) sale sola con el script de la Fase 5 (sección D).
- **Postman (opcional pero la práctica lo menciona):** crea una colección con esas mismas peticiones contra `https://recipestudio.onrender.com`,
  ejecútala con "Run collection" y captura la tabla de resultados.

## 5. Fase 4 - Base de datos Firestore (45 min)
Firestore no es SQL: no hay EXPLAIN ni claves foráneas; la integridad la imponen las **reglas de seguridad** (`firebase/firestore.rules`).
```
python pruebas_whitebox/medir_firestore.py --admin-email TU_ADMIN@correo --admin-pass TU_CONTRASEÑA
```
- Crea un reporte, prueba datos inválidos, consulta, actualiza, elimina y mide el tiempo de cada operación (DB-01 a DB-06).
- Pega las dos tablas que imprime (matriz y tiempos de consulta). Cada fila marcada ✘ es un **hallazgo o una expectativa mal planteada**:
  decide cuál es y explícalo.
- En el informe responde las revisiones de la Fase 4: NOT NULL/UNIQUE = reglas `hasOnly`/tamaños; índices = no se usan
  índices compuestos (se ordena en el cliente a propósito); transacciones = Firestore las ofrece pero el app no las usa
  (DB-06 muestra que gana la última escritura); inyección SQL = no aplica (no hay SQL); permisos = reglas.
- Sin cuenta admin el script corre igual pero omite actualizar/eliminar/concurrencia: **justifícalo** o corre con la cuenta.

## 6. Fase 5 - Conectividad y rendimiento (1 h)
Corre desde el equipo (o el celular con Termux si quieres medir desde móvil; lo normal es desde tu laptop):
```
python pruebas_whitebox/medir_conectividad.py --conexion "WiFi casa" --dispositivo "Laptop Windows 11"
```
Hace solo: **ping** (20 paquetes), **latencia HTTP** (10 solicitudes, tabla B de la práctica), **tasa de descarga** (3 mediciones),
**códigos HTTP**, y **carga** con 1, 5 y 10 usuarios (20 solicitudes cada uno; lectura `GET /estado` y escritura `POST /chat`).
- Antes, **despierta Render** abriendo `https://recipestudio.onrender.com` y espera. El script mide aparte la primera petición.
- Desvincula el celular de la estación antes de la carga: el script crea su propia sesión y la cierra.
- Si el ping sale "bloqueado": Render bloquea ICMP. La práctica lo permite si lo **registras como limitación** y usas HTTP. No son lo mismo.
- **Subida (Mbps):** la mide `medir_firestore.py` (sube una foto de ~100 KB); este servidor solo recibe JSON diminuto.
- CPU y memoria: panel **Metrics** de Render (captura mientras corre la carga).

**Prueba de interrupción (NET-05/NET-06) - se hace a mano con el celular:**
1. App abierta en **Reportar**. Activa el **modo avión**.
2. Escribe un reporte válido y toca Enviar. Mira qué pasa: ¿el botón se queda en "Enviando"? ¿Dice "enviado" aunque no lo esté? Anota.
3. Sin cerrar la app, desactiva el modo avión. Espera 30 s. Abre Firebase Console > Firestore > `reportes`: ¿llegó 1 vez? ¿2 veces?
4. Repite pero tocando Enviar dos veces con el modo avión puesto: ¿se duplicó al volver la red? (La hipótesis está en el hallazgo D-09.)
5. Captura (a) la app, (b) la consola de Firestore, (c) el Logcat de Android Studio filtrado por `Firestore`.

## 7. Fase 6 y las 10 preguntas (1 h)
- `INFORME_WHITEBOX.md` ya trae los hallazgos D-01 a D-11 con pasos, resultado esperado/observado, severidad y corrección propuesta.
  Cada uno tiene la **línea de código** y la evidencia que debes adjuntar. Comprueba los que dependen de Render (D-02) tú mismo.
- Las 10 preguntas están al final del informe con una **respuesta guía** basada en tu proyecto: reescríbelas con tus palabras y tus números.

## 8. Entrega: qué subir
1. `INFORME_WHITEBOX.md` convertido a PDF/Word (con tus capturas y tablas).
2. Código de pruebas: `app/src/test/**`, `backend/test_server.py`, `backend/test_whitebox_servidor.py`, `pruebas_whitebox/*.py`.
3. CSV y .txt generados: `resultados_conectividad.csv`, `resultados_firestore.csv`, `resultado_analisis_estatico.txt`.
4. Capturas: reporte JaCoCo, `coverage report`, lint, Postman, consola de Firestore, Logcat.
5. El commit evaluado: pega la salida de `git log -1 --oneline`.

## 9. Errores frecuentes
- *"No module named coverage"*: `pip install coverage`.
- *El script de red tarda mucho al inicio*: Render estaba dormido; es normal y la 1a petición no cuenta.
- *`medir_firestore.py` dice que no puede crear sesión anónima*: activa **Anónimo** en Firebase > Authentication.
- *Una prueba Kotlin no compila*: copia el error y revisa que `app/build.gradle.kts` tenga `org.json:json` en `testImplementation`.
- *Quiero 100 %*: no. La práctica pide justificar lo que no se cubre en vez de forzar pruebas artificiales.
