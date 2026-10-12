package com.aparcar.api.integration;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class PerfilMigracionTests {
    @Test
    void conservaCuentasExistentesSinInventarNombres() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:perfilMigration;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE visitantes (id INTEGER PRIMARY KEY, email VARCHAR(255))");
        jdbc.update("INSERT INTO visitantes VALUES (1, 'admin@test.com')");
        var migration = new SpringLiquibase();
        migration.setDataSource(source);
        migration.setChangeLog("classpath:db/changelog/009-nombre-estacionamiento.yaml");
        migration.afterPropertiesSet();
        assertEquals("admin@test.com", jdbc.queryForObject("SELECT email FROM visitantes WHERE id=1", String.class));
        assertNull(jdbc.queryForObject("SELECT nombre_estacionamiento FROM visitantes WHERE id=1", String.class));
        migration.afterPropertiesSet();
    }
}
