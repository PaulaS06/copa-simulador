import scala.annotation.tailrec

object Torneo {

  // ===================== RF: correr todo el torneo =====================

  // Devuelve el resultado y los dos generadores en la posición en que quedaron,
  // para que el siguiente torneo continúe desde ahí.
  def simularTorneo(datos: DatosTorneo, rngPartidos: Aleatorios,
                    rngApuestas: Aleatorios): (ResultadoTorneo, Aleatorios, Aleatorios) = {
    val (grupos, r1) = FaseGrupos.simularFaseGrupos(datos.equipos, rngPartidos)
    val cuartos = Eliminatorias.armarCuartos(grupos)
    val (eliminatorias, r2) = Eliminatorias.simularEliminatorias(cuartos, datos.equipos, r1)
    val todos = grupos ++ eliminatorias

    val tablas = FaseGrupos.agruparEquipos(datos.equipos)
      .map { case (grupo, _) => grupo -> FaseGrupos.calcularTabla(grupo, grupos) }

    val statsJugadores = Estadisticas.estadisticasJugadores(datos.equipos, todos)
    val statsEquipos = Estadisticas.estadisticasEquipos(todos)
    val (apuestas, rA) = Apuestas.simularApuestas(datos.apostadores, todos, datos.equipos, rngApuestas)
    val premios = CalculoPremios.calcularPremios(statsJugadores, statsEquipos, apuestas.mejorApostador)
    val campeon = Eliminatorias.ganador(todos.last)

    (ResultadoTorneo(campeon, todos, tablas, statsJugadores, statsEquipos, premios, apuestas), r2, rA)
  }

  // ===================== RF: simular N torneos seguidos =====================

  // Cada torneo sigue usando los números donde terminó el anterior
  def simularVarios(n: Int, datos: DatosTorneo, rngPartidos: Aleatorios,
                    rngApuestas: Aleatorios): List[ResultadoTorneo] = {
    @tailrec
    def correr(faltan: Int, rP: Aleatorios, rA: Aleatorios, hechos: Vector[ResultadoTorneo]): Vector[ResultadoTorneo] =
      if (faltan == 0) hechos
      else {
        val (resultado, nuevoP, nuevoA) = simularTorneo(datos, rP, rA)
        correr(faltan - 1, nuevoP, nuevoA, hechos :+ resultado)
      }
    correr(n, rngPartidos, rngApuestas, Vector()).toList
  }

  // id del equipo -> veces que fue campeón, ordenado por id
  def contarCampeones(torneos: List[ResultadoTorneo]): List[(Int, Int)] =
    torneos.groupBy(_.campeon).toList
      .map { case (equipo, veces) => (equipo, veces.size) }
      .sortBy(_._1)
}
