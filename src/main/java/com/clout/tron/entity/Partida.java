package com.clout.tron.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "partida")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dataHora;

    private String vencedor;      // "PLAYER" ou "BOT"
    private int duracaoTurnos;
}
