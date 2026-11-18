// ===================== Datos de entrada (input.json) =====================

case class Jugador(id: Int, nombre: String, posicion: String,
                   ataque: Int, defensa: Int, disciplina: Int)

case class Equipo(id: Int, nombre: String, grupo: String, jugadores: List[Jugador])

case class Apostador(id: Int, nombre: String, estrategia: String, saldoInicial: Int)

case class DatosTorneo(equipos: List[Equipo], apostadores: List[Apostador])

// ===================== Partidos =====================

// tipo: "gol", "falta", "amarilla" o "roja"
case class Evento(minuto: Int, tipo: String, equipo: Int, jugador: Int)

case class Penales(local: Int, visitante: Int)

case class ResultadoPartido(golesLocal: Int, golesVisitante: Int, eventos: List[Evento])

// fase: "grupos", "cuartos", "semifinal", "tercer_puesto" o "final".
// grupo y fecha solo existen en la fase de grupos; penales solo si hubo empate en eliminatorias.
case class Partido(id: Int, fase: String, grupo: Option[String], fecha: Option[Int],
                   local: Int, visitante: Int, golesLocal: Int, golesVisitante: Int,
                   penales: Option[Penales], eventos: List[Evento])

// ===================== Tabla de posiciones =====================

case class FilaTabla(equipo: Int, pj: Int, pg: Int, pe: Int, pp: Int,
                     gf: Int, gc: Int, dg: Int, puntos: Int) {

  // Suma dos filas del mismo equipo
  def sumar(otra: FilaTabla): FilaTabla =
    FilaTabla(equipo, pj + otra.pj, pg + otra.pg, pe + otra.pe, pp + otra.pp,
      gf + otra.gf, gc + otra.gc, dg + otra.dg, puntos + otra.puntos)
}

object FilaTabla {

  // La fila de UN equipo en UN partido
  def dePartido(equipo: Int, golesAFavor: Int, golesEnContra: Int): FilaTabla = {
    val pg = if (golesAFavor > golesEnContra) 1 else 0
    val pe = if (golesAFavor == golesEnContra) 1 else 0
    val pp = if (golesAFavor < golesEnContra) 1 else 0
    FilaTabla(equipo, 1, pg, pe, pp, golesAFavor, golesEnContra, golesAFavor - golesEnContra, 3 * pg + pe)
  }

  // Las dos filas (local y visitante) que deja un partido
  def dePartido(p: Partido): List[FilaTabla] =
    List(dePartido(p.local, p.golesLocal, p.golesVisitante),
      dePartido(p.visitante, p.golesVisitante, p.golesLocal))
}

// ===================== Estadísticas y premios =====================

case class StatJugador(jugador: Int, equipo: Int, goles: Int, faltas: Int, amarillas: Int, rojas: Int)

case class StatEquipo(equipo: Int, pj: Int, puntos: Int, gf: Int, gc: Int,
                      faltas: Int, amarillas: Int, rojas: Int)

case class Premios(mejorJugador: Int, botaDeOro: Int, mejorEquipo: Int,
                   vallaMenosVencida: Int, fairPlay: Int, mejorApostador: Int)

// ===================== Apuestas =====================

case class Cuotas(pL: Double, pE: Double, pV: Double, cuotaL: Double, cuotaE: Double, cuotaV: Double)

// Lo que un apostador decide antes del partido (todavía no se sabe si acertó)
case class Pronostico(apostador: Int, pronostico: String, cuota: Double, monto: Int)

case class Apuesta(partido: Int, apostador: Int, pronostico: String, cuota: Double, monto: Int, acerto: Boolean)

case class SaldoApostador(apostador: Int, historial: List[Int])

case class ResultadoApuestas(apuestas: List[Apuesta], saldos: List[SaldoApostador], mejorApostador: Int)

// ===================== Torneo completo =====================

case class ResultadoTorneo(campeon: Int, partidos: List[Partido], tablas: List[(String, List[FilaTabla])],
                           estadisticasJugadores: List[StatJugador], estadisticasEquipos: List[StatEquipo],
                           premios: Premios, apuestas: ResultadoApuestas)
