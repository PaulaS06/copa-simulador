package com.copa;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Aleatorios aleatorios = new Aleatorios("data/aleatorios.txt");

        DatosTorneo datos = Datos.cargarDatos("data/input.json");
        System.out.println(datos.equipos().size());
        System.out.println(datos.equipos().get(0).nombre());
        System.out.println(datos.equipos().get(1).jugadores().get(0));
        System.out.println(datos.apostadores().get(0).saldoInicial());
    }
}