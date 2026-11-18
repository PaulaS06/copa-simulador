"""Compila Java sin modificarlo, ejecuta ambos CLI y compara sus JSON completos.

Requiere Java/Javac >= 21 y Gson 2.11.0 (ver README). Solo la preparación de
fixtures usa random; ninguno de los simuladores genera números aleatorios.
"""

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import random
import subprocess
import sys

from comparar import comparar


ROOT = Path(__file__).resolve().parents[1]


def ejecutar(comando):
    subprocess.run(list(map(str, comando)), cwd=ROOT, check=True)


def escribir_json(ruta, datos):
    ruta.write_text(json.dumps(datos, ensure_ascii=False, indent=2), encoding="utf-8")


def generar_numeros(ruta, cantidad, semilla):
    rng = random.Random(semilla)
    ruta.write_text("".join(f"{rng.randrange(1_000_000) / 1_000_000:.6f}\n"
                           for _ in range(cantidad)), encoding="ascii")


def leer(ruta):
    return json.loads(ruta.read_text(encoding="utf-8"))


def comprobar(java, python):
    diferencia = comparar(leer(java), leer(python), ("java", "python"))
    if diferencia:
        raise AssertionError(f"{java.name} / {python.name}: {diferencia}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    parser.add_argument("--gson", default="java/target/dependency/gson-2.11.0.jar")
    args = parser.parse_args()
    destino = ROOT / "out/verificacion"
    destino.mkdir(parents=True, exist_ok=True)
    clases = ROOT / "java/target/classes"
    clases.mkdir(parents=True, exist_ok=True)
    gson = (ROOT / args.gson).resolve()
    if not gson.is_file():
        parser.error(f"Falta {gson}; consulta la preparación en README.md")
    fuentes = sorted((ROOT / "java/src/main/java").rglob("*.java"))
    ejecutar([args.javac, "--release", "21", "-encoding", "UTF-8", "-cp", gson,
              "-d", clases, *fuentes])
    classpath = os.pathsep.join(map(str, (clases, gson)))
    entrada = ROOT / "data/input.json"
    numeros = ROOT / "data/aleatorios.txt"
    if not numeros.exists():
        generar_numeros(numeros, 100_000, 2024)
        print("Creado data/aleatorios.txt con semilla de preparación 2024", flush=True)
    cortos = destino / "aleatorios-257.txt"
    generar_numeros(cortos, 257, 42)
    datos = copy.deepcopy(leer(entrada))
    datos["equipos"].reverse()
    for equipo in datos["equipos"]:
        equipo["jugadores"].reverse()
    datos["apostadores"].extend([
        dict(id=5, nombre="Sin saldo", estrategia="aleatoria", saldo_inicial=0),
        dict(id=6, nombre="Saldo mínimo", estrategia="aleatoria", saldo_inicial=1),
        dict(id=7, nombre="Otra aleatoria", estrategia="aleatoria", saldo_inicial=1000),
    ])
    datos["apostadores"].reverse()
    variante = destino / "input-desordenado.json"
    escribir_json(variante, datos)
    casos = [
        ("uno", entrada, numeros, 1),
        ("diez", entrada, numeros, 10),
        ("cien", entrada, numeros, 100),
        ("circular-uno", entrada, cortos, 1),
        ("circular-diez", entrada, cortos, 10),
        ("desordenado-uno", variante, numeros, 1),
        ("desordenado-diez", variante, cortos, 10),
    ]
    informe = []
    for nombre, input_path, rng_path, cantidad in casos:
        java = destino / f"{nombre}-java.json"
        python = destino / f"{nombre}-python.json"
        opciones = ["--input", input_path, "--aleatorios", rng_path, "--torneos", cantidad]
        ejecutar([args.java, "-cp", classpath, "com.copa.Main", *opciones, "--out", java])
        ejecutar([sys.executable, "python/main.py", *opciones, "--out", python])
        comprobar(java, python)
        informe.append(dict(caso=nombre, torneos=cantidad, iguales=True,
                            lenguaje_java="java", lenguaje_python="python",
                            input_sha256=hashlib.sha256(input_path.read_bytes()).hexdigest(),
                            aleatorios_sha256=hashlib.sha256(rng_path.read_bytes()).hexdigest()))
        print(f"OK {nombre}: JSON completo y lenguaje", flush=True)

    # Main solo exporta el primero. Este adaptador de prueba usa las clases
    # originales para observar apuestas y cursores de CADA torneo.
    audit_dir = destino / "continuidad"
    audit_dir.mkdir(exist_ok=True)
    fuente = destino / "Auditar.java"
    fuente.write_text('''import com.copa.*;
import java.lang.reflect.Field;
public class Auditar {
    public static void main(String[] args) throws Exception {
        var datos = Datos.cargarDatos(args[0]);
        var partidos = new Aleatorios(args[1]);
        var apuestas = new Aleatorios(args[1], 50000);
        Field posicion = Aleatorios.class.getDeclaredField("posicion");
        posicion.setAccessible(true);
        for (int i = 1; i <= 30; i++) {
            var torneo = Torneo.simularTorneo(datos, partidos, apuestas);
            Exportar.exportar(torneo, null, args[2] + "/java-" + i + ".json");
            java.nio.file.Files.writeString(java.nio.file.Path.of(args[2], "cursor-" + i + ".json"),
                "[" + posicion.getInt(partidos) + "," + posicion.getInt(apuestas) + "]");
        }
    }
}
''', encoding="utf-8")
    ejecutar([args.javac, "--release", "21", "-cp", classpath, "-d", clases, fuente])
    ejecutar([args.java, "-cp", classpath, "Auditar", variante, cortos, audit_dir])
    sys.path.insert(0, str(ROOT / "python"))
    from aleatorios import Aleatorios
    from simulador import simular_torneo, exportar
    partidos, apuestas = Aleatorios(cortos), Aleatorios(cortos, 50_000)
    for i in range(1, 31):
        salida = simular_torneo(datos, partidos, apuestas)
        python = audit_dir / f"python-{i}.json"
        exportar(salida, python)
        comprobar(audit_dir / f"java-{i}.json", python)
        if leer(audit_dir / f"cursor-{i}.json") != [partidos.posicion, apuestas.posicion]:
            raise AssertionError(f"Cursores distintos después del torneo {i}")
    informe.append(dict(caso="continuidad", torneos=30, iguales=True, cursores_iguales=True))
    referencia = leer(destino / "uno-java.json")
    copia = leer(destino / "uno-python.json")
    copia["lenguaje"] = "java"
    if comparar(referencia, copia, ("java", "python")) is None:
        raise AssertionError("El comparador no detectó lenguaje incorrecto")
    copia["lenguaje"] = "python"
    copia["partidos"][-1]["goles_local"] += 1
    if comparar(referencia, copia, ("java", "python")) is None:
        raise AssertionError("El comparador no detectó una diferencia anidada")
    escribir_json(destino / "informe.json", informe)
    print("OK continuidad: 30 JSON completos y ambos cursores; controles negativos OK")
    print("Todos los casos coinciden. Informe: out/verificacion/informe.json")


if __name__ == "__main__":
    main()
