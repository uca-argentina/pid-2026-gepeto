package com.aparcar.api.integration;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConvenioMigracionTests {
    @Test
    void conservaElHistorialCargaConveniosUnaVezYEliminaSoloLasCredencialesAsociadas() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:convenioMigration;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE visitantes (id UUID PRIMARY KEY)");
        jdbc.execute("CREATE TABLE reservas (id UUID PRIMARY KEY, precio_total DECIMAL(18,2), precio_final DECIMAL(18,2))");
        UUID anterior = UUID.randomUUID();
        jdbc.update("INSERT INTO reservas (id, precio_total) VALUES (?, 1000.00)", anterior);

        var migration = new SpringLiquibase();
        migration.setDataSource(source);
        migration.setChangeLog("classpath:db/changelog/017-convenios.yaml");
        migration.afterPropertiesSet();

        // La migracion no inventa descuentos sobre reservas anteriores.
        assertEquals(new BigDecimal("1000.00"), jdbc.queryForObject(
                "SELECT precio_total FROM reservas WHERE id = ?", BigDecimal.class, anterior));
        for (String columna : new String[]{"convenio_id", "convenio_nombre", "convenio_descuento_pct", "convenio_descuento_monto"}) {
            assertNull(jdbc.queryForObject("SELECT " + columna + " FROM reservas WHERE id = ?", Object.class, anterior));
        }
        assertNull(jdbc.queryForObject("SELECT precio_final FROM reservas WHERE id = ?", BigDecimal.class, anterior));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM convenios", Integer.class));

        UUID uca = jdbc.queryForObject("SELECT id FROM convenios WHERE nombre = 'UCA'", UUID.class);
        UUID nacion = jdbc.queryForObject("SELECT id FROM convenios WHERE nombre = 'Club Nación'", UUID.class);
        assertEquals("UCA-XXX", jdbc.queryForObject("SELECT formato_credencial FROM convenios WHERE id = ?", String.class, uca));
        assertEquals("NACION-XXX", jdbc.queryForObject("SELECT formato_credencial FROM convenios WHERE id = ?", String.class, nacion));
        assertEquals(new BigDecimal("15.00"), jdbc.queryForObject("SELECT descuento_pct FROM convenios WHERE id = ?", BigDecimal.class, uca));
        assertEquals(new BigDecimal("10.00"), jdbc.queryForObject("SELECT descuento_pct FROM convenios WHERE id = ?", BigDecimal.class, nacion));
        for (int porcentaje : new int[]{0, 101}) {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "UPDATE convenios SET descuento_pct = ? WHERE id = ?", porcentaje, uca));
        }

        UUID visitante = UUID.randomUUID();
        jdbc.update("INSERT INTO visitantes (id) VALUES (?)", visitante);
        String insertarCredencial = "INSERT INTO credenciales_convenio (id, visitante_id, convenio_id, credencial) VALUES (?, ?, ?, ?)";
        jdbc.update(insertarCredencial, UUID.randomUUID(), visitante, uca, "UCA-123");
        jdbc.update(insertarCredencial, UUID.randomUUID(), visitante, nacion, "NACION-123");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                insertarCredencial, UUID.randomUUID(), visitante, uca, "UCA-123"));

        UUID conDescuento = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO reservas (id, precio_total, precio_final, convenio_id, convenio_nombre,
                                      convenio_descuento_pct, convenio_descuento_monto)
                VALUES (?, 1000.00, 850.00, ?, 'UCA', 15.00, 150.00)
                """, conDescuento, uca);
        jdbc.update("UPDATE convenios SET nombre = 'UCA actualizado', descuento_pct = 25 WHERE id = ?", uca);
        jdbc.update("DELETE FROM convenios WHERE id = ?", uca);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM credenciales_convenio WHERE convenio_id = ?", Integer.class, uca));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM credenciales_convenio WHERE convenio_id = ?", Integer.class, nacion));

        // La FK de visitante tambien limpia sus credenciales; ninguna FK une el historial al catalogo.
        jdbc.update("DELETE FROM visitantes WHERE id = ?", visitante);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM credenciales_convenio", Integer.class));
        jdbc.update("DELETE FROM convenios WHERE id = ?", nacion);
        migration.afterPropertiesSet();

        // Reiniciar no vuelve a crear los convenios eliminados ni cambia el precio acordado.
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM convenios", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM reservas", Integer.class));
        assertEquals(uca, jdbc.queryForObject("SELECT convenio_id FROM reservas WHERE id = ?", UUID.class, conDescuento));
        assertEquals("UCA", jdbc.queryForObject("SELECT convenio_nombre FROM reservas WHERE id = ?", String.class, conDescuento));
        assertEquals(new BigDecimal("15.00"), jdbc.queryForObject("SELECT convenio_descuento_pct FROM reservas WHERE id = ?", BigDecimal.class, conDescuento));
        assertEquals(new BigDecimal("150.00"), jdbc.queryForObject("SELECT convenio_descuento_monto FROM reservas WHERE id = ?", BigDecimal.class, conDescuento));
        assertEquals(new BigDecimal("850.00"), jdbc.queryForObject("SELECT precio_final FROM reservas WHERE id = ?", BigDecimal.class, conDescuento));
    }
}
