package com.clout.tron.dto;

import lombok.Data;

@Data
public class FimPartidaRequest {

    private Long partidaId;
    private String vencedor; // "PLAYER" ou "BOT"
    private int turnos;
}
