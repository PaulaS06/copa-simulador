"""Compara dos salidas del simulador y dice si tienen el mismo contenido.

Uso:
    python3 benchmark/comparar.py out/java.json out/python.json
"""
import argparse
import json
from pathlib import Path


def primera_diferencia(a, b, ruta="$"):
    if type(a) is not type(b):
        return f"{ruta}: tipos distintos ({type(a).__name__}, {type(b).__name__})"
    if isinstance(a, dict):
        if a.keys() != b.keys():
            return f"{ruta}: claves distintas ({sorted(a.keys() - b.keys())}, {sorted(b.keys() - a.keys())})"
        for clave in a:
            diferencia = primera_diferencia(a[clave], b[clave], f"{ruta}.{clave}")
            if diferencia:
                return diferencia
    elif isinstance(a, list):
        if len(a) != len(b):
            return f"{ruta}: longitudes distintas ({len(a)}, {len(b)})"
        for i, (izquierda, derecha) in enumerate(zip(a, b)):
            diferencia = primera_diferencia(izquierda, derecha, f"{ruta}[{i}]")
            if diferencia:
                return diferencia
    elif a != b:
        return f"{ruta}: {a!r} != {b!r}"
    return None


def comparar(a, b, lenguajes=None):
    if lenguajes is not None:
        for salida, esperado in zip((a, b), lenguajes):
            if salida.get("lenguaje") != esperado:
                return f"$.lenguaje: se esperaba {esperado!r}, se obtuvo {salida.get('lenguaje')!r}"
    return primera_diferencia({k: v for k, v in a.items() if k != "lenguaje"},
                              {k: v for k, v in b.items() if k != "lenguaje"})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("izquierda")
    parser.add_argument("derecha")
    parser.add_argument("--lenguajes", nargs=2, metavar=("IZQUIERDA", "DERECHA"))
    args = parser.parse_args()
    a, b = (json.loads(Path(p).read_text(encoding="utf-8")) for p in (args.izquierda, args.derecha))
    diferencia = comparar(a, b, args.lenguajes)
    if diferencia:
        print(f"DISTINTOS: {diferencia}")
        return 1
    print("IGUALES: todo el contenido salvo lenguaje")
    if args.lenguajes:
        print(f"lenguaje verificado por separado: {args.lenguajes[0]} / {args.lenguajes[1]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
