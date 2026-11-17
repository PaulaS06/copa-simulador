package com.copa;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Estadisticas {

    /** Cuántos eventos de un tipo tiene una clave (jugador o equipo); 0 si no tiene ninguno. */
    static int contar(Map<Integer, Map<String, Long>> conteo, int clave, String tipo) {
        return conteo.getOrDefault(clave, Map.of())
                .getOrDefault(tipo, 0L)
                .intValue();
    }

    /** Agrupa todos los eventos del torneo por una clave y luego por tipo. */
    static Map<Integer, Map<String, Long>> contarEventos(List<Partido> partidos,
                                                         Function<Evento, Integer> clave) {
        return partidos.stream()
                .flatMap(p -> p.eventos().stream())
                .collect(Collectors.groupingBy(clave,
                        Collectors.groupingBy(Evento::tipo, Collectors.counting())));
    }

    // ---------- Por jugador ----------

    public static List<StatJugador> estadisticasJugadores(List<Equipo> equipos, List<Partido> partidos) {
        Map<Integer, Map<String, Long>> conteo = contarEventos(partidos, Evento::jugador);

        return equipos.stream()
                .flatMap(e -> e.jugadores().stream()
                        .map(j -> new StatJugador(j.id(), e.id(),
                                contar(conteo, j.id(), "gol"),
                                contar(conteo, j.id(), "falta"),
                                contar(conteo, j.id(), "amarilla"),
                                contar(conteo, j.id(), "roja"))))
                .sorted(Comparator.comparingInt(StatJugador::jugador))
                .toList();
    }

    // ---------- Por equipo ----------

    public static List<StatEquipo> estadisticasEquipos(List<Partido> partidos) {
        Map<Integer, Map<String, Long>> conteo = contarEventos(partidos, Evento::equipo);

        Map<Integer, FilaTabla> filas = partidos.stream()
                .flatMap(p -> Stream.of(
                        FilaTabla.dePartido(p.local(), p.golesLocal(), p.golesVisitante()),
                        FilaTabla.dePartido(p.visitante(), p.golesVisitante(), p.golesLocal())))
                .collect(Collectors.toMap(FilaTabla::equipo, f -> f, FilaTabla::sumar));

        return filas.values().stream()
                .map(f -> new StatEquipo(f.equipo(), f.pj(), f.puntos(), f.gf(), f.gc(),
                        contar(conteo, f.equipo(), "falta"),
                        contar(conteo, f.equipo(), "amarilla"),
                        contar(conteo, f.equipo(), "roja")))
                .sorted(Comparator.comparingInt(StatEquipo::equipo))
                .toList();
    }
}