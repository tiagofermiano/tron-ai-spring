package com.clout.tron.controller;

import com.clout.tron.dto.EstadoDTO;
import com.clout.tron.dto.FimPartidaRequest;
import com.clout.tron.dto.MovimentoIARequest;
import com.clout.tron.service.JogadaService;
import com.clout.tron.service.PartidaService;
import com.clout.tron.service.TronAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class GameController {

    private final TronAiService tronAiService;
    private final PartidaService partidaService;
    private final JogadaService jogadaService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/game")
    public String game() {
        return "game";
    }

    // cria nova partida (chamado no início do jogo pelo JS)
    @PostMapping("/api/partidas/nova")
    @ResponseBody
    public ResponseEntity<Long> novaPartida() {
        Long id = partidaService.novaPartida();
        return ResponseEntity.ok(id);
    }

    // movimento da IA
    @PostMapping("/api/ia/movimento")
    @ResponseBody
    public ResponseEntity<String> movimentoIA(@RequestBody MovimentoIARequest req) {

        Long partidaId = req.getPartidaId();
        EstadoDTO estado = req.getEstado();

        String acao = tronAiService.decidirMovimento(estado);

        // registra jogada no histórico
        jogadaService.registrar(partidaId, estado.getTurno(), estado, acao);

        return ResponseEntity.ok(acao);
    }

    // fim de partida
    @PostMapping("/api/partidas/fim")
    @ResponseBody
    public ResponseEntity<Void> fimPartida(@RequestBody FimPartidaRequest req) {
        partidaService.finalizar(req.getPartidaId(), req.getVencedor(), req.getTurnos());

        String resultadoBot = "PLAYER".equalsIgnoreCase(req.getVencedor()) ? "LOSE" : "WIN";
        jogadaService.marcarResultadoPartida(req.getPartidaId(), resultadoBot);

        return ResponseEntity.ok().build();
    }
}
