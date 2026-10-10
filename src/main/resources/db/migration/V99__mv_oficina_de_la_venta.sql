-- =============================================================================
-- V99 — La oficina de cada línea de venta, y los equipos pasan a ser de
-- directores (RF-MV-001 · T-50; requirements/mv.md v0.97.0 §4.13 y §7.3,
-- RN-MV-078; requirements/sp.md v1.119.0, RN-SP-051 y RN-SP-052 enmendadas;
-- modelo-datos.md v0.113.0; 09-10-2026).
--
-- En este orden, y el orden importa:
--   1. movement_details.team_id: la oficina donde se vendió la línea, copiada
--      con su vendedor y nunca recalculada. ON DELETE SET NULL: un equipo se
--      elimina lógicamente, y la acción existe para que las suites que vacían
--      teams no tengan que limpiar antes las ventas (la lección de
--      product_links). NO rellena nada: lo hace RF-MV-058.
--   2. Cierra las pertenencias vigentes de la CÚSPIDE —el vendedor cuyo rol
--      padre no es vendedor (RN-SP-019), hoy MANAGER—, con fecha de fin y sin
--      borrar: es historial (RN-SP-052). Va antes del índice siguiente, que con
--      dos managers en un equipo no se podría crear.
--   3. uq_team_members_equipo_vigente: un director vigente por equipo.
--   4. Los cinco equipos, sin pisar uno no eliminado con el mismo nombre
--      normalizado (la expresión de uq_teams_name).
--   5. movements:fill-line-teams (RF-MV-058) a SUPERADMIN y ADMIN, explícito.
--      No es sensible: requires_recent_mfa queda en false (V75).
--
-- EL NÚMERO: los documentos la reservaron como V97, pero V98 (depósito y
-- operaciones de broker) llegó antes a develop, y Flyway rechaza una versión
-- por debajo de la última aplicada. V97 queda como hueco, igual que V95.
--
-- IDENTIFICADORES LITERALES (Art. V.11): el permiso sigue la serie de V77
-- (7205 → 7206); los equipos estrenan la serie 7207 · 5e7ade.
-- =============================================================================

-- 1. La columna -----------------------------------------------------------------

ALTER TABLE movement_details ADD COLUMN team_id uuid NULL;

ALTER TABLE movement_details
    ADD CONSTRAINT fk_movement_details_team
    FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE SET NULL;

-- Parcial como ix_movement_details_seller: las líneas sin oficina —la venta de
-- un manager, lo vendido antes de que hubiera directores con equipo— nunca
-- forman parte de la respuesta a «qué se vendió en esta oficina».
CREATE INDEX ix_movement_details_team
    ON movement_details (team_id, movement_id)
    WHERE team_id IS NOT NULL;

COMMENT ON COLUMN movement_details.team_id IS
    'La oficina donde se vendio la linea: el equipo del primero de la cadena del vendedor con pertenencia vigente EN EL INSTANTE DE LA VENTA (RN-MV-078). Se copia con seller_id y solo cambia cuando cambia el (RF-MV-016) o una vez de nulo a valor (RF-MV-058). Nula sin vendedor, y cuando nadie de la cadena tenia equipo.';

-- 2. Cierre de las pertenencias de la cúspide -----------------------------------

UPDATE team_members tm
   SET ended_at = now(), updated_at = now()
 WHERE tm.ended_at IS NULL
   AND EXISTS (
         SELECT 1
           FROM user_roles ur
           JOIN roles r ON r.id = ur.role_id
           LEFT JOIN roles padre ON padre.id = r.parent_role_id
          WHERE ur.user_id = tm.user_id
            AND r.role_type = 'VENDEDOR'
            AND (padre.id IS NULL OR padre.role_type <> 'VENDEDOR'));

-- 3. Un director vigente por equipo (RN-SP-052) ---------------------------------

-- La construcción de uq_team_members_vigente, ahora por equipo. Respalda la
-- carrera de dos asignaciones al mismo equipo por un camino que no tome el
-- bloqueo; JpaTeamMemberRepository la traduce al 409 de RF-SP-069 EX-005.
CREATE UNIQUE INDEX uq_team_members_equipo_vigente
    ON team_members (team_id)
    WHERE ended_at IS NULL;

COMMENT ON TABLE teams IS
    'Las oficinas de la fuerza comercial (SP, RN-SP-050 a RN-SP-055): cada equipo tiene como mucho UN director vigente, y con el entra la red que cuelga de el. Agrupa; NO manda —el mando es user_supervisors— y NO concede acceso a ningun dato (D-22). Las lineas de venta guardan la suya en movement_details.team_id (RN-MV-078).';

-- 4. Los cinco equipos ----------------------------------------------------------

INSERT INTO teams (id, name, description, status)
SELECT v.id, v.name, NULL, 'ACTIVO'
  FROM (VALUES
        (CAST('01a10e82-9000-7207-9c4f-5e7ade000001' AS uuid), 'Principal'),
        (CAST('01a10e82-9000-7207-9c4f-5e7ade000002' AS uuid), 'Legendary'),
        (CAST('01a10e82-9000-7207-9c4f-5e7ade000003' AS uuid), 'Elite'),
        (CAST('01a10e82-9000-7207-9c4f-5e7ade000004' AS uuid), 'Prime'),
        (CAST('01a10e82-9000-7207-9c4f-5e7ade000005' AS uuid), 'Master')
       ) AS v (id, name)
 WHERE NOT EXISTS (
         SELECT 1 FROM teams t
          WHERE t.deleted_at IS NULL
            AND f_unaccent(lower(t.name)) = f_unaccent(lower(v.name)))
   AND NOT EXISTS (SELECT 1 FROM teams t WHERE t.id = v.id);

-- 5. El permiso del relleno (RF-MV-058) -----------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-7206-9c4f-5e7ad700006a', 'movements:fill-line-teams', 'movements',
 'fill-line-teams',
 'Rellenar la oficina de las líneas de venta',
 'Poner la oficina de hoy de su vendedor a las líneas de venta que no la tienen, por POST /movements/sales/lines/team-fill (RF-MV-058, RN-MV-078).');

INSERT INTO role_permissions (role_id, permission_id) VALUES
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a10e82-9000-7206-9c4f-5e7ad700006a'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a10e82-9000-7206-9c4f-5e7ad700006a')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
BEGIN
  IF (SELECT count(*) FROM role_permissions
       WHERE permission_id = '01a10e82-9000-7206-9c4f-5e7ad700006a') <> 2 THEN
    RAISE EXCEPTION 'V99: movements:fill-line-teams tiene que ir a SUPERADMIN y ADMIN, y a nadie más';
  END IF;
END $$;
