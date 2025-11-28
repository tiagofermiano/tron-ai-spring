package com.clout.tron.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstadoDTO {

    private int[][] grid;   // 0 = vazio, 1 = player, 2 = IA
    private int px;
    private int py;
    private int bx;
    private int by;
    private boolean gameOver;
    private String vencedor; // null enquanto não acabou
}
