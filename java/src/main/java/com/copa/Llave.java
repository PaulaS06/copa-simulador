package com.copa;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/** Ids de los partidos de cada ronda, para que el front arme el cuadro. */
public record Llave(List<Integer> cuartos, List<Integer> semifinales, int tercerPuesto,
                    @SerializedName("final") int finalTorneo) {}
