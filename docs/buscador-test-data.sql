-- =============================================================================
-- DATOS DE PRUEBA - Buscador Avanzado de Estudiantes (examen 1,5H)
-- Base de datos: erp_little_horizons (la misma del ERP, ver docs/database.sql)
--
-- Por qué existe este script: las matrículas de ejemplo de database.sql son de
-- 2026 (Activa) y 2024/2025 (Completada). El buscador consulta matrículas
-- 'Activa' del año 2025, así que sin estos datos el resultado siempre sería vacío.
--
-- Qué crea (5 estudiantes nuevos y su matrícula 2025):
--   1023456789  Juan Carlos Pérez González   Décimo   Activa     -> APARECE en Décimo
--   1023456790  María Elena López Ramírez    Décimo   Activa     -> APARECE en Décimo
--   1023456791  Carlos Gómez Silva           Décimo   Activa     -> APARECE (sin segundo nombre)
--   1023456792  Laura Sofía Castro Rojas     Décimo   Retirada   -> NO debe aparecer (no está Activa)
--   1023456793  Andrés Mejía                 Noveno   Activa     -> solo aparece en Noveno
--                                                                  (sin segundo nombre ni segundo apellido)
--
-- Resultado esperado en el buscador:
--   Décimo (11) -> 3 estudiantes | Noveno (10) -> 1 estudiante | Once (12) -> ninguno
--
-- Es seguro ejecutarlo más de una vez: si el documento o la matrícula ya existen
-- no se duplican (ON DUPLICATE KEY UPDATE sobre las claves únicas).
-- =============================================================================

USE erp_little_horizons;
SET NAMES utf8mb4;

-- 1) Estudiantes (numero_documento es UNIQUE: si ya existe, no hace nada)
INSERT INTO estudiantes
    (tipo_documento, numero_documento, primer_nombre, segundo_nombre, primer_apellido, segundo_apellido,
     fecha_nacimiento, genero, fecha_ingreso, estado)
VALUES
    ('TI', '1023456789', 'Juan',   'Carlos', 'Pérez',  'González', '2009-03-14', 'M', '2020-01-20', 'Activo'),
    ('TI', '1023456790', 'María',  'Elena',  'López',  'Ramírez',  '2009-07-22', 'F', '2020-01-20', 'Activo'),
    ('TI', '1023456791', 'Carlos', NULL,     'Gómez',  'Silva',    '2009-11-05', 'M', '2021-01-18', 'Activo'),
    ('TI', '1023456792', 'Laura',  'Sofía',  'Castro', 'Rojas',    '2009-05-30', 'F', '2021-01-18', 'Retirado'),
    ('TI', '1023456793', 'Andrés', NULL,     'Mejía',  NULL,       '2010-02-09', 'M', '2022-01-17', 'Activo')
ON DUPLICATE KEY UPDATE numero_documento = numero_documento;

-- 2) Matrículas del año 2025. El estudiante y el grado se buscan por su dato único
--    (documento y nivel_numerico: 10 = Décimo, 9 = Noveno), así no se escriben ids a mano.
--    matriculas tiene UNIQUE (id_estudiante, anio_academico): una matrícula por año.
INSERT INTO matriculas
    (id_estudiante, id_grado, anio_academico, estado_matricula, fecha_matricula, fecha_retiro)
VALUES
    ((SELECT id_estudiante FROM estudiantes WHERE numero_documento = '1023456789'),
     (SELECT id_grado FROM grados WHERE nivel_numerico = 10), 2025, 'Activa',   '2025-01-20', NULL),
    ((SELECT id_estudiante FROM estudiantes WHERE numero_documento = '1023456790'),
     (SELECT id_grado FROM grados WHERE nivel_numerico = 10), 2025, 'Activa',   '2025-01-20', NULL),
    ((SELECT id_estudiante FROM estudiantes WHERE numero_documento = '1023456791'),
     (SELECT id_grado FROM grados WHERE nivel_numerico = 10), 2025, 'Activa',   '2025-01-20', NULL),
    ((SELECT id_estudiante FROM estudiantes WHERE numero_documento = '1023456792'),
     (SELECT id_grado FROM grados WHERE nivel_numerico = 10), 2025, 'Retirada', '2025-01-20', '2025-06-10'),
    ((SELECT id_estudiante FROM estudiantes WHERE numero_documento = '1023456793'),
     (SELECT id_grado FROM grados WHERE nivel_numerico = 9),  2025, 'Activa',   '2025-01-20', NULL)
ON DUPLICATE KEY UPDATE
    id_grado         = VALUES(id_grado),
    estado_matricula = VALUES(estado_matricula),
    fecha_retiro     = VALUES(fecha_retiro);

-- 3) Verificación (solo lectura). Debe mostrar los grados 1..12 y, para 2025 Activa,
--    al menos estos 4 estudiantes: 3 en Décimo (id_grado 11) y 1 en Noveno (id_grado 10).
SELECT id_grado, nombre_grado FROM grados ORDER BY id_grado;

SELECT e.id_estudiante, e.tipo_documento, e.numero_documento,
       e.primer_nombre, e.segundo_nombre, e.primer_apellido, e.segundo_apellido,
       g.id_grado, g.nombre_grado, m.anio_academico, m.estado_matricula
FROM estudiantes e
JOIN matriculas m ON m.id_estudiante = e.id_estudiante
JOIN grados g ON g.id_grado = m.id_grado
WHERE m.anio_academico = 2025 AND m.estado_matricula = 'Activa'
ORDER BY g.id_grado, e.id_estudiante;

-- 4) LIMPIEZA (opcional, descomentar solo si quieres borrar estos datos de prueba).
--    Las matrículas se borran solas por ON DELETE CASCADE.
-- DELETE FROM estudiantes
-- WHERE numero_documento IN ('1023456789', '1023456790', '1023456791', '1023456792', '1023456793');
