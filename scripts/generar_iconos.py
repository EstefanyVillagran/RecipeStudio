"""Genera los íconos de la PWA (PNG) con la misma olla del ícono de Android. Solo librería estándar.
   python scripts/generar_iconos.py"""
import math, os, struct, zlib

SALIDA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "backend", "static", "icons")
FONDO = (0xCF, 0x5A, 0x3A)


def dentro_rect_redondeado(x, y, x0, y0, x1, y1, r):
    if not (x0 <= x <= x1 and y0 <= y <= y1):
        return False
    cx = min(max(x, x0 + r), x1 - r)
    cy = min(max(y, y0 + r), y1 - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def dist_segmento(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


VAPOR = [((44, 33), (41.5, 29), (44, 25)), ((54, 32), (51.5, 28), (54, 24)), ((64, 33), (61.5, 29), (64, 25))]


def glifo(x, y):
    """¿(x, y) está dentro de la olla? Coordenadas del lienzo de 108."""
    if dentro_rect_redondeado(x, y, 30, 57, 78, 80, 9) and y >= 57:
        return True
    if 22 <= x <= 30 and 61 <= y <= 67 or 78 <= x <= 86 and 61 <= y <= 67:
        return True
    if y <= 53 and ((x - 54) / 22) ** 2 + ((y - 53) / 10) ** 2 <= 1:
        return True
    if (x - 54) ** 2 + (y - 40) ** 2 <= 9:
        return True
    return any(dist_segmento(x, y, *p[0], *p[1]) <= 1.4 or dist_segmento(x, y, *p[1], *p[2]) <= 1.4 for p in VAPOR)


def dibujar(tam, escala_glifo, esquinas):
    filas = []
    for j in range(tam):
        fila = bytearray([0])
        for i in range(tam):
            acum = [0, 0, 0, 0]
            for sj in (0.25, 0.75):
                for si in (0.25, 0.75):
                    px, py = (i + si) / tam * 108, (j + sj) / tam * 108
                    if esquinas and not dentro_rect_redondeado(px, py, 0, 0, 108, 108, 24):
                        continue                       # fuera del cuadrado redondeado: transparente
                    gx, gy = 54 + (px - 54) / escala_glifo, 54 + (py - 54) / escala_glifo
                    c = (255, 255, 255) if glifo(gx, gy) else FONDO
                    acum[0] += c[0]; acum[1] += c[1]; acum[2] += c[2]; acum[3] += 255
            n = max(acum[3] // 255, 1)
            fila += bytes([acum[0] // n, acum[1] // n, acum[2] // n, acum[3] // 4])
        filas.append(bytes(fila))
    return filas


def png(ruta, tam, escala_glifo, esquinas):
    crudo = b"".join(dibujar(tam, escala_glifo, esquinas))
    def trozo(tipo, datos):
        c = struct.pack(">I", len(datos)) + tipo + datos
        return c + struct.pack(">I", zlib.crc32(tipo + datos) & 0xFFFFFFFF)
    datos = b"\x89PNG\r\n\x1a\n" + trozo(b"IHDR", struct.pack(">IIBBBBB", tam, tam, 8, 6, 0, 0, 0)) + trozo(b"IDAT", zlib.compress(crudo, 9)) + trozo(b"IEND", b"")
    open(ruta, "wb").write(datos)


if __name__ == "__main__":
    os.makedirs(SALIDA, exist_ok=True)
    png(os.path.join(SALIDA, "icon-192.png"), 192, 1.25, True)
    png(os.path.join(SALIDA, "icon-512.png"), 512, 1.25, True)
    png(os.path.join(SALIDA, "maskable-512.png"), 512, 0.95, False)   # a sangre y con el glifo en la zona segura
    print("Íconos generados en", os.path.abspath(SALIDA))
