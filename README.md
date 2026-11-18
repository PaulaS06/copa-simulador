# Simulador de Copa América

Simulación del torneo en Python, Java y Scala, con un comparador local de rendimiento.

## Comparador

Requisitos: Python 3.10+, JDK 21, Maven y sbt disponibles en `PATH`.
Si acabas de instalarlos y VS Code ya estaba abierto, reinícialo para que recoja el `PATH` actualizado.

Desde la raíz del repositorio:

```powershell
python frontend\server.py
```

Abre <http://127.0.0.1:8000>. El comparador ejecuta los tres programas con los mismos
equipos y una secuencia determinista de números aleatorios. Elige cuántos torneos correr
y cuántas repeticiones hacer; los resultados muestran la mediana, un gráfico y una tabla
por lenguaje, la llave eliminatoria y las tablas de grupos. También verifican que los JSON
sean iguales, ignorando solo el nombre del lenguaje.

La medición abarca desde la lectura de los datos hasta la escritura del JSON. No incluye
compilación ni el arranque de Python/JVM. La primera comparación puede tardar más mientras
Maven y sbt descargan dependencias y compilan.

## Ejecución por consola

- Python: `python python\main.py --torneos 10`
- Scala: desde `scala\`, `sbt "run --torneos 10"`
- Java: compilar con Maven y ejecutar `com.copa.Main` con Gson en el classpath.

Para comprobar la equivalencia de Java y Python, consulta `benchmark\verificar.py`.
