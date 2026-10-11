package com.aparcar.api.integration;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardMigracionTests {
    @Test
    void agregaIndicesDeConsultaSinModificarReservasExistentes() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:dashboardMigration;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE reservas (id INTEGER PRIMARY KEY, estado VARCHAR(20), "
                + "desde TIMESTAMP, ingreso_at TIMESTAMP, egreso_at TIMESTAMP)");
        jdbc.execute("INSERT INTO reservas VALUES (1, 'EN_CURSO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)");
        var migration = new SpringLiquibase();
        migration.setDataSource(source);
        migration.setChangeLog("classpath:db/changelog/012-dashboard-indices.yaml");
        migration.afterPropertiesSet();
        migration.afterPropertiesSet();

        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
                + "WHERE INDEX_NAME LIKE 'IDX_RESERVAS_%'", Integer.class));
        assertEquals("EN_CURSO", jdbc.queryForObject("SELECT estado FROM reservas WHERE id = 1", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservas", Integer.class));
    }
}
