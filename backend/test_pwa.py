"""C2, C3 y C4 (PWA): manifiesto, service worker, íconos, página sin conexión y que el tablero los enlace."""
import json
import os
import struct
import unittest
import urllib.error
import urllib.request

os.environ.setdefault("KITCHENSYNC_ADMIN_PASSWORD", "Admin2026!")
os.environ.setdefault("KITCHENSYNC_AGENTE_PASSWORD", "Agente2026!")

from test_server import Base


class TestPWA(Base):
    def get(self, ruta):
        try:
            r = urllib.request.urlopen(self.url + ruta, timeout=5)
            return r.status, r.headers, r.read()
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read()

    def test_manifiesto_cumple_lo_necesario_para_instalarse(self):
        c, h, cuerpo = self.get("/manifest.webmanifest")
        self.assertEqual(c, 200)
        self.assertIn("manifest+json", h["Content-Type"])
        m = json.loads(cuerpo)
        for campo in ("name", "short_name", "start_url", "scope", "display", "background_color", "theme_color", "icons", "lang"):
            self.assertIn(campo, m, campo)
        self.assertEqual(m["display"], "standalone")
        tamanos = {i["sizes"] for i in m["icons"]}
        self.assertTrue({"192x192", "512x512"} <= tamanos)
        self.assertTrue(any(i.get("purpose") == "maskable" for i in m["icons"]))

    def test_iconos_del_manifiesto_existen_y_son_png_del_tamano_declarado(self):
        for icono in json.loads(self.get("/manifest.webmanifest")[2])["icons"]:
            c, h, datos = self.get(icono["src"])
            self.assertEqual(c, 200, icono["src"])
            self.assertEqual(h["Content-Type"], "image/png")
            self.assertEqual(datos[:8], b"\x89PNG\r\n\x1a\n")
            ancho, alto = struct.unpack(">II", datos[16:24])
            self.assertEqual(f"{ancho}x{alto}", icono["sizes"])

    def test_service_worker_se_sirve_como_javascript_sin_cache_larga(self):
        c, h, cuerpo = self.get("/sw.js")
        self.assertEqual(c, 200)
        self.assertIn("javascript", h["Content-Type"])
        self.assertEqual(h["Cache-Control"], "no-cache")        # así las actualizaciones llegan rápido
        texto = cuerpo.decode()
        for evento in ("'install'", "'activate'", "'fetch'"):
            self.assertIn(evento, texto)
        self.assertIn("offline.html", texto)

    def test_service_worker_no_guarda_en_cache_los_datos_en_vivo(self):
        texto = self.get("/sw.js")[2].decode()
        self.assertIn("estado|sesion", texto)                   # la API pasa directo a la red
        self.assertIn("status: 503", texto)                     # sin red: respuesta JSON controlada

    def test_pagina_sin_conexion_existe(self):
        c, h, cuerpo = self.get("/offline.html")
        self.assertEqual(c, 200)
        self.assertIn("Sin conexión", cuerpo.decode())

    def test_el_tablero_enlaza_manifiesto_registra_el_sw_y_es_responsivo(self):
        html = self.get("/")[2].decode()
        self.assertIn('rel="manifest"', html)
        self.assertIn('name="theme-color"', html)
        self.assertIn('name="viewport"', html)
        self.assertIn("serviceWorker.register('/sw.js')", html)
        self.assertIn("beforeinstallprompt", html)
        self.assertIn("@media (max-width", html)

    def test_solo_se_sirven_los_archivos_de_la_lista(self):
        for ruta in ("/static/sw.js", "/static/../server.py", "/icons/../server.py", "/icons/otro.png", "/server.py"):
            self.assertEqual(self.get(ruta)[0], 404, ruta)


if __name__ == "__main__":
    unittest.main()
