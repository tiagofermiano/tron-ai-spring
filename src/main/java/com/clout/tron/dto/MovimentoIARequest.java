package com.clout.tron.dto;

import lombok.Data;

import java.util.List;

@Data
public class MovimentoIARequest {

    private int boardSize;

    private int playerX;
    private int playerY;

    private int botX;
    private int botY;

    /**
     * Lista de casas ocupadas (trilhas) no tabuleiro.
     * O tron.js manda como: [{ "x": 10, "y": 15 }, ...]
     */
    private List<Posicao> occupied;

    @Data
    public static class Posicao {
        private int x;
        private int y;
    }
}
