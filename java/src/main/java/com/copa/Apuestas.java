package com.copa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class Apuestas {

    // ---------- Cuotas ----------

    static double cuota(double probabilidad) {
        return Math.floor(100.0 / probabilidad) / 100.0;
    }

    public static Cuotas calcularCuotas(Equipo local, Equipo visitante) {
        double fL = Simulador.ataqueEquipo(local) + Simulador.defensaEquipo(local);
        double fV = Simulador.ataqueEquipo(visitante) + Simulador.defensaEquipo(visitante);
        double pL = 0.75 * fL / (fL + fV);
        double pE = 0.25;
        double pV = 0.75 * fV / (fL + fV);
        return new Cuotas(pL, pE, pV, cuota(pL), cuota(pE), cuota(pV));
    }

    static double cuotaDe(Cuotas c, String pronostico) {
        return switch (pronostico) {
            case "L" -> c.cuotaL();
            case "E" -> c.cuotaE();
            default -> c.cuotaV();
        };
    }

    // ---------- Estrategias: cada una es una lambda ----------

    static final Map<String, BiFunction<Cuotas, Aleatorios, String>> ESTRATEGIAS = Map.of(
            "favorito",  (c, rng) -> c.pL() >= c.pV() ? "L" : "V",
            "sorpresa",  (c, rng) -> c.pL() >= c.pV() ? "V" : "L",
            "empate",    (c, rng) -> "E",
            "aleatoria", (c, rng) -> {
                double u = rng.siguiente();
                return u < 1.0 / 3 ? "L" : u < 2.0 / 3 ? "E" : "V";
            }
    );

    /** Resultado al minuto 90: L, E o V (los penales no cuentan). */
    static String resultado(Partido p) {
        if (p.golesLocal() > p.golesVisitante()) return "L";
        if (p.golesLocal() == p.golesVisitante()) return "E";
        return "V";
    }

    // ---------- Todo el torneo ----------

    public static ResultadoApuestas simularApuestas(List<Apostador> apostadores, List<Partido> partidos,
                                                    List<Equipo> equipos, Aleatorios rng) {
        Map<Integer, Equipo> porId = equipos.stream()
                .collect(Collectors.toMap(Equipo::id, e -> e));
        List<Apostador> ordenados = apostadores.stream()
                .sorted(Comparator.comparingInt(Apostador::id))
                .toList();
        List<Partido> enOrden = partidos.stream()
                .sorted(Comparator.comparingInt(Partido::id))
                .toList();

        Map<Integer, Integer> saldo = new TreeMap<>();
        Map<Integer, List<Integer>> historial = new TreeMap<>();
        for (Apostador a : ordenados) {
            saldo.put(a.id(), a.saldoInicial());
            historial.put(a.id(), new ArrayList<>(List.of(a.saldoInicial())));
        }

        List<Apuesta> apuestas = new ArrayList<>();
        for (Partido p : enOrden) {
            Cuotas cuotas = calcularCuotas(porId.get(p.local()), porId.get(p.visitante()));
            String real = resultado(p);

            for (Apostador a : ordenados) {
                int actual = saldo.get(a.id());
                if (actual > 0) {
                    int monto = Math.max(1, actual / 10);
                    String pronostico = ESTRATEGIAS.get(a.estrategia()).apply(cuotas, rng);
                    double cuota = cuotaDe(cuotas, pronostico);
                    boolean acerto = pronostico.equals(real);
                    int nuevo = acerto
                            ? actual - monto + (int) Math.floor(monto * cuota)
                            : actual - monto;
                    saldo.put(a.id(), nuevo);
                    apuestas.add(new Apuesta(p.id(), a.id(), pronostico, cuota, monto, acerto));
                }
                historial.get(a.id()).add(saldo.get(a.id()));
            }
        }

        List<SaldoApostador> saldos = historial.entrySet().stream()
                .map(e -> new SaldoApostador(e.getKey(), List.copyOf(e.getValue())))
                .toList();

        int mejorApostador = saldos.stream()
                .min(Comparator
                        .comparingInt((SaldoApostador s) -> -s.historial().get(s.historial().size() - 1))
                        .thenComparingInt(SaldoApostador::apostador))
                .orElseThrow()
                .apostador();

        return new ResultadoApuestas(List.copyOf(apuestas), saldos, mejorApostador);
    }
}