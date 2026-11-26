"""AUT-06 (Persistencia) y AUT-04 (API): el registro guarda lo que debe y se puede volver a leer."""
import os
import unittest

os.environ.setdefault("KITCHENSYNC_ADMIN_PASSWORD", "Admin2026!")
os.environ.setdefault("KITCHENSYNC_AGENTE_PASSWORD", "Agente2026!")

from test_server import Base


class TestPersistenciaReportes(Base):
    def setUp(self):
        super().setUp()
        self.cel = self.celular()
        self.q = f"?sessionId={self.cel['sessionId']}&token={self.cel['token']}&dispositivo=Pixel%207"

    def nuevo(self, titulo="Falla de sincronización", **extra):
        return self.call("POST", "/reportar", {**self.cel, "titulo": titulo, "descripcion": "No se actualiza la receta", **extra})

    def test_aut06_el_reporte_guardado_se_puede_consultar_despues(self):
        creado = self.nuevo()
        self.assertEqual(creado[0], 200)
        lista = self.call("GET", "/mis_reportes" + self.q)[1]["reportes"]
        self.assertEqual([r["id"] for r in lista], [creado[1]["reporte"]["id"]])
        self.assertEqual(lista[0]["titulo"], "Falla de sincronización")

    def test_aut06_los_datos_persisten_tras_otras_operaciones(self):
        self.nuevo("Primero")
        self.call("POST", "/evento", {**self.cel, "type": "TIMER_RESET"})
        self.nuevo("Segundo")
        self.assertEqual(len(self.call("GET", "/mis_reportes" + self.q)[1]["reportes"]), 2)

    def test_aut06_un_id_repetido_no_duplica_el_registro(self):          # previene D-09 (duplicados al reintentar)
        self.assertEqual(self.nuevo(id="REP-IDEMP")[0], 200)
        self.assertEqual(self.nuevo(id="REP-IDEMP")[0], 409)
        self.assertEqual(len(self.call("GET", "/mis_reportes" + self.q)[1]["reportes"]), 1)

    def test_aut06_un_registro_invalido_no_se_guarda(self):
        self.assertEqual(self.nuevo(titulo="ab")[0], 400)
        self.assertEqual(len(self.call("GET", "/mis_reportes" + self.q)[1]["reportes"]), 0)


if __name__ == "__main__":
    unittest.main()
