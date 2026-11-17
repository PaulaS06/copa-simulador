package com.copa;

import java.util.List;

public record ResultadoApuestas(List<Apuesta> apuestas, List<SaldoApostador> saldos, int mejorApostador) {}