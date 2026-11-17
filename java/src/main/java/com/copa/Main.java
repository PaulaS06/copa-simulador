package com.copa;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        String rutaAleatorios = "data/aleatorios.txt";
        Aleatorios aleatoriosPartidos = new Aleatorios(rutaAleatorios);
        Aleatorios aleatoriosApuestas = new Aleatorios(rutaAleatorios, 50_000);
        DatosTorneo datos = Datos.cargarDatos("data/input.json");

        List<Partido> partidosGrupos = FaseGrupos.simularFaseGrupos(datos.equipos(), aleatoriosPartidos);
        List<Emparejamiento> cuartos = Eliminatorias.armarCuartos(partidosGrupos);
        List<Partido> eliminatorias = Eliminatorias.simularEliminatorias(cuartos, datos.equipos(), aleatoriosPartidos);

        List<Partido> todos = new ArrayList<>(partidosGrupos);
        todos.addAll(eliminatorias);

        List<StatJugador> statsJugadores = Estadisticas.estadisticasJugadores(datos.equipos(), todos);
        List<StatEquipo> statsEquipos = Estadisticas.estadisticasEquipos(todos);
        Premios premios = CalculoPremios.calcularPremios(statsJugadores, statsEquipos);

        ResultadoApuestas apuestas = Apuestas.simularApuestas(
                datos.apostadores(), todos, datos.equipos(), aleatoriosApuestas);

        System.out.println(premios);
        System.out.println("Apuestas hechas: " + apuestas.apuestas().size());
        apuestas.saldos().forEach(s -> System.out.println(
                "Apostador " + s.apostador() + ": " + s.historial().get(0)
                        + " → " + s.historial().get(s.historial().size() - 1)));
        System.out.println("Mejor apostador: " + apuestas.mejorApostador());
    }
}