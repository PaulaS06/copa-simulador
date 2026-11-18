import java.nio.file.{Files, Path}

object Json {

  // ===================== RF: cargar input.json =====================

  def cargarDatos(ruta: String): DatosTorneo = {
    val json = ujson.read(Files.readString(Path.of(ruta)))

    val equipos = json("equipos").arr.toList.map(e =>
      Equipo(e("id").num.toInt, e("nombre").str, e("grupo").str,
        e("jugadores").arr.toList.map(j =>
          Jugador(j("id").num.toInt, j("nombre").str, j("posicion").str,
            j("ataque").num.toInt, j("defensa").num.toInt, j("disciplina").num.toInt))))

    val apostadores = json("apostadores").arr.toList.map(a =>
      Apostador(a("id").num.toInt, a("nombre").str, a("estrategia").str, a("saldo_inicial").num.toInt))

    DatosTorneo(equipos, apostadores)
  }

  // ===================== Conversión a JSON (nombres en snake_case) =====================

  def num(n: Double): ujson.Value = ujson.Num(n)

  def lista[A](elementos: List[A], convertir: A => ujson.Value): ujson.Value = ujson.Arr.from(elementos.map(convertir))

  def evento(e: Evento): ujson.Value =
    ujson.Obj("minuto" -> num(e.minuto), "tipo" -> ujson.Str(e.tipo),
      "equipo" -> num(e.equipo), "jugador" -> num(e.jugador))

  def partido(p: Partido): ujson.Value =
    ujson.Obj(
      "id" -> num(p.id),
      "fase" -> ujson.Str(p.fase),
      "grupo" -> p.grupo.map(ujson.Str(_)).getOrElse(ujson.Null),
      "fecha" -> p.fecha.map(f => num(f)).getOrElse(ujson.Null),
      "local" -> num(p.local),
      "visitante" -> num(p.visitante),
      "goles_local" -> num(p.golesLocal),
      "goles_visitante" -> num(p.golesVisitante),
      "penales" -> p.penales.map(pen => ujson.Obj("local" -> num(pen.local), "visitante" -> num(pen.visitante)))
        .getOrElse(ujson.Null),
      "eventos" -> lista(p.eventos, evento)
    )

  def fila(f: FilaTabla): ujson.Value =
    ujson.Obj("equipo" -> num(f.equipo), "pj" -> num(f.pj), "pg" -> num(f.pg), "pe" -> num(f.pe),
      "pp" -> num(f.pp), "gf" -> num(f.gf), "gc" -> num(f.gc), "dg" -> num(f.dg), "puntos" -> num(f.puntos))

  def statJugador(s: StatJugador): ujson.Value =
    ujson.Obj("jugador" -> num(s.jugador), "equipo" -> num(s.equipo), "goles" -> num(s.goles),
      "faltas" -> num(s.faltas), "amarillas" -> num(s.amarillas), "rojas" -> num(s.rojas))

  def statEquipo(s: StatEquipo): ujson.Value =
    ujson.Obj("equipo" -> num(s.equipo), "pj" -> num(s.pj), "puntos" -> num(s.puntos), "gf" -> num(s.gf),
      "gc" -> num(s.gc), "faltas" -> num(s.faltas), "amarillas" -> num(s.amarillas), "rojas" -> num(s.rojas))

  def premios(p: Premios): ujson.Value =
    ujson.Obj("mejor_jugador" -> num(p.mejorJugador), "bota_de_oro" -> num(p.botaDeOro),
      "mejor_equipo" -> num(p.mejorEquipo), "valla_menos_vencida" -> num(p.vallaMenosVencida),
      "fair_play" -> num(p.fairPlay), "mejor_apostador" -> num(p.mejorApostador))

  def apuesta(a: Apuesta): ujson.Value =
    ujson.Obj("partido" -> num(a.partido), "apostador" -> num(a.apostador),
      "pronostico" -> ujson.Str(a.pronostico), "cuota" -> num(a.cuota),
      "monto" -> num(a.monto), "acerto" -> ujson.Bool(a.acerto))

  def saldo(s: SaldoApostador): ujson.Value =
    ujson.Obj("apostador" -> num(s.apostador), "historial" -> lista(s.historial, (n: Int) => num(n)))

  // Ids de los partidos de cada ronda, para que el front arme el cuadro
  val llave: ujson.Value =
    ujson.Obj("cuartos" -> lista(List(25, 26, 27, 28), (n: Int) => num(n)),
      "semifinales" -> lista(List(29, 30), (n: Int) => num(n)),
      "tercer_puesto" -> num(31), "final" -> num(32))

  // Solo se llena cuando se simula más de un torneo
  def resumenVarios(n: Int, campeones: List[(Int, Int)]): ujson.Value =
    ujson.Obj("torneos" -> num(n),
      "campeones" -> ujson.Obj.from(campeones.map { case (equipo, veces) => equipo.toString -> num(veces) }))

  // ===================== RF: exportar output.json =====================

  def exportar(r: ResultadoTorneo, resumen: Option[ujson.Value], rutaSalida: String): Unit = {
    val salida = ujson.Obj(
      "lenguaje" -> ujson.Str("scala"),
      "campeon" -> num(r.campeon),
      "partidos" -> lista(r.partidos, partido),
      "tablas" -> ujson.Obj.from(r.tablas.map { case (grupo, filas) => grupo -> lista(filas, fila) }),
      "llave" -> llave,
      "estadisticas_jugadores" -> lista(r.estadisticasJugadores, statJugador),
      "estadisticas_equipos" -> lista(r.estadisticasEquipos, statEquipo),
      "premios" -> premios(r.premios),
      "apuestas" -> lista(r.apuestas.apuestas, apuesta),
      "saldos" -> lista(r.apuestas.saldos, saldo),
      "resumen_varios" -> resumen.getOrElse(ujson.Null)
    )

    val ruta = Path.of(rutaSalida)
    Option(ruta.toAbsolutePath.getParent).foreach(carpeta => Files.createDirectories(carpeta))
    Files.writeString(ruta, ujson.write(salida, indent = 2))
  }
}
