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

    private final PartidaRepository repository;

    public void registrar(String vencedor, int turnos) {
        Partida p = Partida.builder()
                .dataHora(LocalDateTime.now())
                .vencedor(vencedor)
                .duracaoTurnos(turnos)
                .build();

        repository.save(p);
    }

    public List<Partida> listar() {
        return repository.findAll();
    }
}
