"""Métricas del comparador: memoria, rendimiento, calidad, confiabilidad, uso funcional
y la clasificación automática con los pesos de la rúbrica.

Reglas (explícitas y verificables):

- Tiempo (25 %): mediana del tiempo que imprime cada programa ("Tiempo: X ms"). Menor es mejor.
- Memoria (20 %): mediana del pico de memoria RAM (RSS) del proceso del simulador, medido desde
  afuera con psutil cada 2 ms. En Scala se mide la JVM que ejecuta CopaSimulador, no sbt. Menor es mejor.
- Rendimiento (20 %): partidos simulados por segundo = 32 * torneos / (mediana en segundos). Mayor es mejor.
- Calidad de código (15 %): líneas de código por función (sin líneas vacías ni comentarios).
  Funciones más cortas = código más modular. Menor es mejor.
- Confiabilidad (10 %): porcentaje de pruebas superadas (salida igual a la de referencia y
  fallos controlados ante entradas inválidas). Mayor es mejor.
- Uso funcional (10 %): operaciones funcionales / (operaciones funcionales + ciclos for/while), en %.
  Mayor es mejor.

Normalización: en cada criterio el mejor lenguaje recibe 100 y los demás una proporción
(100 * mejor / valor si se minimiza, 100 * valor / mejor si se maximiza).
Puntaje final = suma de (puntaje del criterio * peso). Gana el puntaje más alto.
"""

from __future__ import annotations

import re
import subprocess
import tempfile
import time
from pathlib import Path

try:
    import psutil
except ImportError:  # sin psutil el comparador sigue funcionando, solo sin memoria
    psutil = None


ROOT = Path(__file__).resolve().parents[1]

CRITERIOS = [
    # (clave, nombre, peso, "min" o "max", unidad)
    ("tiempo_ms", "Tiempo de ejecución", 25, "min", "ms"),
    ("memoria_mb", "Consumo de memoria", 20, "min", "MB"),
    ("partidos_por_segundo", "Rendimiento", 20, "max", "partidos/s"),
    ("lineas_por_funcion", "Calidad de código", 15, "min", "líneas/función"),
    ("confiabilidad_pct", "Confiabilidad", 10, "max", "%"),
    ("uso_funcional_pct", "Uso funcional", 10, "max", "%"),
]


# ---------------------------------------------------------------------------
# Memoria: ejecutar un comando y medir el pico de RAM del proceso del simulador
# ---------------------------------------------------------------------------

def _procesos_objetivo(raiz, marca):
    """El proceso raíz, o (si hay marca) los procesos descendientes cuyo comando la contienen."""
    if marca is None:
        return [raiz]
    procesos = []
    for proceso in [raiz, *raiz.children(recursive=True)]:
        try:
            if marca in " ".join(proceso.cmdline()):
                procesos.append(proceso)
        except Exception:  # en macOS psutil puede lanzar SystemError/PermissionError si el proceso ya terminó
            pass
    return procesos


def ejecutar_midiendo(comando, cwd, marca=None, timeout=600):
    """Corre el comando y devuelve (codigo_salida, stdout, stderr, pico_memoria_mb o None)."""
    proceso = subprocess.Popen(comando, cwd=cwd, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, text=True)
    pico = 0
    if psutil is not None:
        try:
            raiz = psutil.Process(proceso.pid)
            limite = time.monotonic() + timeout
            while proceso.poll() is None and time.monotonic() < limite:
                try:
                    rss = sum(p.memory_info().rss for p in _procesos_objetivo(raiz, marca))
                    pico = max(pico, rss)
                except Exception:  # en macOS psutil puede lanzar SystemError/PermissionError si el proceso ya terminó
                    pass
                time.sleep(0.002)
        except Exception:
            pass
    stdout, stderr = proceso.communicate(timeout=timeout)
    memoria = round(pico / (1024 * 1024), 2) if pico > 0 else None
    return proceso.returncode, stdout, stderr, memoria


# ---------------------------------------------------------------------------
# Análisis del código fuente: calidad y uso funcional
# ---------------------------------------------------------------------------

FUENTES = {
    "python": ("python", "*.py"),
    "java": ("java/src", "*.java"),
    "scala": ("scala/src", "*.scala"),
}

FUNCIONES = {
    "python": re.compile(r"^\s*def\s+\w+"),
    "java": re.compile(r"^\s*(public|private|protected|static|final|\s)+[\w<>\[\],\s]+\s+\w+\s*\(.*\)\s*(throws\s+[\w,\s]+)?\{\s*$"),
    "scala": re.compile(r"\bdef\s+\w+"),
}

OPERACIONES_FUNCIONALES = {
    "python": [r"\bmap\(", r"\bfilter\(", r"\breduce\(", r"\blambda\b", r"\bsorted\(", r"\bsum\(",
               r"\bany\(", r"\ball\(", r"\bgroupby\(", r"\baccumulate\(", r"\bzip\(",
               r"[\[\(\{][^\[\(\{]*\bfor\b[^\]\)\}]*\bin\b"],
    "java": [r"\.map\(", r"\.filter\(", r"\.reduce\(", r"\.collect\(", r"\.flatMap\(", r"\.sorted\(",
             r"\.mapToInt\(", r"\.mapToObj\(", r"\.min\(", r"\.max\(", r"\.sum\(", r"\.forEach\(",
             r"groupingBy\(", r"toMap\(", r"->", r"::"],
    "scala": [r"\.map\b", r"\.filter\b", r"\.foldLeft\b", r"\.reduce\b", r"\.flatMap\b", r"\.groupBy\b",
              r"\.sortBy\b", r"\.scanLeft\b", r"\.collect\b", r"\.find\b", r"\.zip\b", r"\.sum\b",
              r"\.minBy\b", r"\.maxBy\b", r"@tailrec", r"(?<!case )[^=]=>"],
}

CICLOS = re.compile(r"^\s*(for|while)\b")


def _lineas_de_codigo(texto, lenguaje):
    """Quita comentarios y líneas vacías."""
    if lenguaje == "python":
        texto = re.sub(r'"""[\s\S]*?"""', "", texto)
        comentario = "#"
    else:
        texto = re.sub(r"/\*[\s\S]*?\*/", "", texto)
        comentario = "//"
    lineas = []
    for linea in texto.splitlines():
        limpia = linea.split(comentario, 1)[0].rstrip() if comentario in linea else linea.rstrip()
        if limpia.strip():
            lineas.append(limpia)
    return lineas


def analizar_codigo(lenguaje):
    carpeta, patron = FUENTES[lenguaje]
    lineas = []
    for archivo in sorted((ROOT / carpeta).rglob(patron)):
        lineas += _lineas_de_codigo(archivo.read_text(encoding="utf-8"), lenguaje)

    funciones = sum(1 for linea in lineas if FUNCIONES[lenguaje].search(linea))
    funcionales = sum(len(re.findall(p, linea)) for linea in lineas for p in OPERACIONES_FUNCIONALES[lenguaje])
    ciclos = sum(1 for linea in lineas if CICLOS.search(linea))

    return {
        "lineas_codigo": len(lineas),
        "funciones": funciones,
        "lineas_por_funcion": round(len(lineas) / max(funciones, 1), 2),
        "operaciones_funcionales": funcionales,
        "ciclos": ciclos,
        "uso_funcional_pct": round(100 * funcionales / max(funcionales + ciclos, 1), 2),
    }


# ---------------------------------------------------------------------------
# Confiabilidad: fallos controlados ante entradas inválidas
# ---------------------------------------------------------------------------

def pruebas_confiabilidad(construir_comando, cwd, ruta_input, ruta_aleatorios, marca=None):
    """construir_comando(opciones) devuelve el comando a ejecutar con esas opciones.

    Cada prueba debe terminar en menos de 10 minutos. Devuelve una lista de
    {"prueba": nombre, "superada": bool}.
    """
    resultados = []
    with tempfile.TemporaryDirectory(prefix="copa-confiabilidad-", dir=ROOT / "out") as temporal:
        carpeta = Path(temporal)
        malformado = carpeta / "malformado.json"
        malformado.write_text("{ esto no es json", encoding="utf-8")
        inexistente = carpeta / "no-existe.json"

        casos = [
            ("Input inexistente: termina con error", inexistente, ruta_aleatorios, False),
            ("Input con JSON dañado: termina con error", malformado, ruta_aleatorios, False),
            ("Archivo de aleatorios inexistente: termina con error", ruta_input, carpeta / "no-existe.txt", False),
            ("Salida en una carpeta nueva: la crea y termina bien", ruta_input, ruta_aleatorios, True),
        ]
        for nombre, entrada, aleatorios, debe_funcionar in casos:
            salida = carpeta / "nueva" / "sub" / f"salida-{len(resultados)}.json"
            opciones = ["--input", str(entrada), "--aleatorios", str(aleatorios),
                        "--torneos", "1", "--out", str(salida)]
            try:
                codigo, _, _, _ = ejecutar_midiendo(construir_comando(opciones), cwd, marca, timeout=600)
                if debe_funcionar:
                    superada = codigo == 0 and salida.exists()
                else:
                    superada = codigo != 0
            except (OSError, subprocess.TimeoutExpired):
                superada = False
            resultados.append({"prueba": nombre, "superada": superada})
    return resultados


# ---------------------------------------------------------------------------
# Clasificación automática con los pesos de la rúbrica
# ---------------------------------------------------------------------------

def _puntaje(valor, mejor, sentido):
    if valor is None or mejor is None:
        return None
    if sentido == "min":
        return 100.0 if valor <= 0 else 100.0 * mejor / valor
    return 0.0 if mejor <= 0 else 100.0 * valor / mejor


def clasificar(metricas):
    """metricas: {lenguaje: {clave_criterio: valor}}. Devuelve criterios y ranking."""
    criterios_usados = []
    for clave, nombre, peso, sentido, unidad in CRITERIOS:
        valores = [m.get(clave) for m in metricas.values() if m.get(clave) is not None]
        if len(valores) == len(metricas):  # solo se usa si se midió en todos los lenguajes
            mejor = min(valores) if sentido == "min" else max(valores)
            criterios_usados.append((clave, nombre, peso, sentido, unidad, mejor))

    peso_total = sum(c[2] for c in criterios_usados)
    ranking = []
    for lenguaje, m in metricas.items():
        detalle = []
        total = 0.0
        for clave, nombre, peso, sentido, unidad, mejor in criterios_usados:
            puntaje = _puntaje(m[clave], mejor, sentido)
            peso_efectivo = peso * 100 / peso_total
            aporte = puntaje * peso_efectivo / 100
            total += aporte
            detalle.append({"criterio": nombre, "valor": m[clave], "unidad": unidad,
                            "puntaje": round(puntaje, 2), "peso": round(peso_efectivo, 2),
                            "aporte": round(aporte, 2)})
        ranking.append({"lenguaje": lenguaje, "puntaje_total": round(total, 2), "detalle": detalle})

    ranking.sort(key=lambda r: -r["puntaje_total"])
    for posicion, fila in enumerate(ranking, start=1):
        fila["posicion"] = posicion

    omitidos = [nombre for clave, nombre, *_ in CRITERIOS if clave not in {c[0] for c in criterios_usados}]
    return {
        "criterios": [{"criterio": n, "peso_rubrica": p, "sentido": "menor es mejor" if s == "min" else "mayor es mejor",
                       "unidad": u} for _, n, p, s, u, _ in criterios_usados],
        "criterios_omitidos": omitidos,
        "ranking": ranking,
        "ganador": ranking[0]["lenguaje"] if ranking else None,
    }
