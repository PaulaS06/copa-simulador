"""Genera data/input.json: 16 selecciones de la Copa América 2024, 11 jugadores cada una."""
import json
import random
from pathlib import Path

random.seed(2024)  # fija: así el input.json es siempre el mismo

# (nombre, código, grupo, nivel general del equipo)
EQUIPOS = [
    ("Argentina", "ARG", "A", 85), ("Perú", "PER", "A", 72),
    ("Chile", "CHI", "A", 73),     ("Canadá", "CAN", "A", 71),
    ("México", "MEX", "B", 76),    ("Ecuador", "ECU", "B", 76),
    ("Venezuela", "VEN", "B", 71), ("Jamaica", "JAM", "B", 65),
    ("Estados Unidos", "USA", "C", 76), ("Uruguay", "URU", "C", 82),
    ("Panamá", "PAN", "C", 68),    ("Bolivia", "BOL", "C", 64),
    ("Brasil", "BRA", "D", 83),    ("Colombia", "COL", "D", 81),
    ("Paraguay", "PAR", "D", 72),  ("Costa Rica", "CRC", "D", 67),
]

# (posición, cantidad, ajuste de ataque, ajuste de defensa)
FORMACION = [("POR", 1, -50, 5), ("DEF", 4, -25, 3), ("MED", 3, -5, -10), ("DEL", 3, 5, -40)]


def limitar(valor):
    return max(1, min(100, valor))


def crear_jugadores(id_equipo, codigo, nivel):
    jugadores = []
    numero = 1
    for posicion, cantidad, aj_ataque, aj_defensa in FORMACION:
        for _ in range(cantidad):
            jugadores.append({
                "id": id_equipo * 100 + numero,
                "nombre": f"{codigo} {posicion} {numero}",
                "posicion": posicion,
                "ataque": limitar(nivel + aj_ataque + random.randint(-5, 5)),
                "defensa": limitar(nivel + aj_defensa + random.randint(-5, 5)),
                "disciplina": random.randint(50, 95),
            })
            numero += 1
    return jugadores


equipos = [
    {"id": i, "nombre": nombre, "grupo": grupo, "jugadores": crear_jugadores(i, codigo, nivel)}
    for i, (nombre, codigo, grupo, nivel) in enumerate(EQUIPOS, start=1)
]

apostadores = [
    {"id": 1, "nombre": "Ana", "estrategia": "favorito", "saldo_inicial": 1000},
    {"id": 2, "nombre": "Bruno", "estrategia": "sorpresa", "saldo_inicial": 1000},
    {"id": 3, "nombre": "Camila", "estrategia": "empate", "saldo_inicial": 1000},
    {"id": 4, "nombre": "David", "estrategia": "aleatoria", "saldo_inicial": 1000},
]

ruta = Path(__file__).parent / "input.json"
with open(ruta, "w", encoding="utf-8") as archivo:
    json.dump({"equipos": equipos, "apostadores": apostadores}, archivo, ensure_ascii=False, indent=2)

print(f"Listo: {len(equipos)} equipos y {len(apostadores)} apostadores en {ruta}")
