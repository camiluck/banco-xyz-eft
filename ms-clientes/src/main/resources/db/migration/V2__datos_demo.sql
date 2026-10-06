-- Clientes de demostración (coinciden con los cliente_id de ms-cuentas)
INSERT INTO clientes (id, rut, nombres, apellidos, email, telefono, direccion, fecha_nacimiento, segmento, activo, cuentas_activas, fecha_registro, version) VALUES
 (1, '12345678-5', 'Juan Andrés', 'Pérez Soto',    'juan.perez@correo.cl',   '+56911112222', 'Av. Providencia 1234, Santiago', DATE '1990-04-12', 'PREMIUM', TRUE, 2, CURRENT_TIMESTAMP, 0),
 (2, '15678432-K', 'María José',  'González Rojas', 'maria.gonzalez@correo.cl', '+56933334444', 'Calle Prat 456, Valparaíso',   DATE '1985-09-30', 'PERSONA', TRUE, 1, CURRENT_TIMESTAMP, 0),
 (3, '9876543-3',  'Pedro',       'Muñoz Díaz',     'pedro.munoz@correo.cl',  '+56955556666', 'O''Higgins 789, Concepción',    DATE '1978-01-05', 'PYME',    TRUE, 2, CURRENT_TIMESTAMP, 0);

ALTER TABLE clientes ALTER COLUMN id RESTART WITH 100;
