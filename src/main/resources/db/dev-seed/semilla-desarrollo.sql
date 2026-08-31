-- =============================================================================
-- Semilla de DESARROLLO: quince personas de prueba y sus membresías.
--
-- LA APLICA `DevelopmentDataSeeder` AL ARRANCAR, y solo cuando `ENVIRONMENT`
-- NO es `production`. Vive en el classpath —y por tanto dentro del artefacto,
-- también del de producción— porque tiene que viajar con el proceso que la
-- ejecuta. Que el archivo esté presente en producción y no se aplique es
-- deliberado: **el guardia es la variable de entorno, no la ausencia del
-- archivo**, y la variable se traduce a un dominio cerrado de tres valores
-- (Art. IX.4) que tumba el arranque si no lo reconoce. Sin ese dominio
-- cerrado, `Production`, `prod` y el vacío contarían como «no es producción».
--
-- ESTO NO ES UNA MIGRACIÓN, Y NO DEBE SERLO NUNCA. Vive fuera de
-- `db/migration` a propósito: una migración llega a TODOS los entornos, y esto
-- crearía en producción quince cuentas que comparten el hash de contraseña
-- del superadministrador y que nacen sin marca de cambio obligatorio. No es una
-- siembra: sería un agujero.
--
-- Las CUATRO MEMBRESÍAS sí son catálogo del negocio y las siembra
-- `V46__seed_memberships.sql`. Este guion las da por existentes.
--
-- NO DEJA RASTRO EN LA AUDITORÍA ni pasa por las reglas de negocio: escribe
-- directamente en las tablas. Para datos de prueba vale; para cualquier otra
-- cosa, la API.
--
-- ES REPETIBLE: si las personas ya existen, no hace nada. Eso es lo que
-- permite que corra en CADA ARRANQUE sin duplicar a nadie.
--
-- NO LLEVA `BEGIN`/`COMMIT`: la transacción la pone quien lo ejecuta. El
-- ejecutor lo envuelve en una, y así un fallo a mitad no deja personas sin rol
-- —un estado que `RN-SP-023` prohíbe y que ninguna operación de la API sabría
-- corregir—. Para lanzarlo A MANO contra el entorno local, con `-1`, que es lo
-- que le da esa misma transacción:
--
--   docker exec -i nexus-db psql -U nexus -d nexus -v ON_ERROR_STOP=1 -1 \
--     < src/main/resources/db/dev-seed/semilla-desarrollo.sql
-- =============================================================================

-- Un UUID v7 nuevo en cada llamada (Art. V.11). `gen_random_uuid()` habría sido
-- más corto y habría sembrado v4, que es justo lo que ese artículo evita.
CREATE OR REPLACE FUNCTION pg_temp.uuid_v7() RETURNS uuid AS $$
  SELECT (
      substr(ts, 1, 8) || '-' || substr(ts, 9, 4) || '-7' || substr(r, 1, 3)
      || '-a' || substr(r, 4, 3) || '-' || substr(r, 7, 12)
  )::uuid
  FROM (
    SELECT lpad(to_hex((extract(epoch FROM clock_timestamp()) * 1000)::bigint), 12, '0') AS ts,
           md5(random()::text || clock_timestamp()::text) AS r
  ) AS partes;
$$ LANGUAGE sql VOLATILE;


-- -----------------------------------------------------------------------------
-- Quince personas: tres por cada uno de cinco roles.
--
-- SE EXCLUYE `SUPERADMIN`, porque el privilegio máximo no se
-- reparte en datos de prueba y `RN-SP-001` lo protege. `CONTABILIDAD` y
-- `LIDER_ACADEMICO` ya no existen: se retiraron de la siembra del sistema el
-- 29-08-2026, por decisión del responsable del proyecto.
--
-- CADA PERSONA PORTA UN SOLO ROL, y eso importa más de lo que parece: los roles
-- `MANAGER`, `DIRECTOR` y `AGENTE` son de tipo `VENDEDOR`, y `RN-SP-025` prohíbe
-- que alguien porte dos de ese tipo. Esa regla TODAVÍA NO ESTÁ IMPLEMENTADA, de
-- modo que nada impediría violarla a mano — y la resolución de comisiones de
-- `RF-CM-005` dejaría de ser determinista.
--
-- COMPARTEN EL HASH DE CONTRASEÑA del superadministrador, que es el que está en
-- el `.env` y que quien despliega ya conoce. No se inventa una contraseña nueva
-- que después nadie sepa.
--
-- NACEN SIN MARCA DE CAMBIO OBLIGATORIO, al revés que un alta real por la API:
-- son para probar, y retenerlas obligaría a pasar por ese flujo quince veces
-- antes de poder usarlas.
-- -----------------------------------------------------------------------------
WITH roles_semilla AS (
  SELECT id, code, replace(lower(code), '_', '') AS prefijo
    FROM roles
   WHERE code IN ('ADMIN', 'AGENTE', 'CLIENTE', 'DIRECTOR', 'MANAGER')
     AND deleted_at IS NULL
),
personas AS (
  SELECT r.id AS rol_id,
         r.code AS rol,
         n AS indice,
         r.prefijo || n AS usuario,
         pg_temp.uuid_v7() AS id
    FROM roles_semilla r
    CROSS JOIN generate_series(1, 3) AS n
   WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.username = r.prefijo || n)
),
insertadas AS (
  INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                     must_change_password, status)
  SELECT p.id,
         p.usuario,
         p.usuario || '@factech.co',
         initcap(replace(lower(p.rol), '_', ' ')),
         'Prueba ' || p.indice,
         (SELECT password_hash FROM users WHERE username = 'superadmin'),
         false,
         'ACTIVO'
    FROM personas p
  RETURNING id, username
)
INSERT INTO user_roles (user_id, role_id)
SELECT i.id, p.rol_id
  FROM insertadas i
  JOIN personas p ON p.usuario = i.username;


-- -----------------------------------------------------------------------------
-- Membresías de los tres clientes, escalonadas.
--
-- SIN `ends_at`: quedan VIGENTES. Una con fecha de fin pasada dejaría de
-- conceder, y entonces la oferta de `PM` no tendría nivel del que partir — que
-- es justo lo que estas tres filas existen para poder probar.
--
-- EL ESCALONADO ES DELIBERADO: a `cliente1` se le pueden ofrecer tres upgrades,
-- a `cliente2` dos y a `cliente3` uno. Con los tres en el mismo nivel, la mitad
-- de `RF-PM-007` quedaría sin ejercitar.
--
-- `user_memberships` tiene la clave primaria en `user_id`: UNA membresía por
-- persona y SIN historial. Subir a alguien de nivel reemplaza la fila, no añade
-- otra — de ahí que la liquidación futura de `CM` tenga que guardar el
-- porcentaje que aplicó, porque el nivel de entonces no se puede reconstruir
-- desde aquí.
-- -----------------------------------------------------------------------------
INSERT INTO user_memberships (user_id, membership_id)
SELECT u.id, m.id
  FROM (VALUES ('cliente1', 'FREE'), ('cliente2', 'VIP'), ('cliente3', 'PLATINO'))
       AS asignacion(usuario, membresia)
  JOIN users u ON u.username = asignacion.usuario
  JOIN memberships m ON m.code = asignacion.membresia
 WHERE NOT EXISTS (SELECT 1 FROM user_memberships um WHERE um.user_id = u.id);
