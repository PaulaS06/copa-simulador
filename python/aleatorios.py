"""Lectura circular; cada instancia mantiene su propio cursor entre torneos."""

from pathlib import Path


class Aleatorios:
    def __init__(self, ruta, inicio=0):
        self.numeros = tuple(map(float, Path(ruta).read_text(encoding="utf-8").splitlines()))
        if not self.numeros:
            raise ValueError("El archivo de aleatorios está vacío")
        self.posicion = inicio % len(self.numeros)

    def siguiente(self):
        numero = self.numeros[self.posicion]
        self.posicion = (self.posicion + 1) % len(self.numeros)
        return numero
