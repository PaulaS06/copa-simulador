package com.copa;

public record FilaTabla(int equipo, int pj, int pg, int pe, int pp,
                        int gf, int gc, int dg, int puntos) {

    /** La fila de UN equipo en UN partido. */
    static FilaTabla dePartido(int equipo, int golesAFavor, int golesEnContra) {
        int pg = golesAFavor > golesEnContra ? 1 : 0;
        int pe = golesAFavor == golesEnContra ? 1 : 0;
        int pp = golesAFavor < golesEnContra ? 1 : 0;
        return new FilaTabla(equipo, 1, pg, pe, pp,
                golesAFavor, golesEnContra, golesAFavor - golesEnContra, 3 * pg + pe);
    }

    /** Suma dos filas del mismo equipo. */
    FilaTabla sumar(FilaTabla otra) {
        return new FilaTabla(equipo, pj + otra.pj, pg + otra.pg, pe + otra.pe, pp + otra.pp,
                gf + otra.gf, gc + otra.gc, dg + otra.dg, puntos + otra.puntos);
    }
}