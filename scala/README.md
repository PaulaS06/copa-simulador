# Simulador Copa América — Scala

Scala 3.9.0 + sbt 2.1.0. Única dependencia: `ujson` (leer y escribir JSON).

## Correr en IntelliJ

1. Abrir la carpeta `scala/` como proyecto (ya está importado como proyecto sbt).
2. Abrir `src/main/scala/CopaSimulador.scala` y darle ▶ al `main`.
3. Sin argumentos lee `../data/input.json` y `../data/aleatorios.txt` y escribe `../out/scala.json`.
   Para cambiarlos: `Run > Edit Configurations > Program arguments`, por ejemplo `--torneos 100`.

## Correr por consola (desde `scala/`)

```
sbt "run --input ../data/input.json --aleatorios ../data/aleatorios.txt --out ../out/scala.json"
sbt "run --torneos 1000"
```

`data/aleatorios.txt` no está en el repo: hay que usar el mismo archivo que los demás lenguajes.

## Archivos

| Archivo | Funciones del plan |
|---|---|
| `Aleatorios.scala` | generador compartido (inmutable) |
| `Json.scala` | `cargarDatos`, `exportar` |
| `Simulador.scala` | `simularPartido`, `simularPenales` |
| `FaseGrupos.scala` | `simularFaseGrupos`, `calcularTabla` |
| `Eliminatorias.scala` | `simularEliminatorias` |
| `Estadisticas.scala` | `estadisticasJugadores`, `estadisticasEquipos` |
| `CalculoPremios.scala` | `calcularPremios` |
| `Apuestas.scala` | `calcularCuotas`, `generarApuestas`, `resolverApuestas`, `evolucionSaldos` |
| `Torneo.scala` | `simularTorneo`, `simularVarios` |
| `CopaSimulador.scala` | `main` y argumentos |

Comparar con Java: `python benchmark/comparar.py out/java.json out/scala.json`
