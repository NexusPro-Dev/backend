-- =============================================================================
-- Semilla de DESARROLLO: diecinueve personas de prueba, la estructura que las
-- relaciona y sus membresías.
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
-- crearía en producción diecinueve cuentas que comparten el hash de contraseña
-- del superadministrador y que nacen sin marca de cambio obligatorio. No es una
-- siembra: sería un agujero.
--
-- Las CUATRO MEMBRESÍAS sí son catálogo del negocio y las siembra
-- `V9__semilla_catalogos_y_superadmin.sql`. Este guion las da por existentes.
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
-- Diecinueve personas, repartidas por lo que hace falta poder probar.
--
-- YA NO SON TRES DE CADA. `AGENTE` son nueve porque cada uno de los tres
-- directores necesita tres personas a cargo, y `ADMIN` es UNO SOLO por decisión
-- del responsable del proyecto: desde el 10-09-2026 sí entra en la estructura
-- —los tres managers cuelgan de él—, y un segundo administrador solo añadiría
-- una bifurcación que la rama comercial ya ejercita más abajo. El reparto vive
-- en la tabla `plan` de aquí abajo, en un solo sitio.
--
-- SE EXCLUYE `SUPERADMIN`, porque el privilegio máximo no se
-- reparte en datos de prueba y `RN-SP-001` lo protege. Sigue sin sembrarse a
-- nadie con ese rol: el superadministrador de `V22` aparece más abajo, pero
-- como RAÍZ de la estructura y no como una persona de prueba más.
--
-- `CONTABILIDAD` y `LIDER_ACADEMICO` ya no existen: se retiraron de la siembra
-- del sistema el 29-08-2026, por decisión del responsable del proyecto.
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
-- son para probar, y retenerlas obligaría a pasar por ese flujo diecinueve veces
-- antes de poder usarlas.
-- -----------------------------------------------------------------------------
WITH plan AS (
  -- CUÁNTAS DE CADA ROL, y ya no tres de todo. Los números salen de lo que hay
  -- que poder probar: `AGENTE` son nueve porque cada uno de los tres directores
  -- necesita tres personas a cargo, y `ADMIN` es uno solo porque tres no
  -- ejercitarían nada que uno no ejercite: la bifurcación de la jerarquía ya se
  -- ve en los tres managers que cuelgan de él.
  SELECT * FROM (VALUES
      ('ADMIN',    1),
      ('MANAGER',  3),
      ('DIRECTOR', 3),
      ('AGENTE',   9),
      ('CLIENTE',  3)
  ) AS p(rol, cuantas)
),
roles_semilla AS (
  SELECT r.id,
         r.code,
         replace(lower(r.code), '_', '') AS prefijo,
         p.cuantas
    FROM roles r
    JOIN plan p ON p.rol = r.code
   WHERE r.deleted_at IS NULL
),
personas AS (
  SELECT r.id AS rol_id,
         r.code AS rol,
         n AS indice,
         r.prefijo || n AS usuario,
         pg_temp.uuid_v7() AS id
    FROM roles_semilla r
    CROSS JOIN LATERAL generate_series(1, r.cuantas) AS n
   WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.username = r.prefijo || n)
),
insertadas AS (
  -- `country_id` SE DECLARA EXPLICITAMENTE y no se deja al relleno de `V64`
  -- (`RN-SP-034`): aquella migracion pone Colombia a las filas QUE YA EXISTIAN,
  -- y estas se crean despues. Sin declararlo, el INSERT falla por `NOT NULL` —
  -- que es el comportamiento correcto y no el que esta semilla quiere probar.
  --
  -- Se resuelve POR CODIGO y no por identificador literal: en una base donde
  -- Colombia ya se hubiera registrado por la API, la fila buena es la suya.
  INSERT INTO users (id, username, email, first_name, last_name, password_hash,
                     must_change_password, status, country_id,
                     document_type_id, document_number, phone,
                     address_line1, city)
  SELECT p.id,
         p.usuario,
         p.usuario || '@factech.co',
         initcap(replace(lower(p.rol), '_', ' ')),
         'Prueba ' || p.indice,
         (SELECT password_hash FROM users WHERE username = 'superadmin'),
         false,
         'ACTIVO',
         (SELECT id FROM countries WHERE code = 'COL'),
         -- `RN-SP-035` y `RN-SP-037`: el documento y el telefono son obligatorios
         -- en la API, de modo que la semilla los declara EXPLICITAMENTE. `V71`
         -- rellena las filas que YA EXISTIAN; estas se crean despues.
         --
         -- CADA PERSONA CON UN NUMERO DISTINTO: repetirlos violaria
         -- `uq_users_document` y la semilla fallaria a medias. Se deriva del
         -- nombre de usuario, que ya es unico por construccion.
         (SELECT id FROM document_types WHERE abbreviation = 'CC'),
         upper(regexp_replace(p.usuario, '[^A-Za-z0-9]', '', 'g')),
         -- Telefono con la forma que `ck_users_phone_format` admite: digitos con
         -- un `+` opcional, hasta quince.
         '+57300' || lpad((abs(hashtext(p.usuario)) % 10000000)::text, 7, '0'),
         'Calle ' || p.indice || ' # 10-20',
         'Bogota'
    FROM personas p
  RETURNING id, username
)
-- `role_type` se copia DEL ROL (`RN-SP-025`, `V52`): la clave foranea compuesta
-- no admite otra cosa, y aportarlo desde aqui seria poder mentir.
INSERT INTO user_roles (user_id, role_id, role_type)
SELECT i.id, r.id, r.role_type
  FROM insertadas i
  JOIN personas p ON p.usuario = i.username
  JOIN roles r ON r.id = p.rol_id;



-- -----------------------------------------------------------------------------
-- La estructura: quién está a cargo de quién.
--
-- SIN ESTO LOS DATOS DE PRUEBA NACÍAN EN UN ESTADO QUE EL SISTEMA PROHÍBE.
-- `RN-SP-019` dice que todo el que porte un rol de clasificación `VENDEDOR`
-- debe tener superior comercial, y hasta que este bloque existió la semilla
-- creaba directores y agentes sin ninguno. No fallaba nada —esa regla todavía
-- no está implementada—, y esa es justamente la trampa: `RF-SP-041` y
-- `RF-SP-042` se probarían contra una base que no puede existir en producción.
--
-- EL ÁRBOL LLEGA DE PUNTA A PUNTA DESDE EL 10-09-2026, por decisión del
-- responsable del proyecto, y se pidió para poder VER LA ESTRUCTURA COMPLETA en
-- local. Hasta hoy nacía partida en dos: la fuerza comercial colgaba de tres
-- managers que no colgaban de nadie, y `admin1` quedaba suelto.
--
--     superadmin
--       └── admin1
--             ├── manager1 ← director1 ← agente1, agente2, agente3
--             ├── manager2 ← director2 ← agente4, agente5, agente6
--             └── manager3 ← director3 ← agente7, agente8, agente9
--
-- LAS CUATRO FILAS DE ARRIBA SON DEUDA DECLARADA, Y CONVIENE LEERLO ANTES DE
-- APOYARSE EN ELLAS: `RF-SP-041` LAS RECHAZARÍA. Colgar un manager de un
-- administrador sale `409 VAL-004` —`RN-SP-019` exceptúa al vendedor de mayor
-- rango, y el rol padre de `MANAGER` es `ADMIN`, que no es `VENDEDOR`—, y
-- colgar al administrador del superadministrador sale `409 VAL-003`, porque no
-- pertenece a la fuerza comercial y no tiene superior que asignar. `RN-SP-020`
-- tampoco las cubre: tiene rama de vendedor y rama de consumidor, y un
-- `FUNCIONARIO` no cae en ninguna de las dos.
--
-- LO QUE NO SON ES INCOHERENTES, y por eso la deuda es de alcance y no de
-- diseño: `SUPERADMIN → ADMIN → MANAGER` es exactamente el parentesco que
-- declara el catálogo de roles (`V7`), el mismo que `RN-SP-020` exige entre
-- vendedores. Lo que falta es decidir si la estructura de personas deja de ser
-- COMERCIAL para ser la jerarquía completa. Mientras no se decida, esto vive
-- SOLO AQUÍ: ninguna regla, ningún endpoint y ninguna prueba de otro
-- requerimiento deben apoyarse en estas cuatro filas.
--
-- Y CAMBIA QUIÉN ES LA CÚSPIDE EN DESARROLLO: la única persona sin superior pasa
-- a ser `superadmin`. Es lo que `RF-SP-042` publica OMITIENDO `supervisor`
-- (`CA-SP-445`), de modo que el caso sigue siendo observable en local — pero
-- ahora hay UNA sola cúspide y no cuatro.
--
-- LA FORMA DE LA RAMA COMERCIAL LA FIJA `RN-SP-020`, no el gusto: el superior
-- porta el ROL PADRE INMEDIATO del subordinado. Quien es `AGENTE` reporta a un
-- `DIRECTOR`, nunca a otro `AGENTE` ni directamente a un `MANAGER`.
--
-- LOS CLIENTES TAMBIÉN CUELGAN, desde el 04-09-2026. Antes este comentario
-- decía que quedaban fuera «porque no son vendedores», y eso dejó de ser cierto
-- el 01-09-2026 con `RN-SP-028`: el cliente cuelga de su vendedor EN ESTA MISMA
-- TABLA, con el cliente en `user_id` y el vendedor en `supervisor_id`. La
-- semilla iba tres días por detrás del diseño, y mientras tanto NO HABÍA NI UNA
-- CARTERA en desarrollo — de modo que la mitad comercial de una venta no se
-- podía ver funcionando en local.
--
-- LOS TRES CUELGAN A PROFUNDIDAD DISTINTA, y esa es la decisión de este bloque:
--
--     cliente1 → agente1     el caso normal
--     cliente2 → director1   salta un escalón
--     cliente3 → manager1    salta dos
--
-- `RN-SP-020` lo permite: su RAMA DE CONSUMIDOR solo exige que el superior
-- porte ALGÚN rol `VENDEDOR`, sin parentesco que comprobar — un cliente no
-- tiene rol vendedor del que derivar un padre, y cualquiera de la fuerza
-- comercial puede traerlo. Es la diferencia con la rama comercial, donde un
-- agente sí debe colgar de un director y de nadie más.
--
-- Y NO ES UN CAPRICHO: la cadena de comisiones se recorre HACIA ARRIBA desde
-- quien tiene al cliente, de modo que su profundidad decide cuántos cobran. Con
-- los tres colgados de un agente, «el cliente de un director» sería un caso que
-- el diseño admite y que en desarrollo no existiría — y es justo el que obliga
-- a decidir a qué tarifa cobra quien está pegado al cliente cuando NO es un
-- agente.
--
-- `ADMIN` NO TIENE CARTERA, y eso no ha cambiado: entra en el árbol POR ARRIBA,
-- como superior de los managers, y no como vendedor que trae clientes.
--
-- TRES A CARGO POR DIRECTOR Y NO UNO, por decisión del responsable del
-- proyecto: un equipo de uno no distingue «el equipo de alguien» de «alguien»,
-- y `RN-SP-022` —que rechaza desactivar a quien tiene personas a cargo— se
-- cumpliría por accidente con cualquier implementación. Con los clientes
-- dentro, `director1` pasa a tener CUATRO a cargo —tres agentes y un cliente— y
-- `agente1` y `manager1` uno cada uno: es la mezcla que `RF-SP-042` tiene que
-- saber devolver distinguiendo por rol.
--
-- ES REPETIBLE como el resto: si la persona ya tiene un superior VIGENTE, no se
-- toca. Reasignar es `RF-SP-041`, no trabajo de esta semilla.
-- -----------------------------------------------------------------------------
INSERT INTO user_supervisors (id, user_id, supervisor_id)
SELECT pg_temp.uuid_v7(), subordinado.id, superior.id
  FROM (
        -- Tres agentes por director: 1-3 al primero, 4-6 al segundo, 7-9 al tercero.
        SELECT 'agente' || n                      AS de,
               'director' || ((n - 1) / 3 + 1)    AS a
          FROM generate_series(1, 9) AS n
         UNION ALL
        -- Y cada director bajo su manager.
        SELECT 'director' || n, 'manager' || n
          FROM generate_series(1, 3) AS n
         UNION ALL
        -- La rama de funcionarios, desde el 10-09-2026: los tres managers bajo
        -- el único administrador, y el administrador bajo el superadministrador.
        -- Son las cuatro filas que la cabecera declara como deuda: `RF-SP-041`
        -- no sabría producirlas.
        SELECT 'manager' || n, 'admin1'
          FROM generate_series(1, 3) AS n
         UNION ALL
        SELECT 'admin1', 'superadmin'
         UNION ALL
        -- Los clientes, cada uno a una profundidad distinta. Se enumeran a mano
        -- y no con una serie: son tres casos ELEGIDOS —normal, un salto, dos
        -- saltos— y una formula los volveria a hacer intercambiables.
        SELECT * FROM (VALUES
            ('cliente1', 'agente1'),
            ('cliente2', 'director1'),
            ('cliente3', 'manager1')
        ) AS cartera(de, a)
       ) AS enlace
  JOIN users subordinado ON subordinado.username = enlace.de
  JOIN users superior    ON superior.username    = enlace.a
 WHERE NOT EXISTS (
         SELECT 1
           FROM user_supervisors vigente
          WHERE vigente.user_id = subordinado.id
            AND vigente.ended_at IS NULL
       );


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
-- TODA PERSONA LLEVA MEMBRESIA desde `V57` (`RN-SP-018`), y no solo los
-- clientes: el superadministrador y los funcionarios arrancan en Free. Sin esas
-- filas, `V57` se las pondria igual — pero la semilla dejaria de describir el
-- estado que produce y habria que ir a leer la migracion para saberlo.
--
-- `user_memberships` ES UN HISTORIAL desde `V56`: `id` propio, y `closed_at`
-- nulo marca la fila ABIERTA, que es la actual. Conceder otra membresia cierra
-- la que hubiera e inserta una nueva. La semilla escribe solo la abierta: en
-- desarrollo nadie ha subido de nivel todavia, y fabricar un historial falso
-- haria que las consultas parecieran correctas por el motivo equivocado.
--
-- EL `id` SE CONSTRUYE, NO SE GENERA AL AZAR (Art. V.11, y `V3`): un v7 cuyo
-- prefijo temporal sale de `now()`, igual que hace `V56` con `started_at`.
-- -----------------------------------------------------------------------------
INSERT INTO user_memberships (id, user_id, membership_id)
SELECT (
           lpad(to_hex((extract(epoch FROM now()) * 1000)::bigint), 12, '0')
        || '7' || substr(md5(random()::text || u.id::text), 1, 3)
        || substr('89ab', 1 + (random() * 3)::int, 1)
        || substr(md5(random()::text || m.id::text), 1, 3)
        || substr(md5(random()::text || u.id::text || m.id::text), 1, 12)
       )::uuid,
       u.id, m.id
  FROM (VALUES ('superadmin', 'BECA'), ('admin1', 'BECA'), ('manager1', 'BECA'),
               ('director1', 'BECA'), ('agente1', 'BECA'), ('agente2', 'BECA'), ('agente3', 'BECA'),
               ('cliente1', 'BECA'), ('cliente2', 'VIP'), ('cliente3', 'PLATINO'))
       AS asignacion(usuario, membresia)
  JOIN users u ON u.username = asignacion.usuario
  JOIN memberships m ON m.code = asignacion.membresia
 WHERE NOT EXISTS (SELECT 1 FROM user_memberships um
                    WHERE um.user_id = u.id AND um.closed_at IS NULL);
