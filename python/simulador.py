"""Reglas del torneo, traducidas de java/src/main/java/com/copa.

Las transformaciones de datos no modifican sus entradas. La mutación queda
localizada en el estado de cada partido, las apuestas y los dos lectores.
No reagrupar operaciones de coma flotante ni anticipar llamadas a siguiente().
"""

import json
from collections import Counter
from functools import reduce
from itertools import chain, groupby
from math import floor
from pathlib import Path


def cargar_datos(ruta):
    return json.loads(Path(ruta).read_text(encoding="utf-8"))


def agrupar(elementos, clave):
    return {k: tuple(g) for k, g in groupby(sorted(elementos, key=clave), key=clave)}


def ataque_equipo(equipo):
    return sum(map(lambda j: j["ataque"], equipo["jugadores"])) / 11.0


def defensa_equipo(equipo):
    return sum(map(lambda j: j["defensa"], equipo["jugadores"])) / 11.0


def disponibles(equipo, expulsados):
    return sorted(filter(lambda j: j["id"] not in expulsados, equipo["jugadores"]),
                  key=lambda j: j["id"])


def elegir_ponderado(jugadores, u):
    objetivo = u * sum(map(lambda j: j["ataque"], jugadores))
    acumulado = 0
    for jugador in jugadores:
        acumulado += jugador["ataque"]
        if acumulado > objetivo:
            return jugador
    return jugadores[-1]


def simular_partido(local, visitante, rng):
    p_gl = 0.012 * ataque_equipo(local) / defensa_equipo(visitante)
    p_gv = 0.012 * ataque_equipo(visitante) / defensa_equipo(local)
    eventos, expulsados, amarillas = [], set(), Counter()
    goles_local = goles_visitante = 0

    def evento(minuto, tipo, equipo, jugador):
        eventos.append(dict(minuto=minuto, tipo=tipo, equipo=equipo["id"], jugador=jugador["id"]))

    for minuto in range(1, 91):
        u = rng.siguiente()
        if u < p_gl:
            autor = elegir_ponderado(disponibles(local, expulsados), rng.siguiente())
            evento(minuto, "gol", local, autor)
            goles_local += 1
        elif u < p_gl + p_gv:
            autor = elegir_ponderado(disponibles(visitante, expulsados), rng.siguiente())
            evento(minuto, "gol", visitante, autor)
            goles_visitante += 1
        elif u < p_gl + p_gv + 0.15:
            infractor = local if rng.siguiente() < 0.5 else visitante
            en_cancha = disponibles(infractor, expulsados)
            jugador = en_cancha[floor(rng.siguiente() * len(en_cancha))]
            evento(minuto, "falta", infractor, jugador)
            u4 = rng.siguiente()  # Siempre se consume, aunque no haya tarjeta.
            umbral = 0.01 + 0.25 * (100 - jugador["disciplina"]) / 100
            if u4 < 0.01:
                evento(minuto, "roja", infractor, jugador)
                expulsados.add(jugador["id"])
            elif u4 < umbral:
                amarillas[jugador["id"]] += 1
                if amarillas[jugador["id"]] == 2:
                    evento(minuto, "roja", infractor, jugador)
                    expulsados.add(jugador["id"])
                else:
                    evento(minuto, "amarilla", infractor, jugador)
    return dict(goles_local=goles_local, goles_visitante=goles_visitante, eventos=eventos)


def simular_penales(rng):
    local = visitante = 0
    for _ in range(5):
        local += int(rng.siguiente() < 0.75)
        visitante += int(rng.siguiente() < 0.75)
    # Equivalente a la recursión Java, sin límite de pila de Python.
    while local == visitante:
        local += int(rng.siguiente() < 0.75)
        visitante += int(rng.siguiente() < 0.75)
    return dict(local=local, visitante=visitante)


def jugar(id_partido, fase, local, visitante, rng, grupo=None, fecha=None):
    resultado = simular_partido(local, visitante, rng)
    penales = None
    if fase != "grupos" and resultado["goles_local"] == resultado["goles_visitante"]:
        penales = simular_penales(rng)
    return dict(id=id_partido, fase=fase, grupo=grupo, fecha=fecha,
                local=local["id"], visitante=visitante["id"],
                goles_local=resultado["goles_local"], goles_visitante=resultado["goles_visitante"],
                penales=penales, eventos=resultado["eventos"])


PLANTILLA = (((0, 1), (2, 3)), ((0, 2), (1, 3)), ((0, 3), (1, 2)))


def agrupar_equipos(equipos):
    return agrupar(sorted(equipos, key=lambda e: e["id"]), lambda e: e["grupo"])


def simular_fase_grupos(equipos, rng):
    grupos = agrupar_equipos(equipos)
    partidos = []
    for fecha, cruces in enumerate(PLANTILLA, 1):
        for grupo, miembros in grupos.items():
            for local, visitante in cruces:
                partidos.append(jugar(len(partidos) + 1, "grupos", miembros[local],
                                      miembros[visitante], rng, grupo, fecha))
    return partidos


def fila_partido(equipo, gf, gc):
    pg, pe, pp = int(gf > gc), int(gf == gc), int(gf < gc)
    return dict(equipo=equipo, pj=1, pg=pg, pe=pe, pp=pp, gf=gf, gc=gc,
                dg=gf - gc, puntos=3 * pg + pe)


def sumar_filas(a, b):
    return {k: a[k] if k == "equipo" else a[k] + b[k] for k in a}


def filas_equipos(partidos):
    filas = chain.from_iterable(map(lambda p: (
        fila_partido(p["local"], p["goles_local"], p["goles_visitante"]),
        fila_partido(p["visitante"], p["goles_visitante"], p["goles_local"])), partidos))
    return list(map(lambda grupo: reduce(sumar_filas, grupo),
                    agrupar(filas, lambda f: f["equipo"]).values()))


def calcular_tabla(grupo, partidos):
    return sorted(filas_equipos(filter(lambda p: p["grupo"] == grupo, partidos)),
                  key=lambda f: (-f["puntos"], -f["dg"], -f["gf"], f["equipo"]))


def armar_cuartos(partidos_grupos):
    tablas = {g: calcular_tabla(g, partidos_grupos) for g in "ABCD"}
    return [(tablas[a][0]["equipo"], tablas[b][1]["equipo"])
            for a, b in (("A", "B"), ("B", "A"), ("C", "D"), ("D", "C"))]


def ganador(p):
    if p["goles_local"] != p["goles_visitante"]:
        return p["local"] if p["goles_local"] > p["goles_visitante"] else p["visitante"]
    return p["local"] if p["penales"]["local"] > p["penales"]["visitante"] else p["visitante"]


def perdedor(p):
    return p["visitante"] if ganador(p) == p["local"] else p["local"]


def simular_eliminatorias(cuartos, equipos, rng):
    por_id = {e["id"]: e for e in equipos}

    def partido(id_partido, fase, local, visitante):
        return jugar(id_partido, fase, por_id[local], por_id[visitante], rng)

    cf = [partido(i, "cuartos", local, visitante)
          for i, (local, visitante) in enumerate(cuartos, 25)]
    sf1 = partido(29, "semifinal", ganador(cf[0]), ganador(cf[1]))
    sf2 = partido(30, "semifinal", ganador(cf[2]), ganador(cf[3]))
    tercero = partido(31, "tercer_puesto", perdedor(sf1), perdedor(sf2))
    final = partido(32, "final", ganador(sf1), ganador(sf2))
    return cf + [sf1, sf2, tercero, final]


def contar_eventos(partidos, clave):
    eventos = chain.from_iterable(map(lambda p: p["eventos"], partidos))
    return {k: Counter(map(lambda e: e["tipo"], grupo))
            for k, grupo in agrupar(eventos, lambda e: e[clave]).items()}


def estadisticas_jugadores(equipos, partidos):
    conteo = contar_eventos(partidos, "jugador")

    def estadistica(equipo, jugador):
        c = conteo.get(jugador["id"], Counter())
        return dict(jugador=jugador["id"], equipo=equipo["id"], goles=c["gol"],
                    faltas=c["falta"], amarillas=c["amarilla"], rojas=c["roja"])

    return sorted((estadistica(e, j) for e in equipos for j in e["jugadores"]),
                  key=lambda s: s["jugador"])


def estadisticas_equipos(partidos):
    conteo = contar_eventos(partidos, "equipo")

    def estadistica(fila):
        c = conteo.get(fila["equipo"], Counter())
        return {**{k: fila[k] for k in ("equipo", "pj", "puntos", "gf", "gc")},
                "faltas": c["falta"], "amarillas": c["amarilla"], "rojas": c["roja"]}

    return list(map(estadistica, filas_equipos(partidos)))


def calcular_cuotas(local, visitante):
    f_l = ataque_equipo(local) + defensa_equipo(local)
    f_v = ataque_equipo(visitante) + defensa_equipo(visitante)
    probabilidades = dict(L=0.75 * f_l / (f_l + f_v), E=0.25, V=0.75 * f_v / (f_l + f_v))
    return dict(probabilidades=probabilidades,
                cuotas={k: floor(100.0 / p) / 100.0 for k, p in probabilidades.items()})


def pronostico_aleatorio(rng):
    u = rng.siguiente()
    return "L" if u < 1.0 / 3 else "E" if u < 2.0 / 3 else "V"


ESTRATEGIAS = {
    "favorito": lambda p, rng: "L" if p["L"] >= p["V"] else "V",
    "sorpresa": lambda p, rng: "V" if p["L"] >= p["V"] else "L",
    "empate": lambda p, rng: "E",
    "aleatoria": lambda p, rng: pronostico_aleatorio(rng),
}


def resultado(p):
    return "L" if p["goles_local"] > p["goles_visitante"] else (
        "E" if p["goles_local"] == p["goles_visitante"] else "V")


def generar_apuestas(partido, apostador, saldo, cuotas, rng):
    """Genera una apuesta; no se llama para apostadores sin saldo."""
    pronostico = ESTRATEGIAS[apostador["estrategia"]](cuotas["probabilidades"], rng)
    return dict(partido=partido["id"], apostador=apostador["id"], pronostico=pronostico,
                cuota=cuotas["cuotas"][pronostico], monto=max(1, saldo // 10),
                acerto=pronostico == resultado(partido))


def resolver_apuestas(saldo, apuesta):
    return saldo - apuesta["monto"] + (
        floor(apuesta["monto"] * apuesta["cuota"]) if apuesta["acerto"] else 0)


def evolucion_saldos(historial):
    return [dict(apostador=id_apostador, historial=list(historial[id_apostador]))
            for id_apostador in sorted(historial)]


def simular_apuestas(apostadores, partidos, equipos, rng):
    por_id = {e["id"]: e for e in equipos}
    ordenados = sorted(apostadores, key=lambda a: a["id"])
    historial = {a["id"]: [a["saldo_inicial"]] for a in ordenados}
    apuestas = []
    for partido in sorted(partidos, key=lambda p: p["id"]):
        cuotas = calcular_cuotas(por_id[partido["local"]], por_id[partido["visitante"]])
        for apostador in ordenados:
            valores = historial[apostador["id"]]
            actual = valores[-1]
            if actual > 0:
                apuesta = generar_apuestas(partido, apostador, actual, cuotas, rng)
                actual = resolver_apuestas(actual, apuesta)
                apuestas.append(apuesta)
            valores.append(actual)
    saldos = evolucion_saldos(historial)
    mejor = mejor_segun(saldos, lambda s: (-s["historial"][-1], s["apostador"]))["apostador"]
    return dict(apuestas=apuestas, saldos=saldos, mejor_apostador=mejor)


def mejor_segun(candidatos, criterio):
    return min(candidatos, key=criterio)


MEJOR_JUGADOR = lambda s: (-(10 * s["goles"] - 2 * s["amarillas"] - 5 * s["rojas"] - s["faltas"]), s["jugador"])
BOTA_DE_ORO = lambda s: (-s["goles"], s["amarillas"] + s["rojas"], s["jugador"])
MEJOR_EQUIPO = lambda s: (-s["puntos"], -(s["gf"] - s["gc"]), -s["gf"], s["equipo"])
VALLA_MENOS_VENCIDA = lambda s: (s["gc"] / s["pj"], s["equipo"])
FAIR_PLAY = lambda s: (s["amarillas"] + 3 * s["rojas"], s["faltas"], s["equipo"])


def calcular_premios(jugadores, equipos, mejor_apostador):
    return dict(mejor_jugador=mejor_segun(jugadores, MEJOR_JUGADOR)["jugador"],
                bota_de_oro=mejor_segun(jugadores, BOTA_DE_ORO)["jugador"],
                mejor_equipo=mejor_segun(equipos, MEJOR_EQUIPO)["equipo"],
                valla_menos_vencida=mejor_segun(equipos, VALLA_MENOS_VENCIDA)["equipo"],
                fair_play=mejor_segun(equipos, FAIR_PLAY)["equipo"],
                mejor_apostador=mejor_apostador)


def simular_torneo(datos, aleatorios_partidos, aleatorios_apuestas):
    equipos = datos["equipos"]
    grupos = simular_fase_grupos(equipos, aleatorios_partidos)
    todos = grupos + simular_eliminatorias(armar_cuartos(grupos), equipos, aleatorios_partidos)
    jugadores = estadisticas_jugadores(equipos, todos)
    stats_equipos = estadisticas_equipos(todos)
    apuestas = simular_apuestas(datos["apostadores"], todos, equipos, aleatorios_apuestas)
    return dict(lenguaje="python", campeon=ganador(todos[-1]), partidos=todos,
                tablas={g: calcular_tabla(g, grupos) for g in agrupar_equipos(equipos)},
                llave={"cuartos": [25, 26, 27, 28], "semifinales": [29, 30],
                       "tercer_puesto": 31, "final": 32},
                estadisticas_jugadores=jugadores, estadisticas_equipos=stats_equipos,
                premios=calcular_premios(jugadores, stats_equipos, apuestas["mejor_apostador"]),
                apuestas=apuestas["apuestas"], saldos=apuestas["saldos"], resumen_varios=None)


def simular_varios(datos, aleatorios_partidos, aleatorios_apuestas, cantidad):
    if cantidad < 1:
        raise ValueError("La cantidad de torneos debe ser mayor que cero")
    primero = simular_torneo(datos, aleatorios_partidos, aleatorios_apuestas)
    campeones = Counter([primero["campeon"]])
    for _ in range(1, cantidad):
        torneo = simular_torneo(datos, aleatorios_partidos, aleatorios_apuestas)
        campeones[torneo["campeon"]] += 1
    resumen = None if cantidad == 1 else dict(
        torneos=cantidad, campeones={str(k): campeones[k] for k in sorted(campeones)})
    return {**primero, "resumen_varios": resumen}


def exportar(salida, ruta):
    archivo = Path(ruta)
    archivo.parent.mkdir(parents=True, exist_ok=True)
    archivo.write_text(json.dumps(salida, ensure_ascii=False, indent=2, allow_nan=False), encoding="utf-8")
