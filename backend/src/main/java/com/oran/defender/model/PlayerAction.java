package com.oran.defender.model;

import jakarta.persistence.*;

@Entity
public class PlayerAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
