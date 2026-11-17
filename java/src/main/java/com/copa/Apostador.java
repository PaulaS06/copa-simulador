package com.copa;

import com.google.gson.annotations.SerializedName;

public record Apostador(int id, String nombre, String estrategia,
                        @SerializedName("saldo_inicial") int saldoInicial) {}