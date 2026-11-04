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
 * Aplica 015-puntos.yaml sobre una base con reservas: no les inventa puntos
 * (sin backfill), crea la billetera y el libro con sus restricciones, y se
 * puede volver a correr sin cambios.
 */
class PuntosMigracionTests {
    @Test
    void agregaLosPuntosSinBackfillYConSusRestricciones() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:puntosMigration;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE reservas (id UUID PRIMARY KEY, precio_total DECIMAL(18,2))");
        UUID anterior = UUID.randomUUID();
        jdbc.update("INSERT INTO reservas VALUES (?, 1000.00)", anterior);

        var migration = new SpringLiquibase();
        migration.setDataSource(source);
        migration.setChangeLog("classpath:db/changelog/015-puntos.yaml");
        migration.afterPropertiesSet();

        // Las reservas existentes quedan sin canje ni acreditacion.
        assertNull(jdbc.queryForObject("SELECT puntos_usados FROM reservas WHERE id = ?", Integer.class, anterior));
        assertNull(jdbc.queryForObject("SELECT precio_final FROM reservas WHERE id = ?", Object.class, anterior));
        assertNull(jdbc.queryForObject("SELECT puntos_acreditados FROM reservas WHERE id = ?", Integer.class, anterior));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM billeteras_puntos", Integer.class));

        UUID visitante = UUID.randomUUID();
        jdbc.update("INSERT INTO billeteras_puntos (id, visitante_id) VALUES (?, ?)", UUID.randomUUID(), visitante);
        assertEquals(0, jdbc.queryForObject("SELECT saldo FROM billeteras_puntos", Integer.class));
        // Una billetera por cuenta, y el saldo nunca negativo.
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO billeteras_puntos (id, visitante_id) VALUES (?, ?)", UUID.randomUUID(), visitante));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE billeteras_puntos SET saldo = -1 WHERE visitante_id = ?", visitante));

        // Una reserva acredita una sola vez; el bonus de la misma reserva es otro tipo.
        String movimiento = """
                INSERT INTO movimientos_puntos (id, visitante_id, tipo, puntos, reserva_id, descripcion, fecha)
                VALUES (?, ?, ?, 20, ?, 'x', CURRENT_TIMESTAMP)
                """;
        jdbc.update(movimiento, UUID.randomUUID(), visitante, "ACREDITACION_FRANJA", anterior);
        jdbc.update(movimiento, UUID.randomUUID(), visitante, "BONUS_RACHA", anterior);
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update(movimiento, UUID.randomUUID(), visitante, "ACREDITACION_FRANJA", anterior));

        migration.afterPropertiesSet();
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM movimientos_puntos", Integer.class));
    }
}
