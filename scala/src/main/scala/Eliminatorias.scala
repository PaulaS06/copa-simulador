object Eliminatorias {

  // ===================== Quién ganó =====================

  def ganador(p: Partido): Int =
    if (p.golesLocal != p.golesVisitante) {
      if (p.golesLocal > p.golesVisitante) p.local else p.visitante
    }
    else p.penales match {
      case Some(pen) if pen.local > pen.visitante => p.local
      case _                                      => p.visitante
    }

  def perdedor(p: Partido): Int = if (ganador(p) == p.local) p.visitante else p.local

  // ===================== La llave =====================

  // (local, visitante) de cada cuarto: 1A-2B, 1B-2A, 1C-2D, 1D-2C
  def armarCuartos(partidosGrupos: List[Partido]): List[(Int, Int)] = {
    val primero = (g: String) => FaseGrupos.calcularTabla(g, partidosGrupos)(0).equipo
    val segundo = (g: String) => FaseGrupos.calcularTabla(g, partidosGrupos)(1).equipo
    List(
      (primero("A"), segundo("B")),
      (primero("B"), segundo("A")),
      (primero("C"), segundo("D")),
      (primero("D"), segundo("C"))
    )
  }

  // Un partido de eliminatoria: si queda empatado se define por penales
  def jugar(id: Int, fase: String, local: Int, visitante: Int,
            porId: Map[Int, Equipo], rng: Aleatorios): (Partido, Aleatorios) = {
    val (res, r1) = Simulador.simularPartido(porId(local), porId(visitante), rng)
    val (penales, r2) =
      if (res.golesLocal == res.golesVisitante) {
        val (pen, r) = Simulador.simularPenales(r1)
        (Some(pen), r)
      }
      else (None, r1)
    (Partido(id, fase, None, None, local, visitante, res.golesLocal, res.golesVisitante, penales, res.eventos), r2)
  }

  // ===================== RF: cuartos, semis, tercer puesto y final =====================

  // Los partidos 1-24 son de grupos; aquí van del 25 al 32
  def simularEliminatorias(cuartos: List[(Int, Int)], equipos: List[Equipo],
                           rng: Aleatorios): (List[Partido], Aleatorios) = {
    val porId = equipos.map(e => e.id -> e).toMap

    val (cf, r1) = cuartos.zipWithIndex.foldLeft((Vector[Partido](), rng)) {
      case ((jugados, r), ((local, visitante), i)) =>
        val (partido, r2) = jugar(25 + i, "cuartos", local, visitante, porId, r)
        (jugados :+ partido, r2)
    }
    val (sf1, r2) = jugar(29, "semifinal", ganador(cf(0)), ganador(cf(1)), porId, r1)
    val (sf2, r3) = jugar(30, "semifinal", ganador(cf(2)), ganador(cf(3)), porId, r2)
    val (tercero, r4) = jugar(31, "tercer_puesto", perdedor(sf1), perdedor(sf2), porId, r3)
    val (laFinal, r5) = jugar(32, "final", ganador(sf1), ganador(sf2), porId, r4)

    (cf.toList ++ List(sf1, sf2, tercero, laFinal), r5)
  }
}
