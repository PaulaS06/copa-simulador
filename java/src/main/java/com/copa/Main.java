package com.copa;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        Aleatorios aleatorios = new Aleatorios("data/aleatorios.txt");
        DatosTorneo datos = Datos.cargarDatos("data/input.json");

        List<Partido> partidosGrupos = FaseGrupos.simularFaseGrupos(datos.equipos(), aleatorios);
        List<Emparejamiento> cuartos = Eliminatorias.armarCuartos(partidosGrupos);
        List<Partido> eliminatorias = Eliminatorias.simularEliminatorias(cuartos, datos.equipos(), aleatorios);

        for (Partido p : eliminatorias) {
            String penales = p.penales() != null
                    ? " (penales " + p.penales().local() + "-" + p.penales().visitante() + ")"
                    : "";
            System.out.println(p.fase() + ": " + p.local() + " " + p.golesLocal()
                    + " - " + p.golesVisitante() + " " + p.visitante() + penales
                    + "  → gana " + Eliminatorias.ganador(p));
        }

        Partido finalTorneo = eliminatorias.get(eliminatorias.size() - 1);
        System.out.println("CAMPEÓN: equipo " + Eliminatorias.ganador(finalTorneo));
    }
}