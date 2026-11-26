"""Pruebas de la estrategia de multimedia: URLs prefirmadas de S3 (firma, validaciones y códigos HTTP)."""
import datetime
import os
import unittest
import urllib.parse

os.environ.setdefault("KITCHENSYNC_ADMIN_PASSWORD", "Admin2026!")
os.environ.setdefault("KITCHENSYNC_AGENTE_PASSWORD", "Agente2026!")

import s3_presign
from test_server import Base

VARS = ("S3_BUCKET", "AWS_REGION", "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN")


class TestFirmaSigV4(unittest.TestCase):
    def test_vector_oficial_de_aws(self):
        # Ejemplo de la documentación de AWS (Signature V4 por parámetros de consulta): la firma debe coincidir exactamente
        u = s3_presign.firmar_url("GET", "examplebucket.s3.amazonaws.com", "/test.txt", "us-east-1", "AKIAIOSFODNN7EXAMPLE",
                                  "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", expira=86400,
                                  ahora=datetime.datetime(2013, 5, 24, tzinfo=datetime.timezone.utc))
        self.assertTrue(u.endswith("X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404"))

    def test_put_firma_el_content_type(self):
        cfg = s3_presign.S3Config("mi-bucket", "us-east-1", "AKIATEST", "secreto")
        d = s3_presign.url_subida(cfg, "image/jpeg")
        q = urllib.parse.parse_qs(urllib.parse.urlparse(d["url"]).query)
        self.assertEqual(q["X-Amz-SignedHeaders"], ["content-type;host"])
        self.assertEqual(q["X-Amz-Expires"], ["300"])
        self.assertRegex(d["key"], s3_presign.PATRON_CLAVE.pattern)

    def test_token_de_sesion_aws_academy_se_firma(self):
        cfg = s3_presign.S3Config("b", "us-east-1", "ASIATEST", "s", session_token="TOKEN/+=")
        q = urllib.parse.parse_qs(urllib.parse.urlparse(s3_presign.url_subida(cfg, "image/png")["url"]).query)
        self.assertEqual(q["X-Amz-Security-Token"], ["TOKEN/+="])

    def test_tipo_no_permitido(self):
        with self.assertRaises(ValueError):
            s3_presign.url_subida(s3_presign.S3Config("b", "r", "a", "s"), "application/x-msdownload")

    def test_clave_de_descarga_invalida(self):
        cfg = s3_presign.S3Config("b", "r", "a", "s")
        for mala in ("", "../etc/passwd", "fotos/2026/x.jpg", "fotos/20261114/ZZZ.jpg", "otra/20261114/" + "a" * 24 + ".jpg"):
            with self.assertRaises(ValueError, msg=mala):
                s3_presign.url_descarga(cfg, mala)


class TestEndpointsS3(Base):
    def setUp(self):
        super().setUp()
        self.previos = {k: os.environ.pop(k, None) for k in VARS}
        self.sid, self.tok = self.vincular()
        self.auth = f"sessionId={self.sid}&token={self.tok}"

    def tearDown(self):
        for k, v in self.previos.items():
            os.environ.pop(k, None)
            if v is not None:
                os.environ[k] = v
        super().tearDown()

    def configurar(self):
        os.environ.update({"S3_BUCKET": "recipestudio-fotos", "AWS_REGION": "us-east-1",
                           "AWS_ACCESS_KEY_ID": "AKIATEST", "AWS_SECRET_ACCESS_KEY": "secreto"})

    def test_sin_sesion_da_401(self):
        self.assertEqual(self.call("GET", "/api/upload-url")[0], 401)

    def test_sesion_falsa_da_403(self):
        self.assertEqual(self.call("GET", "/api/upload-url?sessionId=x&token=y")[0], 403)

    def test_sin_configurar_s3_da_503(self):
        r = self.call("GET", f"/api/upload-url?{self.auth}")
        self.assertEqual(r[0], 503)
        self.assertIn("error", r[1])

    def test_tipo_invalido_da_400(self):
        self.configurar()
        self.assertEqual(self.call("GET", f"/api/upload-url?{self.auth}&contentType=text/html")[0], 400)

    def test_url_de_subida_valida(self):
        self.configurar()
        c, d = self.call("GET", f"/api/upload-url?{self.auth}")
        self.assertEqual(c, 200)
        self.assertEqual(d["method"], "PUT")
        self.assertTrue(d["url"].startswith("https://recipestudio-fotos.s3.us-east-1.amazonaws.com/fotos/"))
        self.assertIn("X-Amz-Signature=", d["url"])
        self.assertNotIn("secreto", str(d))          # la clave secreta nunca sale del servidor

    def test_descarga_con_clave_invalida_da_400(self):
        self.configurar()
        self.assertEqual(self.call("GET", f"/api/download-url?{self.auth}&key=../secreto.txt")[0], 400)

    def test_descarga_valida(self):
        self.configurar()
        clave = self.call("GET", f"/api/upload-url?{self.auth}")[1]["key"]
        c, d = self.call("GET", f"/api/download-url?{self.auth}&key={clave}")
        self.assertEqual(c, 200)
        self.assertIn(clave, d["url"])


if __name__ == "__main__":
    unittest.main()
