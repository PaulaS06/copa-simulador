import java.nio.file.{Files, Path}

object CopaSimulador {

  // Convierte ["--input", "a.json", "--out", "b.json"] en Map(input -> a.json, out -> b.json)
  def leerArgumentos(args: Array[String]): Map[String, String] =
    args.grouped(2)
      .collect { case Array(clave, valor) => clave.stripPrefix("--") -> valor }
      .toMap

  // Desde IntelliJ el programa corre dentro de scala/, desde la terminal en la raíz del repo.
  // Si no se encuentra la carpeta data/ aquí, se busca un nivel arriba.
  val raiz: String = if (Files.exists(Path.of("data"))) "" else "../"

  def main(args: Array[String]): Unit = {
    val opciones = leerArgumentos(args)
    val rutaInput = opciones.getOrElse("input", raiz + "data/input.json")
    val rutaAleatorios = opciones.getOrElse("aleatorios", raiz + "data/aleatorios.txt")
    val rutaSalida = opciones.getOrElse("out", raiz + "out/scala.json")
    val cantidadTorneos = opciones.get("torneos").flatMap(_.toIntOption).filter(_ > 0).getOrElse(1)

    val inicio = System.nanoTime()

    val datos = Json.cargarDatos(rutaInput)
    val numeros = Aleatorios.leerNumeros(rutaAleatorios)
    val aleatoriosPartidos = Aleatorios.desde(numeros, 0)
    val aleatoriosApuestas = Aleatorios.desde(numeros, 50000)

    val (primero, campeones) = Torneo.simularVarios(cantidadTorneos, datos, aleatoriosPartidos, aleatoriosApuestas)
    val resumen =
      if (cantidadTorneos > 1) Some(Json.resumenVarios(cantidadTorneos, campeones))
      else None

    Json.exportar(primero, resumen, rutaSalida)

    val milisegundos = (System.nanoTime() - inicio) / 1000000.0
    println(s"Torneos simulados: $cantidadTorneos | Campeón del primero: ${primero.campeon} " +
      s"| Archivo: $rutaSalida | Tiempo: $milisegundos ms")
  }
}
