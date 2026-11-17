package com.copa;

import java.util.List;

public record ResultadoPartido(int golesLocal, int golesVisitante, List<Evento> eventos) {}