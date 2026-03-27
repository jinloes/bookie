package com.bookie.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "outlook_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutlookToken {

    @Id
    private Long id;

    @Column(nullable = false, length = 4096)
    private String accessToken;

    @Column(nullable = false, length = 4096)
    private String refreshToken;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}