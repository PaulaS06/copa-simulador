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


def medir(comando: list[str], cwd: Path, salida: Path) -> dict[str, object]:
    stdout = ejecutar(comando, cwd)
    coincidencia = TIEMPO_RE.search(stdout)
    if not coincidencia:
        raise RuntimeError(f"No se encontró el tiempo de ejecución en la salida de {salida.name}.")
    try:
        resultado = json.loads(salida.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise RuntimeError(f"No se pudo leer la salida JSON de {salida.name}: {error}") from error
    return {
        "tiempo_ms": float(coincidencia.group(1)),
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
            salida_python = medir(
                [
                    sys.executable,
                    str(ROOT / "python" / "main.py"),
                    *opciones,
                    "--out",
                    str(rutas["python"]),
                ],
                ROOT,
                rutas["python"],
            )
            classpath = str(ROOT / "java" / "target" / "classes") + (
                ";" if sys.platform == "win32" else ":"
            ) + str(ROOT / "java" / "target" / "dependency" / "*")
            salida_java = medir(
                [
                    "java",
                    "-cp",
                    classpath,
                    "com.copa.Main",
                    *opciones,
                    "--out",
                    str(rutas["java"]),
                ],
                ROOT,
                rutas["java"],
            )
            comando_scala = (
                f'run --input {os.path.relpath(ROOT / "data" / "input.json", ROOT / "scala")} '
                f'--aleatorios {os.path.relpath(archivo_aleatorios, ROOT / "scala")} '
                f'--torneos {torneos} --out {os.path.relpath(rutas["scala"], ROOT / "scala")}'
            )
            salida_scala = medir(
                [nombre_ejecutable("sbt"), "--batch", comando_scala],
                ROOT / "scala",
                rutas["scala"],
            )
            resultados["python"].append(salida_python)
            resultados["java"].append(salida_java)
            resultados["scala"].append(salida_scala)

    referencia = resultados["python"][-1]["salida"]
    diferencia = None
    for lenguaje in ("java", "scala"):
        diferencia = primera_diferencia(referencia, resultados[lenguaje][-1]["salida"])
        if diferencia:
            diferencia = f"python / {lenguaje}: {diferencia}"
            break

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
