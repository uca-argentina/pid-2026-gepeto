-- Cuenta real para el principal por defecto de @WithMockUser(authorities = "ADMIN").
INSERT INTO visitantes (id, nombre, documento, email, password, is_active, tiene_discapacidad)
VALUES ('00000000-0000-0000-0000-000000009001', 'Administrador de prueba', 'admin-test', 'user', 'hash', true, false);
INSERT INTO visitante_authorities (visitante_id, authority)
VALUES ('00000000-0000-0000-0000-000000009001', 'ADMIN');
