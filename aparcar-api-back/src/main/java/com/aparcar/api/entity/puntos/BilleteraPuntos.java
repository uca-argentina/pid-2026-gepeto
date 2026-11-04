package com.aparcar.api.entity.puntos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalDateJdbcType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Los puntos de una persona: saldo y racha. Se crea la primera vez que hace
 * falta (al acreditar o al canjear); quien nunca uso puntos no tiene fila.
 *
 * <p>El saldo es la suma de sus {@link MovimientoPuntos} y nunca es negativo.
 * El visitante se guarda como id, sin FK, igual que el resto de los registros
 * de auditoria: el libro de puntos sobrevive a que la cuenta se elimine.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "billeteras_puntos")
@Check(constraints = "saldo >= 0")
public class BilleteraPuntos {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "visitante_id", nullable = false, unique = true, updatable = false)
    private UUID visitanteId;

    @Column(nullable = false)
    private Integer saldo = 0;

    @Column(name = "racha_actual", nullable = false)
    private Integer rachaActual = 0;

    @Column(name = "mejor_racha", nullable = false)
    private Integer mejorRacha = 0;

    /**
     * Ultimo dia (hora del predio) que conto para la racha. LocalDate directo:
     * pasar por java.sql.Date lo correria un dia si la JVM arranco en otra zona.
     */
    @Column(name = "ultimo_dia_racha")
    @JdbcType(LocalDateJdbcType.class)
    private LocalDate ultimoDiaRacha;

    @Version
    @Column(nullable = false)
    private Long version;

    public BilleteraPuntos(UUID visitanteId) {
        this.visitanteId = visitanteId;
    }
}
