# La IA de sustitución (Gemini): clave gratuita y errores comunes

## Conseguir una clave que SÍ funcione (plan gratuito)
1. Entra a https://aistudio.google.com/apikey con tu cuenta de Google.
2. **Create API key** y, cuando pregunte por el proyecto, elige **Create API key in new project** (un proyecto nuevo, sin facturación).
   No uses un proyecto que tenga una cuenta de facturación vinculada (por ejemplo el de Firebase si lo pasaste a Blaze).
3. En `local.properties` pon `GEMINI_API_KEY=tu_clave` (sin comillas ni espacios).
4. **Sync** y **Rebuild Project**. La clave se mete al compilar: hay que volver a instalar la app.

## Errores
| Mensaje en la app | Significa | Qué hacer |
|---|---|---|
| HTTP 402 / "facturación sin saldo" | La clave está en un proyecto con facturación de **prepago con saldo $0** | Crea una clave nueva en un proyecto SIN facturación (pasos de arriba), o agrega saldo en AI Studio > Billing |
| 401 / 403 | Clave mal copiada o sin permiso | Copia la clave de nuevo, sin espacios |
| 429 | Cupo gratuito por minuto agotado | Espera un minuto |
| "falta GEMINI_API_KEY" | La app se compiló sin la clave | Revisa `local.properties` y recompila |
Si la IA falla, la app muestra las reglas guardadas como respaldo ("Mientras tanto, según las reglas guardadas...").

## Si no quieres o no puedes usar ninguna IA
La pestaña Sustitutos tiene el bloque **"¿Sin IA o sin internet? Escribe qué tienes"**: calcula el cambio de sabor y textura con perfiles guardados en la app
(`shared/.../domain/EstimadorSabor.kt`, 58 ingredientes). No usa internet, clave ni saldo. La IA con foto es un extra, no un requisito.
