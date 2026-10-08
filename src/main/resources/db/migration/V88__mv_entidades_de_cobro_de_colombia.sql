-- =============================================================================
-- V88 — Las entidades de cobro de Colombia (RN-MV-054; requirements/mv.md
-- v0.96.0 §7.11; specs/mv/032-registrar-entidad-de-cobro/ tasks.md T-11;
-- 08-10-2026).
--
-- Por decisión del responsable del proyecto, el catálogo deja de nacer vacío:
-- las de Colombia se siembran EN TODOS LOS ENTORNOS, como la conversión de V70.
-- Veintidós bancos y cuatro billeteras móviles, activos, con el CÓDIGO ACH como
-- `code`: el de cuatro dígitos con que los identifican PSE y las pasarelas
-- (para eso V66 dejó que el código empiece por dígito). Las de otros países, y
-- cualquier otra de Colombia, las da de alta administración (RF-MV-032).
--
-- NO PISA NADA: `ON CONFLICT (code) DO NOTHING`. Una entidad que administración
-- hubiera registrado antes con el mismo código se queda como está, y solo las
-- filas que entran de verdad llevan su auditoría (CREATE, sin actor, como V70).
-- El guion es idempotente; la prueba de la siembra lo aplica dos veces.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 de V79 —`01a10e829000`—,
-- secuencia 7013; nodo `5e7ad70008NN` para la entidad y `5e7ad70009NN` para su
-- auditoría.
-- =============================================================================

WITH sembradas AS (
    INSERT INTO payout_institutions (id, code, name, kind, country_id)
    SELECT e.id::uuid, e.code, e.name, e.kind,
           '01a07bbd-5200-7001-9c4f-5e7ad3000101'::uuid   -- COL
      FROM (VALUES
        ('01a10e82-9000-7013-9c4f-5e7ad7000801', '1001', 'Banco de Bogotá',      'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000802', '1002', 'Banco Popular',        'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000803', '1006', 'Itaú',                 'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000804', '1007', 'Bancolombia',          'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000805', '1012', 'GNB Sudameris',        'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000806', '1013', 'BBVA Colombia',        'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000807', '1019', 'Scotiabank Colpatria', 'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000808', '1023', 'Banco de Occidente',   'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000809', '1032', 'Banco Caja Social',    'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000810', '1040', 'Banco Agrario',        'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000811', '1047', 'Banco Mundo Mujer',    'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000812', '1051', 'Davivienda',           'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000813', '1052', 'Banco AV Villas',      'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000814', '1053', 'Banco W',              'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000815', '1059', 'Bancamía',             'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000816', '1060', 'Banco Pichincha',      'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000817', '1062', 'Banco Falabella',      'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000818', '1063', 'Banco Finandina',      'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000819', '1066', 'Banco Coopcentral',    'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000820', '1069', 'Banco Serfinanza',     'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000821', '1070', 'Lulo Bank',            'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000822', '1809', 'Nu Colombia',          'BANCO'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000823', '1097', 'dale!',                'BILLETERA_MOVIL'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000824', '1507', 'Nequi',                'BILLETERA_MOVIL'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000825', '1551', 'Daviplata',            'BILLETERA_MOVIL'),
        ('01a10e82-9000-7013-9c4f-5e7ad7000826', '1801', 'MOVii',                'BILLETERA_MOVIL')
      ) AS e (id, code, name, kind)
    ON CONFLICT (code) DO NOTHING
    RETURNING id, code, name, kind, country_id, is_active
)
INSERT INTO audit_change_log (
    id, occurred_at, actor_id, correlation_id, ip_address, user_agent,
    module, entity, entity_id, action, changes
)
SELECT
    replace(s.id::text, '5e7ad70008', '5e7ad70009')::uuid,
    now(), NULL, NULL, NULL, NULL,
    'MV', 'payout_institutions', s.id, 'CREATE',
    -- La forma de la que escribe PayoutInstitutionService.register.
    jsonb_build_object(
        'after', jsonb_build_object('code', s.code, 'name', s.name, 'kind', s.kind,
                                    'country_id', s.country_id, 'is_active', s.is_active))
  FROM sembradas s;

DO $$
DECLARE
    faltan integer;
BEGIN
    SELECT 26 - count(*) INTO faltan
      FROM payout_institutions
     WHERE code IN ('1001', '1002', '1006', '1007', '1012', '1013', '1019', '1023',
                    '1032', '1040', '1047', '1051', '1052', '1053', '1059', '1060',
                    '1062', '1063', '1066', '1069', '1070', '1809', '1097', '1507',
                    '1551', '1801');
    IF faltan <> 0 THEN
        RAISE EXCEPTION 'V88: faltan % entidades de cobro de Colombia', faltan;
    END IF;
END $$;
