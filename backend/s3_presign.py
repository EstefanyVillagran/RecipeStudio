"""URLs prefirmadas de AWS S3 (Signature V4, firma por parámetros de consulta). Solo librería estándar.

Flujo de la estrategia de multimedia de RecipeStudio:
  1) la app pide al servidor  GET /api/upload-url  (solo con una sesión vinculada),
  2) el servidor devuelve una URL PUT firmada que caduca en pocos minutos,
  3) la app sube la foto DIRECTO a S3 con un PUT: el servidor nunca recibe los bytes de la imagen.
Las claves de AWS viven únicamente en variables de entorno del servidor (nunca en la app).
"""
import datetime
import hashlib
import hmac
import os
import re
import secrets
import urllib.parse

TIPOS_PERMITIDOS = {"image/jpeg": "jpg", "image/png": "png", "image/webp": "webp"}
# fotos/AAAAMMDD/<24 hex>.<ext>  -> cualquier otra ruta se rechaza (evita pedir URLs de objetos ajenos o de "../")
PATRON_CLAVE = re.compile(r"^fotos/\d{8}/[a-f0-9]{24}\.(jpg|png|webp)$")


class S3Config:
    def __init__(self, bucket, region, access_key, secret_key, session_token=None):
        self.bucket, self.region = bucket, region
        self.access_key, self.secret_key, self.session_token = access_key, secret_key, session_token

    @property
    def host(self):
        return f"{self.bucket}.s3.{self.region}.amazonaws.com"

    @classmethod
    def desde_entorno(cls):
        """None si falta algo: el servidor responde 503 y la app usa su respaldo (Firestore)."""
        e = os.environ
        bucket, region = e.get("S3_BUCKET", "").strip(), e.get("AWS_REGION", "").strip()
        ak, sk = e.get("AWS_ACCESS_KEY_ID", "").strip(), e.get("AWS_SECRET_ACCESS_KEY", "").strip()
        if not (bucket and region and ak and sk):
            return None
        return cls(bucket, region, ak, sk, e.get("AWS_SESSION_TOKEN", "").strip() or None)   # token: AWS Academy / STS


def _hmac(clave, mensaje):
    return hmac.new(clave, mensaje.encode("utf-8"), hashlib.sha256).digest()


def firmar_url(metodo, host, ruta, region, access_key, secret_key, session_token=None,
               expira=300, ahora=None, content_type=None):
    """URL prefirmada. Si se pasa content_type queda FIRMADO: el cliente debe enviar exactamente esa cabecera."""
    ahora = ahora or datetime.datetime.now(datetime.timezone.utc)
    amz_fecha = ahora.strftime("%Y%m%dT%H%M%SZ")
    fecha = amz_fecha[:8]
    alcance = f"{fecha}/{region}/s3/aws4_request"
    cabeceras = {"host": host}
    if content_type:
        cabeceras["content-type"] = content_type
    firmadas = ";".join(sorted(cabeceras))
    params = {
        "X-Amz-Algorithm": "AWS4-HMAC-SHA256",
        "X-Amz-Credential": f"{access_key}/{alcance}",
        "X-Amz-Date": amz_fecha,
        "X-Amz-Expires": str(int(expira)),
        "X-Amz-SignedHeaders": firmadas,
    }
    if session_token:
        params["X-Amz-Security-Token"] = session_token
    consulta = "&".join(f"{urllib.parse.quote(k, safe='')}={urllib.parse.quote(v, safe='')}" for k, v in sorted(params.items()))
    ruta_codificada = urllib.parse.quote(ruta, safe="/~")
    canonica = "\n".join([
        metodo, ruta_codificada, consulta,
        "".join(f"{k}:{cabeceras[k].strip()}\n" for k in sorted(cabeceras)),
        firmadas, "UNSIGNED-PAYLOAD",
    ])
    a_firmar = "\n".join(["AWS4-HMAC-SHA256", amz_fecha, alcance, hashlib.sha256(canonica.encode("utf-8")).hexdigest()])
    k = _hmac(("AWS4" + secret_key).encode("utf-8"), fecha)
    for parte in (region, "s3", "aws4_request"):
        k = _hmac(k, parte)
    firma = hmac.new(k, a_firmar.encode("utf-8"), hashlib.sha256).hexdigest()
    return f"https://{host}{ruta_codificada}?{consulta}&X-Amz-Signature={firma}"


def nueva_clave(content_type, ahora=None):
    ahora = ahora or datetime.datetime.now(datetime.timezone.utc)
    return f"fotos/{ahora:%Y%m%d}/{secrets.token_hex(12)}.{TIPOS_PERMITIDOS[content_type]}"


def url_subida(cfg, content_type, expira=300):
    if content_type not in TIPOS_PERMITIDOS:
        raise ValueError("tipo de archivo no permitido")
    clave = nueva_clave(content_type)
    url = firmar_url("PUT", cfg.host, "/" + clave, cfg.region, cfg.access_key, cfg.secret_key,
                     cfg.session_token, expira, content_type=content_type)
    return {"url": url, "key": clave, "method": "PUT", "expiresIn": expira, "headers": {"Content-Type": content_type}}


def url_descarga(cfg, clave, expira=3600):
    if not PATRON_CLAVE.match(clave or ""):
        raise ValueError("clave de objeto inválida")
    url = firmar_url("GET", cfg.host, "/" + clave, cfg.region, cfg.access_key, cfg.secret_key, cfg.session_token, expira)
    return {"url": url, "key": clave, "expiresIn": expira}
