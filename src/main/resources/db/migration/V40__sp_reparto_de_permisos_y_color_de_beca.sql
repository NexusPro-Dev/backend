-- =============================================================================
-- V40 — El reparto de permisos se ensancha y BECA cambia de color
-- (security.md §4.5 v0.72.0, 24-09-2026).
--
-- Decisión del responsable del proyecto, aplicada primero sobre el entorno de
-- desarrollo y traída aquí después. Esta migración NO inventa nada: es el
-- diferencial entre lo que siembran V8, V28 y las suyas, y lo que ese entorno
-- tiene hoy — obtenido comparando las dos bases fila a fila, no de memoria.
--
-- LA RESERVA DE LA RAÍZ BAJA DE SEIS PERMISOS A DOS, y es lo que más cambia.
-- `V8` dejaba fuera de ADMIN los cuatro `movements:` además de
-- `audit:read-security` y `currencies:update`. El propio apartado de la reserva
-- dejaba escrito el coste: por RN-SEG-003 un rol no puede portar lo que su padre
-- no porta, y la cadena es SUPERADMIN → ADMIN → MANAGER → DIRECTOR → AGENTE, de
-- modo que con ADMIN fuera **no había a quién delegar el trabajo diario de la
-- fuerza comercial**. Levantarla es lo que permite el resto de esta migración.
--
-- SE ESCRIBE CON `UPDATE`/`INSERT` Y NO EDITANDO V8 NI V9. Aquellas están
-- aplicadas en bases vivas, y cambiarlas rompe la suma de comprobación de
-- Flyway y obliga a recrearlas — perdiendo justamente los datos que esto viene a
-- conservar. Es la diferencia con el documento del superadministrador, que se
-- escribió en el sitio porque se quería EN EL ALTA y la base se iba a reiniciar.
--
-- TODO VA POR CÓDIGO Y NO POR IDENTIFICADOR. Los códigos son estables
-- (`uq_permissions_code`, `uq_roles_code`) y legibles; copiar aquí treinta UUID
-- haría la migración imposible de revisar, que es lo único que la protege de un
-- error de reparto.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Lo que cada rol PASA A PORTAR.
--
--    `ON CONFLICT DO NOTHING` porque la fila puede existir ya: el entorno de
--    desarrollo la tiene desde que se hizo a mano, y esta migración no puede
--    fallar al encontrarla.
-- ---------------------------------------------------------------------------

CREATE TEMP TABLE reparto_v40 (rol varchar(50), permiso varchar(100)) ON COMMIT DROP;

INSERT INTO reparto_v40 (rol, permiso) VALUES
-- ADMIN recibe el libro de ventas entero: es el levantamiento de la reserva.
('ADMIN',    'movements:read'),
('ADMIN',    'movements:create'),
('ADMIN',    'movements:confirm'),
('ADMIN',    'movements:void'),
-- Y la cadena comercial recibe lo que hasta ahora no podía declarar. Leer y
-- registrar, no confirmar ni anular: cobrar y deshacer siguen siendo de
-- administración.
('MANAGER',  'movements:read'),
('MANAGER',  'movements:create'),
('DIRECTOR', 'movements:read'),
('DIRECTOR', 'movements:create'),
-- El cliente registra su propia compra y no lee el libro de nadie.
('CLIENTE',  'movements:create'),
-- Vender y repartir enlaces, que es el trabajo de la fuerza comercial.
('MANAGER',  'products:sale'),
('MANAGER',  'products:hotlink'),
('DIRECTOR', 'products:sale'),
('DIRECTOR', 'products:hotlink'),
('AGENTE',   'products:sale'),
('AGENTE',   'products:hotlink'),
-- El cliente ve la oferta que puede comprar, y reseña lo que compró.
('CLIENTE',  'products:sale'),
('CLIENTE',  'products:comment'),
('CLIENTE',  'products:update-comment'),
-- Quién cuelga de quién, y los clientes y vendedores de cada cual.
('MANAGER',  'users:read-team'),
('MANAGER',  'users:read-clients'),
('MANAGER',  'users:read-sellers'),
('DIRECTOR', 'users:read-team'),
('DIRECTOR', 'users:read-clients'),
('DIRECTOR', 'users:read-sellers'),
-- `users:read-sellers` Y NO `users:read-own-sellers`, y no es un cruce: se
-- preguntó y se confirmó el 24-09-2026. Abre GET /users/{id}/sellers —por
-- identificador— y no GET /users/me/sellers, que es la que el cliente pierde
-- unas líneas más abajo.
('CLIENTE',  'users:read-sellers');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM reparto_v40 x
  JOIN roles r       ON r.code = x.rol
  JOIN permissions p ON p.code = x.permiso
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Lo que se RETIRA. Son tres filas y las tres son deliberadas.
--
--    `movements:list-sales` de CLIENTE: no vende, de modo que el listado de
--    ventas del alcance no responde nada para él.
--
--    `users:read-own-sellers` de CLIENTE y de AGENTE: el cliente consulta por
--    identificador con `users:read-sellers`, que recibe arriba; y un agente no
--    tiene vendedores por encima que consultar.
-- ---------------------------------------------------------------------------

DELETE FROM role_permissions rp
 USING roles r, permissions p
 WHERE rp.role_id = r.id
   AND rp.permission_id = p.id
   AND (   (r.code = 'CLIENTE' AND p.code = 'movements:list-sales')
        OR (r.code = 'CLIENTE' AND p.code = 'users:read-own-sellers')
        OR (r.code = 'AGENTE'  AND p.code = 'users:read-own-sellers'));

-- ---------------------------------------------------------------------------
-- 3. El color de BECA.
--
--    `9E9E9E` era un gris de relleno; `3DFFD5` es el que la marca usa. La
--    columna guarda el hexadecimal SIN `#` y en mayúsculas (`V3`), y por eso no
--    se escribe con almohadilla ni en minúscula.
-- ---------------------------------------------------------------------------

UPDATE memberships
   SET color = '3DFFD5', updated_at = now()
 WHERE code = 'BECA' AND color <> '3DFFD5';

-- ---------------------------------------------------------------------------
-- 4. Guardas.
--
--    POR CONJUNTO Y NO POR RECUENTO, a propósito y al revés que V37. Un número
--    fijo aquí se rompería el día que el catálogo cambie de tamaño por otro
--    motivo —`V38` retira dos permisos en una rama que todavía no está
--    mezclada—, y el fallo señalaría a esta migración sin tener nada que ver.
--    Lo que esta migración decide es QUÉ queda reservado, no cuántos hay.
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    reserva    text;
    sin_padre  integer;
    color_beca varchar(6);
BEGIN
    SELECT string_agg(p.code, ', ' ORDER BY p.code) INTO reserva
      FROM permissions p
     WHERE NOT EXISTS (SELECT 1 FROM role_permissions rp
                         JOIN roles r ON r.id = rp.role_id
                        WHERE r.code = 'ADMIN' AND rp.permission_id = p.id);
    IF reserva IS DISTINCT FROM 'audit:read-security, currencies:update' THEN
        RAISE EXCEPTION 'V40: la reserva de la raiz debe quedar en audit:read-security y currencies:update; quedo en: %', reserva;
    END IF;

    -- Contencion (RN-SEG-003): ningun rol con un permiso que su padre no porte.
    -- Se comprueba DESPUES de repartir porque es justo lo que el reparto podria
    -- romper: dar algo a DIRECTOR sin darselo a MANAGER lo rompe en silencio.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id
                          AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V40: % asociaciones rompen la contencion de RN-SEG-003', sin_padre;
    END IF;

    SELECT color INTO color_beca FROM memberships WHERE code = 'BECA';
    IF color_beca <> '3DFFD5' THEN
        RAISE EXCEPTION 'V40: BECA debe quedar en 3DFFD5; quedo en %', color_beca;
    END IF;
END $$;
