package com.aparcar.api.integration;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class TarifaMigracionTests {
    @Test
    void migracionConservaReservasAnterioresSinAsignarlesUnPrecio() throws Exception {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:tarifaMigration;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(datasource);
        jdbc.execute("CREATE TABLE reservas (id INTEGER PRIMARY KEY)");
        jdbc.update("INSERT INTO reservas(id) VALUES (1)");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(datasource);
        liquibase.setChangeLog("classpath:db/changelog/008-tarifas.yaml");
        liquibase.afterPropertiesSet();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservas", Integer.class));
        assertNull(jdbc.queryForObject("SELECT precio_total FROM reservas WHERE id = 1", java.math.BigDecimal.class));
        assertNull(jdbc.queryForObject("SELECT tipo_tarifa FROM reservas WHERE id = 1", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM tarifas", Integer.class));
        // Es idempotente: el segundo arranque no vuelve a agregar las columnas.
        liquibase.afterPropertiesSet();
    }
}
