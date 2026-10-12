package com.aparcar.api.integration;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Aplica 011-acceso-reservas.yaml sobre una base con reservas ya cargadas: no
 * tiene que tocar sus datos (el codigo lo asigna el backfill, no SQL), y tiene
 * que dejar el codigo unico y la tabla de intentos lista.
 */
class ReservaAccesoMigracionTests {
    @Test
    void agregaLasColumnasSinTocarLasReservasYElCodigoEsUnico() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:accesoMigration;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE reservas (id UUID PRIMARY KEY, estado VARCHAR(20))");
        jdbc.execute("CREATE TABLE reserva_movimientos (id UUID PRIMARY KEY, accion VARCHAR(20), "
                + "fecha TIMESTAMP WITH TIME ZONE)");
        UUID existente = UUID.randomUUID();
        UUID otra = UUID.randomUUID();
        jdbc.update("INSERT INTO reservas VALUES (?, 'CONFIRMADA')", existente);
        jdbc.update("INSERT INTO reservas VALUES (?, 'CANCELADA')", otra);

        var migration = new SpringLiquibase();
        migration.setDataSource(source);
        migration.setChangeLog("classpath:db/changelog/011-acceso-reservas.yaml");
        migration.afterPropertiesSet();

        assertEquals("CONFIRMADA", jdbc.queryForObject("SELECT estado FROM reservas WHERE id = ?", String.class, existente));
        assertNull(jdbc.queryForObject("SELECT codigo FROM reservas WHERE id = ?", String.class, existente));
        // Varias reservas sin codigo conviven: el indice unico admite varios NULL.
        jdbc.update("UPDATE reservas SET codigo = 'ABCDE12345', estado = 'EN_CURSO' WHERE id = ?", existente);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE reservas SET codigo = 'ABCDE12345' WHERE id = ?", otra));

        jdbc.update("""
                INSERT INTO acceso_intentos (id, fecha, operacion, codigo, reserva_id, resultado, actor_email)
                VALUES (?, CURRENT_TIMESTAMP, 'CONSULTA', 'ZZZZZ99999', NULL, 'INEXISTENTE', 'admin@test.com')
                """, UUID.randomUUID());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM acceso_intentos", Integer.class));

        // Volver a correr la migracion no hace nada.
        migration.afterPropertiesSet();
        assertEquals("ABCDE12345", jdbc.queryForObject("SELECT codigo FROM reservas WHERE id = ?", String.class, existente));
    }
}
