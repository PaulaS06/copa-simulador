import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

// ===================== Generador de números "aleatorios" =====================
// Nada de Random: los 3 lenguajes leen la misma lista de data/aleatorios.txt y la recorren en orden.
// Es inmutable: siguiente devuelve el número y un generador nuevo que apunta a la siguiente posición.

case class Aleatorios(numeros: Vector[Double], posicion: Int) {

  def siguiente: (Double, Aleatorios) =
    (numeros(posicion), copy(posicion = (posicion + 1) % numeros.size))
}

object Aleatorios {

  def leerNumeros(ruta: String): Vector[Double] =
    Files.readAllLines(Path.of(ruta)).asScala.toVector
      .filter(_.trim.nonEmpty)
      .map(_.trim.toDouble)

  // Empieza en la posición "inicio" (los partidos usan 0 y las apuestas 50000)
  def desde(numeros: Vector[Double], inicio: Int): Aleatorios =
    Aleatorios(numeros, inicio % numeros.size)
}
