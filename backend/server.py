"""KitchenSync - servidor de la estación de cocina (solo librería estándar).

Mejoras respecto a la versión anterior:
  * El QR lo genera el servidor (token aleatorio, caduca en 10 min) y /conectar lo valida.
  * Todos los eventos exigen sessionId + token de la sesión vinculada.
  * Login (solo web) para admin y agente, con contraseñas PBKDF2. Los celulares solo usan el QR.
  * Chat de estaciones y reportes de soporte; el celular se identifica con la sesión del QR.
  * La estación se encuentra sola en la red (descubrimiento UDP): no hay que escribir la IP.
  * Panel de soporte: solo admin/agente ven los reportes (token Bearer).
  * Ya no se sirven archivos arbitrarios del disco.

Ejecutar:  py server.py      (variables opcionales: PORT, KITCHENSYNC_HOST,
           KITCHENSYNC_ADMIN_PASSWORD, KITCHENSYNC_AGENTE_PASSWORD)
"""
import hashlib
import hmac
import http.server
import ipaddress
import json
import os
import re
import secrets
import socket
import threading
import time

import s3_presign
import urllib.parse
from datetime import datetime

PORT = int(os.environ.get("PORT", "8080"))
QR_TTL = 600            # segundos de vida del QR sin vincular
AUTH_TTL = 2 * 3600     # vida del token de login
MAX_BODY = 16 * 1024
MAX_FALLOS, BLOQUEO = 5, 60

CATEGORIAS = ("Sincronización", "Receta", "QR / Vinculación", "Biometría", "Otro")
PRIORIDADES = ("Baja", "Media", "Alta")
ESTADOS = ("Pendiente", "En proceso", "Resuelto")

RECETAS = {
    "Pasta al pomodoro": {
        "Spaghetti artesanal": True, "Tomates maduros": True, "Ajo fresco": True,
        "Albahaca fresca": False, "Aceite de oliva extra virgen": False,
        "Sal de mar y pimienta": False},
    "Enchiladas Suizas": {
        "Tortillas de maíz": True, "Pollo cocido deshebrado": True,
        "Tomatillo verde": False, "Queso manchego rallado": False},
}
RECETA_POR_DEFECTO = "Pasta al pomodoro"


class ApiError(Exception):
    def __init__(self, status, mensaje):
        super().__init__(mensaje)
        self.status, self.mensaje = status, mensaje


def _normalizar_url_publica(valor):
    texto = (valor or "").strip()
    if not texto:
        return None
    if texto.startswith("//"):
        texto = "https:" + texto
    if "://" not in texto:
        texto = "https://" + texto
    parsed = urllib.parse.urlparse(texto)
    host = parsed.hostname
    if not host:
        return None
    try:
        ip = ipaddress.ip_address(host)
        if ip.is_loopback or ip.is_private or ip.is_link_local or ip.is_multicast or ip.is_unspecified:
            return None
    except ValueError:
        if host.lower() in {"localhost", "0.0.0.0"}:
            return None
    scheme = parsed.scheme or "https"
    port = parsed.port
    if port in (None, 80, 443):
        netloc = host
    else:
        netloc = f"{host}:{port}"
    return f"{scheme}://{netloc}"


def get_local_ip():
    for clave in ("KITCHENSYNC_HOST", "KITCHENSYNC_PUBLIC_URL", "RENDER_EXTERNAL_URL"):
        valor = os.environ.get(clave)
        if valor:
            normalizada = _normalizar_url_publica(valor)
            if normalizada:
                return normalizada
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))   # no envía datos; solo elige la interfaz de red
            return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"


def _hash(password, salt):
    return hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 120_000)


def _texto(valor, nombre, minimo, maximo):
    if not isinstance(valor, str) or not valor.strip():
        raise ApiError(400, f"{nombre} es obligatorio")
    valor = valor.strip()
    if len(valor) < minimo or len(valor) > maximo:
        raise ApiError(400, f"{nombre} debe tener entre {minimo} y {maximo} caracteres")
    return valor


def _opcional(valor, nombre, maximo):
    if valor is None or valor == "":
        return ""
    if not isinstance(valor, str):
        raise ApiError(400, f"{nombre} inválido")
    valor = valor.strip()
    if len(valor) > maximo:
        raise ApiError(400, f"{nombre} no puede pasar de {maximo} caracteres")
    return valor


def _validar_receta(d):
    """Receta enviada por un celular (propia o del catálogo): se valida todo antes de guardarla en el estado."""
    nombre = _texto(d.get("nombre"), "Nombre de la receta", 1, 60)
    ings = d.get("ingredientes")
    if not isinstance(ings, list) or not 1 <= len(ings) <= 40:
        raise ApiError(400, "La receta debe tener entre 1 y 40 ingredientes")
    lista, vistos = [], set()
    for i in ings:
        if not isinstance(i, dict):
            raise ApiError(400, "Ingrediente inválido")
        n = _texto(i.get("nombre"), "Ingrediente", 1, 80)
        if n.lower() in vistos:
            continue                      # nombres repetidos: el estado se guarda por nombre
        vistos.add(n.lower())
        lista.append({"nombre": n, "medida": _opcional(i.get("medida"), "Cantidad", 30), "listo": bool(i.get("listo", False))})
    pasos = d.get("pasos") or []
    if not isinstance(pasos, list) or len(pasos) > 15:
        raise ApiError(400, "Máximo 15 pasos")
    return {
        "nombre": nombre, "categoria": _opcional(d.get("categoria"), "Categoría", 60),
        "tiempo": _opcional(d.get("tiempo"), "Tiempo", 20), "dificultad": _opcional(d.get("dificultad"), "Dificultad", 30),
        "porciones": _opcional(d.get("porciones"), "Porciones", 20), "chefTip": _opcional(d.get("chefTip"), "Consejo", 300),
        "instruccion": _opcional(d.get("instruccion"), "Instrucciones", 800),
        "pasos": [_texto(x, "Paso", 1, 300) for x in pasos], "ingredientes": lista,
    }


def _entero(valor, nombre, minimo, maximo):
    try:
        n = int(valor)
    except (TypeError, ValueError):
        raise ApiError(400, f"{nombre} inválido")
    if not minimo <= n <= maximo:
        raise ApiError(400, f"{nombre} fuera de rango")
    return n


def _fmt_timer(seg):
    return f"{seg // 60:02d}:{seg % 60:02d}"


class Core:
    """Lógica de negocio; el handler HTTP solo traduce peticiones."""

    def __init__(self, base_url=None):
        self.lock = threading.RLock()
        self._base_fija = base_url
        self.usuarios = self._crear_usuarios()
        self.auth, self.fallos = {}, {}
        self.reportes, self.chat = [], []
        self.contador_reportes = 0
        self.reiniciar()

    @property
    def base_url(self):
        if self._base_fija:
            return self._base_fija
        for clave in ("KITCHENSYNC_HOST", "KITCHENSYNC_PUBLIC_URL", "RENDER_EXTERNAL_URL"):
            valor = os.environ.get(clave)
            if valor:
                normalizada = _normalizar_url_publica(valor)
                if normalizada:
                    return normalizada
        return f"http://{get_local_ip()}:{PORT}"

    # ---------- usuarios y login ----------
    @staticmethod
    def _crear_usuarios():
        # Solo el personal entra por la web. Los celulares NO tienen cuenta: entran únicamente con el QR.
        demo = [
            # Sin variable de entorno la contraseña es aleatoria: no existen credenciales conocidas en el código
            # (antes eran Admin2026! / Agente2026! y funcionaban en el servidor público).
            ("admin", "Chef Administrador", "admin", os.environ.get("KITCHENSYNC_ADMIN_PASSWORD") or secrets.token_urlsafe(18)),
            ("agente", "Agente de Soporte", "agente", os.environ.get("KITCHENSYNC_AGENTE_PASSWORD") or secrets.token_urlsafe(18)),
        ]
        usuarios = {}
        for usuario, nombre, rol, password in demo:
            salt = secrets.token_bytes(16)
            usuarios[usuario] = {"nombre": nombre, "rol": rol, "salt": salt, "hash": _hash(password, salt)}
        return usuarios

    def login(self, body):
        usuario = _texto(body.get("usuario"), "Usuario", 3, 30).lower()
        password = body.get("password")
        if not isinstance(password, str) or not 8 <= len(password) <= 64:
            raise ApiError(401, "Credenciales inválidas")
        with self.lock:
            fallos, hasta = self.fallos.get(usuario, (0, 0))
            if hasta > time.time():
                raise ApiError(429, "Demasiados intentos. Espera un minuto")
            cuenta = self.usuarios.get(usuario)
            # se calcula siempre el hash para no revelar si el usuario existe
            salt = cuenta["salt"] if cuenta else b"0" * 16
            ok = hmac.compare_digest(_hash(password, salt), cuenta["hash"] if cuenta else b"x" * 32)
            if not (cuenta and ok):
                fallos += 1
                self.fallos[usuario] = (fallos, time.time() + BLOQUEO if fallos >= MAX_FALLOS else 0)
                raise ApiError(401, "Credenciales inválidas")
            self.fallos.pop(usuario, None)
            token = secrets.token_urlsafe(24)
            self.auth[token] = {"usuario": usuario, "nombre": cuenta["nombre"], "rol": cuenta["rol"],
                                "exp": time.time() + AUTH_TTL}
            return {"token": token, "usuario": usuario, "nombre": cuenta["nombre"], "rol": cuenta["rol"]}

    def autenticar(self, bearer, roles=None):
        with self.lock:
            datos = self.auth.get(bearer or "")
            if not datos or datos["exp"] < time.time():
                self.auth.pop(bearer or "", None)
                raise ApiError(401, "Sesión no válida")
            if roles and datos["rol"] not in roles:
                raise ApiError(403, "No tienes permiso para esta operación")
            return datos

    def principal(self, bearer, datos, roles=None):
        """Quién hace la petición: personal con login (Bearer) o celular vinculado por QR."""
        if bearer:
            return self.autenticar(bearer, roles)
        datos = datos or {}
        with self.lock:
            if not self.estado["conectado"] or not self._token_ok(datos.get("sessionId"), datos.get("token")):
                raise ApiError(401, "Vincula tu celular con el QR")
            if roles:
                raise ApiError(403, "No tienes permiso para esta operación")
            disp = str(datos.get("dispositivo") or "Celular").strip()
            if not re.fullmatch(r"[\w .-]{1,30}", disp):
                disp = "Celular"
            return {"usuario": f"{disp}@{datos['sessionId']}", "nombre": f"📱 {disp}", "rol": "usuario"}

    def logout(self, bearer):
        with self.lock:
            self.auth.pop(bearer or "", None)
        return {"status": "ok"}

    # ---------- sesión QR ----------
    def reiniciar(self):
        with self.lock:
            self.estado = {
                "conectado": False, "sessionId": None,
                "receta": RECETA_POR_DEFECTO, "timer": "03:00", "timerSeconds": 180, "timerRunning": False,
                "ingredientes": dict(RECETAS[RECETA_POR_DEFECTO]), "sustituciones": [],
                "recetaDetalle": None, "rev": 0,
            }
            self.sesion = None
            self._nueva_sesion()

    def _nueva_sesion(self):
        self.sesion = {"sessionId": "station-" + secrets.token_hex(4),
                       "token": secrets.token_urlsafe(16), "creada": time.time(),
                       "panelKey": secrets.token_urlsafe(12)}

    def crear_sesion_qr(self, es_local=True, q=None):
        """QR de la sesión. Con la estación ya vinculada sigue sirviendo para INVITAR a otro celular, pero solo
        a quien tiene la llave de la pantalla (panelKey, que recibe la página de la estación antes de
        vincular), a la propia PC (localhost) o a un celular ya vinculado. Detrás de un proxy como Render el
        origen nunca es localhost, por eso existe la llave."""
        with self.lock:
            q = q or {}
            if not self.estado["conectado"] and time.time() - self.sesion["creada"] > QR_TTL:
                self._nueva_sesion()
            s = self.sesion
            if self.estado["conectado"]:
                llave = q.get("panelKey")
                es_panel = isinstance(llave, str) and hmac.compare_digest(llave, s["panelKey"])
                if not (es_local or es_panel or self._token_ok(q.get("sessionId"), q.get("token"))):
                    raise ApiError(409, "La estación ya está vinculada")
            qr = f"{self.base_url}/conectar?sessionId={s['sessionId']}&token={s['token']}"
            resp = {"sessionId": s["sessionId"], "qrUrl": qr, "conectado": self.estado["conectado"],
                    "expiraEn": int(max(QR_TTL - (time.time() - s["creada"]), 0))}
            if not self.estado["conectado"]:
                resp["panelKey"] = s["panelKey"]   # solo antes de vincular: la guarda la página de la estación
            return resp

    # ---------- Multimedia: URLs prefirmadas de S3 ----------
    def _sesion_en_consulta(self, q):
        sid, tok = q.get("sessionId"), q.get("token")
        if not sid or not tok:
            raise ApiError(401, "Vincula tu celular con el QR")
        with self.lock:
            if not (self.estado["conectado"] and self._token_ok(sid, tok)):
                raise ApiError(403, "Sesión inválida")

    def url_subida(self, q):
        """GET /api/upload-url: URL PUT firmada (caduca en 5 min). La foto viaja directo del celular a S3."""
        self._sesion_en_consulta(q)
        tipo = q.get("contentType", "image/jpeg")
        if tipo not in s3_presign.TIPOS_PERMITIDOS:
            raise ApiError(400, "Solo se permiten imágenes JPG, PNG o WEBP")
        cfg = s3_presign.S3Config.desde_entorno()
        if cfg is None:
            raise ApiError(503, "El almacenamiento S3 no está configurado en el servidor")
        return s3_presign.url_subida(cfg, tipo)

    def url_descarga(self, q):
        """GET /api/download-url?key=...: URL GET firmada (caduca en 1 h) para mostrar una foto privada."""
        self._sesion_en_consulta(q)
        clave = q.get("key", "")
        if not s3_presign.PATRON_CLAVE.match(clave):
            raise ApiError(400, "Clave de archivo inválida")
        cfg = s3_presign.S3Config.desde_entorno()
        if cfg is None:
            raise ApiError(503, "El almacenamiento S3 no está configurado en el servidor")
        return s3_presign.url_descarga(cfg, clave)

    def _token_ok(self, session_id, token):
        s = self.sesion
        return (isinstance(session_id, str) and isinstance(token, str)
                and hmac.compare_digest(session_id, s["sessionId"]) and hmac.compare_digest(token, s["token"]))

    def conectar(self, session_id, token):
        with self.lock:
            if not self._token_ok(session_id, token):
                raise ApiError(403, "QR inválido")
            if not self.estado["conectado"] and time.time() - self.sesion["creada"] > QR_TTL:
                raise ApiError(403, "El QR caducó, recarga la pantalla")
            self.estado["conectado"] = True
            self.estado["sessionId"] = session_id
            return {"status": "ok", "mensaje": "Celular Vinculado", "sessionId": session_id}

    def exigir_sesion(self, session_id, token):
        with self.lock:
            if not self.estado["conectado"] or not self._token_ok(session_id, token):
                raise ApiError(403, "Sesión no vinculada")

    def desvincular(self, session_id, token, bearer):
        with self.lock:
            es_admin = False
            try:
                self.autenticar(bearer, ("admin",))
                es_admin = True
            except ApiError:
                pass
            if not es_admin:
                self.exigir_sesion(session_id, token)
            self.reiniciar()
            return {"status": "reset"}

    def desvincular_panel(self, panel_key):
        with self.lock:
            if not isinstance(panel_key, str) or not hmac.compare_digest(panel_key, self.sesion["panelKey"]):
                raise ApiError(403, "La estación no está autorizada")
            self.reiniciar()
            return {"status": "reset"}

    def estado_publico(self):
        with self.lock:
            e = dict(self.estado)
            e["chat"] = list(self.chat[-5:])
            e["contadores"] = self._contadores()
            e["reportesPendientes"] = sum(1 for r in self.reportes if r["estado"] != "Resuelto")
            return e

    # ---------- eventos de cocina ----------
    def evento(self, p):
        self.exigir_sesion(p.get("sessionId"), p.get("token"))
        tipo = p.get("type")
        with self.lock:
            e = self.estado
            if tipo == "INGREDIENT_TOGGLE":
                nombre = _texto(p.get("ingredient"), "Ingrediente", 1, 80)
                norm = lambda s: s.lower().replace("extra virgen", "").strip()
                clave = next((k for k in e["ingredientes"] if norm(k) == norm(nombre)), None)
                if clave is not None:
                    e["ingredientes"][clave] = bool(p.get("completed"))
            elif tipo == "TIMER_TOGGLE":
                e["timerRunning"] = bool(p.get("running", False))
            elif tipo == "TIMER_TICK":
                self._set_timer(_entero(p.get("timerSeconds", e["timerSeconds"]), "Tiempo", 0, 86400))
            elif tipo == "TIMER_RESET":
                self._set_timer(180)
                e["timerRunning"] = False
            elif tipo == "CHANGE_RECIPE":
                detalle = p.get("recipe") if isinstance(p.get("recipe"), dict) else None
                if detalle is not None:
                    # el celular manda la receta completa: así la PC y los demás celulares ven EXACTAMENTE los mismos
                    # ingredientes (sin catálogos duplicados que se desincronizan) y se admiten recetas propias
                    det = _validar_receta(detalle)
                    e["receta"], e["recetaDetalle"] = det["nombre"], det
                    e["ingredientes"] = {i["nombre"]: i["listo"] for i in det["ingredientes"]}
                else:
                    nombre = _texto(p.get("recipeName") or p.get("recipe"), "Receta", 1, 60)
                    if nombre not in RECETAS:
                        raise ApiError(404, "Receta no encontrada")
                    e["receta"], e["ingredientes"], e["recetaDetalle"] = nombre, dict(RECETAS[nombre]), None
            elif tipo == "SUSTITUCION":
                original = _texto(p.get("original"), "Original", 1, 60)
                sustituto = _texto(p.get("sustituto"), "Sustituto", 1, 60)
                e["sustituciones"].append({
                    "original": original, "sustituto": sustituto,
                    "factorCantidad": p.get("factorCantidad", 1.0),
                    "impactoSaborPct": _entero(p.get("impactoSaborPct", 0), "Impacto sabor", 0, 100),
                    "impactoTexturaPct": _entero(p.get("impactoTexturaPct", 0), "Impacto textura", 0, 100),
                    "nota": str(p.get("nota", "Reemplazo autorizado"))[:200]})
                del e["sustituciones"][:-20]
            else:
                raise ApiError(400, "Tipo de evento desconocido")
            e["rev"] = e.get("rev", 0) + 1       # versión del estado: los celulares ignoran lecturas más viejas que su último cambio
            return {"status": "ok", "event": tipo, "estado": self.estado_publico()}

    def _set_timer(self, seg):
        self.estado["timerSeconds"], self.estado["timer"] = seg, _fmt_timer(seg)

    # ---------- chat ----------
    def _contadores(self):
        c = {}
        for m in self.chat:
            c[m["emisor"]] = c.get(m["emisor"], 0) + 1
        return c

    def enviar_chat(self, bearer, body):
        u = self.principal(bearer, body)
        texto = _texto(body.get("texto"), "El mensaje", 1, 280)
        with self.lock:
            msg = {"emisor": u["nombre"], "rol": u["rol"], "texto": texto,
                   "hora": datetime.now().strftime("%H:%M:%S")}
            self.chat.append(msg)
            del self.chat[:-200]
            return {"status": "ok", "mensaje": msg, "contadores": self._contadores()}

    def leer_chat(self, bearer, q=None):
        self.principal(bearer, q)
        with self.lock:
            return {"mensajes": list(self.chat), "contadores": self._contadores()}

    def limpiar_chat(self, bearer):
        self.autenticar(bearer, ("admin", "agente"))
        with self.lock:
            self.chat.clear()
            return {"status": "ok"}

    # ---------- soporte / reportes ----------
    def reportar(self, bearer, body):
        u = self.principal(bearer, body)
        titulo = _texto(body.get("titulo"), "El título", 3, 80)
        descripcion = _texto(body.get("descripcion"), "La descripción", 5, 500)
        categoria = body.get("categoria", "Otro")
        prioridad = body.get("prioridad", "Media")
        if categoria not in CATEGORIAS:
            raise ApiError(400, "Categoría inválida")
        if prioridad not in PRIORIDADES:
            raise ApiError(400, "Prioridad inválida")
        with self.lock:
            rep_id = body.get("id")
            if rep_id:
                if not isinstance(rep_id, str) or not re.fullmatch(r"[A-Za-z0-9-]{3,20}", rep_id):
                    raise ApiError(400, "Identificador inválido")
                if any(r["id"] == rep_id for r in self.reportes):
                    raise ApiError(409, "Identificador repetido")
            else:
                self.contador_reportes += 1
                rep_id = f"REP-{self.contador_reportes:03d}"
                while any(r["id"] == rep_id for r in self.reportes):
                    self.contador_reportes += 1
                    rep_id = f"REP-{self.contador_reportes:03d}"
            rep = {"id": rep_id, "titulo": titulo, "descripcion": descripcion, "categoria": categoria,
                   "prioridad": prioridad, "estado": "Pendiente", "creadoPor": u["usuario"],
                   "nombre": u["nombre"], "creado": datetime.now().isoformat(timespec="seconds"),
                   "asignadoA": None, "notas": []}
            self.reportes.append(rep)
            return {"status": "guardado", "reporte": rep}

    def mis_reportes(self, bearer, q=None):
        u = self.principal(bearer, q)
        with self.lock:
            return {"reportes": [r for r in self.reportes if r["creadoPor"] == u["usuario"]]}

    def listar_reportes(self, bearer):
        u = self.autenticar(bearer, ("admin", "agente"))
        with self.lock:
            lista = self.reportes if u["rol"] == "admin" else [r for r in self.reportes if r["asignadoA"] == u["usuario"]]
            return {"reportes": list(lista), "agentes": [k for k, v in self.usuarios.items() if v["rol"] == "agente"]}

    def _buscar(self, rep_id):
        rep = next((r for r in self.reportes if r["id"] == rep_id), None)
        if rep is None:
            raise ApiError(404, "Reporte no encontrado")
        return rep

    def asignar(self, bearer, body):
        self.autenticar(bearer, ("admin",))
        with self.lock:
            rep = self._buscar(body.get("id"))
            agente = body.get("agente")
            if agente not in self.usuarios or self.usuarios[agente]["rol"] != "agente":
                raise ApiError(400, "Agente inválido")
            rep["asignadoA"] = agente
            if rep["estado"] == "Pendiente":
                rep["estado"] = "En proceso"
            return {"status": "ok", "reporte": rep}

    def cambiar_estado(self, bearer, body):
        u = self.autenticar(bearer, ("admin", "agente"))
        with self.lock:
            rep = self._buscar(body.get("id"))
            if u["rol"] == "agente" and rep["asignadoA"] != u["usuario"]:
                raise ApiError(403, "Este caso no está asignado a ti")
            if body.get("estado") not in ESTADOS:
                raise ApiError(400, "Estado inválido")
            rep["estado"] = body["estado"]
            nota = body.get("nota")
            if nota:
                rep["notas"].append({"autor": u["nombre"], "texto": _texto(nota, "La nota", 1, 300),
                                     "hora": datetime.now().strftime("%H:%M:%S")})
            return {"status": "ok", "reporte": rep}


CORE = Core()

DISCOVERY_PORT = 48555
DISCOVERY_PREGUNTA = b"KITCHENSYNC?"


def responder_descubrimiento(datos, ip_local):
    """Respuesta al broadcast del celular: 'KITCHENSYNC|http://ip:puerto' (o None si no es nuestra pregunta)."""
    if datos.strip() != DISCOVERY_PREGUNTA:
        return None
    return f"KITCHENSYNC|http://{ip_local}:{PORT}".encode("utf-8")


def _ip_hacia(destino):
    for clave in ("KITCHENSYNC_HOST", "KITCHENSYNC_PUBLIC_URL", "RENDER_EXTERNAL_URL"):
        valor = os.environ.get(clave)
        if valor:
            normalizada = _normalizar_url_publica(valor)
            if normalizada:
                return urllib.parse.urlparse(normalizada).hostname or valor
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect((destino, 9))
            return s.getsockname()[0]
    except OSError:
        return get_local_ip()


def servir_descubrimiento():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind(("", DISCOVERY_PORT))
    except OSError:
        print(" Aviso: no se pudo abrir el descubrimiento automático (UDP %d)." % DISCOVERY_PORT)
        return
    while True:
        try:
            datos, origen = s.recvfrom(256)
            resp = responder_descubrimiento(datos, _ip_hacia(origen[0]))
            if resp:
                s.sendto(resp, origen)
        except OSError:
            continue


# Archivos de la PWA: solo estos (lista cerrada): ninguna otra ruta del disco se puede pedir
ESTATICOS = {
    "/manifest.webmanifest": ("static/manifest.webmanifest", "application/manifest+json; charset=utf-8", "public, max-age=3600"),
    "/sw.js": ("static/sw.js", "text/javascript; charset=utf-8", "no-cache"),
    "/offline.html": ("static/offline.html", "text/html; charset=utf-8", "no-cache"),
    "/icons/icon-192.png": ("static/icons/icon-192.png", "image/png", "public, max-age=86400"),
    "/icons/icon-512.png": ("static/icons/icon-512.png", "image/png", "public, max-age=86400"),
    "/icons/maskable-512.png": ("static/icons/maskable-512.png", "image/png", "public, max-age=86400"),
}


class Handler(http.server.BaseHTTPRequestHandler):
    server_version = "KitchenSync"
    timeout = 15   # un cliente lento o mal portado no puede retener un hilo para siempre

    def _json(self, status, payload):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        try:
            self.wfile.write(data)
        except (ConnectionError, BrokenPipeError):
            pass

    def _bearer(self):
        cab = self.headers.get("Authorization", "")
        return cab[7:] if cab.startswith("Bearer ") else None

    def _body(self):
        try:
            n = int(self.headers.get("Content-Length") or 0)
        except ValueError:                      # antes: 500 con "abc"
            raise ApiError(400, "Content-Length inválido")
        if n < 0:                               # antes: read(-1) dejaba el hilo colgado
            raise ApiError(400, "Content-Length inválido")
        if n > MAX_BODY:
            raise ApiError(413, "Petición demasiado grande")
        try:
            body = json.loads(self.rfile.read(n).decode("utf-8") or "{}") if n else {}
        except (ValueError, UnicodeDecodeError):
            raise ApiError(400, "JSON inválido")
        if not isinstance(body, dict):
            raise ApiError(400, "JSON inválido")
        return body

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()

    def do_GET(self):
        self._dispatch("GET")

    def _metodo_no_permitido(self):
        # Antes PUT/DELETE/PATCH cerraban la conexión sin respuesta (TypeError en log_message)
        self._json(405, {"error": "Método no permitido"})

    do_PUT = do_DELETE = do_PATCH = _metodo_no_permitido

    def do_POST(self):
        self._dispatch("POST")

    def _dispatch(self, metodo):
        url = urllib.parse.urlparse(self.path)
        q = {k: v[0] for k, v in urllib.parse.parse_qs(url.query).items()}
        ruta, c, b = url.path, CORE, self._bearer()
        try:
            if metodo == "GET":
                if ruta in ("/", "/index.html"):
                    return self._index()
                if ruta in ESTATICOS:
                    return self._estatico(ruta)
                respuestas = {
                    "/estado": lambda: c.estado_publico(),
                    "/sesion": lambda: c.crear_sesion_qr(self.client_address[0] in ("127.0.0.1", "::1"), q),
                    "/conectar": lambda: c.conectar(q.get("sessionId"), q.get("token")),
                    "/chat": lambda: c.leer_chat(b, q),
                    "/mis_reportes": lambda: c.mis_reportes(b, q),
                    "/api/upload-url": lambda: c.url_subida(q),
                    "/api/download-url": lambda: c.url_descarga(q),
                    "/admin/reportes": lambda: c.listar_reportes(b),
                }
            else:
                body = self._body()
                respuestas = {
                    "/evento": lambda: c.evento(body),
                    "/login": lambda: c.login(body),
                    "/logout": lambda: c.logout(b),
                    "/chat": lambda: c.enviar_chat(b, body),
                    "/chat/limpiar": lambda: c.limpiar_chat(b),
                    "/reportar": lambda: c.reportar(b, body),
                    "/admin/asignar": lambda: c.asignar(b, body),
                    "/admin/estado": lambda: c.cambiar_estado(b, body),
                    "/reset": lambda: c.desvincular_panel(body.get("panelKey")) if body.get("panelKey") else c.desvincular(body.get("sessionId"), body.get("token"), b),
                }
            if ruta not in respuestas:
                raise ApiError(404, "Endpoint no encontrado")
            self._json(200, respuestas[ruta]())
        except ApiError as err:
            self._json(err.status, {"error": err.mensaje})
        except Exception:  # nunca se filtran detalles técnicos al cliente
            self._json(500, {"error": "Error interno del servidor"})

    def _estatico(self, ruta):
        relativa, tipo, cache = ESTATICOS[ruta]
        try:
            with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), relativa), "rb") as f:
                data = f.read()
        except OSError:
            raise ApiError(404, "Recurso no encontrado")
        self.send_response(200)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", cache)
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(data)

    def _index(self):
        ruta = os.path.join(os.path.dirname(os.path.abspath(__file__)), "index.html")
        with open(ruta, "r", encoding="utf-8") as f:
            html = f.read()
        host, _, port = CORE.base_url.split("//", 1)[1].partition(":")
        data = html.replace("__SERVER_HOST__", host).replace("__SERVER_PORT__", port).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        if args and ("/estado" in str(args[0]) or "/chat" in str(args[0])):
            return
        # no se registran query strings: contienen el token del QR
        # solo se limpian los textos: send_error() pasa códigos numéricos que el formato %d exige como int
        super().log_message(fmt, *[a.split("?")[0] if isinstance(a, str) else a for a in args])


def main():
    http.server.ThreadingHTTPServer.allow_reuse_address = True
    # Cola de conexiones pendientes: con el valor por defecto (5) y 10 usuarios simultáneos algunos SYN se descartan
    # y el cliente reintenta 1 s después (P95 de ~1000 ms medido en la auditoría, defecto D-04).
    http.server.ThreadingHTTPServer.request_queue_size = 128
    try:
        httpd = http.server.ThreadingHTTPServer(("", PORT), Handler)
    except OSError:
        print(f"El puerto {PORT} está ocupado. Cierra el proceso anterior (netstat -ano | findstr :{PORT}).")
        raise SystemExit(1)
    print("=" * 50)
    print(f" KitchenSync activo en  http://localhost:{PORT}")
    print(f" Dirección para el celular: {CORE.base_url}")
    print(" Web (personal): admin / agente  -> botón Soporte")
    print(" Celulares: solo con el QR. Se encuentran solos en la misma red (UDP %d)." % DISCOVERY_PORT)
    print(" Si Windows pregunta, permite Python en redes privadas (TCP %d y UDP %d)." % (PORT, DISCOVERY_PORT))
    print("=" * 50)
    threading.Thread(target=servir_descubrimiento, daemon=True).start()
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nServidor detenido.")


if __name__ == "__main__":
    main()
