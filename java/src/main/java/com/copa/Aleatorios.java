package com.copa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Aleatorios {

    private final List<Double> numeros;
    private int posicion = 0;

    public Aleatorios(String ruta) throws IOException {
        this.numeros = Files.readAllLines(Path.of(ruta))
                .stream()
                .map(Double::parseDouble)
                .toList();
    }

    public double siguiente() {
        double numero = numeros.get(posicion);
        posicion = (posicion + 1) % numeros.size();
        return numero;
    }
}