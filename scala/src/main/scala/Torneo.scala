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

  // Cada torneo sigue usando los números donde terminó el anterior.
  // Solo se guarda el primer torneo completo (es el que se exporta); de los demás
  // se cuenta el campeón y se descartan, para no acumular memoria.
  // Devuelve el primer torneo y (id del equipo -> veces que fue campeón), ordenado por id.
  def simularVarios(n: Int, datos: DatosTorneo, rngPartidos: Aleatorios,
                    rngApuestas: Aleatorios): (ResultadoTorneo, List[(Int, Int)]) = {
    val (primero, rP1, rA1) = simularTorneo(datos, rngPartidos, rngApuestas)

    @tailrec
    def contar(faltan: Int, rP: Aleatorios, rA: Aleatorios, conteo: Map[Int, Int]): Map[Int, Int] =
      if (faltan == 0) conteo
      else {
        val (resultado, nuevoP, nuevoA) = simularTorneo(datos, rP, rA)
        contar(faltan - 1, nuevoP, nuevoA,
          conteo.updated(resultado.campeon, conteo.getOrElse(resultado.campeon, 0) + 1))
      }

    val campeones = contar(n - 1, rP1, rA1, Map(primero.campeon -> 1))
    (primero, campeones.toList.sortBy(_._1))
  }
}
