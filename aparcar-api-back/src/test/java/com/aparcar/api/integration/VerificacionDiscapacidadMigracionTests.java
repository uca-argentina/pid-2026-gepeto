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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aplica 014-verificacion-discapacidad.yaml sobre una base con cuentas: no les
 * toca el acceso (el plazo de gracia lo da DiscapacidadMigracionBackfill al
 * arrancar), crea las tablas y se puede volver a correr sin cambios.
 */
class VerificacionDiscapacidadMigracionTests {
    @Test
    void agregaLaVerificacionSinTocarElAccesoDeLasCuentasExistentes() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:verificacionMigration;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE visitantes (id UUID PRIMARY KEY, email VARCHAR(255), tiene_discapacidad BOOLEAN)");
        UUID autodeclarada = UUID.randomUUID();
        jdbc.update("INSERT INTO visitantes VALUES (?, 'ana@test.com', TRUE)", autodeclarada);

        var migration = new SpringLiquibase();
        migration.setDataSource(source);
        migration.setChangeLog("classpath:db/changelog/014-verificacion-discapacidad.yaml");
        migration.afterPropertiesSet();

        // El acceso no cambia en la migracion: no pierde nada de golpe.
        assertTrue(jdbc.queryForObject("SELECT tiene_discapacidad FROM visitantes WHERE id = ?", Boolean.class, autodeclarada));
        assertNull(jdbc.queryForObject(
                "SELECT discapacidad_acceso_provisorio_hasta FROM visitantes WHERE id = ?", Object.class, autodeclarada));

        UUID solicitud = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO verificaciones_discapacidad (id, visitante_id, estado, fecha_creacion, documento_clave,
                    documento_nombre, documento_tipo, documento_tamano, documento_sha256)
                VALUES (?, ?, 'EN_REVISION', CURRENT_TIMESTAMP, 'clave.pdf', 'cud.pdf', 'application/pdf', 10, 'x')
                """, solicitud, autodeclarada);
        assertEquals(0L, jdbc.queryForObject("SELECT version FROM verificaciones_discapacidad", Long.class));
        // Una solicitud siempre pertenece a una cuenta existente.
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO verificaciones_discapacidad (id, visitante_id, estado, fecha_creacion, documento_clave,
                    documento_nombre, documento_tipo, documento_tamano, documento_sha256)
                VALUES (?, ?, 'EN_REVISION', CURRENT_TIMESTAMP, 'clave.pdf', 'cud.pdf', 'application/pdf', 10, 'x')
                """, UUID.randomUUID(), UUID.randomUUID()));
        // La auditoria de accesos no depende de la solicitud ni de la cuenta.
        jdbc.update("""
                INSERT INTO verificacion_documento_accesos (id, verificacion_id, fecha, actor_id, actor_email, actor_rol)
                VALUES (?, ?, CURRENT_TIMESTAMP, ?, 'admin@test.com', 'ADMIN')
                """, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        migration.afterPropertiesSet();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM verificaciones_discapacidad", Integer.class));
    }
}
