package com.clout.tron.controller;

import com.clout.tron.service.PartidaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HistoricoController {

    private final PartidaService service;

    @GetMapping("/historico")
    public String historico(Model model) {
        model.addAttribute("partidas", service.listarHistorico());
        return "historico";
    }
}
