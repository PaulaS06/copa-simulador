package com.copa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FaseGrupos {

    /** Quién juega contra quién en cada fecha (posiciones dentro del grupo ordenado por id). */
    static final int[][][] PLANTILLA = {
            {{0, 1}, {2, 3}},   // fecha 1: t1 vs t2, t3 vs t4
            {{0, 2}, {1, 3}},   // fecha 2: t1 vs t3, t2 vs t4
            {{0, 3}, {1, 2}}    // fecha 3: t1 vs t4, t2 vs t3
    };

    /** Agrupa los equipos por grupo (A, B, C, D en orden) y ordena cada grupo por id. */
    public static Map<String, List<Equipo>> agruparEquipos(List<Equipo> equipos) {
        return equipos.stream()
                .sorted(Comparator.comparingInt(Equipo::id))
                .collect(Collectors.groupingBy(Equipo::grupo, TreeMap::new, Collectors.toList()));
    }

    public static List<Partido> simularFaseGrupos(List<Equipo> equipos, Aleatorios rng) {
        Map<String, List<Equipo>> grupos = agruparEquipos(equipos);
        List<Partido> partidos = new ArrayList<>();
        int id = 1;

        for (int fecha = 1; fecha <= 3; fecha++) {
            for (Map.Entry<String, List<Equipo>> grupo : grupos.entrySet()) {
                for (int[] cruce : PLANTILLA[fecha - 1]) {
                    Equipo local = grupo.getValue().get(cruce[0]);
                    Equipo visitante = grupo.getValue().get(cruce[1]);
                    ResultadoPartido r = Simulador.simularPartido(local, visitante, rng);
                    partidos.add(new Partido(id++, "grupos", grupo.getKey(), fecha,
                            local.id(), visitante.id(),
                            r.golesLocal(), r.golesVisitante(), null, r.eventos()));
                }
            }
        }
        return List.copyOf(partidos);
    }

    public static List<FilaTabla> calcularTabla(String grupo, List<Partido> partidos) {
        Map<Integer, FilaTabla> porEquipo = partidos.stream()
                .filter(p -> grupo.equals(p.grupo()))
                .flatMap(p -> Stream.of(
                        FilaTabla.dePartido(p.local(), p.golesLocal(), p.golesVisitante()),
                        FilaTabla.dePartido(p.visitante(), p.golesVisitante(), p.golesLocal())))
                .collect(Collectors.toMap(FilaTabla::equipo, f -> f, FilaTabla::sumar));

        return porEquipo.values().stream()
                .sorted(Comparator
                        .comparingInt((FilaTabla f) -> -f.puntos())
                        .thenComparingInt(f -> -f.dg())
                        .thenComparingInt(f -> -f.gf())
                        .thenComparingInt(FilaTabla::equipo))
                .toList();
    }
}