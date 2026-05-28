package com.oran.defender.model;

import jakarta.persistence.*;

@Entity
public class NetworkCell {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
