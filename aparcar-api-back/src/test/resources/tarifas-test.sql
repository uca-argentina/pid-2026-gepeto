DELETE FROM tarifas;
INSERT INTO tarifas (tipo, hora, fraccion, media_jornada, jornada_completa, version) VALUES
('AUTO', 1000.00, 300.00, 9000.00, 16000.00, 0),
('MOTO', 500.00, 150.00, 4500.00, 8000.00, 0),
('ACCESIBLE', 800.00, 200.00, 7000.00, 12000.00, 0),
('CARGA', 2000.00, 600.00, 18000.00, 32000.00, 0);
