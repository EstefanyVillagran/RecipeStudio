"""
Práctica 11 - Fase 5 y Fase 3 (servidor). Mide conectividad, latencia HTTP, tasa de descarga, códigos HTTP y carga.
Solo usa la librería estándar de Python (no hay que instalar nada).

Uso básico (contra tu servidor en Render):
    python pruebas_whitebox/medir_conectividad.py --conexion "WiFi casa" --dispositivo "Laptop Windows 11"

Contra un servidor local:
    python pruebas_whitebox/medir_conectividad.py --url http://127.0.0.1:8080

IMPORTANTE: los números salen de TU ejecución real. No los edites: pégalos tal cual en el informe.
Si el servidor de Render estaba dormido, la 1a petición tarda mucho (arranque en frío): el script la mide APARTE.
"""
import argparse
import concurrent.futures
import csv
import json
import math
import platform
import re
import socket
import ssl
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime

RESULTADOS = []   # filas para el CSV


def http(metodo, url, cuerpo=None, timeout=60, cabeceras=None):
    """Devuelve (codigo, milisegundos, bytes_recibidos, cuerpo). codigo 0 = sin conexión."""
    datos = json.dumps(cuerpo).encode() if isinstance(cuerpo, (dict, list)) else cuerpo
    req = urllib.request.Request(url, data=datos, method=metodo)
    if isinstance(cuerpo, (dict, list)):
        req.add_header("Content-Type", "application/json")
    for k, v in (cabeceras or {}).items():
        req.add_header(k, v)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            body = r.read()
            return r.status, (time.perf_counter() - t0) * 1000, len(body), body
    except urllib.error.HTTPError as e:
        body = e.read()
        return e.code, (time.perf_counter() - t0) * 1000, len(body), body
    except Exception as e:                      # sin red, DNS, timeout...
        return 0, (time.perf_counter() - t0) * 1000, 0, str(e).encode()


def percentil(valores, p):
    """Percentil por rango más cercano (nearest-rank)."""
    v = sorted(valores)
    return v[max(0, math.ceil(p / 100 * len(v)) - 1)]


def resumen(valores):
    return {"min": min(valores), "prom": statistics.mean(valores), "max": max(valores),
            "desv": statistics.pstdev(valores) if len(valores) > 1 else 0.0, "p95": percentil(valores, 95)}


def tabla(titulo, cabecera, filas):
    print(f"\n### {titulo}")
    print("| " + " | ".join(cabecera) + " |")
    print("|" + "|".join("---" for _ in cabecera) + "|")
    for f in filas:
        print("| " + " | ".join(str(x) for x in f) + " |")


# ------------------------------------------------------------------ A. ping
def prueba_ping(host, n=20):
    print(f"\n## A. Ping ICMP a {host} ({n} paquetes)")
    es_win = platform.system() == "Windows"
    cmd = ["ping", "-n" if es_win else "-c", str(n), host]
    try:
        salida = subprocess.run(cmd, capture_output=True, text=True, timeout=90, errors="replace").stdout
    except (FileNotFoundError, subprocess.TimeoutExpired) as e:
        print(f"  No se pudo ejecutar ping ({e}). Anota la limitación y usa solo las mediciones HTTP.")
        return
    tiempos = [float(x.replace(",", ".")) for x in re.findall(r"(?:time|tiempo)\s*[=<]\s*([\d.,]+)\s*ms", salida, re.I)]
    perdida = re.search(r"(\d+(?:\.\d+)?)%\s*(?:packet loss|loss|perdidos|de pérdida)", salida, re.I)
    if not tiempos:
        print("  Sin respuestas. Muchos servidores (Render incluido) bloquean ICMP: REGÍSTRALO como limitación de la prueba")
        print("  y no lo confundas con el tiempo de respuesta de la API (ver sección B).")
        print("  Salida del comando:\n" + salida[-600:])
        RESULTADOS.append(["NET-01", "ping", "bloqueado/sin respuesta", "", "", "", ""])
        return
    r = resumen(tiempos)
    pct = perdida.group(1) if perdida else "?"
    tabla("Resultado del ping", ["Mín (ms)", "Prom (ms)", "Máx (ms)", "Desv. est. (ms)", "Pérdida (%)", "Muestras"],
          [[f"{r['min']:.1f}", f"{r['prom']:.1f}", f"{r['max']:.1f}", f"{r['desv']:.1f}", pct, len(tiempos)]])
    RESULTADOS.append(["NET-01", "ping", f"{r['min']:.1f}", f"{r['prom']:.1f}", f"{r['max']:.1f}", f"{r['desv']:.1f}", pct])


# ------------------------------------------------------------------ B. latencia HTTP (10 solicitudes)
def prueba_latencia(base, ruta="/estado", n=10):
    print(f"\n## B. Latencia HTTP: {n} solicitudes consecutivas a GET {ruta}")
    c0, t0, b0, _ = http("GET", base + ruta)
    print(f"  Solicitud de calentamiento (no cuenta): HTTP {c0}, {t0:.0f} ms  <- si es muy lenta, el servidor estaba dormido")
    filas, tiempos, tamanos = [], [], []
    for i in range(1, n + 1):
        c, t, b, _ = http("GET", base + ruta)
        filas.append([i, c, f"{t:.1f}", b])
        tiempos.append(t)
        tamanos.append(b)
    tabla("Tabla B (una fila por solicitud)", ["Solicitud", "Código HTTP", "Tiempo (ms)", "Tamaño (bytes)"], filas)
    r = resumen(tiempos)
    exitos = sum(1 for f in filas if f[1] == 200)
    tabla("Resumen de latencia", ["Mín", "Prom", "Máx", "Desv. est.", "P95", "Exitosas", "Tamaño prom (bytes)"],
          [[f"{r['min']:.1f}", f"{r['prom']:.1f}", f"{r['max']:.1f}", f"{r['desv']:.1f}", f"{r['p95']:.1f}",
            f"{exitos}/{n}", f"{statistics.mean(tamanos):.0f}"]])
    print(f"  Cada solicitud abre una conexión nueva (incluye DNS + TLS): es el costo real que ve la app. Objetivo orientativo: prom <= 500 ms.")
    RESULTADOS.append(["NET-02", f"latencia {ruta}", f"{r['min']:.1f}", f"{r['prom']:.1f}", f"{r['max']:.1f}", f"{r['desv']:.1f}", f"{exitos}/{n}"])


# ------------------------------------------------------------------ C. tasa de descarga
def prueba_descarga(base, ruta="/", n=3):
    print(f"\n## C. Tasa de descarga: {n} mediciones de GET {ruta}")
    print("  Fórmula: Mbps = (bytes x 8) / (segundos x 1.000.000)")
    filas = []
    for i in range(1, n + 1):
        c, t, b, _ = http("GET", base + ruta)
        seg = t / 1000
        mbps = (b * 8) / (seg * 1_000_000) if seg > 0 else 0
        filas.append([i, c, b, f"{seg:.3f}", f"{mbps:.3f}"])
        RESULTADOS.append(["NET-03", f"descarga {ruta} #{i}", b, f"{seg:.3f}", f"{mbps:.3f}", "", ""])
    tabla("Descarga", ["Medición", "HTTP", "Bytes", "Tiempo (s)", "Tasa (Mbps)"], filas)
    print("  Nota: el archivo es pequeño, así que la tasa efectiva está dominada por la latencia y NO es el ancho de banda del enlace.")
    print("  La SUBIDA se mide con medir_firestore.py (foto de ~100 KB); este servidor solo recibe JSON de pocos bytes.")


# ------------------------------------------------------------------ D. códigos HTTP
def socket_crudo(base, peticion):
    u = urllib.parse.urlparse(base)
    puerto = u.port or (443 if u.scheme == "https" else 80)
    s = socket.create_connection((u.hostname, puerto), timeout=10)
    if u.scheme == "https":
        s = ssl.create_default_context().wrap_socket(s, server_hostname=u.hostname)
    try:
        s.sendall(peticion.replace("{host}", u.hostname).encode())
        return s.recv(200).split(b"\r\n")[0].decode(errors="replace")
    except socket.timeout:
        return "SIN RESPUESTA (timeout)"
    finally:
        s.close()


def prueba_codigos(base):
    print("\n## D. Códigos HTTP y estructura de las respuestas (WEB-01 a WEB-06)")
    casos = [
        ("WEB-01", "Consulta exitosa", "GET", "/estado", None, 200),
        ("WEB-02a", "Recurso inexistente", "GET", "/noexiste", None, 404),
        ("WEB-02b", "JSON mal formado", "POST", "/evento", b"{no es json", 400),
        ("WEB-02c", "Método no permitido", "PUT", "/estado", None, 405),
        ("WEB-02d", "Cuerpo demasiado grande (20 KB)", "POST", "/evento", b'{"x":"' + b"a" * 20000 + b'"}', 413),
        ("WEB-03", "Validación: usuario vacío", "POST", "/login", {"usuario": "   ", "password": "12345678"}, 400),
        ("WEB-04a", "Sin credenciales (no autenticado)", "GET", "/admin/reportes", None, 401),
        ("WEB-04b", "Login con credenciales malas", "POST", "/login", {"usuario": "admin", "password": "incorrecta123"}, 401),
        ("WEB-04c", "Sesión QR falsa (autenticado sin permiso)", "POST", "/evento", {"sessionId": "x", "token": "y", "type": "TIMER_RESET"}, 403),
        ("WEB-04d", "Chat sin vincular", "GET", "/chat", None, 401),
    ]
    filas, fallos = [], 0
    for id_, desc, m, ruta, cuerpo, esperado in casos:
        c, t, b, body = http(m, base + ruta, cuerpo)
        try:
            data = json.loads(body.decode())
            json_ok = isinstance(data, dict) and (c < 400 or "error" in data)
        except Exception:
            json_ok = False
        ok = c == esperado
        fallos += 0 if ok else 1
        filas.append([id_, desc, esperado, c, "✔" if ok else "✘", "✔" if json_ok else "✘", f"{t:.0f}", b])
        RESULTADOS.append([id_, desc, esperado, c, "OK" if ok else "FALLA", "", ""])
    linea = socket_crudo(base, "POST /evento HTTP/1.1\r\nHost: {host}\r\nContent-Length: abc\r\nConnection: close\r\n\r\n")
    ok = "400" in linea
    fallos += 0 if ok else 1
    filas.append(["WEB-03b", "Content-Length no numérico", 400, linea.split(" ")[1] if " " in linea else linea, "✔" if ok else "✘", "-", "-", "-"])
    tabla("Matriz de códigos", ["ID", "Situación", "Esperado", "Obtenido", "Código", "JSON {error}", "ms", "bytes"], filas)
    c, t, b, body = http("GET", base + "/estado")
    texto = body.decode(errors="replace").lower()
    filtrados = [p for p in ("password", "token", "hash", "salt", "secret") if p in texto]
    print(f"\n  Datos sensibles en GET /estado (password/token/hash/salt/secret): {'NINGUNO ✔' if not filtrados else 'ENCONTRADO ✘ -> ' + str(filtrados)}")
    print(f"  Casos que no coinciden con lo esperado: {fallos}  (cada ✘ es un defecto candidato para la Fase 6)")


# ------------------------------------------------------------------ E. carga controlada
def crear_sesion(base):
    c, _, _, body = http("GET", base + "/sesion")
    if c != 200:
        return None
    d = json.loads(body)
    qs = urllib.parse.parse_qs(urllib.parse.urlparse(d["qrUrl"]).query)
    sid, tok = qs["sessionId"][0], qs["token"][0]
    c2, _, _, _ = http("GET", f"{base}/conectar?sessionId={sid}&token={tok}")
    return (sid, tok) if c2 == 200 else None


def escenario(base, concurrentes, total, construir):
    tiempos, codigos, bytes_total = [], [], 0
    def una(i):
        metodo, ruta, cuerpo = construir(i)
        return http(metodo, base + ruta, cuerpo)
    t0 = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrentes) as ex:
        for c, t, b, _ in ex.map(una, range(total)):
            tiempos.append(t); codigos.append(c); bytes_total += b
    pared = time.perf_counter() - t0
    r = resumen(tiempos)
    ok = sum(1 for c in codigos if 200 <= c < 300)
    mbps = (bytes_total * 8) / (pared * 1_000_000) if pared > 0 else 0
    return [concurrentes, total, f"{r['prom']:.0f}", f"{r['p95']:.0f}", f"{r['max']:.0f}", ok, total - ok, f"{mbps:.3f}", f"{total / pared:.1f}"]


def prueba_carga(base, total=20):
    print(f"\n## E. Carga controlada: {total} solicitudes por escenario con 1, 5 y 10 usuarios concurrentes")
    print("  Usa SOLO tu servidor de pruebas. Datos no sensibles. (CPU y memoria: míralos en el panel 'Metrics' de Render.)")
    cab = ["Usuarios", "Solicitudes", "Prom (ms)", "P95 (ms)", "Máx (ms)", "Exitosas", "Fallidas", "Tasa (Mbps)", "Solicitudes/s"]
    lectura = [escenario(base, u, total, lambda i: ("GET", "/estado", None)) for u in (1, 5, 10)]
    tabla("Endpoint de LECTURA: GET /estado", cab, lectura)
    sesion = crear_sesion(base)
    if not sesion:
        print("\n  No se pudo crear una sesión de prueba (la estación ya está vinculada?). Desvincula el celular y vuelve a correr,")
        print("  o justifica en el informe por qué no se midió el endpoint de escritura.")
        return
    sid, tok = sesion
    escritura = [escenario(base, u, total, lambda i: ("POST", "/chat", {"sessionId": sid, "token": tok, "dispositivo": "PruebaCarga", "texto": f"carga {i}"})) for u in (1, 5, 10)]
    tabla("Endpoint de ESCRITURA: POST /chat", cab, escritura)
    http("POST", base + "/chat/limpiar", {"sessionId": sid, "token": tok})
    http("POST", base + "/reset", {"sessionId": sid, "token": tok})        # deja la estación como estaba
    for fila in lectura:
        RESULTADOS.append(["NET-07", "carga lectura", *fila[:4], f"{fila[5]}/{fila[1]}", ""])
    for fila in escritura:
        RESULTADOS.append(["NET-07", "carga escritura", *fila[:4], f"{fila[5]}/{fila[1]}", ""])


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--url", default="https://recipestudio.onrender.com")
    ap.add_argument("--conexion", default="(escribe: WiFi / datos móviles)")
    ap.add_argument("--dispositivo", default="(escribe el equipo desde el que mides)")
    ap.add_argument("--sin-carga", action="store_true", help="omitir la prueba de carga")
    ap.add_argument("--sin-ping", action="store_true")
    a = ap.parse_args()
    base = a.url.rstrip("/")
    host = urllib.parse.urlparse(base).hostname
    print("# Mediciones de conectividad - RecipeStudio")
    print(f"- Fecha: {datetime.now():%Y-%m-%d %H:%M:%S}\n- Servidor: {base}\n- Cliente: {a.dispositivo} ({platform.system()} {platform.release()}, Python {platform.python_version()})")
    print(f"- Tipo de conexión: {a.conexion}")
    if not a.sin_ping:
        prueba_ping(host)
    prueba_latencia(base)
    prueba_descarga(base)
    prueba_codigos(base)
    if not a.sin_carga:
        prueba_carga(base)
    with open("resultados_conectividad.csv", "w", newline="", encoding="utf-8") as f:
        csv.writer(f).writerows([["id", "prueba", "v1", "v2", "v3", "v4", "v5"]] + RESULTADOS)
    print("\nGuardado: resultados_conectividad.csv  |  Copia estas tablas al informe y COMPÁRALAS con los objetivos de la práctica.")


if __name__ == "__main__":
    sys.exit(main())
