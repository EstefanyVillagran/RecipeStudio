"""
Práctica 11 - Fase 4 y parte de la Fase 5 (subida/descarga). Pruebas de la base de datos (Firestore) usando su API REST,
es decir, SIN la app: así se ve exactamente lo que responden las reglas de seguridad y cuánto tarda cada operación.
Solo usa la librería estándar de Python.

Uso:
    python pruebas_whitebox/medir_firestore.py --admin-email admin@recipestudio.com --admin-pass TU_CONTRASEÑA
(sin --admin-* se ejecutan solo las pruebas como celular anónimo; las de actualizar/eliminar con permisos quedan como "OMITIDA")

Lo que hace:
  DB-01 insertar válido | DB-02 integridad (campos incompletos, campo extra, estado indebido)
  DB-03 consultas (por id existente, inexistente, por filtro) con tiempos | DB-04 actualizar | DB-05 eliminar
  DB-06 concurrencia (dos escrituras simultáneas al mismo documento) | NET-03/04 descarga y subida de una foto de ~100 KB
Todo lo que crea queda marcado con el prefijo REP-WB- y se intenta borrar al final.
"""
import argparse
import base64
import concurrent.futures
import csv
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime

API_KEY_PUBLICA = "AIzaSyC1eQzbzkiQVBAhsevlPeUng0zdcChfuKk"   # la misma de admin.html (clave web pública de Firebase)
RES = []


def rest(metodo, url, cuerpo=None, token=None):
    """(codigo, ms, bytes_enviados, bytes_recibidos, json)"""
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    req = urllib.request.Request(url, data=datos, method=metodo)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            cuerpo_r = r.read()
            cod = r.status
    except urllib.error.HTTPError as e:
        cuerpo_r, cod = e.read(), e.code
    except Exception as e:
        return 0, (time.perf_counter() - t0) * 1000, len(datos or b""), 0, {"error": str(e)}
    ms = (time.perf_counter() - t0) * 1000
    try:
        j = json.loads(cuerpo_r) if cuerpo_r else {}
    except ValueError:
        j = {}
    return cod, ms, len(datos or b""), len(cuerpo_r), j


def valor(v):
    if v is None:
        return {"nullValue": None}
    if isinstance(v, bool):
        return {"booleanValue": v}
    if isinstance(v, int):
        return {"integerValue": str(v)}
    if isinstance(v, list):
        return {"arrayValue": {"values": [valor(x) for x in v]}}
    return {"stringValue": str(v)}


def campos(d):
    return {"fields": {k: valor(v) for k, v in d.items()}}


def estado_error(j):
    e = j.get("error", {}) if isinstance(j, dict) else {}
    return f"{e.get('status', '')}" if isinstance(e, dict) else ""


def registrar(id_, operacion, esperado, cod, ms, extra=""):
    ok = cod == esperado
    RES.append([id_, operacion, esperado, cod, f"{ms:.0f}", "OK" if ok else "FALLA", extra])
    print(f"| {id_} | {operacion} | {esperado} | {cod} | {ms:.0f} | {'✔' if ok else '✘'} | {extra} |")
    return ok


def omitida(id_, operacion, motivo):
    RES.append([id_, operacion, "", "", "", "OMITIDA", motivo])
    print(f"| {id_} | {operacion} | - | - | - | OMITIDA | {motivo} |")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--project", default="recipestudio-e9730")
    ap.add_argument("--api-key", default=os.environ.get("FIREBASE_API_KEY", API_KEY_PUBLICA))
    ap.add_argument("--admin-email", default=os.environ.get("ADMIN_EMAIL"))
    ap.add_argument("--admin-pass", default=os.environ.get("ADMIN_PASS"))
    a = ap.parse_args()
    DOCS = f"https://firestore.googleapis.com/v1/projects/{a.project}/databases/(default)/documents"
    AUTH = "https://identitytoolkit.googleapis.com/v1/accounts"

    print("# Pruebas de base de datos - Firestore (proyecto " + a.project + ")")
    print(f"- Fecha: {datetime.now():%Y-%m-%d %H:%M:%S}  | Cliente: Python {sys.version.split()[0]}  | API REST de Firestore\n")

    # ---- identidades ----
    c, _, _, _, j = rest("POST", f"{AUTH}:signUp?key={a.api_key}", {"returnSecureToken": True})
    if c != 200:
        print(f"No se pudo crear la sesión anónima (HTTP {c}: {estado_error(j)}). ¿Está activado el acceso Anónimo en Authentication?")
        return 1
    anon_tok, anon_uid = j["idToken"], j["localId"]
    staff_tok = None
    rol_staff = None
    if a.admin_email and a.admin_pass:
        c, _, _, _, j = rest("POST", f"{AUTH}:signInWithPassword?key={a.api_key}",
                             {"email": a.admin_email, "password": a.admin_pass, "returnSecureToken": True})
        if c == 200:
            staff_tok = j["idToken"]
        else:
            print(f"(No se pudo entrar como soporte: {estado_error(j) or c}. Se omiten las pruebas con permisos.)")

    print("| ID | Operación | Esperado | Obtenido | ms | Resultado | Detalle |")
    print("|---|---|---|---|---|---|---|")

    base_doc = lambda i: {"titulo": "Prueba White Box", "descripcion": "Documento de prueba", "categoria": "Otro", "prioridad": "Media",
                          "estado": "Nuevo", "dispositivo": "script-wb", "ownerUid": anon_uid, "asignadoA": None,
                          "creadoEn": int(time.time() * 1000)}
    sufijo = str(int(time.time()))
    rid = f"REP-WB-{sufijo}"

    # ---- DB-01 inserción válida ----
    c, ms, _, _, j = rest("POST", f"{DOCS}/reportes?documentId={rid}", campos(base_doc(0)), anon_tok)
    ok_ins = registrar("DB-01", "Insertar reporte válido (celular anónimo)", 200, c, ms, estado_error(j))

    # ---- DB-02 integridad / reglas ----
    mal = base_doc(0); mal["titulo"] = "ab"
    c, ms, *_, j = rest("POST", f"{DOCS}/reportes?documentId=REP-WB-corto-{sufijo}", campos(mal), anon_tok)
    registrar("DB-02a", "Insertar con título de 2 caracteres (regla: 3-80)", 403, c, ms, estado_error(j))
    extra = base_doc(0); extra["admin"] = True
    c, ms, *_, j = rest("POST", f"{DOCS}/reportes?documentId=REP-WB-extra-{sufijo}", campos(extra), anon_tok)
    registrar("DB-02b", "Insertar con un campo no permitido", 403, c, ms, estado_error(j))
    res = base_doc(0); res["estado"] = "Resuelto"
    c, ms, *_, j = rest("POST", f"{DOCS}/reportes?documentId=REP-WB-estado-{sufijo}", campos(res), anon_tok)
    registrar("DB-02c", "Insertar con estado 'Resuelto' (regla: solo 'Nuevo')", 403, c, ms, estado_error(j))
    ajeno = base_doc(0); ajeno["ownerUid"] = "otro-usuario"
    c, ms, *_, j = rest("POST", f"{DOCS}/reportes?documentId=REP-WB-ajeno-{sufijo}", campos(ajeno), anon_tok)
    registrar("DB-02d", "Insertar a nombre de OTRO usuario", 403, c, ms, estado_error(j))
    c, ms, *_, j = rest("POST", f"{DOCS}/reportes?documentId={rid}", campos(base_doc(0)), anon_tok)
    registrar("DB-02e", "Insertar con ID repetido (UNIQUE)", 403, c, ms, estado_error(j) + " (sin reglas sería 409 ALREADY_EXISTS; con reglas un create sobre un doc existente se trata como update y se rechaza)")
    c, ms, *_, j = rest("POST", f"{DOCS}/reportes?documentId=REP-WB-sinauth-{sufijo}", campos(base_doc(0)))
    registrar("DB-02f", "Insertar SIN autenticación", 403, c, ms, estado_error(j))

    # ---- DB-03 consultas con tiempo (>= 3 consultas, 5 repeticiones cada una) ----
    print("\n| Consulta | Repeticiones | Mín (ms) | Prom (ms) | Máx (ms) | Bytes/resp |\n|---|---|---|---|---|---|")
    consultas = [
        ("GET por ID existente", "GET", f"{DOCS}/reportes/{rid}", None),
        ("runQuery ownerUid == mi uid", "POST", f"{DOCS}:runQuery",
         {"structuredQuery": {"from": [{"collectionId": "reportes"}],
                              "where": {"fieldFilter": {"field": {"fieldPath": "ownerUid"}, "op": "EQUAL", "value": {"stringValue": anon_uid}}}}}),
        ("GET por ID inexistente", "GET", f"{DOCS}/reportes/REP-NO-EXISTE-{sufijo}", None),
    ]
    codigos_consulta = {}
    for nombre, m, url, cuerpo in consultas:
        tiempos, tam, cod = [], 0, 0
        for _ in range(5):
            cod, ms, _, rb, _j = rest(m, url, cuerpo, anon_tok)
            tiempos.append(ms); tam = rb
        codigos_consulta[nombre] = cod
        RES.append(["DB-03", nombre, "", cod, f"{statistics.mean(tiempos):.0f}", "", f"min {min(tiempos):.0f} max {max(tiempos):.0f}"])
        print(f"| {nombre} (HTTP {cod}) | 5 | {min(tiempos):.0f} | {statistics.mean(tiempos):.0f} | {max(tiempos):.0f} | {tam} |")
    print()
    registrar("DB-03a", "Consulta por ID existente (dueño)", 200, codigos_consulta["GET por ID existente"], 0, "persistencia: el dato insertado se recupera")
    registrar("DB-03b", "Consulta con filtro por mi uid", 200, codigos_consulta["runQuery ownerUid == mi uid"], 0)
    registrar("DB-03c", "Consulta por ID inexistente (anónimo)", 403, codigos_consulta["GET por ID inexistente"], 0, "las reglas dan 403 (no 404): no se revela si el ID existe")
    c, ms, *_, j = rest("POST", f"{DOCS}:runQuery", {"structuredQuery": {"from": [{"collectionId": "reportes"}]}}, anon_tok)
    registrar("DB-03d", "Listar TODOS los reportes siendo celular anónimo", 403, c, ms, estado_error(j))

    # ---- DB-04 / DB-05 sin permisos ----
    c, ms, *_, j = rest("PATCH", f"{DOCS}/reportes/{rid}?updateMask.fieldPaths=estado", campos({"estado": "Resuelto"}), anon_tok)
    registrar("DB-04a", "Actualizar estado siendo celular (no soporte)", 403, c, ms, estado_error(j))
    c, ms, *_, j = rest("DELETE", f"{DOCS}/reportes/{rid}", None, anon_tok)
    registrar("DB-05a", "Eliminar siendo celular (no soporte)", 403, c, ms, estado_error(j))

    # ---- DB-04 / DB-05 / DB-06 con soporte ----
    if staff_tok and ok_ins:
        c, ms, *_, j = rest("PATCH", f"{DOCS}/reportes/{rid}?updateMask.fieldPaths=estado", campos({"estado": "En proceso"}), staff_tok)
        registrar("DB-04b", "Actualizar estado como soporte", 200, c, ms, estado_error(j))
        c, ms, *_, j = rest("GET", f"{DOCS}/reportes/{rid}", None, anon_tok)
        est = j.get("fields", {}).get("estado", {}).get("stringValue") if isinstance(j, dict) else None
        registrar("DB-04c", f"Consulta tras actualizar (estado leído: {est})", 200 if est == "En proceso" else -1, 200 if est == "En proceso" else c, ms)
        c, ms, *_, j = rest("PATCH", f"{DOCS}/reportes/{rid}?updateMask.fieldPaths=titulo", campos({"titulo": "Cambiado"}), staff_tok)
        registrar("DB-04d", "Soporte intenta cambiar el título (campo no editable)", 403, c, ms, estado_error(j))
        c, ms, *_, j = rest("GET", f"{DOCS}/reportes/REP-NO-EXISTE-{sufijo}", None, staff_tok)
        registrar("DB-03e", "Consulta por ID inexistente como soporte", 404, c, ms, estado_error(j))

        # concurrencia: dos escrituras simultáneas al mismo documento
        def escribir(est):
            return rest("PATCH", f"{DOCS}/reportes/{rid}?updateMask.fieldPaths=estado", campos({"estado": est}), staff_tok)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as ex:
            f1, f2 = ex.submit(escribir, "Resuelto"), ex.submit(escribir, "Nuevo")
            r1, r2 = f1.result(), f2.result()
        c3, _, *_, j3 = rest("GET", f"{DOCS}/reportes/{rid}", None, staff_tok)
        final = j3.get("fields", {}).get("estado", {}).get("stringValue")
        registrar("DB-06", f"2 escrituras simultáneas: HTTP {r1[0]} y {r2[0]}; valor final '{final}' (gana la última, sin error)", 200, r1[0] if r1[0] == r2[0] else -1, max(r1[1], r2[1]),
                  "Firestore no avisa del conflicto; para detectarlo hay que usar transacciones/precondición updateTime")
        # eliminación
        c, ms, *_, j = rest("DELETE", f"{DOCS}/reportes/{rid}", None, staff_tok)
        registrar("DB-05b", "Eliminar como soporte (solo rol admin; agente => 403)", 200, c, ms, estado_error(j))
        c, ms, *_, j = rest("GET", f"{DOCS}/reportes/{rid}", None, staff_tok)
        registrar("DB-05c", "Consulta tras eliminar (debe no existir)", 404, c, ms, estado_error(j))
    else:
        for id_, op in [("DB-04b", "Actualizar como soporte"), ("DB-05b", "Eliminar como soporte"), ("DB-06", "Concurrencia")]:
            omitida(id_, op, "falta --admin-email/--admin-pass")

    # ---- NET-03 / NET-04 subida y descarga de una foto de ~100 KB (colección progreso) ----
    foto = base64.b64encode(os.urandom(100_000)).decode()
    ruta_foto = f"{DOCS}/progreso/{anon_uid}/recetas/wb_foto"
    cuerpo_foto = campos({"nombre": "wb_foto", "hecha": True, "completados": ["1"], "actualizadoEn": int(time.time() * 1000), "fotoB64": foto})
    print("\n| Medición | Dirección | HTTP | Bytes | Tiempo (s) | Tasa (Mbps) |\n|---|---|---|---|---|---|")
    for i in range(1, 4):
        c, ms, enviados, _, j = rest("PATCH", ruta_foto, cuerpo_foto, anon_tok)
        mbps = enviados * 8 / (ms / 1000) / 1_000_000 if ms else 0
        print(f"| {i} | SUBIDA | {c} | {enviados} | {ms/1000:.3f} | {mbps:.3f} |")
        RES.append(["NET-04", f"subida #{i}", "", c, f"{ms:.0f}", "", f"{enviados} bytes; {mbps:.3f} Mbps"])
    for i in range(1, 4):
        c, ms, _, recibidos, j = rest("GET", ruta_foto, None, anon_tok)
        mbps = recibidos * 8 / (ms / 1000) / 1_000_000 if ms else 0
        print(f"| {i} | DESCARGA | {c} | {recibidos} | {ms/1000:.3f} | {mbps:.3f} |")
        RES.append(["NET-03", f"descarga #{i}", "", c, f"{ms:.0f}", "", f"{recibidos} bytes; {mbps:.3f} Mbps"])
    rest("DELETE", ruta_foto, None, anon_tok)

    with open("resultados_firestore.csv", "w", newline="", encoding="utf-8") as f:
        csv.writer(f).writerows([["id", "operacion", "esperado", "obtenido", "ms", "resultado", "detalle"]] + RES)
    fallas = [r for r in RES if r[5] == "FALLA"]
    print(f"\nGuardado: resultados_firestore.csv | Pruebas con resultado distinto al esperado: {len(fallas)}")
    for r in fallas:
        print("  - " + r[0] + ": " + r[1])
    print("Cada FALLA es un hallazgo: investiga si es un defecto de las reglas o una expectativa mal planteada y explícalo en el informe.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
