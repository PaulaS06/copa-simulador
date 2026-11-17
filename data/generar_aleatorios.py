import random

with open("data/aleatorios.txt", "w") as archivo:
    for _ in range(100_000):
        numero = random.randrange(1_000_000) / 1_000_000
        archivo.write(f"{numero:.6f}\n")