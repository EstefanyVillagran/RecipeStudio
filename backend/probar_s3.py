"""
Prueba de humo de S3 (sin Android): comprueba que el servidor firma bien, que AWS acepta la subida y que la foto vuelve igual.
Uso:   python backend/probar_s3.py --url https://recipestudio.onrender.com
Requisito: que NINGÚN celular esté vinculado (el script crea su propia sesión y la cierra al final).
"""
import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

# JPEG mínimo válido (1x1 píxel)
JPEG = bytes.fromhex("ffd8ffe000104a46494600010100000100010000ffdb004300080606070605080707070909080a0c140d0c0b0b0c1912130f141d1a1f1e1d1a1c1c20242e2720222c231c1c2837292c30313434341f27393d38323c2e333432ffc0000b080001000101011100ffc4001f0000010501010101010100000000000000000102030405060708090a0bffda0008010100003f00fbd0ffd9")


def pedir(metodo, url, cuerpo=None, cabeceras=None):
    req = urllib.request.Request(url, data=cuerpo, method=metodo, headers=cabeceras or {})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:
        return 0, str(e).encode()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="https://recipestudio.onrender.com")
    base = ap.parse_args().url.rstrip("/")

    print("1) Creando una sesión de prueba (si el servidor estaba dormido, tarda hasta 1 min)...")
    c, b = pedir("GET", base + "/sesion")
    if c != 200:
        print(f"   ✘ HTTP {c}: {b[:120]!r}. ¿Hay un celular vinculado? Sal de la sesión y vuelve a correr.")
        return 1
    d = json.loads(b)
    qs = urllib.parse.parse_qs(urllib.parse.urlparse(d["qrUrl"]).query)
    sid, tok = qs["sessionId"][0], qs["token"][0]
    pedir("GET", f"{base}/conectar?sessionId={sid}&token={tok}")
    auth = urllib.parse.urlencode({"sessionId": sid, "token": tok})
    try:
        print("2) Pidiendo la URL prefirmada de subida al servidor...")
        c, b = pedir("GET", f"{base}/api/upload-url?{auth}")
        if c == 503:
            print("   ✘ 503: al servidor le faltan variables de entorno (S3_BUCKET, AWS_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY).")
            return 1
        if c != 200:
            print(f"   ✘ HTTP {c}: {b[:200]!r}")
            return 1
        firmada = json.loads(b)
        print(f"   ✔ URL recibida (caduca en {firmada['expiresIn']} s), clave: {firmada['key']}")

        print("3) Subiendo una foto de prueba DIRECTO a S3 (PUT)...")
        c, b = pedir("PUT", firmada["url"], JPEG, firmada["headers"])
        if c != 200:
            print(f"   ✘ S3 respondió {c}: {b[:300].decode(errors='replace')}")
            print("   Causas típicas: 403 SignatureDoesNotMatch (clave o región incorrectas), AccessDenied (la política IAM no permite s3:PutObject),")
            print("   ExpiredToken (credenciales temporales de AWS Academy vencidas: actualiza AWS_ACCESS_KEY_ID/SECRET/SESSION_TOKEN en Render).")
            return 1
        print("   ✔ S3 aceptó la subida")

        print("4) Pidiendo la URL de descarga y comprobando que la foto vuelve idéntica...")
        c, b = pedir("GET", f"{base}/api/download-url?{auth}&key={urllib.parse.quote(firmada['key'])}")
        c2, descargada = pedir("GET", json.loads(b)["url"]) if c == 200 else (c, b)
        if c2 == 200 and descargada == JPEG:
            print("   ✔ La foto descargada es idéntica a la subida. TODO FUNCIONA.")
            return 0
        print(f"   ✘ Descarga falló (HTTP {c2}) o los bytes no coinciden")
        return 1
    finally:
        pedir("POST", base + "/reset", json.dumps({"sessionId": sid, "token": tok}).encode(), {"Content-Type": "application/json"})


if __name__ == "__main__":
    sys.exit(main())
