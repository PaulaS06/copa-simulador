package com.copa;

import java.util.List;

public record Partido(int id, String fase, String grupo, Integer fecha,
                      int local, int visitante,
                      int golesLocal, int golesVisitante,
                      ResultadoPenales penales,
                      List<Evento> eventos) {}