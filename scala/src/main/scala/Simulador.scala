import scala.annotation.tailrec

object Simulador {

  val factorGol = 0.012
  val probFalta = 0.15
  val probRoja = 0.01
  val factorAmarilla = 0.25
  val probPenal = 0.75

  // ===================== Fuerza del equipo =====================

  def ataqueEquipo(equipo: Equipo): Double = equipo.jugadores.map(_.ataque).sum / 11.0

  def defensaEquipo(equipo: Equipo): Double = equipo.jugadores.map(_.defensa).sum / 11.0

  // ===================== Ayudantes =====================

  // Jugadores que siguen en la cancha, ordenados por id
  def disponibles(equipo: Equipo, expulsados: Set[Int]): List[Jugador] =
    equipo.jugadores.filter(j => !expulsados.contains(j.id)).sortBy(_.id)

  // Escoge un jugador con más probabilidad mientras más ataque tenga.
  // Se gana el primero cuyo ataque acumulado supere u * (suma de ataques).
  def elegirPonderado(jugadores: List[Jugador], u: Double): Jugador = {
    val objetivo = u * jugadores.map(_.ataque).sum
    val acumulados = jugadores.map(_.ataque).scanLeft(0)(_ + _).tail
    jugadores.zip(acumulados)
      .find { case (_, acumulado) => acumulado > objetivo }
      .map(_._1)
      .getOrElse(jugadores.last)
  }

  // ===================== RF: simular un partido minuto a minuto =====================

  // Todo lo que cambia durante el partido. Cada minuto recibe un estado y devuelve uno nuevo.
  case class EstadoPartido(rng: Aleatorios, golesLocal: Int, golesVisitante: Int, eventos: Vector[Evento],
                           expulsados: Set[Int], amarillas: Map[Int, Int])

  def simularPartido(local: Equipo, visitante: Equipo, rng: Aleatorios): (ResultadoPartido, Aleatorios) = {
    val pGL = factorGol * ataqueEquipo(local) / defensaEquipo(visitante)
    val pGV = factorGol * ataqueEquipo(visitante) / defensaEquipo(local)

    val inicio = EstadoPartido(rng, 0, 0, Vector(), Set(), Map())
    val fin = (1 to 90).foldLeft(inicio)((estado, minuto) => jugarMinuto(local, visitante, pGL, pGV, estado, minuto))
    (ResultadoPartido(fin.golesLocal, fin.golesVisitante, fin.eventos.toList), fin.rng)
  }

  def jugarMinuto(local: Equipo, visitante: Equipo, pGL: Double, pGV: Double,
                  estado: EstadoPartido, minuto: Int): EstadoPartido = {
    val (u, rng) = estado.rng.siguiente
    val siguiente = estado.copy(rng = rng)
    if (u < pGL) {
      val conGol = gol(local, minuto, siguiente)
      conGol.copy(golesLocal = conGol.golesLocal + 1)
    }
    else if (u < pGL + pGV) {
      val conGol = gol(visitante, minuto, siguiente)
      conGol.copy(golesVisitante = conGol.golesVisitante + 1)
    }
    else if (u < pGL + pGV + probFalta) falta(local, visitante, minuto, siguiente)
    else siguiente
  }

  def gol(equipo: Equipo, minuto: Int, estado: EstadoPartido): EstadoPartido = {
    val (u2, rng) = estado.rng.siguiente
    val autor = elegirPonderado(disponibles(equipo, estado.expulsados), u2)
    estado.copy(rng = rng, eventos = estado.eventos :+ Evento(minuto, "gol", equipo.id, autor.id))
  }

  def falta(local: Equipo, visitante: Equipo, minuto: Int, estado: EstadoPartido): EstadoPartido = {
    val (u2, rng2) = estado.rng.siguiente
    val infractor = if (u2 < 0.5) local else visitante
    val enCancha = disponibles(infractor, estado.expulsados)

    val (u3, rng3) = rng2.siguiente
    val jugador = enCancha(math.floor(u3 * enCancha.size).toInt)
    val conFalta = estado.copy(eventos = estado.eventos :+ Evento(minuto, "falta", infractor.id, jugador.id))

    val (u4, rng4) = rng3.siguiente
    val umbralAmarilla = probRoja + factorAmarilla * (100 - jugador.disciplina) / 100
    val tarjeta = conFalta.copy(rng = rng4)

    if (u4 < probRoja) expulsar(tarjeta, minuto, infractor, jugador)
    else if (u4 < umbralAmarilla) {
      val total = tarjeta.amarillas.getOrElse(jugador.id, 0) + 1
      val conAmarilla = tarjeta.copy(amarillas = tarjeta.amarillas.updated(jugador.id, total))
      if (total == 2) expulsar(conAmarilla, minuto, infractor, jugador)
      else conAmarilla.copy(eventos = conAmarilla.eventos :+ Evento(minuto, "amarilla", infractor.id, jugador.id))
    }
    else tarjeta
  }

  def expulsar(estado: EstadoPartido, minuto: Int, equipo: Equipo, jugador: Jugador): EstadoPartido =
    estado.copy(eventos = estado.eventos :+ Evento(minuto, "roja", equipo.id, jugador.id),
      expulsados = estado.expulsados + jugador.id)

  // ===================== RF: definir por penales =====================

  def simularPenales(rng: Aleatorios): (Penales, Aleatorios) = {
    val (local, visitante, rngFinal) = (1 to 5).foldLeft((0, 0, rng)) { case ((l, v, r), _) =>
      val (uL, r1) = r.siguiente
      val (uV, r2) = r1.siguiente
      (l + (if (uL < probPenal) 1 else 0), v + (if (uV < probPenal) 1 else 0), r2)
    }
    if (local != visitante) (Penales(local, visitante), rngFinal)
    else muerteSubita(local, visitante, rngFinal)
  }

  // Un par de cobros más; si los dos anotan o los dos fallan, se repite
  @tailrec
  def muerteSubita(local: Int, visitante: Int, rng: Aleatorios): (Penales, Aleatorios) = {
    val (uL, r1) = rng.siguiente
    val (uV, r2) = r1.siguiente
    val anotaLocal = uL < probPenal
    val anotaVisitante = uV < probPenal
    val nuevoLocal = local + (if (anotaLocal) 1 else 0)
    val nuevoVisitante = visitante + (if (anotaVisitante) 1 else 0)
    if (anotaLocal != anotaVisitante) (Penales(nuevoLocal, nuevoVisitante), r2)
    else muerteSubita(nuevoLocal, nuevoVisitante, r2)
  }
}
