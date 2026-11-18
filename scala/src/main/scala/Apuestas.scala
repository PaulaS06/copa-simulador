object Apuestas {

  def cuota(probabilidad: Double): Double = math.floor(100.0 / probabilidad) / 100.0

  def calcularCuotas(local: Equipo, visitante: Equipo): Cuotas = {
    val fL = Simulador.ataqueEquipo(local) + Simulador.defensaEquipo(local)
    val fV = Simulador.ataqueEquipo(visitante) + Simulador.defensaEquipo(visitante)
    val pL = 0.75 * fL / (fL + fV)
    val pE = 0.25
    val pV = 0.75 * fV / (fL + fV)
    Cuotas(pL, pE, pV, cuota(pL), cuota(pE), cuota(pV))
  }

  def cuotaDe(c: Cuotas, pronostico: String): Double = pronostico match {
    case "L" => c.cuotaL
    case "E" => c.cuotaE
    case _   => c.cuotaV
  }

  //  Estrategias: cada una es una lambda 

  // Reciben las cuotas y el generador; devuelven el pronóstico (L, E o V) y el generador.
  // Solo "aleatoria" gasta un número.
  val estrategias: Map[String, (Cuotas, Aleatorios) => (String, Aleatorios)] = Map(
    "favorito"  -> ((c, rng) => (if (c.pL >= c.pV) "L" else "V", rng)),
    "sorpresa"  -> ((c, rng) => (if (c.pL >= c.pV) "V" else "L", rng)),
    "empate"    -> ((_, rng) => ("E", rng)),
    "aleatoria" -> ((_, rng) => {
      val (u, siguiente) = rng.siguiente
      (if (u < 1.0 / 3) "L" else if (u < 2.0 / 3) "E" else "V", siguiente)
    })
  )

  // Resultado al minuto 90: L, E o V (los penales no cuentan)
  def resultado(p: Partido): String =
    if (p.golesLocal > p.golesVisitante) "L"
    else if (p.golesLocal == p.golesVisitante) "E"
    else "V"

  //  RF: pronósticos de un partido 

  // Solo apuestan quienes tienen saldo; cada uno apuesta el 10% (mínimo 1)
  def generarApuestas(cuotas: Cuotas, apostadores: List[Apostador], saldos: Map[Int, Int],
                      rng: Aleatorios): (List[Pronostico], Aleatorios) = {
    val (pronosticos, rngFinal) = apostadores
      .filter(a => saldos(a.id) > 0)
      .foldLeft((Vector[Pronostico](), rng)) { case ((hechos, r), a) =>
        val (pronostico, r2) = estrategias(a.estrategia)(cuotas, r)
        val monto = math.max(1, saldos(a.id) / 10)
        (hechos :+ Pronostico(a.id, pronostico, cuotaDe(cuotas, pronostico), monto), r2)
      }
    (pronosticos.toList, rngFinal)
  }

  //  RF: resolver las apuestas de un partido 

  def resolverApuestas(partido: Partido, pronosticos: List[Pronostico]): List[Apuesta] =
    pronosticos.map(p => Apuesta(partido.id, p.apostador, p.pronostico, p.cuota, p.monto,
      p.pronostico == resultado(partido)))

  // Si acertó recibe monto * cuota; si no, pierde el monto
  def nuevoSaldo(saldo: Int, apuesta: Apuesta): Int =
    if (apuesta.acerto) saldo - apuesta.monto + math.floor(apuesta.monto * apuesta.cuota).toInt
    else saldo - apuesta.monto

  def aplicarApuestas(saldos: Map[Int, Int], apuestas: List[Apuesta]): Map[Int, Int] =
    apuestas.foldLeft(saldos)((acc, a) => acc.updated(a.apostador, nuevoSaldo(acc(a.apostador), a)))

  //  RF: evolución del saldo 

  // Recibe los saldos de todos después de cada partido y arma el historial de cada apostador
  def evolucionSaldos(apostadores: List[Apostador], saldosPorPartido: List[Map[Int, Int]]): List[SaldoApostador] =
    apostadores.map(a => SaldoApostador(a.id, saldosPorPartido.map(_(a.id))))

  //  Todo el torneo 

  def simularApuestas(apostadores: List[Apostador], partidos: List[Partido], equipos: List[Equipo],
                      rng: Aleatorios): (ResultadoApuestas, Aleatorios) = {
    val porId = equipos.map(e => e.id -> e).toMap
    val ordenados = apostadores.sortBy(_.id)
    val inicial = ordenados.map(a => a.id -> a.saldoInicial).toMap

    val (saldosPorPartido, apuestas, rngFinal) =
      partidos.sortBy(_.id).foldLeft((Vector(inicial), Vector[Apuesta](), rng)) {
        case ((historia, hechas, r), partido) =>
          val cuotas = calcularCuotas(porId(partido.local), porId(partido.visitante))
          val (pronosticos, r2) = generarApuestas(cuotas, ordenados, historia.last, r)
          val resueltas = resolverApuestas(partido, pronosticos)
          (historia :+ aplicarApuestas(historia.last, resueltas), hechas ++ resueltas, r2)
      }

    val saldos = evolucionSaldos(ordenados, saldosPorPartido.toList)
    val mejorApostador = saldos.minBy(s => (-s.historial.last, s.apostador)).apostador
    (ResultadoApuestas(apuestas.toList, saldos, mejorApostador), rngFinal)
  }
}
