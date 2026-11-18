object Estadisticas {

  // clave (jugador o equipo) -> tipo de evento -> cantidad
  type Conteo = Map[Int, Map[String, Int]]

  // Agrupa todos los eventos del torneo por una clave y luego por tipo
  def contarEventos(partidos: List[Partido], clave: Evento => Int): Conteo =
    partidos.flatMap(_.eventos)
      .groupBy(clave)
      .map { case (k, eventos) => k -> eventos.groupBy(_.tipo).map { case (tipo, lista) => tipo -> lista.size } }

  // Cuántos eventos de un tipo tiene una clave; 0 si no tiene ninguno
  def contar(conteo: Conteo, clave: Int, tipo: String): Int =
    conteo.getOrElse(clave, Map()).getOrElse(tipo, 0)

  // ===================== RF: estadísticas por jugador =====================

  def estadisticasJugadores(equipos: List[Equipo], partidos: List[Partido]): List[StatJugador] = {
    val conteo = contarEventos(partidos, _.jugador)
    equipos.flatMap(e => e.jugadores.map(j =>
        StatJugador(j.id, e.id,
          contar(conteo, j.id, "gol"),
          contar(conteo, j.id, "falta"),
          contar(conteo, j.id, "amarilla"),
          contar(conteo, j.id, "roja"))))
      .sortBy(_.jugador)
  }

  // ===================== RF: estadísticas por equipo =====================

  // Cuenta todos los partidos del torneo (grupos y eliminatorias)
  def estadisticasEquipos(partidos: List[Partido]): List[StatEquipo] = {
    val conteo = contarEventos(partidos, _.equipo)
    partidos.flatMap(FilaTabla.dePartido)
      .groupBy(_.equipo)
      .values
      .map(_.reduce(_.sumar(_)))
      .map(f => StatEquipo(f.equipo, f.pj, f.puntos, f.gf, f.gc,
        contar(conteo, f.equipo, "falta"),
        contar(conteo, f.equipo, "amarilla"),
        contar(conteo, f.equipo, "roja")))
      .toList
      .sortBy(_.equipo)
  }
}
