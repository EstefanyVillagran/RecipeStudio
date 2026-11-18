# Prácticas 13 y 14 — Automatización y validación continua

## 1. Componentes seleccionados

| Componente crítico | Riesgo detectado | Automatización aplicada |
|---|---|---|
| Cálculo de sustituciones | Cantidades cero, `NaN`, infinitas o ingredientes desconocidos podían producir resultados incorrectos | Pruebas unitarias JUnit/Kotlin |
| Comunicación HTTP con `KitchenApi` | Un servidor caído o respuestas HTTP incorrectas podían presentarse como respuestas válidas | Servidor local MockWebServer y pruebas de integración HTTP |
| Persistencia local con `AppDatabase` y `RecetaRepository` | Podían duplicarse registros o no recuperarse cambios de recetas e ingredientes | Pruebas de integración entre repositorio, base local y dominio |

La adaptación a integración no depende de Firebase ni de Internet: se usa MockWebServer para el API y bases/objetos en memoria para persistencia local.

## 2. Matriz de automatización

| ID | Tipo | Componente | Estado |
|---|---|---|---|
| AUT-01 | Unitarias | Sustitución válida y ajuste de cantidad | Aprobada |
| AUT-02 | Unitarias | Entrada vacía o ingrediente desconocido | Aprobada |
| AUT-03 | Unitarias | Cantidades cero, `NaN`, infinitas y límites | Aprobada |
| AUT-04 | Integración | Respuestas 200, 401, 403 y 500 de `KitchenApi` | Aprobada |
| AUT-05 | Integración | Métodos GET/POST, parámetros y cuerpo JSON | Aprobada |
| AUT-06 | Persistencia | Guardado, reemplazo y recuperación de recetas | Aprobada |
| AUT-07 | CI | Ejecución en push | Configurada |
| AUT-08 | CI | Ejecución en pull request | Configurada |

## 3. Evidencia de ejecución local

Ejecutadas en Windows con el Gradle Wrapper:

- `./gradlew :app:testDebugUnitTest`: **BUILD SUCCESSFUL**, 69 pruebas, 0 fallos, 0 errores, 0 omitidas; tiempo acumulado de ejecución JUnit: 1.225 segundos.
- `./gradlew :app:lintDebug`: **BUILD SUCCESSFUL** en 3 minutos 6 segundos; 0 errores y 17 advertencias.

La advertencia principal de lint señala versiones antiguas de SDK/dependencias, un uso de JavaScript en WebView, iconos adaptativos sin etiqueta monochrome y algunas comprobaciones personalizadas obsoletas. No impide la compilación ni las pruebas.

El informe HTML de lint está en `app/build/reports/lint-results-debug.html`. Los informes XML JUnit están en `app/build/test-results/testDebugUnitTest/` y el informe web en `app/build/reports/tests/testDebugUnitTest/`.

## 4. Errores encontrados y correcciones

1. Las pruebas HTTP originales dependían de `com.sun.net.httpserver`, que no está disponible de forma fiable en el compilador del módulo Android.
   - Corrección: sustitución por MockWebServer, una implementación compatible con JUnit y GitHub Actions.
2. Cálculos con cantidades `NaN` o infinitas podían superar las validaciones tradicionales `> 0`.
   - Corrección: incorporación de `cantidad.isFinite()` antes del cálculo.
3. La prueba de persistencia contenía una afirmación redundante sobre el estado del ingrediente.
   - Corrección: validación directa del nombre persistido y del progreso de la receta.

## 5. Pipeline de integración continua

El archivo `.github/workflows/android-ci.yml` ejecuta automáticamente:

1. Checkout del repositorio.
2. JDK 17 con caché de Gradle.
3. Permiso de ejecución para `gradlew`.
4. `./gradlew :app:testDebugUnitTest --stacktrace`.
5. `./gradlew :app:lintDebug --stacktrace`.
6. Publicación de informes como artefactos de GitHub Actions.

El flujo se dispara con `push` y `pull_request`. Después de hacer el push final debe comprobarse la pestaña **Actions** del repositorio y conservarse la URL/captura del workflow exitoso como evidencia.

## 6. Evidencia pendiente en GitHub

Para completar el entregable:

1. Confirmar el estado remoto: `git status`.
2. Crear una rama o continuar en `main` según las reglas del profesor.
3. Ejecutar `git add . && git commit -m "Prácticas 13 y 14: pruebas y CI"`.
4. Ejecutar `git push origin main`.
5. Abrir GitHub → Actions.
6. Descargar/capturar el artefacto `informes-pruebas-android`.

## Conclusión técnica

La automatización permite detectar errores de cálculo, comunicación y persistencia antes de que lleguen a una versión publicada. Las pruebas unitarias fijan las reglas de negocio, las pruebas de integración verifican que los componentes colaboren correctamente y el pipeline impide que se acepten cambios que rompan compilación, pruebas o análisis lint. De este modo, nuevas modificaciones pueden validarse de forma repetible y rápida, reduciendo regresiones y conservando la calidad técnica de RecipeStudio/KitchenSync.
