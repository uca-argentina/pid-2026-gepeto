package com.aparcar.api.integration;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReservaTrazabilidadMigracionTests {
    @Test
    void conservaReservasAnterioresSinInventarMovimientosYPermiteRegistrarNuevos() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:trazabilidadMigration;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE reservas (id UUID PRIMARY KEY, estado VARCHAR(20))");
        UUID reservaId = UUID.randomUUID();
        jdbc.update("INSERT INTO reservas VALUES (?, 'CANCELADA')", reservaId);
        var migration = new SpringLiquibase();
        migration.setDataSource(source);
        migration.setChangeLog("classpath:db/changelog/010-trazabilidad-reservas.yaml");
        migration.afterPropertiesSet();

        assertEquals("CANCELADA", jdbc.queryForObject("SELECT estado FROM reservas WHERE id = ?", String.class, reservaId));
        assertEquals(0L, jdbc.queryForObject("SELECT version FROM reservas WHERE id = ?", Long.class, reservaId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM reserva_movimientos", Integer.class));
        UUID actorId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO reserva_movimientos (id, reserva_id, accion, fecha, actor_id, actor_nombre, actor_email, actor_rol)
                VALUES (?, ?, 'CANCELACION', CURRENT_TIMESTAMP, ?, 'Ana', 'ana@test.com', 'ADMIN')
                """, UUID.randomUUID(), reservaId, actorId);
        // No hay FK a la cuenta del autor: su identidad historica sobrevive a su baja.
        assertEquals(actorId, jdbc.queryForObject("SELECT actor_id FROM reserva_movimientos", UUID.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO reserva_movimientos (id, reserva_id, accion, fecha, actor_id, actor_nombre, actor_email, actor_rol)
                VALUES (?, ?, 'ALTA', CURRENT_TIMESTAMP, ?, 'Ana', 'ana@test.com', 'ADMIN')
                """, UUID.randomUUID(), UUID.randomUUID(), actorId));
        migration.afterPropertiesSet();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reserva_movimientos", Integer.class));
    }
}
