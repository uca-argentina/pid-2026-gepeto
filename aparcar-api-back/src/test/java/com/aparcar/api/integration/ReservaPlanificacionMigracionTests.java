package com.aparcar.api.integration;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservaPlanificacionMigracionTests {
    @Test
    void conservaReservasInicializaCandadoYExigeUnaAsignacionCoherente() throws Exception {
        // H2 conserva la sesion que compila los CHECK con IN: la misma conexion
        // debe seguir abierta al comprobar las restricciones despues de Liquibase.
        var source = new SingleConnectionDataSource("jdbc:h2:mem:planificacionMigration", "sa", "", true);
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE reservas (id UUID PRIMARY KEY, cochera_id UUID NOT NULL, estado VARCHAR(20),
                    codigo VARCHAR(10), tipo_tarifa VARCHAR(20), precio_total DECIMAL(18,2),
                    fecha_creacion TIMESTAMP WITH TIME ZONE)
                """);
        UUID existente = UUID.randomUUID();
        UUID cochera = UUID.randomUUID();
        jdbc.update("INSERT INTO reservas (id, cochera_id, estado, codigo) VALUES (?, ?, 'CONFIRMADA', 'ABCDE12345')",
                existente, cochera);

        var migracion = new SpringLiquibase();
        migracion.setDataSource(source);
        migracion.setChangeLog("classpath:db/changelog/013-reservas-recurrentes-espera.yaml");
        migracion.afterPropertiesSet();

        assertEquals(cochera, jdbc.queryForObject("SELECT cochera_id FROM reservas WHERE id = ?", UUID.class, existente));
        assertEquals("ABCDE12345", jdbc.queryForObject("SELECT codigo FROM reservas WHERE id = ?", String.class, existente));
        assertNull(jdbc.queryForObject("SELECT recurrencia_id FROM reservas WHERE id = ?", UUID.class, existente));
        assertNull(jdbc.queryForObject("SELECT confirmada_desde_espera_at FROM reservas WHERE id = ?", Object.class, existente));
        assertNull(jdbc.queryForObject("SELECT notificacion_leida_at FROM reservas WHERE id = ?", Object.class, existente));
        assertEquals(1, jdbc.queryForObject("SELECT id FROM planificacion_reservas", Integer.class));

        UUID espera = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO reservas (id, estado, tipo_tarifa, precio_total)
                VALUES (?, 'LISTA_ESPERA', 'AUTO', 1000)
                """, espera);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE reservas SET codigo = '12345ABCDE' WHERE id = ?", espera));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE reservas SET cochera_id = ? WHERE id = ?", cochera, espera));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE reservas SET estado = 'CONFIRMADA' WHERE id = ?", espera));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE reservas SET precio_total = NULL WHERE id = ?", espera));

        jdbc.update("UPDATE reservas SET estado = 'CANCELADA' WHERE id = ?", espera);
        migracion.afterPropertiesSet();
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM reservas", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM planificacion_reservas", Integer.class));
        source.destroy();
    }
}
