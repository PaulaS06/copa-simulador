package com.copa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Simulador {

    static final double FACTOR_GOL = 0.012;
    static final double PROB_FALTA = 0.15;
    static final double PROB_ROJA = 0.01;
    static final double FACTOR_AMARILLA = 0.25;

    // ---------- Fuerza del equipo ----------

    public static double ataqueEquipo(Equipo equipo) {
        return equipo.jugadores().stream()
                .mapToInt(Jugador::ataque)
                .sum() / 11.0;
    }

    public static double defensaEquipo(Equipo equipo) {
        return equipo.jugadores().stream()
                .mapToInt(Jugador::defensa)
                .sum() / 11.0;
    }

    // ---------- Ayudantes ----------

    /** Jugadores que siguen en la cancha, ordenados por id. */
    static List<Jugador> disponibles(Equipo equipo, Set<Integer> expulsados) {
        return equipo.jugadores().stream()
                .filter(j -> !expulsados.contains(j.id()))
                .sorted(Comparator.comparingInt(Jugador::id))
                .toList();
    }

    /** Escoge un jugador con más probabilidad mientras más ataque tenga. */
    static Jugador elegirPonderado(List<Jugador> jugadores, double u) {
        int sumaAtaque = jugadores.stream().mapToInt(Jugador::ataque).sum();
        double objetivo = u * sumaAtaque;
        int acumulado = 0;
        for (Jugador j : jugadores) {
            acumulado += j.ataque();
            if (acumulado > objetivo) {
                return j;
            }
        }
        return jugadores.get(jugadores.size() - 1);
    }

    // ---------- El partido ----------

    public static ResultadoPartido simularPartido(Equipo local, Equipo visitante, Aleatorios rng) {
        double pGL = FACTOR_GOL * ataqueEquipo(local) / defensaEquipo(visitante);
        double pGV = FACTOR_GOL * ataqueEquipo(visitante) / defensaEquipo(local);

        List<Evento> eventos = new ArrayList<>();
        Set<Integer> expulsados = new HashSet<>();
        Map<Integer, Integer> amarillas = new HashMap<>();
        int golesLocal = 0;
        int golesVisitante = 0;

        for (int minuto = 1; minuto <= 90; minuto++) {
            double u = rng.siguiente();

            if (u < pGL) {
                Jugador autor = elegirPonderado(disponibles(local, expulsados), rng.siguiente());
                eventos.add(new Evento(minuto, "gol", local.id(), autor.id()));
                golesLocal++;

            } else if (u < pGL + pGV) {
                Jugador autor = elegirPonderado(disponibles(visitante, expulsados), rng.siguiente());
                eventos.add(new Evento(minuto, "gol", visitante.id(), autor.id()));
                golesVisitante++;

            } else if (u < pGL + pGV + PROB_FALTA) {
                Equipo infractor = rng.siguiente() < 0.5 ? local : visitante;
                List<Jugador> enCancha = disponibles(infractor, expulsados);
                Jugador jugador = enCancha.get((int) Math.floor(rng.siguiente() * enCancha.size()));
                eventos.add(new Evento(minuto, "falta", infractor.id(), jugador.id()));

                double u4 = rng.siguiente();
                double umbralAmarilla = PROB_ROJA + FACTOR_AMARILLA * (100 - jugador.disciplina()) / 100;

                if (u4 < PROB_ROJA) {
                    eventos.add(new Evento(minuto, "roja", infractor.id(), jugador.id()));
                    expulsados.add(jugador.id());
                } else if (u4 < umbralAmarilla) {
                    int total = amarillas.merge(jugador.id(), 1, Integer::sum);
                    if (total == 2) {
                        eventos.add(new Evento(minuto, "roja", infractor.id(), jugador.id()));
                        expulsados.add(jugador.id());
                    } else {
                        eventos.add(new Evento(minuto, "amarilla", infractor.id(), jugador.id()));
                    }
                }
            }
        }
        return new ResultadoPartido(golesLocal, golesVisitante, List.copyOf(eventos));
    }
}