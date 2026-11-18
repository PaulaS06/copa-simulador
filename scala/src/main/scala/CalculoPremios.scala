import scala.math.Ordering.Double.TotalOrdering

object CalculoPremios {

  // El primero de la lista según el criterio (el criterio da una clave: menor = mejor)
  def mejorSegun[A, K](candidatos: List[A], criterio: A => K)(using Ordering[K]): A =
    candidatos.minBy(criterio)

  // ===================== Criterios: cada uno es una lambda guardada en una variable =====================

  val mejorJugador: StatJugador => (Int, Int) =
    s => (-(10 * s.goles - 2 * s.amarillas - 5 * s.rojas - s.faltas), s.jugador)

  val botaDeOro: StatJugador => (Int, Int, Int) =
    s => (-s.goles, s.amarillas + s.rojas, s.jugador)

  val mejorEquipo: StatEquipo => (Int, Int, Int, Int) =
    s => (-s.puntos, -(s.gf - s.gc), -s.gf, s.equipo)

  val vallaMenosVencida: StatEquipo => (Double, Int) =
    s => (s.gc.toDouble / s.pj, s.equipo)

  val fairPlay: StatEquipo => (Int, Int, Int) =
    s => (s.amarillas + 3 * s.rojas, s.faltas, s.equipo)

  // ===================== RF: todos los premios =====================

  def calcularPremios(jugadores: List[StatJugador], equipos: List[StatEquipo], mejorApostador: Int): Premios =
    Premios(
      mejorSegun(jugadores, mejorJugador).jugador,
      mejorSegun(jugadores, botaDeOro).jugador,
      mejorSegun(equipos, mejorEquipo).equipo,
      mejorSegun(equipos, vallaMenosVencida).equipo,
      mejorSegun(equipos, fairPlay).equipo,
      mejorApostador
    )
}
