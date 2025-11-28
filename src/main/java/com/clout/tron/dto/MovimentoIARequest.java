package com.clout.tron.dto;

import lombok.Data;

@Data
public class MovimentoIARequest {

    private Long partidaId; // id da partida corrente
    private EstadoDTO estado;
}
