object FaseGrupos {

  // Quién juega contra quién en cada fecha (posiciones dentro del grupo ordenado por id)
  val plantilla = List(
    List((0, 1), (2, 3)), // fecha 1: t1 vs t2, t3 vs t4
    List((0, 2), (1, 3)), // fecha 2: t1 vs t3, t2 vs t4
    List((0, 3), (1, 2))  // fecha 3: t1 vs t4, t2 vs t3
  )

  // (grupo, equipos) con los grupos en orden A, B, C, D y cada grupo ordenado por id
  def agruparEquipos(equipos: List[Equipo]): List[(String, List[Equipo])] =
    equipos.sortBy(_.id).groupBy(_.grupo).toList.sortBy(_._1)

  // ===================== RF: fase de grupos =====================

  // Orden de los partidos: fecha 1 (grupo A, B, C, D), fecha 2 (A, B, C, D), fecha 3...
  def simularFaseGrupos(equipos: List[Equipo], rng: Aleatorios): (List[Partido], Aleatorios) = {
    val cruces = for {
      (crucesFecha, indice) <- plantilla.zipWithIndex
      (grupo, delGrupo)     <- agruparEquipos(equipos)
      (iLocal, iVisitante)  <- crucesFecha
    } yield (indice + 1, grupo, delGrupo(iLocal), delGrupo(iVisitante))

    val (partidos, rngFinal) = cruces.foldLeft((Vector[Partido](), rng)) {
      case ((jugados, r), (fecha, grupo, local, visitante)) =>
        val (res, r2) = Simulador.simularPartido(local, visitante, r)
        val partido = Partido(jugados.size + 1, "grupos", Some(grupo), Some(fecha), local.id, visitante.id,
          res.golesLocal, res.golesVisitante, None, res.eventos)
        (jugados :+ partido, r2)
    }
    (partidos.toList, rngFinal)
  }

  // ===================== RF: tabla de posiciones =====================

  // Puntos, luego diferencia de gol, luego goles a favor y por último el id
  def calcularTabla(grupo: String, partidos: List[Partido]): List[FilaTabla] =
    partidos.filter(_.grupo.contains(grupo))
      .flatMap(FilaTabla.dePartido)
      .groupBy(_.equipo)
      .values
      .map(_.reduce(_.sumar(_)))
      .toList
      .sortBy(f => (-f.puntos, -f.dg, -f.gf, f.equipo))
}
