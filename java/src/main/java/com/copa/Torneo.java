package com.copa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Torneo {

    /** Simula un torneo completo: grupos, eliminatorias, estadísticas, apuestas y premios. */
    public static ResultadoTorneo simularTorneo(DatosTorneo datos,
                                                Aleatorios aleatoriosPartidos,
                                                Aleatorios aleatoriosApuestas) {
        List<Partido> grupos = FaseGrupos.simularFaseGrupos(datos.equipos(), aleatoriosPartidos);
        List<Emparejamiento> cuartos = Eliminatorias.armarCuartos(grupos);
        List<Partido> eliminatorias = Eliminatorias.simularEliminatorias(cuartos, datos.equipos(), aleatoriosPartidos);

        List<Partido> todos = new ArrayList<>(grupos);
        todos.addAll(eliminatorias);

        Map<String, List<FilaTabla>> tablas = FaseGrupos.agruparEquipos(datos.equipos()).keySet().stream()
                .collect(Collectors.toMap(g -> g, g -> FaseGrupos.calcularTabla(g, grupos),
                        (a, b) -> a, TreeMap::new));

        List<StatJugador> statsJugadores = Estadisticas.estadisticasJugadores(datos.equipos(), todos);
        List<StatEquipo> statsEquipos = Estadisticas.estadisticasEquipos(todos);
        ResultadoApuestas apuestas = Apuestas.simularApuestas(
                datos.apostadores(), todos, datos.equipos(), aleatoriosApuestas);
        Premios premios = CalculoPremios.calcularPremios(statsJugadores, statsEquipos, apuestas.mejorApostador());

        int campeon = Eliminatorias.ganador(todos.get(todos.size() - 1));

        return new ResultadoTorneo(campeon, List.copyOf(todos), tablas,
                statsJugadores, statsEquipos, premios, apuestas);
    }
}
