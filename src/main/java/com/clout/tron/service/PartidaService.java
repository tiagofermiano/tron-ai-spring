package com.clout.tron.service;

import com.clout.tron.entity.Partida;
import com.clout.tron.repository.PartidaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PartidaService {

    private final PartidaRepository partidaRepository;

    public Long novaPartida() {
        Partida p = new Partida();
        p.setDataHora(LocalDateTime.now());
        p.setDuracaoTurnos(0);
        p.setVencedor(null);
        partidaRepository.save(p);
        return p.getId();
    }

    public void finalizar(Long partidaId, String vencedor, int turnos) {
        Partida p = partidaRepository.findById(partidaId)
                .orElseThrow(() -> new IllegalArgumentException("Partida não encontrada: " + partidaId));
        p.setVencedor(vencedor);
        p.setDuracaoTurnos(turnos);
        partidaRepository.save(p);
    }

    public List<Partida> listarHistorico() {
        return partidaRepository.findAllByOrderByDataHoraDesc();
    }
}
