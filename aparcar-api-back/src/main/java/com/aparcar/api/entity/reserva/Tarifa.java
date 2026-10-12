package com.aparcar.api.entity.reserva;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "tarifas")
@Getter
@Setter
@NoArgsConstructor
public class Tarifa {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CocheraTipo tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal hora;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fraccion;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal mediaJornada;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal jornadaCompleta;

    @Version
    private Long version;
}
