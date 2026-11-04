package com.aparcar.api.entity.convenio;

import com.aparcar.api.entity.auth.Visitante;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "credenciales_convenio", uniqueConstraints = @UniqueConstraint(
        name = "uk_credenciales_convenio_titular", columnNames = {"visitante_id", "convenio_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CredencialConvenio {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    @JoinColumn(name = "visitante_id", nullable = false)
    private Visitante visitante;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    @JoinColumn(name = "convenio_id", nullable = false)
    private Convenio convenio;

    @Column(nullable = false, length = 100)
    private String credencial;
}
