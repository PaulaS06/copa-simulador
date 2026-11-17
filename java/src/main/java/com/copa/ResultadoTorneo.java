package com.copa;

import java.util.List;
import java.util.Map;

/** Todo lo que produce UN torneo. */
public record ResultadoTorneo(int campeon, List<Partido> partidos, Map<String, List<FilaTabla>> tablas,
                              List<StatJugador> estadisticasJugadores, List<StatEquipo> estadisticasEquipos,
                              Premios premios, ResultadoApuestas apuestas) {}
