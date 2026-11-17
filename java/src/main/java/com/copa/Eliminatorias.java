package com.copa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Eliminatorias {

    static final double PROB_PENAL = 0.75;

    // ---------- Penales ----------

    public static ResultadoPenales simularPenales(Aleatorios rng) {
        int local = 0;
        int visitante = 0;
        for (int cobro = 1; cobro <= 5; cobro++) {
            if (rng.siguiente() < PROB_PENAL) local++;
            if (rng.siguiente() < PROB_PENAL) visitante++;
        }
        if (local != visitante) {
            return new ResultadoPenales(local, visitante);
        }
        return muerteSubita(local, visitante, rng);
    }

    /** Un par de cobros más; si los dos anotan o los dos fallan, se repite. */
    static ResultadoPenales muerteSubita(int local, int visitante, Aleatorios rng) {
        boolean anotaLocal = rng.siguiente() < PROB_PENAL;
        boolean anotaVisitante = rng.siguiente() < PROB_PENAL;
        int nuevoLocal = local + (anotaLocal ? 1 : 0);
        int nuevoVisitante = visitante + (anotaVisitante ? 1 : 0);
        if (anotaLocal != anotaVisitante) {
            return new ResultadoPenales(nuevoLocal, nuevoVisitante);
        }
        return muerteSubita(nuevoLocal, nuevoVisitante, rng);
    }

    // ---------- Quién ganó ----------

    public static int ganador(Partido p) {
        if (p.golesLocal() != p.golesVisitante()) {
            return p.golesLocal() > p.golesVisitante() ? p.local() : p.visitante();
        }
        return p.penales().local() > p.penales().visitante() ? p.local() : p.visitante();
    }

    public static int perdedor(Partido p) {
        return ganador(p) == p.local() ? p.visitante() : p.local();
    }

    // ---------- La llave ----------

    public static List<Emparejamiento> armarCuartos(List<Partido> partidosGrupos) {
        Function<String, Integer> primero = g -> FaseGrupos.calcularTabla(g, partidosGrupos).get(0).equipo();
        Function<String, Integer> segundo = g -> FaseGrupos.calcularTabla(g, partidosGrupos).get(1).equipo();

        return List.of(
                new Emparejamiento(primero.apply("A"), segundo.apply("B")),   // CF1
                new Emparejamiento(primero.apply("B"), segundo.apply("A")),   // CF2
                new Emparejamiento(primero.apply("C"), segundo.apply("D")),   // CF3
                new Emparejamiento(primero.apply("D"), segundo.apply("C"))    // CF4
        );
    }

    static Partido jugar(int id, String fase, Emparejamiento Emparejamiento, Map<Integer, Equipo> porId, Aleatorios rng) {
        Equipo local = porId.get(Emparejamiento.local());
        Equipo visitante = porId.get(Emparejamiento.visitante());
        ResultadoPartido r = Simulador.simularPartido(local, visitante, rng);
        ResultadoPenales penales = r.golesLocal() == r.golesVisitante() ? simularPenales(rng) : null;
        return new Partido(id, fase, null, null, local.id(), visitante.id(),
                r.golesLocal(), r.golesVisitante(), penales, r.eventos());
    }

    public static List<Partido> simularEliminatorias(List<Emparejamiento> cuartos, List<Equipo> equipos, Aleatorios rng) {
        Map<Integer, Equipo> porId = equipos.stream()
                .collect(Collectors.toMap(Equipo::id, e -> e));

        List<Partido> cf = new ArrayList<>();
        int id = 25;  // los partidos 1-24 son de grupos
        for (Emparejamiento Emparejamiento : cuartos) {
            cf.add(jugar(id++, "cuartos", Emparejamiento, porId, rng));
        }

        Partido sf1 = jugar(29, "semifinal",
                new Emparejamiento(ganador(cf.get(0)), ganador(cf.get(1))), porId, rng);
        Partido sf2 = jugar(30, "semifinal",
                new Emparejamiento(ganador(cf.get(2)), ganador(cf.get(3))), porId, rng);
        Partido tercerPuesto = jugar(31, "tercer_puesto",
                new Emparejamiento(perdedor(sf1), perdedor(sf2)), porId, rng);
        Partido finalTorneo = jugar(32, "final",
                new Emparejamiento(ganador(sf1), ganador(sf2)), porId, rng);

        List<Partido> todos = new ArrayList<>(cf);
        todos.add(sf1);
        todos.add(sf2);
        todos.add(tercerPuesto);
        todos.add(finalTorneo);
        return List.copyOf(todos);
    }
}