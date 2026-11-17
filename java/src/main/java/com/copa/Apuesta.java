package com.copa;

public record Apuesta(int partido, int apostador, String pronostico,
                      double cuota, int monto, boolean acerto) {}