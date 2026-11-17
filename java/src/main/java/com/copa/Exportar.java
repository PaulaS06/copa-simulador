package com.copa;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Exportar {

    static final Llave LLAVE = new Llave(List.of(25, 26, 27, 28), List.of(29, 30), 31, 32);

    public static void exportar(ResultadoTorneo r, ResumenVarios resumen, String rutaSalida) throws IOException {
        Salida salida = new Salida("java", r.campeon(), r.partidos(), r.tablas(), LLAVE,
                r.estadisticasJugadores(), r.estadisticasEquipos(), r.premios(),
                r.apuestas().apuestas(), r.apuestas().saldos(), resumen);

        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .serializeNulls()
                .setPrettyPrinting()
                .create();

        Path ruta = Path.of(rutaSalida);
        if (ruta.getParent() != null) {
            Files.createDirectories(ruta.getParent());
        }
        Files.writeString(ruta, gson.toJson(salida));
    }
}
