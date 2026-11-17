package com.copa;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Datos {
    public static DatosTorneo cargarDatos(String ruta) throws IOException {
        String json = Files.readString(Path.of(ruta));
        return new Gson().fromJson(json, DatosTorneo.class);
    }
}