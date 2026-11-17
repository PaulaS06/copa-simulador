package com.copa;

import java.util.List;

public record Equipo(int id, String nombre, String grupo, List<Jugador> jugadores) {}