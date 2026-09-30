-- =============================================================================
-- V57 — Los quince países de la región (requirements/sp.md v1.89.0 §10.6,
-- 30-09-2026).
--
-- Hasta hoy el catálogo solo sembraba Colombia (V9), la fila que RN-SP-034
-- obliga a tener para el superadministrador; el resto se daba de alta por la
-- API. El responsable del proyecto pidió sembrar los países donde opera la
-- plataforma. Llegaron con código de dos letras y nombre en inglés, y se
-- preguntó antes de escribir: CÓDIGO ALFA-3 Y NOMBRE EN ESPAÑOL, como Colombia
-- y como exige ck_countries_code_format. Sin cambio de esquema.
--
-- NO PISA LO QUE YA HAYA: un país que alguien dio de alta por la API choca con
-- uq_countries_code o con uq_countries_name, y `ON CONFLICT DO NOTHING` lo deja
-- como está —RN-SP-009 no admite editarlo ni borrarlo—. Por eso la guarda
-- comprueba por CÓDIGO y no por identificador.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 del 30-09-2026 a
-- medianoche UTC —`01a0ef9c6800`— continuando la serie de países (5e7ad3)
-- donde V9 la dejó con Colombia: 000101 → 000102 a 000115.
-- =============================================================================

INSERT INTO countries (id, code, name) VALUES
('01a0ef9c-6800-7002-9c4f-5e7ad3000102', 'ARG', 'Argentina'),
('01a0ef9c-6800-7003-9c4f-5e7ad3000103', 'BOL', 'Bolivia'),
('01a0ef9c-6800-7004-9c4f-5e7ad3000104', 'BRA', 'Brasil'),
('01a0ef9c-6800-7005-9c4f-5e7ad3000105', 'CHL', 'Chile'),
('01a0ef9c-6800-7006-9c4f-5e7ad3000106', 'CRI', 'Costa Rica'),
('01a0ef9c-6800-7007-9c4f-5e7ad3000107', 'DOM', 'República Dominicana'),
('01a0ef9c-6800-7008-9c4f-5e7ad3000108', 'ECU', 'Ecuador'),
('01a0ef9c-6800-7009-9c4f-5e7ad3000109', 'GTM', 'Guatemala'),
('01a0ef9c-6800-700a-9c4f-5e7ad3000110', 'MEX', 'México'),
('01a0ef9c-6800-700b-9c4f-5e7ad3000111', 'NIC', 'Nicaragua'),
('01a0ef9c-6800-700c-9c4f-5e7ad3000112', 'PAN', 'Panamá'),
('01a0ef9c-6800-700d-9c4f-5e7ad3000113', 'PER', 'Perú'),
('01a0ef9c-6800-700e-9c4f-5e7ad3000114', 'ESP', 'España'),
('01a0ef9c-6800-700f-9c4f-5e7ad3000115', 'USA', 'Estados Unidos')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Guarda: los quince códigos existen, sembrados aquí o dados de alta antes.
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    faltan integer;
BEGIN
    SELECT count(*) INTO faltan
      FROM unnest(ARRAY['ARG', 'BOL', 'BRA', 'CHL', 'COL', 'CRI', 'DOM', 'ECU',
                        'GTM', 'MEX', 'NIC', 'PAN', 'PER', 'ESP', 'USA']) AS esperado(code)
     WHERE NOT EXISTS (SELECT 1 FROM countries c WHERE c.code = esperado.code);
    IF faltan <> 0 THEN
        RAISE EXCEPTION 'V57: faltan % de los quince países de la región', faltan;
    END IF;
END $$;
