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

  // ===================== Escritor de JSON =====================
  // Propio para controlar los números: los enteros salen como 4 y los decimales siempre con punto (4.0),
  // igual que Java (Gson) y Python (json). Así el comparador los ve del mismo tipo.

  enum Valor {
    case Entero(n: Int)
    case Decimal(d: Double)
    case Texto(s: String)
    case Booleano(b: Boolean)
    case Nulo
    case Lista(elementos: List[Valor])
    case Objeto(campos: List[(String, Valor)])
  }

  import Valor.*

  // Pone el texto entre comillas y escapa los caracteres especiales
  def comillas(s: String): String =
    "\"" + s.flatMap {
      case '"'          => "\\\""
      case '\\'         => "\\\\"
      case c if c < ' ' => "\\u%04x".format(c.toInt)
      case c            => c.toString
    } + "\""

  // Convierte el valor a texto con sangría de 2 espacios
  def escribir(v: Valor, nivel: Int = 0): String = {
    val sangria = "  " * (nivel + 1)
    val cierre = "\n" + "  " * nivel
    v match {
      case Entero(n)   => n.toString
      case Decimal(d)  => d.toString
      case Texto(s)    => comillas(s)
      case Booleano(b) => b.toString
      case Nulo        => "null"
      case Lista(Nil)  => "[]"
      case Lista(xs)   =>
        xs.map(x => sangria + escribir(x, nivel + 1)).mkString("[\n", ",\n", cierre + "]")
      case Objeto(Nil) => "{}"
      case Objeto(cs)  =>
        cs.map { case (clave, x) => sangria + comillas(clave) + ": " + escribir(x, nivel + 1) }
          .mkString("{\n", ",\n", cierre + "}")
    }
  }

  // ===================== Conversión a JSON (nombres en snake_case) =====================

  def num(n: Int): Valor = Entero(n)

  def obj(campos: (String, Valor)*): Valor = Objeto(campos.toList)

  def lista[A](elementos: List[A], convertir: A => Valor): Valor = Lista(elementos.map(convertir))

  def evento(e: Evento): Valor =
    obj("minuto" -> num(e.minuto), "tipo" -> Texto(e.tipo),
      "equipo" -> num(e.equipo), "jugador" -> num(e.jugador))

  def partido(p: Partido): Valor =
    obj(
      "id" -> num(p.id),
      "fase" -> Texto(p.fase),
      "grupo" -> p.grupo.map(Texto(_)).getOrElse(Nulo),
      "fecha" -> p.fecha.map(f => num(f)).getOrElse(Nulo),
      "local" -> num(p.local),
      "visitante" -> num(p.visitante),
      "goles_local" -> num(p.golesLocal),
      "goles_visitante" -> num(p.golesVisitante),
      "penales" -> p.penales.map(pen => obj("local" -> num(pen.local), "visitante" -> num(pen.visitante)))
        .getOrElse(Nulo),
      "eventos" -> lista(p.eventos, evento)
    )

  def fila(f: FilaTabla): Valor =
    obj("equipo" -> num(f.equipo), "pj" -> num(f.pj), "pg" -> num(f.pg), "pe" -> num(f.pe),
      "pp" -> num(f.pp), "gf" -> num(f.gf), "gc" -> num(f.gc), "dg" -> num(f.dg), "puntos" -> num(f.puntos))

  def statJugador(s: StatJugador): Valor =
    obj("jugador" -> num(s.jugador), "equipo" -> num(s.equipo), "goles" -> num(s.goles),
      "faltas" -> num(s.faltas), "amarillas" -> num(s.amarillas), "rojas" -> num(s.rojas))

  def statEquipo(s: StatEquipo): Valor =
    obj("equipo" -> num(s.equipo), "pj" -> num(s.pj), "puntos" -> num(s.puntos), "gf" -> num(s.gf),
      "gc" -> num(s.gc), "faltas" -> num(s.faltas), "amarillas" -> num(s.amarillas), "rojas" -> num(s.rojas))

  def premios(p: Premios): Valor =
    obj("mejor_jugador" -> num(p.mejorJugador), "bota_de_oro" -> num(p.botaDeOro),
      "mejor_equipo" -> num(p.mejorEquipo), "valla_menos_vencida" -> num(p.vallaMenosVencida),
      "fair_play" -> num(p.fairPlay), "mejor_apostador" -> num(p.mejorApostador))

  def apuesta(a: Apuesta): Valor =
    obj("partido" -> num(a.partido), "apostador" -> num(a.apostador),
      "pronostico" -> Texto(a.pronostico), "cuota" -> Decimal(a.cuota),
      "monto" -> num(a.monto), "acerto" -> Booleano(a.acerto))

  def saldo(s: SaldoApostador): Valor =
    obj("apostador" -> num(s.apostador), "historial" -> lista(s.historial, (n: Int) => num(n)))

  // Ids de los partidos de cada ronda, para que el front arme el cuadro
  val llave: Valor =
    obj("cuartos" -> lista(List(25, 26, 27, 28), (n: Int) => num(n)),
      "semifinales" -> lista(List(29, 30), (n: Int) => num(n)),
      "tercer_puesto" -> num(31), "final" -> num(32))

  // Solo se llena cuando se simula más de un torneo
  def resumenVarios(n: Int, campeones: List[(Int, Int)]): Valor =
    obj("torneos" -> num(n),
      "campeones" -> Objeto(campeones.map { case (equipo, veces) => equipo.toString -> num(veces) }))

  // ===================== RF: exportar output.json =====================

  def exportar(r: ResultadoTorneo, resumen: Option[Valor], rutaSalida: String): Unit = {
    val salida = obj(
      "lenguaje" -> Texto("scala"),
      "campeon" -> num(r.campeon),
      "partidos" -> lista(r.partidos, partido),
      "tablas" -> Objeto(r.tablas.map { case (grupo, filas) => grupo -> lista(filas, fila) }),
      "llave" -> llave,
      "estadisticas_jugadores" -> lista(r.estadisticasJugadores, statJugador),
      "estadisticas_equipos" -> lista(r.estadisticasEquipos, statEquipo),
      "premios" -> premios(r.premios),
      "apuestas" -> lista(r.apuestas.apuestas, apuesta),
      "saldos" -> lista(r.apuestas.saldos, saldo),
      "resumen_varios" -> resumen.getOrElse(Nulo)
    )

    val ruta = Path.of(rutaSalida)
    Option(ruta.toAbsolutePath.getParent).foreach(carpeta => Files.createDirectories(carpeta))
    Files.writeString(ruta, escribir(salida))
  }
}
