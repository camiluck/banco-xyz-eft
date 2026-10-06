-- Datos de demostración (PIN de todas las tarjetas: 1234, guardado con BCrypt)
INSERT INTO cuentas (id, numero, cliente_id, tipo, saldo, estado, pin_hash, intentos_fallidos_pin, fecha_apertura, version) VALUES
 (1, 'XYZ-0001-10000001', 1, 'AHORRO',    1500000.00, 'ACTIVA', '$2a$10$1oCVpZ0WNMb0S/bbdSLLueLhXrk/m..ONJ5VySusl/mIf6Opz/LKa', 0, CURRENT_TIMESTAMP, 0),
 (2, 'XYZ-0001-10000002', 1, 'CORRIENTE',  350000.00, 'ACTIVA', '$2a$10$1oCVpZ0WNMb0S/bbdSLLueLhXrk/m..ONJ5VySusl/mIf6Opz/LKa', 0, CURRENT_TIMESTAMP, 0),
 (3, 'XYZ-0002-10000003', 2, 'CORRIENTE',  820000.00, 'ACTIVA', '$2a$10$1oCVpZ0WNMb0S/bbdSLLueLhXrk/m..ONJ5VySusl/mIf6Opz/LKa', 0, CURRENT_TIMESTAMP, 0),
 (4, 'XYZ-0003-10000004', 3, 'AHORRO',      50000.00, 'ACTIVA', '$2a$10$1oCVpZ0WNMb0S/bbdSLLueLhXrk/m..ONJ5VySusl/mIf6Opz/LKa', 0, CURRENT_TIMESTAMP, 0),
 (5, 'XYZ-0003-10000005', 3, 'VISTA',            0.00, 'ACTIVA', '$2a$10$1oCVpZ0WNMb0S/bbdSLLueLhXrk/m..ONJ5VySusl/mIf6Opz/LKa', 0, CURRENT_TIMESTAMP, 0);

INSERT INTO movimientos (id, cuenta_id, tipo, monto, saldo_resultante, referencia, descripcion, fecha) VALUES
 (1, 1, 'CREDITO', 1500000.00, 1500000.00, 'MIGRACION-LEGACY-1', 'Saldo migrado desde sistema legacy', CURRENT_TIMESTAMP),
 (2, 2, 'CREDITO',  350000.00,  350000.00, 'MIGRACION-LEGACY-2', 'Saldo migrado desde sistema legacy', CURRENT_TIMESTAMP),
 (3, 3, 'CREDITO',  820000.00,  820000.00, 'MIGRACION-LEGACY-3', 'Saldo migrado desde sistema legacy', CURRENT_TIMESTAMP),
 (4, 4, 'CREDITO',   50000.00,   50000.00, 'MIGRACION-LEGACY-4', 'Saldo migrado desde sistema legacy', CURRENT_TIMESTAMP);

-- Los nuevos registros parten después de los datos de demo
ALTER TABLE cuentas ALTER COLUMN id RESTART WITH 100;
ALTER TABLE movimientos ALTER COLUMN id RESTART WITH 100;
