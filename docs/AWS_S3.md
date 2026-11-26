# Fotos en AWS S3 con URL prefirmada (paso a paso)

**Idea:** la app NUNCA recibe las claves de AWS. Le pide al servidor (Render) una URL firmada que caduca en 5 minutos y sube la
foto directo a S3 con un `PUT`. El servidor no procesa la imagen (no se satura) y el bucket sigue privado.

```
Celular --(1) GET /api/upload-url + sesión QR--> Servidor (Render, firma con SigV4)
Celular <--(2) URL PUT firmada (5 min) ---------- Servidor
Celular --(3) PUT foto.jpg (directo) -----------> S3 (bucket privado)
Celular --(4) guarda la clave fotos/... en Firestore y en el muro
```

## 1. Crear el bucket (AWS Console > S3 > Create bucket)
1. Nombre único, por ejemplo `recipestudio-fotos-TUNOMBRE`. Región cercana (ej. `us-east-1`). Anótalos.
2. Deja activado **Block all public access** (el bucket NO es público; las fotos se ven con URLs temporales).
3. No hace falta configurar CORS (la app es nativa, no un navegador).

## 2. Crear el usuario de acceso (IAM > Users > Create user)
1. Nombre `recipestudio-server`, sin acceso a la consola.
2. Permisos > *Attach policies directly* > **Create policy** > JSON (cambia el nombre del bucket):
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject"],
    "Resource": "arn:aws:s3:::recipestudio-fotos-TUNOMBRE/fotos/*"
  }]
}
```
3. Entra al usuario > *Security credentials* > **Create access key** (tipo "Application running outside AWS") y copia
   el **Access key ID** y el **Secret access key** (el secreto solo se muestra una vez).

> **¿Usas AWS Academy / Learner Lab?** No puedes crear usuarios IAM: usa las credenciales temporales de *AWS Details > AWS CLI*
> (Access key, Secret y **Session token**). Se vencen en unas horas: actualízalas en Render antes de la demo y pon también `AWS_SESSION_TOKEN`.

## 3. Variables de entorno en Render (Dashboard > tu servicio > Environment)
| Variable | Valor |
|---|---|
| `S3_BUCKET` | `recipestudio-fotos-TUNOMBRE` |
| `AWS_REGION` | `us-east-1` (la del bucket) |
| `AWS_ACCESS_KEY_ID` | tu Access key ID |
| `AWS_SECRET_ACCESS_KEY` | tu Secret access key |
| `AWS_SESSION_TOKEN` | solo si usas credenciales temporales (Academy) |
| `KITCHENSYNC_ADMIN_PASSWORD` / `KITCHENSYNC_AGENTE_PASSWORD` | contraseñas fuertes de tu elección |
Guarda y espera a que Render redespliegue. **Nunca pongas estas claves en el código ni en Git.**
(También hay que subir a Render la carpeta `backend/` nueva: trae `s3_presign.py` y los endpoints `/api/upload-url` y `/api/download-url`.)

## 4. Probarlo SIN la app
```
python backend/probar_s3.py --url https://recipestudio.onrender.com
```
Debe terminar con "La foto descargada es idéntica a la subida. TODO FUNCIONA". Si falla, el propio script te dice la causa
probable (503 = faltan variables; 403 SignatureDoesNotMatch = región o clave mal; ExpiredToken = credenciales de Academy vencidas).

## 5. Cómo se ve en la app
Al terminar una receta y tomar la **foto final** (resolución completa), la app la sube a S3, guarda la clave en Firestore y la publica en la
pestaña **Muro**. Si el servidor no tiene S3 configurado o no hay internet, la receta se guarda igual con una miniatura (no se rompe nada).

## 6. Seguridad (para tu documentación)
- Las claves de AWS solo existen en variables de entorno del servidor; la app solo ve URLs temporales.
- Solo se firman JPG/PNG/WEBP; la clave del objeto es aleatoria (`fotos/AAAAMMDD/<24 hex>.jpg`) y se valida con una expresión regular (no se pueden pedir rutas ajenas).
- La URL PUT firma el `Content-Type`: S3 rechaza otro tipo de archivo. Para limitar el tamaño la app valida ≤ 5 MB (con POST firmado también podría exigirlo S3).
- Pruebas: `backend/test_s3.py` (incluye el vector oficial de firma de AWS) y `shared/src/commonTest/.../S3UploadClientTest.kt`.
