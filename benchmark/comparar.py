"""Compara dos salidas del simulador y dice si tienen el mismo contenido.

Uso:
    python3 benchmark/comparar.py out/java.json out/python.json
"""
import json
import sys

if len(sys.argv) != 3:
    print("Uso: python3 benchmark/comparar.py out/java.json out/python.json")
    sys.exit(1)

a = json.load(open(sys.argv[1], encoding="utf-8"))
b = json.load(open(sys.argv[2], encoding="utf-8"))

# "lenguaje" siempre es distinto ("java", "python"...), así que no se compara
a.pop("lenguaje", None)
b.pop("lenguaje", None)

if a == b:
    print("IGUALES ✅")
else:
    print("DISTINTOS ❌")
    for clave in sorted(set(a) | set(b)):
        if a.get(clave) != b.get(clave):
            print(f"  - difiere en: {clave}")
    sys.exit(1)