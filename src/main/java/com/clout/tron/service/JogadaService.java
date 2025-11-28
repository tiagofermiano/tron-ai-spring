package com.clout.tron.service;

import com.clout.tron.dto.EstadoDTO;
import com.clout.tron.entity.Jogada;
import com.clout.tron.entity.Partida;
import com.clout.tron.repository.JogadaRepository;
import com.clout.tron.repository.PartidaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JogadaService {

    private final JogadaRepository jogadaRepository;
    private final PartidaRepository partidaRepository;
    private final ObjectMapper objectMapper;

    public void registrar(Long partidaId, int turno, EstadoDTO estado, String acao) {
        Partida partida = partidaRepository.findById(partidaId)
                .orElseThrow(() -> new IllegalArgumentException("Partida não encontrada: " + partidaId));

        try {
            Jogada jogada = new Jogada();
            jogada.setPartida(partida);
            jogada.setTurno(turno);
            jogada.setAcao(acao);
            jogada.setResultado("MID");
            jogada.setEstadoJson(objectMapper.writeValueAsString(estado));
            jogadaRepository.save(jogada);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao registrar jogada", e);
        }
    }

    public void marcarResultadoPartida(Long partidaId, String resultadoBot) {
        List<Jogada> jogadas = jogadaRepository.findByPartidaId(partidaId);
        for (Jogada j : jogadas) {
            j.setResultado(resultadoBot);
        }
        jogadaRepository.saveAll(jogadas);
    }

    public List<Jogada> ultimasParaAprendizado(int limite) {
        return jogadaRepository.findTop300ByOrderByIdDesc();
    }
}
