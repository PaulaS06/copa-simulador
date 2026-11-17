package com.copa;

import java.util.Map;

/** Solo se llena cuando se simulan varios torneos: cuántas veces campeonó cada equipo. */
public record ResumenVarios(int torneos, Map<Integer, Long> campeones) {}
