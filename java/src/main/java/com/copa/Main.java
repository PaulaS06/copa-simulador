package com.copa;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        Aleatorios aleatorios = new Aleatorios("data/aleatorios.txt");
        DatosTorneo datos = Datos.cargarDatos("data/input.json");

        List<Partido> partidos = FaseGrupos.simularFaseGrupos(datos.equipos(), aleatorios);
        System.out.println("Partidos: " + partidos.size());

        for (String grupo : List.of("A", "B", "C", "D")) {
            System.out.println("Grupo " + grupo);
            FaseGrupos.calcularTabla(grupo, partidos).forEach(System.out::println);
        }
    }
}