package com.copa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        Aleatorios aleatorios = new Aleatorios("data/aleatorios.txt");
        DatosTorneo datos = Datos.cargarDatos("data/input.json");

        List<Partido> partidosGrupos = FaseGrupos.simularFaseGrupos(datos.equipos(), aleatorios);
        List<Emparejamiento> cuartos = Eliminatorias.armarCuartos(partidosGrupos);
        List<Partido> eliminatorias = Eliminatorias.simularEliminatorias(cuartos, datos.equipos(), aleatorios);

        List<Partido> todos = new ArrayList<>(partidosGrupos);
        todos.addAll(eliminatorias);

        List<StatJugador> statsJugadores = Estadisticas.estadisticasJugadores(datos.equipos(), todos);
        List<StatEquipo> statsEquipos = Estadisticas.estadisticasEquipos(todos);

        System.out.println("Top 5 goleadores:");
        statsJugadores.stream()
                .sorted(Comparator.comparingInt(StatJugador::goles).reversed())
                .limit(5)
                .forEach(System.out::println);

        System.out.println("Equipos:");
        statsEquipos.forEach(System.out::println);
    }
}