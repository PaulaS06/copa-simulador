package com.copa;

import java.util.List;
import java.util.Map;

/** La forma exacta del output.json (Gson convierte los nombres a snake_case). */
public record Salida(String lenguaje, int campeon, List<Partido> partidos,
                     Map<String, List<FilaTabla>> tablas, Llave llave,
                     List<StatJugador> estadisticasJugadores, List<StatEquipo> estadisticasEquipos,
                     Premios premios, List<Apuesta> apuestas, List<SaldoApostador> saldos,
                     ResumenVarios resumenVarios) {}
