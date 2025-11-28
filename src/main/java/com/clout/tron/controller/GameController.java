package com.clout.tron.controller;

import com.clout.tron.dto.FimPartidaRequest;
import com.clout.tron.dto.MovimentoIARequest;
import com.clout.tron.service.PartidaService;
import com.clout.tron.service.TronAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class GameController {

    private final TronAiService ai;
    private final PartidaService partidaService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/game")
    public String game() {
        return "game";
    }

    @PostMapping("/api/ia/movimento")
    @ResponseBody
    public ResponseEntity<String> movimentoIA(@RequestBody MovimentoIARequest req) {
        String resposta = ai.decidirJogada(req);
        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/api/partidas/fim")
    @ResponseBody
    public ResponseEntity<Void> fimPartida(@RequestBody FimPartidaRequest req) {
        partidaService.registrar(req.getVencedor(), req.getTurnos());
        return ResponseEntity.ok().build();
    }
}
