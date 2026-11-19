# Simulador de Copa América

Simulación del torneo en Python, Java y Scala, con un comparador local que mide los tres
lenguajes y escoge automáticamente el mejor según los criterios y pesos de la rúbrica.

## 1. Qué hay que instalar

| Herramienta | Para qué | Versión |
|---|---|---|
| Python | Simulador en Python y el comparador | 3.10 o más |
| JDK | Simulador en Java | 21 |
| Maven | Compilar Java y descargar Gson | cualquiera reciente |
| sbt | Compilar y correr Scala | la que trae `scala/project/build.properties` se descarga sola |
| psutil (librería de Python) | Medir la memoria en el comparador | cualquiera reciente |

Sin psutil el comparador funciona igual, pero el criterio de memoria aparece como "no se pudo medir".

### Mac

```bash
brew install python openjdk@21 maven sbt
```

psutil va en un entorno virtual (Homebrew no deja instalar librerías en el Python del sistema).
Se crea **una sola vez**, fuera del repo:

```bash
python3 -m venv ~/.venvs/copa
source ~/.venvs/copa/bin/activate
python3 -m pip install psutil
```

Cada vez que abras una terminal nueva, actívalo antes de usar el comparador:
`source ~/.venvs/copa/bin/activate` (aparece `(copa)` al inicio de la línea).

### Windows

1. Instala Python 3.10+ desde <https://www.python.org/downloads/> (marca "Add Python to PATH").
2. Instala JDK 21 (por ejemplo Eclipse Temurin), Maven y sbt, y verifica que estén en el `PATH`.
3. En PowerShell: `python -m pip install psutil`

Si acabas de instalarlos y VS Code o IntelliJ ya estaban abiertos, reinícialos para que tomen el `PATH` nuevo.

### Comprobar que todo quedó instalado

```bash
python3 --version     # Windows: python --version
java -version         # debe decir 21
mvn -v                # "Java version" debe decir 21
sbt --version
```

## 2. Comparador (lo principal)

Desde la raíz del repositorio:

- **Mac:** `python3 frontend/server.py` (con el entorno `(copa)` activado)
- **Windows:** `python frontend\server.py`

Abre <http://127.0.0.1:8000>, elige cuántos torneos y repeticiones, y dale a comparar.
La primera vez tarda más porque Maven y sbt descargan dependencias y compilan.
Para apagar el servidor: `Ctrl + C`.

El comparador:

1. Corre los tres programas con los mismos equipos y los mismos números aleatorios.
2. Verifica que los tres JSON sean iguales (solo cambia el nombre del lenguaje).
3. Mide los 6 criterios de la rúbrica y arma la clasificación con sus pesos.
4. Muestra la llave eliminatoria y las tablas de grupos.

### Cómo se mide cada criterio

Las reglas exactas están al inicio de `benchmark/metricas.py`.

| Criterio | Peso | Cómo se mide | Mejor |
|---|---|---|---|
| Tiempo de ejecución | 25 % | Mediana del tiempo que imprime cada programa (desde leer los datos hasta escribir el JSON; no incluye compilar ni arrancar Python/JVM) | menor |
| Consumo de memoria | 20 % | Mediana del pico de RAM del proceso (psutil, cada 2 ms). En Scala se mide la JVM del simulador, no sbt | menor |
| Rendimiento | 20 % | Partidos simulados por segundo = 32 × torneos / tiempo | mayor |
| Calidad de código | 15 % | Líneas de código por función (sin comentarios ni líneas vacías) | menor |
| Confiabilidad | 10 % | % de pruebas superadas: salida igual a los demás lenguajes y fallo controlado ante input inexistente, JSON dañado, aleatorios inexistentes, y salida en carpeta nueva | mayor |
| Uso funcional | 10 % | Operaciones funcionales / (operaciones funcionales + ciclos `for`/`while`) | mayor |

En cada criterio el mejor lenguaje recibe 100 y los demás una proporción. El puntaje final
es la suma ponderada; gana el más alto.

## 3. Correr cada lenguaje por separado

Todos aceptan los mismos parámetros: `--input`, `--aleatorios`, `--torneos` y `--out`
(por defecto `data/input.json`, `data/aleatorios.txt`, `1` y `out/<lenguaje>.json`).

`data/aleatorios.txt` no está en Git. Para crearlo: `python3 data/generar_aleatorios.py`.
Para comparar lenguajes, todos deben usar **el mismo** archivo.

- **Python** (desde la raíz): `python3 python/main.py --torneos 10`
- **Java:** desde IntelliJ, ▶ en `java/src/main/java/com/copa/Main.java` (con el proyecto abierto en la raíz del repo).
  Por consola, desde la raíz:
  ```bash
  mvn -q -f java/pom.xml compile dependency:copy-dependencies -DoutputDirectory=target/dependency
  java -cp "java/target/classes:java/target/dependency/*" com.copa.Main --torneos 10
  ```
  (En Windows el separador del classpath es `;` en vez de `:`.)
- **Scala** (desde `scala/`): `sbt "run --torneos 10"`, o en IntelliJ abrir la carpeta `scala/` y darle ▶ a `CopaSimulador.scala`.

### Comparar dos salidas

```bash
python3 benchmark/comparar.py out/java.json out/python.json
```

Debe decir `IGUALES`. Si dice `DISTINTOS`, muestra la primera diferencia.
Para una verificación más completa entre Java y Python, ver `benchmark/verificar.py`.
