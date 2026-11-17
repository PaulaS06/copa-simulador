package com.copa;

import java.util.Comparator;
import java.util.List;

public class CalculoPremios {

    /** Devuelve el primero de la lista según el criterio (el criterio ordena del mejor al peor). */
    static <T> T mejorSegun(List<T> candidatos, Comparator<T> criterio) {
        return candidatos.stream()
                .min(criterio)
                .orElseThrow();
    }

    // ---------- Criterios (cada uno es una lambda guardada en una variable) ----------

    static final Comparator<StatJugador> MEJOR_JUGADOR = Comparator
            .comparingInt((StatJugador s) -> -(10 * s.goles() - 2 * s.amarillas() - 5 * s.rojas() - s.faltas()))
            .thenComparingInt(StatJugador::jugador);

    static final Comparator<StatJugador> BOTA_DE_ORO = Comparator
            .comparingInt((StatJugador s) -> -s.goles())
            .thenComparingInt(s -> s.amarillas() + s.rojas())
            .thenComparingInt(StatJugador::jugador);

    static final Comparator<StatEquipo> MEJOR_EQUIPO = Comparator
            .comparingInt((StatEquipo s) -> -s.puntos())
            .thenComparingInt(s -> -(s.gf() - s.gc()))
            .thenComparingInt(s -> -s.gf())
            .thenComparingInt(StatEquipo::equipo);

    static final Comparator<StatEquipo> VALLA_MENOS_VENCIDA = Comparator
            .comparingDouble((StatEquipo s) -> (double) s.gc() / s.pj())
            .thenComparingInt(StatEquipo::equipo);

    static final Comparator<StatEquipo> FAIR_PLAY = Comparator
            .comparingInt((StatEquipo s) -> s.amarillas() + 3 * s.rojas())
            .thenComparingInt(StatEquipo::faltas)
            .thenComparingInt(StatEquipo::equipo);

    // ---------- Todos los premios ----------

    public static Premios calcularPremios(List<StatJugador> jugadores, List<StatEquipo> equipos,
                                          int mejorApostador) {
        return new Premios(
                mejorSegun(jugadores, MEJOR_JUGADOR).jugador(),
                mejorSegun(jugadores, BOTA_DE_ORO).jugador(),
                mejorSegun(equipos, MEJOR_EQUIPO).equipo(),
                mejorSegun(equipos, VALLA_MENOS_VENCIDA).equipo(),
                mejorSegun(equipos, FAIR_PLAY).equipo(),
                mejorApostador
        );
    }
}