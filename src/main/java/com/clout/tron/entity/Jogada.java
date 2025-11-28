package com.clout.tron.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "jogada")
@Getter
@Setter
public class Jogada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_partida")
    private Partida partida;

    private Integer turno;

    @Column(name = "estado_json", columnDefinition = "TEXT")
    private String estadoJson;

    private String acao;      // UP/DOWN/LEFT/RIGHT
    private String resultado; // "WIN", "LOSE", "MID"
}
