"""Servidor local y benchmark para la comparación de los tres simuladores."""

from __future__ import annotations

import json
import os
import random
import re
import shutil
import statistics
import subprocess
import sys
import tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]
FRONTEND = ROOT / "frontend"
sys.path.insert(0, str(ROOT / "benchmark"))
import metricas  # noqa: E402  (memoria, calidad, confiabilidad, uso funcional y clasificación)
TIEMPO_RE = re.compile(r"Tiempo:\s*([\d.]+)\s*ms")
NUMEROS_ALEATORIOS = 100_000
MAX_TORNEOS = 1_000
MAX_REPETICIONES = 5


def validar_opciones(datos: object) -> tuple[int, int]:
    if not isinstance(datos, dict):
        raise ValueError("Se esperaba un objeto JSON.")
    torneos = datos.get("torneos", 1)
    repeticiones = datos.get("repeticiones", 3)
    if type(torneos) is not int or not 1 <= torneos <= MAX_TORNEOS:
        raise ValueError(f"El número de torneos debe estar entre 1 y {MAX_TORNEOS}.")
    if type(repeticiones) is not int or not 1 <= repeticiones <= MAX_REPETICIONES:
        raise ValueError(f"Las repeticiones deben estar entre 1 y {MAX_REPETICIONES}.")
    return torneos, repeticiones


def primera_diferencia(a: object, b: object, ruta: str = "$") -> str | None:
    if type(a) is not type(b):
        return f"{ruta}: tipos distintos"
    if isinstance(a, dict):
        if ruta == "$":
            a = {clave: valor for clave, valor in a.items() if clave != "lenguaje"}
            b = {clave: valor for clave, valor in b.items() if clave != "lenguaje"}
        if a.keys() != b.keys():
            return f"{ruta}: claves distintas"
        for clave in a:
            diferencia = primera_diferencia(a[clave], b[clave], f"{ruta}.{clave}")
            if diferencia:
                return diferencia
    elif isinstance(a, list):
        if len(a) != len(b):
            return f"{ruta}: longitudes distintas"
        for indice, (izquierda, derecha) in enumerate(zip(a, b)):
            diferencia = primera_diferencia(izquierda, derecha, f"{ruta}[{indice}]")
            if diferencia:
                return diferencia
    elif a != b:
        return f"{ruta}: valores distintos"
    return None


def resumen_visual(salida: dict[str, object], nombres_equipos: dict[str, str]) -> dict[str, object]:
    grupos = [
        {
            "grupo": grupo,
            "filas": [
                {
                    "posicion": posicion,
                    "equipo": nombres_equipos.get(str(fila["equipo"]), str(fila["equipo"])),
                    "pj": fila["pj"],
                    "pg": fila["pg"],
                    "pe": fila["pe"],
                    "pp": fila["pp"],
                    "gf": fila["gf"],
                    "gc": fila["gc"],
                    "dg": fila["dg"],
                    "puntos": fila["puntos"],
                }
                for posicion, fila in enumerate(filas, start=1)
            ],
        }
        for grupo, filas in sorted(salida["tablas"].items())
    ]
    fases_finales = {"cuartos", "semifinal", "tercer_puesto", "final"}
    partidos = [
        {
            "id": partido["id"],
            "fase": partido["fase"],
            "local": nombres_equipos.get(str(partido["local"]), str(partido["local"])),
            "visitante": nombres_equipos.get(str(partido["visitante"]), str(partido["visitante"])),
            "goles_local": partido["goles_local"],
            "goles_visitante": partido["goles_visitante"],
            "penales": partido["penales"],
        }
        for partido in salida["partidos"]
        if partido["fase"] in fases_finales
    ]
    return {"grupos": grupos, "llave": sorted(partidos, key=lambda partido: partido["id"])}


def ejecutar(comando: list[str], cwd: Path) -> str:
    try:
        proceso = subprocess.run(
            comando,
            cwd=cwd,
            check=True,
            capture_output=True,
            text=True,
            timeout=600,
        )
    except FileNotFoundError as error:
        raise RuntimeError(f"No se encontró el comando '{comando[0]}'.") from error
    except subprocess.TimeoutExpired as error:
        raise RuntimeError(f"El comando '{comando[0]}' superó el límite de 10 minutos.") from error
    except subprocess.CalledProcessError as error:
        detalle = (error.stderr or error.stdout or str(error)).strip()
        raise RuntimeError(f"Falló '{comando[0]}': {detalle[-2000:]}") from error
    return proceso.stdout


def nombre_ejecutable(comando: str) -> str:
    return f"{comando}.cmd" if sys.platform == "win32" else comando


def medir(comando: list[str], cwd: Path, salida: Path, marca: str | None = None) -> dict[str, object]:
    try:
        codigo, stdout, stderr, memoria_mb = metricas.ejecutar_midiendo(comando, cwd, marca)
    except FileNotFoundError as error:
        raise RuntimeError(f"No se encontró el comando '{comando[0]}'.") from error
    except subprocess.TimeoutExpired as error:
        raise RuntimeError(f"El comando '{comando[0]}' superó el límite de 10 minutos.") from error
    if codigo != 0:
        detalle = (stderr or stdout or f"código {codigo}").strip()
        raise RuntimeError(f"Falló '{comando[0]}': {detalle[-2000:]}")
    coincidencia = TIEMPO_RE.search(stdout)
    if not coincidencia:
        raise RuntimeError(f"No se encontró el tiempo de ejecución en la salida de {salida.name}.")
    try:
        resultado = json.loads(salida.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise RuntimeError(f"No se pudo leer la salida JSON de {salida.name}: {error}") from error
    return {
        "tiempo_ms": float(coincidencia.group(1)),
        "memoria_mb": memoria_mb,
        "campeon": resultado.get("campeon"),
        "salida": resultado,
    }


def preparar_java() -> None:
    for comando in ("java", "javac", "mvn"):
        if shutil.which(comando) is None:
            raise RuntimeError(
                f"No se encontró '{comando}'. Para comparar Java instala JDK 21 y Maven, "
                "y verifica que estén disponibles en PATH."
            )
    ejecutar(
        [
            nombre_ejecutable("mvn"),
            "-q",
            "-f",
            str(ROOT / "java" / "pom.xml"),
            "compile",
            "dependency:copy-dependencies",
            "-DoutputDirectory=target/dependency",
        ],
        ROOT,
    )


def classpath_java() -> str:
    separador = ";" if sys.platform == "win32" else ":"
    return str(ROOT / "java" / "target" / "classes") + separador + str(ROOT / "java" / "target" / "dependency" / "*")


def comando_python(opciones: list[str]) -> list[str]:
    return [sys.executable, str(ROOT / "python" / "main.py"), *opciones]


def comando_java(opciones: list[str]) -> list[str]:
    return ["java", "-cp", classpath_java(), "com.copa.Main", *opciones]


def comando_scala(opciones: list[str]) -> list[str]:
    # sbt corre dentro de scala/, así que las rutas se pasan relativas a esa carpeta
    partes = []
    for indice, valor in enumerate(opciones):
        es_ruta = indice > 0 and opciones[indice - 1] in ("--input", "--aleatorios", "--out")
        partes.append(os.path.relpath(valor, ROOT / "scala") if es_ruta else valor)
    return [nombre_ejecutable("sbt"), "--batch", "run " + " ".join(partes)]


COMANDOS = {
    "python": (comando_python, ROOT, None),
    "java": (comando_java, ROOT, None),
    "scala": (comando_scala, ROOT / "scala", "CopaSimulador"),
}


def mediana_o_none(valores: list[object]) -> float | None:
    if not valores or any(valor is None for valor in valores):
        return None
    return round(statistics.median(valores), 2)


def calcular_metricas(resultados, torneos, diferencias, archivo_aleatorios) -> dict[str, dict[str, object]]:
    salida = {}
    for lenguaje, corridas in resultados.items():
        construir, cwd, marca = COMANDOS[lenguaje]
        mediana_ms = statistics.median(corrida["tiempo_ms"] for corrida in corridas)
        pruebas = [{"prueba": "Salida igual a la de los demás lenguajes", "superada": diferencias[lenguaje] is None}]
        pruebas += metricas.pruebas_confiabilidad(
            construir, cwd, ROOT / "data" / "input.json", archivo_aleatorios, marca)
        codigo = metricas.analizar_codigo(lenguaje)
        salida[lenguaje] = {
            "tiempo_ms": round(mediana_ms, 2),
            "memoria_mb": mediana_o_none([corrida["memoria_mb"] for corrida in corridas]),
            "partidos_por_segundo": round(32 * torneos / max(mediana_ms / 1000, 1e-9), 2),
            "lineas_por_funcion": codigo["lineas_por_funcion"],
            "confiabilidad_pct": round(100 * sum(p["superada"] for p in pruebas) / len(pruebas), 2),
            "uso_funcional_pct": codigo["uso_funcional_pct"],
            "codigo": codigo,
            "pruebas": pruebas,
        }
    return salida


def comparar(torneos: int, repeticiones: int) -> dict[str, object]:
    preparar_java()
    if shutil.which("sbt") is None:
        raise RuntimeError(
            "No se encontró 'sbt'. Para comparar Scala instala sbt y verifica que esté en PATH."
        )

    resultados: dict[str, list[dict[str, object]]] = {
        "python": [],
        "java": [],
        "scala": [],
    }
    (ROOT / "out").mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="copa-benchmark-", dir=ROOT / "out") as temporal:
        directorio = Path(temporal)
        archivo_aleatorios = directorio / "aleatorios.txt"
        generador = random.Random(2024)
        archivo_aleatorios.write_text(
            "".join(f"{generador.randrange(1_000_000) / 1_000_000:.6f}\n"
                    for _ in range(NUMEROS_ALEATORIOS)),
            encoding="ascii",
        )

        for numero in range(repeticiones):
            rutas = {
                lenguaje: directorio / f"{lenguaje}-{numero}.json"
                for lenguaje in resultados
            }
            opciones = [
                "--input",
                str(ROOT / "data" / "input.json"),
                "--aleatorios",
                str(archivo_aleatorios),
                "--torneos",
                str(torneos),
            ]
            salida_python = medir(comando_python([*opciones, "--out", str(rutas["python"])]),
                                  ROOT, rutas["python"])
            salida_java = medir(comando_java([*opciones, "--out", str(rutas["java"])]),
                                ROOT, rutas["java"])
            salida_scala = medir(comando_scala([*opciones, "--out", str(rutas["scala"])]),
                                 ROOT / "scala", rutas["scala"], marca="CopaSimulador")
            resultados["python"].append(salida_python)
            resultados["java"].append(salida_java)
            resultados["scala"].append(salida_scala)

        referencia = resultados["python"][-1]["salida"]
        diferencia = None
        diferencias = {"python": None}
        for lenguaje in ("java", "scala"):
            diferencias[lenguaje] = primera_diferencia(referencia, resultados[lenguaje][-1]["salida"])
            if diferencias[lenguaje] and diferencia is None:
                diferencia = f"python / {lenguaje}: {diferencias[lenguaje]}"
        if diferencia:
            diferencias["python"] = diferencia

        tabla_metricas = calcular_metricas(resultados, torneos, diferencias, archivo_aleatorios)

    nombres_equipos = {
        str(equipo["id"]): equipo["nombre"]
        for equipo in json.loads((ROOT / "data" / "input.json").read_text(encoding="utf-8"))["equipos"]
    }
    visualizacion = resumen_visual(referencia, nombres_equipos)
    lenguajes = []
    for clave, nombre in (("python", "Python"), ("java", "Java"), ("scala", "Scala")):
        corridas = resultados[clave]
        lenguajes.append({
            "id": clave,
            "nombre": nombre,
            "tiempos_ms": [corrida["tiempo_ms"] for corrida in corridas],
            "memorias_mb": [corrida["memoria_mb"] for corrida in corridas],
            "mediana_ms": statistics.median(corrida["tiempo_ms"] for corrida in corridas),
            "campeon": nombres_equipos.get(
                str(corridas[-1]["campeon"]), str(corridas[-1]["campeon"])
            ),
        })
    return {
        "torneos": torneos,
        "repeticiones": repeticiones,
        "salidas_iguales": diferencia is None,
        "diferencia": diferencia,
        **visualizacion,
        "lenguajes": lenguajes,
        "metricas": tabla_metricas,
        "clasificacion": metricas.clasificar(tabla_metricas),
        "memoria_disponible": metricas.psutil is not None,
    }


class Manejador(BaseHTTPRequestHandler):
    def responder(self, estado: int, contenido: bytes, tipo: str) -> None:
        self.send_response(estado)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(contenido)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(contenido)

    def responder_json(self, estado: int, datos: dict[str, object]) -> None:
        contenido = json.dumps(datos, ensure_ascii=False).encode("utf-8")
        self.responder(estado, contenido, "application/json; charset=utf-8")

    def do_GET(self) -> None:
        archivos = {
            "/": ("index.html", "text/html; charset=utf-8"),
            "/styles.css": ("styles.css", "text/css; charset=utf-8"),
            "/app.js": ("app.js", "text/javascript; charset=utf-8"),
        }
        ruta = urlparse(self.path).path
        if ruta not in archivos:
            self.responder(404, b"Not found", "text/plain; charset=utf-8")
            return
        nombre, tipo = archivos[ruta]
        self.responder(200, (FRONTEND / nombre).read_bytes(), tipo)

    def do_POST(self) -> None:
        if urlparse(self.path).path != "/api/benchmark":
            self.responder_json(404, {"error": "Ruta no encontrada."})
            return
        try:
            longitud = int(self.headers.get("Content-Length", "0"))
            if not 0 < longitud <= 1024:
                raise ValueError("La solicitud está vacía o es demasiado grande.")
            datos = json.loads(self.rfile.read(longitud))
            torneos, repeticiones = validar_opciones(datos)
            self.responder_json(200, comparar(torneos, repeticiones))
        except (ValueError, json.JSONDecodeError) as error:
            self.responder_json(400, {"error": str(error)})
        except RuntimeError as error:
            self.responder_json(503, {"error": str(error)})
        except OSError as error:
            self.responder_json(500, {"error": f"No se pudo completar la comparación: {error}"})

    def log_message(self, formato: str, *args: object) -> None:
        print(f"[frontend] {self.address_string()} - {formato % args}")


def main() -> None:
    servidor = ThreadingHTTPServer(("127.0.0.1", 8000), Manejador)
    print("Comparador disponible en http://127.0.0.1:8000 (Ctrl+C para detener)")
    try:
        servidor.serve_forever()
    except KeyboardInterrupt:
        print("\nServidor detenido.")
    finally:
        servidor.server_close()


if __name__ == "__main__":
    main()
