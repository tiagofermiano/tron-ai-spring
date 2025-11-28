package com.clout.tron.dto;

import lombok.Data;

import java.util.List;

@Data
public class EstadoDTO {
    private int boardSize;
    private int playerX;
    private int playerY;
    private int botX;
    private int botY;
    private int turno;

    // NOVO: direção atual da moto do bot (UP, DOWN, LEFT, RIGHT)
    private String botDirection;

    private List<Posicao> occupied;

    @Data
    public static class Posicao {
        private int x;
        private int y;
    }
}
