package com.clout.tron.service;

import com.clout.tron.dto.EstadoDTO;
import com.clout.tron.entity.Partida;
import com.clout.tron.repository.PartidaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GameService {

    private final PartidaRepository partidaRepository;

    private static final int TAM = 30;

    private int[][] grid;
    private int px, py; // player
    private int bx, by; // bot
    private boolean gameOver;
    private String vencedor;
    private int turnos;

    public synchronized EstadoDTO novoJogo() {
        grid = new int[TAM][TAM];

        px = 5;
        py = TAM / 2;

        bx = TAM - 6;
        by = TAM / 2;

        gameOver = false;
        vencedor = null;
        turnos = 0;

        marcar(px, py, 1);
        marcar(bx, by, 2);

        return estadoAtual();
    }

    public synchronized EstadoDTO moverJogador(String direcao) {
        if (gameOver) {
            return estadoAtual();
        }

        // move player
        int[] proxP = mover(px, py, direcao);
        if (colidiu(proxP[0], proxP[1])) {
            gameOver = true;
            vencedor = "IA";
            salvarPartida();
            return estadoAtual();
        }

        px = proxP[0];
        py = proxP[1];
        marcar(px, py, 1);

        // move bot (algoritmo simples -> tenta se aproximar do player)
        String dirBot = escolherMovimentoBot();
        int[] proxB = mover(bx, by, dirBot);

        if (colidiu(proxB[0], proxB[1])) {
            gameOver = true;
            vencedor = "PLAYER";
            salvarPartida();
        } else {
            bx = proxB[0];
            by = proxB[1];
            marcar(bx, by, 2);
        }

        turnos++;
        return estadoAtual();
    }

    public List<Partida> listarPartidas() {
        return partidaRepository.findAll();
    }

    // ----------------- helpers -------------------

    private void marcar(int x, int y, int valor) {
        if (dentro(x, y)) {
            grid[y][x] = valor;
        }
    }

    private boolean dentro(int x, int y) {
        return x >= 0 && x < TAM && y >= 0 && y < TAM;
    }

    private boolean colidiu(int x, int y) {
        return !dentro(x, y) || grid[y][x] != 0;
    }

    private int[] mover(int x, int y, String direcao) {
        return switch (direcao) {
            case "CIMA" -> new int[]{x, y - 1};
            case "BAIXO" -> new int[]{x, y + 1};
            case "ESQUERDA" -> new int[]{x - 1, y};
            case "DIREITA" -> new int[]{x + 1, y};
            default -> new int[]{x, y};
        };
    }

    private String escolherMovimentoBot() {
        // tenta ir na horizontal em direção ao player,
        // se não der, tenta vertical, depois alternativas.
        String[] tentativas = new String[4];
        int idx = 0;

        if (bx > px) tentativas[idx++] = "ESQUERDA";
        if (bx < px) tentativas[idx++] = "DIREITA";
        if (by > py) tentativas[idx++] = "CIMA";
        if (by < py) tentativas[idx++] = "BAIXO";

        // completa com movimentos restantes
        if (!contém(tentativas, "ESQUERDA")) tentativas[idx++] = "ESQUERDA";
        if (!contém(tentativas, "DIREITA")) tentativas[idx++] = "DIREITA";
        if (!contém(tentativas, "CIMA")) tentativas[idx++] = "CIMA";
        if (!contém(tentativas, "BAIXO")) tentativas[idx] = "BAIXO";

        for (String dir : tentativas) {
            if (dir == null) continue;
            int[] prox = mover(bx, by, dir);
            if (!colidiu(prox[0], prox[1])) {
                return dir;
            }
        }
        return "CIMA"; // fallback tonto
    }

    private boolean contém(String[] arr, String valor) {
        for (String s : arr) {
            if (valor.equals(s)) return true;
        }
        return false;
    }

    private EstadoDTO estadoAtual() {
        return new EstadoDTO(grid, px, py, bx, by, gameOver, vencedor);
    }

    private void salvarPartida() {
        Partida p = Partida.builder()
                .vencedor(vencedor)
                .dataHora(LocalDateTime.now())
                .duracaoTurnos(turnos)
                .build();
        partidaRepository.save(p);
    }
}
