package com.clout.tron.service;

import com.clout.tron.dto.MovimentoIARequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TronAiService {

    /**
     * Decide a próxima jogada da IA com base no estado atual do tabuleiro.
     * Evita paredes/rastros e tenta ficar longe do player e das bordas.
     */
    public String decidirJogada(MovimentoIARequest req) {

        int n  = req.getBoardSize();
        int px = req.getPlayerX();
        int py = req.getPlayerY();
        int bx = req.getBotX();
        int by = req.getBotY();

        // grade de casas ocupadas
        boolean[][] ocupado = new boolean[n][n];
        if (req.getOccupied() != null) {
            for (MovimentoIARequest.Posicao p : req.getOccupied()) {
                int x = p.getX();
                int y = p.getY();
                if (x >= 0 && x < n && y >= 0 && y < n) {
                    ocupado[y][x] = true;
                }
            }
        }

        List<MoveScore> candidatos = new ArrayList<>();

        // avalia as 4 direções possíveis
        for (Direction d : Direction.values()) {
            int nx = bx + d.dx;
            int ny = by + d.dy;

            // fora do tabuleiro? descarta
            if (nx < 0 || nx >= n || ny < 0 || ny >= n) {
                continue;
            }

            // casa já ocupada (parede/rastro)? descarta
            if (ocupado[ny][nx]) {
                continue;
            }

            // --- heurística simples ---

            // distância do player (quanto maior, mais foge de você)
            int distPlayer = Math.abs(nx - px) + Math.abs(ny - py);

            // distância mínima até qualquer borda
            int distParede = Math.min(
                    Math.min(nx, n - 1 - nx),
                    Math.min(ny, n - 1 - ny)
            );

            // score total (dá pra tunar depois)
            int score = distPlayer + distParede;

            candidatos.add(new MoveScore(d, score));
        }

        if (candidatos.isEmpty()) {
            // todas as casas ao redor estão ocupadas -> sem saída :(
            // ainda assim devolve algo válido
            return Direction.UP.name();
        }

        // escolhe o movimento com melhor score
        candidatos.sort(Comparator.comparingInt(MoveScore::score).reversed());
        Direction melhor = candidatos.get(0).direction;

        return melhor.name();
    }

    // direções possíveis com seus deltas
    private enum Direction {
        UP(0, -1),
        DOWN(0, 1),
        LEFT(-1, 0),
        RIGHT(1, 0);

        final int dx;
        final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    // structzinha pra guardar score de cada direção
    private record MoveScore(Direction direction, int score) {}
}
