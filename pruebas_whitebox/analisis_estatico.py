"""
Fase 1 - Análisis estático (Práctica 11). Solo usa la librería estándar de Python.
Uso (desde la carpeta del proyecto):   python pruebas_whitebox/analisis_estatico.py > resultado_analisis_estatico.txt

Qué hace:
  1. Complejidad ciclomática de cada función de Python (exacta, con el módulo ast) y de Kotlin (aproximada).
  2. Busca patrones de riesgo del checklist de la práctica: excepciones genéricas, !!, credenciales, http sin cifrar...
  3. Verifica que los archivos con secretos estén en .gitignore.
"""
import ast
import os
import re
import sys

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
IGNORAR = {"build", ".gradle", ".idea", ".git", "__pycache__", ".vercel", "node_modules"}


def archivos(ext):
    for base, dirs, files in os.walk(RAIZ):
        dirs[:] = [d for d in dirs if d not in IGNORAR]
        for f in files:
            if f.endswith(ext):
                yield os.path.join(base, f)


def rel(p):
    return os.path.relpath(p, RAIZ).replace("\\", "/")


# ---------- 1a. complejidad Python (exacta) ----------
def complejidad_python():
    filas = []
    for ruta in archivos(".py"):
        if "pruebas_whitebox" in ruta or os.path.basename(ruta).startswith("test_"):
            continue
        arbol = ast.parse(open(ruta, encoding="utf-8").read())
        for nodo in ast.walk(arbol):
            if isinstance(nodo, (ast.FunctionDef, ast.AsyncFunctionDef)):
                c = 1
                for n in ast.walk(nodo):
                    if isinstance(n, (ast.If, ast.For, ast.While, ast.ExceptHandler, ast.IfExp, ast.Assert)):
                        c += 1
                    elif isinstance(n, ast.BoolOp):
                        c += len(n.values) - 1
                    elif isinstance(n, ast.comprehension):
                        c += 1 + len(n.ifs)
                filas.append((c, f"{rel(ruta)}:{nodo.lineno}", nodo.name))
    return sorted(filas, reverse=True)


# ---------- 1b. complejidad Kotlin (aproximada) ----------
TOKENS_KT = [r"\bif\b", r"\bfor\b", r"\bwhile\b", r"\bcatch\b", r"&&", r"\|\|", r"\?:", r"\bwhen\b"]


def complejidad_kotlin():
    filas = []
    for ruta in archivos(".kt"):
        if "/test/" in rel(ruta) or "/androidTest/" in rel(ruta):
            continue
        txt = open(ruta, encoding="utf-8").read()
        for m in re.finditer(r"\bfun\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?(\w+)\s*\(", txt):
            i = txt.find("{", m.end())
            igual = txt.find("=", m.end())
            if i == -1:
                continue
            # cuerpo con llaves balanceadas
            prof, j = 0, i
            while j < len(txt):
                if txt[j] == "{":
                    prof += 1
                elif txt[j] == "}":
                    prof -= 1
                    if prof == 0:
                        break
                j += 1
            cuerpo = txt[i:j]
            c = 1 + sum(len(re.findall(t, cuerpo)) for t in TOKENS_KT)
            # cada rama "cond ->" de un when cuenta como decisión
            c += len(re.findall(r"^\s*(?:in |is |else|[\w\".<>=!\s]+?)\s*->", cuerpo, re.M)) // 1
            linea = txt.count("\n", 0, m.start()) + 1
            filas.append((c, f"{rel(ruta)}:{linea}", m.group(1)))
    return sorted(filas, reverse=True)


# ---------- 2. patrones de riesgo ----------
PATRONES = [
    ("Kotlin", r"!!", "Operador !! (puede lanzar NullPointerException)"),
    ("Kotlin", r"catch\s*\(\s*\w+\s*:\s*Exception\s*\)", "catch genérico de Exception"),
    ("Kotlin", r"printStackTrace", "printStackTrace (no hay registro controlado)"),
    ("Kotlin", r"runBlocking|GlobalScope", "runBlocking / GlobalScope (bloquea hilo o escapa del ciclo de vida)"),
    ("Kotlin", r"http://", "URL http sin cifrar"),
    ("Kotlin", r"Log\.[dviwe]\([^)]*(token|password|clave)", "Log que puede imprimir token o contraseña"),
    ("Python", r"except\s*:", "except vacío"),
    ("Python", r"except\s+Exception", "except Exception genérico"),
    ("Python", r"\beval\(|\bexec\(|shell=True", "eval/exec/shell=True"),
    ("Cualquiera", r"AIza[0-9A-Za-z_\-]{35}", "Clave tipo API de Google"),
    ("Cualquiera", r"(?i)password\s*=\s*[\"'][^\"']{4,}[\"']", "Contraseña escrita en el código"),
    ("Cualquiera", r"(?i)\"(Admin|Agente)\d{4}!\"", "Credencial de demostración conocida"),
]


def patrones():
    res = {}
    for ruta in list(archivos(".kt")) + list(archivos(".py")) + list(archivos(".html")) + list(archivos(".json")) + list(archivos(".kts")):
        r = rel(ruta)
        if "pruebas_whitebox" in r or "/test/" in r or r.startswith("backend/test_"):
            continue
        txt = open(ruta, encoding="utf-8", errors="ignore").read().splitlines()
        for lenguaje, rx, desc in PATRONES:
            if lenguaje == "Kotlin" and not r.endswith(".kt"):
                continue
            if lenguaje == "Python" and not r.endswith(".py"):
                continue
            for n, linea in enumerate(txt, 1):
                if re.search(rx, linea):
                    res.setdefault(desc, []).append(f"{r}:{n}")
    return res


def main():
    print("=== ANÁLISIS ESTÁTICO - RecipeStudio ===\n")
    print("--- 1. Complejidad ciclomática (Python: exacta) - top 8 ---")
    for c, ub, nombre in complejidad_python()[:8]:
        print(f"  CC={c:<3} {nombre:<28} {ub}")
    print("\n--- 1. Complejidad ciclomática (Kotlin: aproximada) - top 8 ---")
    for c, ub, nombre in complejidad_kotlin()[:8]:
        print(f"  CC~{c:<3} {nombre:<28} {ub}")
    print("\n--- 2. Patrones de riesgo ---")
    res = patrones()
    if not res:
        print("  (ninguno)")
    for desc, lugares in sorted(res.items()):
        print(f"  [{len(lugares)}] {desc}")
        for l in lugares[:6]:
            print(f"        {l}")
        if len(lugares) > 6:
            print(f"        ... y {len(lugares) - 6} más")
    print("\n--- 3. Secretos fuera de Git ---")
    gi = os.path.join(RAIZ, ".gitignore")
    contenido = open(gi, encoding="utf-8").read() if os.path.exists(gi) else ""
    print("  local.properties en .gitignore:", "SÍ" if "local.properties" in contenido else "NO  <-- riesgo: la clave de Gemini se subiría")
    print("\nNota: la complejidad de Kotlin es una aproximación por conteo de decisiones; confirma con Android Studio")
    print("(Analyze > Inspect Code) y con './gradlew lint'.")


if __name__ == "__main__":
    sys.exit(main())
