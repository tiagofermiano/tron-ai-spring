package com.clout.tron.repository;

import com.clout.tron.entity.Jogada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JogadaRepository extends JpaRepository<Jogada, Long> {

    List<Jogada> findTop300ByOrderByIdDesc();

    List<Jogada> findByPartidaId(Long partidaId);
    List<Jogada> findTop50ByEstadoJsonOrderByIdDesc(String estadoJson);
}
