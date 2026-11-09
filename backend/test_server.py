"""Pruebas de caja negra del servidor (Práctica 8). Ejecutar:  python -m unittest test_server -v"""
import http.server
import json
import os
import threading
import unittest
import urllib.error
import urllib.request

import server


class Base(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.httpd = http.server.ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        cls.url = f"http://127.0.0.1:{cls.httpd.server_address[1]}"
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()

    def setUp(self):
        server.CORE.__init__(base_url=self.url)

    def call(self, metodo, ruta, cuerpo=None, token=None):
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
        req = urllib.request.Request(self.url + ruta, data=datos, method=metodo)
        req.add_header("Content-Type", "application/json")
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read())

    def login(self, usuario, password=None):
        pw = password or {"admin": "Admin2026!", "agente": "Agente2026!"}.get(usuario, "Cocina2026!")
        return self.call("POST", "/login", {"usuario": usuario, "password": pw})[1]

    def celular(self, nombre="Pixel 7"):
        """Vincula por QR y devuelve los datos que el celular envía en cada petición."""
        sid, tok = self.vincular()
        return {"sessionId": sid, "token": tok, "dispositivo": nombre}

    def vincular(self):
        s = self.call("GET", "/sesion")[1]
        qs = s["qrUrl"].split("?")[1]
        sid, tok = [p.split("=")[1] for p in qs.split("&")]
        self.call("GET", f"/conectar?sessionId={sid}&token={tok}")
        return sid, tok


class TestVinculacion(Base):
    def test_req01_qr_valido_vincula(self):
        sid, tok = self.vincular()
        self.assertTrue(self.call("GET", "/estado")[1]["conectado"])

    def test_req02_qr_con_token_falso_se_rechaza(self):
        s = self.call("GET", "/sesion")[1]
        self.assertEqual(self.call("GET", f"/conectar?sessionId={s['sessionId']}&token=falso")[0], 403)
        self.assertFalse(self.call("GET", "/estado")[1]["conectado"])

    def test_estado_no_expone_token(self):
        self.vincular()
        self.assertNotIn("token", self.call("GET", "/estado")[1])

    def test_sesion_ya_vinculada_permite_invitar_otro_celular_desde_la_pc(self):
        sid, tok = self.vincular()
        r = self.call("GET", "/sesion")          # la PC (localhost) puede mostrar el QR de invitación
        self.assertEqual(r[0], 200)
        self.assertEqual(r[1]["sessionId"], sid)
        # el segundo celular se une con el mismo QR y la estación sigue vinculada
        self.assertEqual(self.call("GET", f"/conectar?sessionId={sid}&token={tok}")[0], 200)
        self.assertTrue(self.call("GET", "/estado")[1]["conectado"])

    def test_sesion_ya_vinculada_no_entrega_el_qr_a_extranos_de_la_red(self):
        self.vincular()
        with self.assertRaises(server.ApiError) as ctx:
            server.CORE.crear_sesion_qr(es_local=False, q={})
        self.assertEqual(ctx.exception.status, 409)


class TestEventos(Base):
    def test_req03_tachar_ingrediente_se_refleja(self):
        sid, tok = self.vincular()
        c, _ = self.call("POST", "/evento", {"type": "INGREDIENT_TOGGLE", "sessionId": sid, "token": tok,
                                              "ingredient": "Albahaca fresca", "completed": True})
        self.assertEqual(c, 200)
        self.assertTrue(self.call("GET", "/estado")[1]["ingredientes"]["Albahaca fresca"])

    def test_req04_evento_sin_token_rechazado(self):
        self.vincular()
        c, _ = self.call("POST", "/evento", {"type": "TIMER_TOGGLE", "running": True})
        self.assertEqual(c, 403)
        self.assertFalse(self.call("GET", "/estado")[1]["timerRunning"])

    def test_req05_timer_valida_rango(self):
        sid, tok = self.vincular()
        c, _ = self.call("POST", "/evento", {"type": "TIMER_TICK", "sessionId": sid, "token": tok, "timerSeconds": "abc"})
        self.assertEqual(c, 400)
        self.call("POST", "/evento", {"type": "TIMER_TICK", "sessionId": sid, "token": tok, "timerSeconds": 95})
        self.assertEqual(self.call("GET", "/estado")[1]["timer"], "01:35")

    def test_cambio_de_receta_carga_sus_ingredientes(self):
        sid, tok = self.vincular()
        self.call("POST", "/evento", {"type": "CHANGE_RECIPE", "sessionId": sid, "token": tok, "recipeName": "Enchiladas Suizas"})
        self.assertIn("Tomatillo verde", self.call("GET", "/estado")[1]["ingredientes"])
        c, _ = self.call("POST", "/evento", {"type": "CHANGE_RECIPE", "sessionId": sid, "token": tok, "recipeName": "Inexistente"})
        self.assertEqual(c, 404)


class TestLogin(Base):
    def test_req06_login_valido_devuelve_rol(self):
        self.assertEqual(self.login("agente")["rol"], "agente")

    def test_req07_error_generico_no_revela_si_existe_usuario(self):
        a = self.call("POST", "/login", {"usuario": "admin", "password": "incorrecta1"})
        b = self.call("POST", "/login", {"usuario": "noexiste", "password": "incorrecta1"})
        self.assertEqual(a, b)

    def test_req08_campos_vacios_y_password_corta(self):
        self.assertEqual(self.call("POST", "/login", {"usuario": "   ", "password": "Admin2026!"})[0], 400)
        self.assertEqual(self.call("POST", "/login", {"usuario": "admin", "password": "corta"})[0], 401)

    def test_bloqueo_tras_cinco_fallos(self):
        for _ in range(5):
            self.call("POST", "/login", {"usuario": "admin", "password": "incorrecta1"})
        self.assertEqual(self.call("POST", "/login", {"usuario": "admin", "password": "Admin2026!"})[0], 429)


class TestChat(Base):
    def test_req09_mensaje_vacio_rechazado(self):
        c = self.celular()
        self.assertEqual(self.call("POST", "/chat", {**c, "texto": "   "})[0], 400)

    def test_emisor_sale_de_la_sesion_y_cuenta_mensajes(self):
        c = self.celular("Pixel 7")
        _, r = self.call("POST", "/chat", {**c, "texto": "Horno listo"})
        self.assertEqual(r["mensaje"]["emisor"], "📱 Pixel 7")
        self.assertEqual(r["contadores"], {"📱 Pixel 7": 1})
        self.assertRegex(r["mensaje"]["hora"], r"^\d\d:\d\d:\d\d$")

    def test_chat_sin_qr_se_rechaza(self):
        self.assertEqual(self.call("GET", "/chat")[0], 401)
        self.assertEqual(self.call("POST", "/chat", {"texto": "hola", "sessionId": "x", "token": "y"})[0], 401)

    def test_celular_no_puede_limpiar_pero_admin_si(self):
        c = self.celular()
        self.call("POST", "/chat", {**c, "texto": "hola"})
        self.assertEqual(self.call("POST", "/chat/limpiar", c)[0], 401)
        ta = self.login("admin")["token"]
        self.assertEqual(self.call("POST", "/chat/limpiar", {}, ta)[0], 200)
        q = f"?sessionId={c['sessionId']}&token={c['token']}&dispositivo=Pixel"
        self.assertEqual(self.call("GET", "/chat" + q)[1]["mensajes"], [])

    def test_los_celulares_no_tienen_login(self):
        self.assertEqual(self.call("POST", "/login", {"usuario": "cocinero1", "password": "Cocina2026!"})[0], 401)


class TestSoporte(Base):
    def reporte(self, c, **extra):
        cuerpo = {**c, "titulo": "Receta no disponible", "descripcion": "No carga la receta secreta", **extra}
        return self.call("POST", "/reportar", cuerpo)

    def test_req10_reporte_valido_y_campos_vacios(self):
        c = self.celular()
        self.assertEqual(self.reporte(c)[0], 200)
        self.assertEqual(self.call("POST", "/reportar", {**c, "titulo": "", "descripcion": ""})[0], 400)

    def test_req11_id_repetido(self):
        c = self.celular()
        self.assertEqual(self.reporte(c, id="REP001")[0], 200)
        self.assertEqual(self.reporte(c, id="REP001")[0], 409)

    def test_reporte_sin_qr_se_rechaza(self):
        self.assertEqual(self.call("POST", "/reportar", {"titulo": "abc", "descripcion": "abcde"})[0], 401)

    def test_solo_admin_ve_todos_y_celular_no_entra_al_panel(self):
        c1 = self.celular("Pixel 7")
        c2 = {**c1, "dispositivo": "Galaxy A54"}  # segundo celular con el mismo QR
        self.reporte(c1)
        self.reporte(c2)
        self.assertEqual(self.call("GET", "/admin/reportes")[0], 401)
        self.assertEqual(self.call("GET", f"/admin/reportes?sessionId={c1['sessionId']}&token={c1['token']}")[0], 401)
        ta = self.login("admin")["token"]
        self.assertEqual(len(self.call("GET", "/admin/reportes", token=ta)[1]["reportes"]), 2)
        q = f"?sessionId={c1['sessionId']}&token={c1['token']}&dispositivo=Pixel 7".replace(" ", "%20")
        self.assertEqual(len(self.call("GET", "/mis_reportes" + q)[1]["reportes"]), 1)

    def test_req12_flujo_admin_asigna_y_agente_resuelve(self):
        rid = self.reporte(self.celular())[1]["reporte"]["id"]
        ta, tg = self.login("admin")["token"], self.login("agente")["token"]
        self.assertEqual(self.call("GET", "/admin/reportes", token=tg)[1]["reportes"], [])
        self.assertEqual(self.call("POST", "/admin/estado", {"id": rid, "estado": "Resuelto"}, tg)[0], 403)
        self.assertEqual(self.call("POST", "/admin/asignar", {"id": rid, "agente": "agente"}, ta)[0], 200)
        self.assertEqual(self.call("POST", "/admin/asignar", {"id": rid, "agente": "agente"}, tg)[0], 403)
        c, r = self.call("POST", "/admin/estado", {"id": rid, "estado": "Resuelto", "nota": "Corregido"}, tg)
        self.assertEqual((c, r["reporte"]["estado"]), (200, "Resuelto"))

    def test_descubrimiento_automatico(self):
        self.assertEqual(server.responder_descubrimiento(b"KITCHENSYNC?", "192.168.1.5"),
                         f"KITCHENSYNC|http://192.168.1.5:{server.PORT}".encode())
        self.assertIsNone(server.responder_descubrimiento(b"otra cosa", "192.168.1.5"))

    def test_base_url_publica_usa_render_y_no_ip_local(self):
        prev = os.environ.get("KITCHENSYNC_HOST")
        os.environ["KITCHENSYNC_HOST"] = "https://recipestudio.onrender.com"
        try:
            self.assertEqual(server.get_local_ip(), "https://recipestudio.onrender.com")
            c = server.Core(base_url=None)
            self.assertEqual(c.base_url, "https://recipestudio.onrender.com")
        finally:
            if prev is None:
                os.environ.pop("KITCHENSYNC_HOST", None)
            else:
                os.environ["KITCHENSYNC_HOST"] = prev

    def test_pagina_principal_incluye_dashboard_y_no_soporte(self):
        with urllib.request.urlopen(self.url + "/") as r:
            html = r.read().decode()
        self.assertIn("RecipeStudio Dashboard", html)
        self.assertNotIn("Panel de soporte", html)
        self.assertNotIn("btn-soporte", html)
        self.assertNotIn("__SERVER_HOST__", html)

    def test_no_se_sirven_archivos_del_disco(self):
        self.assertEqual(self.call("GET", "/server.py")[0], 404)


if __name__ == "__main__":
    unittest.main()
