package com.clout.tron.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "partida")
@Getter
@Setter
public class Partida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String vencedor; // "PLAYER" ou "BOT"

    @Column(name = "duracao_turnos")
    private Integer duracaoTurnos;

    @Column(name = "data_hora")
    private LocalDateTime dataHora;
}
