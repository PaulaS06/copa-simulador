package com.copa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Aleatorios {

    private final List<Double> numeros;
    private int posicion = 0;

    /** Lee el archivo y empieza desde la posición 0. */
    public Aleatorios(String ruta) throws IOException {
        this.numeros = Files.readAllLines(Path.of(ruta))
                .stream()
                .map(Double::parseDouble)
                .toList();
    }

    /** Lee el archivo y empieza desde otra posición. */
    public Aleatorios(String ruta, int inicio) throws IOException {
        this(ruta);
        this.posicion = inicio % numeros.size();
    }

    public double siguiente() {
        double numero = numeros.get(posicion);
        posicion = (posicion + 1) % numeros.size();
        return numero;
    }
}