package com.copa;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {

    /** Convierte ["--input", "a.json", "--out", "b.json"] en {input=a.json, out=b.json}. */
    static Map<String, String> leerArgumentos(String[] args) {
        Map<String, String> opciones = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            opciones.put(args[i].replace("--", ""), args[i + 1]);
        }
        return opciones;
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> opciones = leerArgumentos(args);
        String rutaInput = opciones.getOrDefault("input", "data/input.json");
        String rutaAleatorios = opciones.getOrDefault("aleatorios", "data/aleatorios.txt");
        String rutaSalida = opciones.getOrDefault("out", "out/java.json");
        int cantidadTorneos = Integer.parseInt(opciones.getOrDefault("torneos", "1"));

        long inicio = System.nanoTime();
        DatosTorneo datos = Datos.cargarDatos(rutaInput);
        Aleatorios aleatoriosPartidos = new Aleatorios(rutaAleatorios);
        Aleatorios aleatoriosApuestas = new Aleatorios(rutaAleatorios, 50_000);

        // Solo se guarda el primer torneo completo (es el que se exporta).
        // De los demás se cuenta el campeón y se descartan, para no acumular memoria.
        ResultadoTorneo primero = Torneo.simularTorneo(datos, aleatoriosPartidos, aleatoriosApuestas);

        ResumenVarios resumen = null;
        if (cantidadTorneos > 1) {
            Map<Integer, Long> campeones = Stream.concat(
                            Stream.of(primero.campeon()),
                            IntStream.range(1, cantidadTorneos)
                                    .mapToObj(i -> Torneo.simularTorneo(datos, aleatoriosPartidos, aleatoriosApuestas).campeon()))
                    .collect(Collectors.groupingBy(campeon -> campeon, TreeMap::new, Collectors.counting()));
            resumen = new ResumenVarios(cantidadTorneos, campeones);
        }

        Exportar.exportar(primero, resumen, rutaSalida);
        double milisegundos = (System.nanoTime() - inicio) / 1_000_000.0;

        System.out.println("Torneos simulados: " + cantidadTorneos
                + " | Campeón del primero: " + primero.campeon()
                + " | Archivo: " + rutaSalida
                + " | Tiempo: " + milisegundos + " ms");
    }
}
