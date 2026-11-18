# Plan de trabajo (2 semanas) y estado

| # | Entregable | Estado |
|---|---|---|
| 1 | Núcleo KMM (`shared`): validaciones, cálculo de sustitución, reglas, cliente S3 con Ktor, `expect/actual` (huella y QR) | Hecho |
| 2 | S3 con URL prefirmada: servidor (`s3_presign.py`, `/api/upload-url`, `/api/download-url`) + cliente KMM + pruebas | Hecho (falta que configures AWS: `docs/AWS_S3.md`) |
| 3 | Muro de recetas con foto final (S3 + Firestore) | Hecho |
| 4 | Alarma y vibración del temporizador + pantalla siempre encendida | Hecho |
| 5 | Pantalla de Receta: pasos con Anterior/Siguiente, notas personales, cambiar foto final | Hecho (rediseño visual completo: pendiente) |
| 6 | Crear / editar / borrar recetas propias (nube) y 8 recetas incluidas | Hecho (huella para recetas secretas: pendiente) |
| 7 | Escalar porciones, lista de compras compartible, "¿Qué cocino hoy?" con foto de alacena (IA) | Pendiente |
| 8 | Notificación cuando soporte responde, modo oscuro, pantallas vacías del Chat | Pendiente |
| 9 | Cuenta opcional con correo (recuperar reportes en otro celular) | Pendiente |
| 10 | Pruebas de interfaz (Compose UI Test), cobertura y documentación final alineada con lo construido | Pendiente |

| 11 | Sincronización v2: la receta viaja completa a la estación; cambios propios protegidos contra el parpadeo del sondeo | Hecho |
| 12 | Ícono de la app | Hecho |
