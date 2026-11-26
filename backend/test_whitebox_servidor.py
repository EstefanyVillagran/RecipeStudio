"""
Práctica 11 - Pruebas White Box del servidor (Python). Cada prueba indica la RUTA o la CONDICIÓN del código que ejercita.
Ejecutar:  python -m unittest test_whitebox_servidor -v
Cobertura (instalar una vez: pip install coverage):
    coverage run --branch -m unittest test_server test_whitebox_servidor
    coverage report -m --include=server.py          # % de instrucciones y de ramas
    coverage html --include=server.py               # abre htmlcov/index.html
"""
import os
import socket
import unittest

os.environ.setdefault("KITCHENSYNC_ADMIN_PASSWORD", "Admin2026!")
os.environ.setdefault("KITCHENSYNC_AGENTE_PASSWORD", "Agente2026!")

import server
from test_server import Base


class TestNormalizarUrlPublica(unittest.TestCase):
    """_normalizar_url_publica (complejidad ciclomática 15): una prueba por camino independiente."""
    n = staticmethod(server._normalizar_url_publica)

    def test_c01_vacio_o_none(self):            self.assertIsNone(self.n("")); self.assertIsNone(self.n(None))
    def test_c02_doble_barra_agrega_https(self):  self.assertEqual(self.n("//app.onrender.com"), "https://app.onrender.com")
    def test_c03_sin_esquema_agrega_https(self):  self.assertEqual(self.n("app.onrender.com"), "https://app.onrender.com")
    def test_c04_sin_host(self):                self.assertIsNone(self.n("https://"))
    def test_c05_ip_loopback(self):             self.assertIsNone(self.n("http://127.0.0.1:8080"))
    def test_c06_ip_privada(self):              self.assertIsNone(self.n("http://10.24.33.202:10000"))   # el caso del QR con IP interna
    def test_c07_ip_publica_valida(self):       self.assertEqual(self.n("http://8.8.8.8:8080"), "http://8.8.8.8:8080")
    def test_c08_localhost_por_nombre(self):    self.assertIsNone(self.n("http://localhost:8080")); self.assertIsNone(self.n("0.0.0.0"))
    def test_c09_dominio_publico_puerto_443(self): self.assertEqual(self.n("https://x.com:443"), "https://x.com")
    def test_c10_dominio_con_puerto_distinto(self): self.assertEqual(self.n("https://x.com:8443"), "https://x.com:8443")
    def test_c11_valores_limite_de_puerto(self):
        self.assertEqual(self.n("http://x.com:80"), "http://x.com")      # 80 se omite
        self.assertEqual(self.n("http://x.com:81"), "http://x.com:81")   # 81 se conserva


class TestDescubrimiento(unittest.TestCase):
    def test_pregunta_correcta(self):
        self.assertEqual(server.responder_descubrimiento(b"KITCHENSYNC?\n", "1.2.3.4"), b"KITCHENSYNC|http://1.2.3.4:%d" % server.PORT)

    def test_pregunta_ajena_se_ignora(self):
        self.assertIsNone(server.responder_descubrimiento(b"otra cosa", "1.2.3.4"))


class TestBodyYCabeceras(Base):
    """Handler._body: validación de Content-Length (defectos D-02 y D-03 de la auditoría)."""

    def _crudo(self, content_length):
        host, puerto = self.httpd.server_address
        s = socket.create_connection((host, puerto), timeout=3)
        s.sendall(f"POST /evento HTTP/1.1\r\nHost: x\r\nContent-Length: {content_length}\r\n\r\n".encode())
        try:
            return s.recv(100).split(b"\r\n")[0]
        finally:
            s.close()

    def test_content_length_no_numerico_da_400(self):
        self.assertIn(b"400", self._crudo("abc"))

    def test_content_length_negativo_da_400_sin_colgar(self):
        self.assertIn(b"400", self._crudo("-1"))

    def test_cuerpo_demasiado_grande_da_413(self):
        self.assertIn(b"413", self._crudo(server.MAX_BODY + 1))

    def test_valor_limite_exacto_del_maximo_no_da_413(self):
        # Cuerpo de EXACTAMENTE MAX_BODY bytes: se acepta (la sesión no está vinculada -> 403, no 413)
        relleno = server.MAX_BODY - len(b'{"x":""}')
        cuerpo = b'{"x":"' + b"a" * relleno + b'"}'
        self.assertEqual(len(cuerpo), server.MAX_BODY)
        host, puerto = self.httpd.server_address
        s = socket.create_connection((host, puerto), timeout=3)
        s.sendall(f"POST /evento HTTP/1.1\r\nHost: x\r\nContent-Length: {len(cuerpo)}\r\n\r\n".encode() + cuerpo)
        linea = s.recv(100).split(b"\r\n")[0]
        s.close()
        self.assertIn(b"403", linea)

    def test_json_que_no_es_objeto_da_400(self):
        self.assertEqual(self.call("POST", "/evento", [1, 2, 3])[0], 400)


class TestEventosRamas(Base):
    """Core.evento: una rama por tipo de evento."""

    def setUp(self):
        super().setUp()
        self.cel = self.celular()

    def ev(self, tipo, **payload):
        return self.call("POST", "/evento", {**self.cel, "type": tipo, **payload})

    def test_timer_toggle_tick_y_reset(self):
        self.assertEqual(self.ev("TIMER_TOGGLE", running=True)[0], 200)
        self.assertEqual(self.ev("TIMER_TICK", timerSeconds=42)[0], 200)
        s = self.ev("TIMER_RESET")[1]["estado"]
        self.assertEqual(s["timerSeconds"], 180)
        self.assertFalse(s["timerRunning"])

    def test_tick_fuera_de_rango_da_400(self):
        self.assertEqual(self.ev("TIMER_TICK", timerSeconds=86401)[0], 400)   # valor límite superior + 1
        self.assertEqual(self.ev("TIMER_TICK", timerSeconds=-1)[0], 400)      # valor límite inferior - 1

    def test_cambiar_receta_existente_e_inexistente(self):
        self.assertEqual(self.ev("CHANGE_RECIPE", recipeName="Receta que no existe")[0], 404)

    def test_sustitucion_valida_y_con_impacto_invalido(self):
        ok = self.ev("SUSTITUCION", original="Azúcar", sustituto="Miel", impactoSaborPct=10, impactoTexturaPct=5)
        self.assertEqual(ok[0], 200)
        self.assertEqual(self.ev("SUSTITUCION", original="Azúcar", sustituto="Miel", impactoSaborPct=101)[0], 400)

    def test_tipo_desconocido_da_400(self):
        self.assertEqual(self.ev("NO_EXISTE")[0], 400)


class TestSoporteAutorizacion(Base):
    """401 (sin credenciales) vs 403 (con credenciales pero sin permiso) y estados de un reporte."""

    def setUp(self):
        super().setUp()
        self.cel = self.celular()
        self.rep = self.call("POST", "/reportar", {**self.cel, "titulo": "Falla de sync", "descripcion": "No se actualiza"})[1]["reporte"]
        self.admin = self.login("admin")["token"]
        self.agente = self.login("agente")["token"]

    def test_listar_sin_token_da_401(self):
        self.assertEqual(self.call("GET", "/admin/reportes")[0], 401)

    def test_asignar_con_agente_da_403(self):                  # autenticado pero sin permiso
        self.assertEqual(self.call("POST", "/admin/asignar", {"id": self.rep["id"], "agente": "agente"}, token=self.agente)[0], 403)

    def test_asignar_reporte_inexistente_da_404(self):
        self.assertEqual(self.call("POST", "/admin/asignar", {"id": "REP-999", "agente": "agente"}, token=self.admin)[0], 404)

    def test_asignar_a_agente_invalido_da_400(self):
        self.assertEqual(self.call("POST", "/admin/asignar", {"id": self.rep["id"], "agente": "admin"}, token=self.admin)[0], 400)

    def test_asignar_cambia_pendiente_a_en_proceso(self):
        r = self.call("POST", "/admin/asignar", {"id": self.rep["id"], "agente": "agente"}, token=self.admin)
        self.assertEqual((r[0], r[1]["reporte"]["estado"]), (200, "En proceso"))

    def test_agente_no_cambia_estado_de_caso_ajeno(self):
        self.assertEqual(self.call("POST", "/admin/estado", {"id": self.rep["id"], "estado": "Resuelto"}, token=self.agente)[0], 403)

    def test_estado_invalido_da_400(self):
        self.assertEqual(self.call("POST", "/admin/estado", {"id": self.rep["id"], "estado": "Cualquiera"}, token=self.admin)[0], 400)

    def test_admin_puede_reiniciar_sin_sesion_de_qr(self):     # camino es_admin == True de desvincular
        self.assertEqual(self.call("POST", "/reset", {}, token=self.admin)[0], 200)


class TestReportarConId(Base):
    def setUp(self):
        super().setUp()
        self.cel = self.celular()

    def nuevo(self, **extra):
        return self.call("POST", "/reportar", {**self.cel, "titulo": "Titulo", "descripcion": "Descripcion", **extra})

    def test_id_manual_valido_y_repetido_da_409(self):
        self.assertEqual(self.nuevo(id="REP-ABC")[0], 200)
        self.assertEqual(self.nuevo(id="REP-ABC")[0], 409)

    def test_id_con_caracteres_invalidos_da_400(self):
        self.assertEqual(self.nuevo(id="../etc")[0], 400)

    def test_categoria_y_prioridad_invalidas_dan_400(self):
        self.assertEqual(self.nuevo(categoria="Inventada")[0], 400)
        self.assertEqual(self.nuevo(prioridad="Urgentisima")[0], 400)


if __name__ == "__main__":
    unittest.main()


class TestRecetaCompartida(Base):
    """CHANGE_RECIPE con la receta completa: sincronización por nombre exacto y validación del contenido."""

    def setUp(self):
        super().setUp()
        self.cel = self.celular()

    def cambiar(self, receta):
        return self.call("POST", "/evento", {**self.cel, "type": "CHANGE_RECIPE", "recipe": receta})

    def receta(self, **extra):
        base = {"nombre": "Guacamole de la abuela", "categoria": "COCINA MEXICANA", "tiempo": "15 min",
                "ingredientes": [{"nombre": "Aguacate", "medida": "3 pzas", "listo": True}, {"nombre": "Limón", "medida": "1"}],
                "pasos": ["Machaca el aguacate", "Agrega limón y sal"]}
        base.update(extra)
        return base

    def test_receta_propia_queda_en_el_estado_con_sus_ingredientes(self):
        r = self.cambiar(self.receta())
        self.assertEqual(r[0], 200)
        e = r[1]["estado"]
        self.assertEqual(e["receta"], "Guacamole de la abuela")
        self.assertEqual(e["ingredientes"], {"Aguacate": True, "Limón": False})
        self.assertEqual(e["recetaDetalle"]["pasos"][1], "Agrega limón y sal")

    def test_toggle_de_ingrediente_de_receta_propia(self):
        self.cambiar(self.receta())
        r = self.call("POST", "/evento", {**self.cel, "type": "INGREDIENT_TOGGLE", "ingredient": "Limón", "completed": True})
        self.assertTrue(r[1]["estado"]["ingredientes"]["Limón"])

    def test_la_version_del_estado_sube_con_cada_cambio(self):
        v1 = self.cambiar(self.receta())[1]["estado"]["rev"]
        v2 = self.call("POST", "/evento", {**self.cel, "type": "TIMER_RESET"})[1]["estado"]["rev"]
        self.assertGreater(v2, v1)

    def test_validaciones(self):
        self.assertEqual(self.cambiar(self.receta(ingredientes=[]))[0], 400)                       # sin ingredientes
        self.assertEqual(self.cambiar(self.receta(ingredientes=[{"nombre": ""}]))[0], 400)        # nombre vacío
        self.assertEqual(self.cambiar(self.receta(ingredientes=[{"nombre": "x"}] * 41))[0], 400)   # demasiados
        self.assertEqual(self.cambiar(self.receta(nombre="a" * 61))[0], 400)                       # nombre largo
        self.assertEqual(self.cambiar(self.receta(pasos=["p"] * 16))[0], 400)                      # demasiados pasos
        self.assertEqual(self.cambiar(self.receta(ingredientes="no es lista"))[0], 400)

    def test_ingredientes_repetidos_se_unifican(self):
        r = self.cambiar(self.receta(ingredientes=[{"nombre": "Sal"}, {"nombre": "sal"}, {"nombre": "Pimienta"}]))
        self.assertEqual(len(r[1]["estado"]["recetaDetalle"]["ingredientes"]), 2)

    def test_receta_de_catalogo_por_nombre_sigue_funcionando(self):
        r = self.call("POST", "/evento", {**self.cel, "type": "CHANGE_RECIPE", "recipeName": "Enchiladas Suizas"})
        self.assertEqual(r[0], 200)
        self.assertIsNone(r[1]["estado"]["recetaDetalle"])

    def test_sin_sesion_no_se_puede_cambiar_la_receta(self):
        self.assertEqual(self.call("POST", "/evento", {"sessionId": "x", "token": "y", "type": "CHANGE_RECIPE", "recipe": self.receta()})[0], 403)
