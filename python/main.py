"""CLI compatible con Main.java; ejecutar desde la raíz del repositorio."""

import argparse
import time

from aleatorios import Aleatorios
from simulador import cargar_datos, exportar, simular_varios


def main():
    parser = argparse.ArgumentParser(description="Simulador de Copa América")
    parser.add_argument("--input", default="data/input.json")
    parser.add_argument("--aleatorios", default="data/aleatorios.txt")
    parser.add_argument("--out", default="out/python.json")
    parser.add_argument("--torneos", type=int, default=1)
    args = parser.parse_args()
    if args.torneos < 1:
        parser.error("--torneos debe ser mayor que cero")
    inicio = time.perf_counter_ns()
    datos = cargar_datos(args.input)
    partidos = Aleatorios(args.aleatorios)
    apuestas = Aleatorios(args.aleatorios, 50_000)
    salida = simular_varios(datos, partidos, apuestas, args.torneos)
    exportar(salida, args.out)
    milisegundos = (time.perf_counter_ns() - inicio) / 1_000_000
    print(f"Torneos simulados: {args.torneos} | Campeón del primero: {salida['campeon']} "
          f"| Archivo: {args.out} | Tiempo: {milisegundos:.3f} ms")


if __name__ == "__main__":
    main()
