-- =============================================================================
-- V9 — Semillas de los catálogos y el superadministrador.
--
-- IDENTIFICADORES LITERALES (Art. V.11), los mismos que tuvieron siempre: las
-- pruebas y la semilla de desarrollo los referencian. Lo único que entra por
-- MARCADOR DE POSICIÓN de Flyway es la credencial inicial del
-- superadministrador: un hash escrito en el repositorio es una credencial en
-- el repositorio (RNF-SEG-003), y un despliegue que no la declare debe FALLAR
-- AL ARRANCAR, no arrancar con una conocida (Art. IX.5).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- La moneda de casa. Con su registro de creación: es una entidad del negocio
-- que RF-SP-011 debe poder mostrar.
-- ---------------------------------------------------------------------------

INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active) VALUES
('01a03336-6d00-7001-9c4f-5e7ad3000001', 'USD', 'Dólar estadounidense', '$', 2, true, true);

INSERT INTO audit_change_log (
    id, occurred_at, actor_id, correlation_id, ip_address, user_agent,
    module, entity, entity_id, action, changes
)
SELECT
    '01a03336-6d00-7011-9c4f-5e7ad3000001'::uuid,
    now(), NULL, NULL, NULL, NULL,
    'SP', 'currencies', c.id, 'CREATE',
    jsonb_build_object(
        'code',           c.code,
        'name',           c.name,
        'symbol',         c.symbol,
        'decimal_places', c.decimal_places,
        'is_default',     c.is_default,
        'is_active',      c.is_active)
  FROM currencies c
 WHERE c.code = 'USD';

-- ---------------------------------------------------------------------------
-- La cadena de membresías: ORO en la cima (level 1), PLATINO, VIP y BECA en el
-- suelo (level 4). BECA es la que recibe toda persona al nacer (RN-SP-018) y
-- no tiene costo. Se insertan de la cima hacia abajo porque cada una apunta a
-- la de arriba; las unicidades DEFERRABLE admitirían el otro orden, pero este
-- se lee.
-- ---------------------------------------------------------------------------

INSERT INTO memberships (id, code, name, description, parent_membership_id, level, color) VALUES
('01a04ad0-e800-7004-9c4f-5e7ad7000004', 'ORO', 'Oro',
 'Nivel más alto de la cadena. Es la cima y no está sujeta a ninguna otra.',
 NULL, 1, 'FFB300'),
('01a04ad0-e800-7003-9c4f-5e7ad7000003', 'PLATINO', 'Platino',
 'Nivel intermedio alto, por debajo de Oro.',
 '01a04ad0-e800-7004-9c4f-5e7ad7000004', 2, 'B0BEC5'),
('01a04ad0-e800-7002-9c4f-5e7ad7000002', 'VIP', 'VIP',
 'Primer nivel de pago, por debajo de Platino.',
 '01a04ad0-e800-7003-9c4f-5e7ad7000003', 3, '7E57C2'),
('01a04ad0-e800-7001-9c4f-5e7ad7000001', 'BECA', 'Beca',
 'Nivel de entrada, sin costo. Es el suelo de la cadena.',
 '01a04ad0-e800-7002-9c4f-5e7ad7000002', 4, '9E9E9E');

-- ---------------------------------------------------------------------------
-- Países, tipos de documento y brokers: los tres catálogos que el formulario
-- de registro necesita antes de que exista la cuenta (RF-SP-045) y que NO se
-- administran por API. Solo documentos de MAYOR DE EDAD (RN-SP-036).
-- ---------------------------------------------------------------------------

INSERT INTO countries (id, code, name) VALUES
('01a07bbd-5200-7001-9c4f-5e7ad3000101', 'COL', 'Colombia');

INSERT INTO document_types (id, abbreviation, name) VALUES
('01a080e3-ae00-7001-9c4f-5e7ad6000001', 'CC',  'Cédula de ciudadanía'),
('01a080e3-ae00-7002-9c4f-5e7ad6000002', 'CE',  'Cédula de extranjería'),
('01a080e3-ae00-7003-9c4f-5e7ad6000003', 'PA',  'Pasaporte'),
('01a080e3-ae00-7004-9c4f-5e7ad6000004', 'NIT', 'Número de identificación tributaria');

INSERT INTO brokers (id, name) VALUES
('01a081f0-6000-7101-9c4f-5e7adb000001', 'IQOPTION'),
('01a081f0-6000-7102-9c4f-5e7adb000002', 'EXNOVA'),
('01a081f0-6000-7103-9c4f-5e7adb000003', 'EXOPTION');

-- ---------------------------------------------------------------------------
-- Los catálogos de MV. GRATIS es INTERNO: sirve para pagar una venta de
-- importe cero y nadie lo elige, lo pone el sistema (RN-MV-023).
-- ---------------------------------------------------------------------------

INSERT INTO movement_types (id, code, name, prefix) VALUES
('01a061ba-3400-7001-9c4f-5e7ad7000011', 'VENTA', 'Venta', 'VTA');

INSERT INTO payment_methods (id, code, name, is_active, visibility) VALUES
('01a061ba-3400-7002-9c4f-5e7ad7000021', 'CREDIT_CARD', 'Tarjeta de credito',        true, 'PUBLICO'),
('01a061ba-3400-7003-9c4f-5e7ad7000022', 'PSE',         'Multiples métodos de pago', true, 'PUBLICO'),
('01a061ba-3400-7003-9c4f-5e7ad7000023', 'POINTS',      'Pagar con puntos',          true, 'PUBLICO'),
('01a08646-7a00-7001-9c4f-5e7adb000001', 'GRATIS',      'Sin costo',                 true, 'INTERNO');

-- =============================================================================
-- El superadministrador: la única persona que existe antes de que nadie pueda
-- registrar a otra. Nace en Colombia, con BECA como toda persona, con la
-- obligación de cambiar la contraseña en su primera entrada, y con el rol
-- SUPERADMIN.
-- =============================================================================

DO $guarda$
BEGIN
    IF btrim('${superadmin_password_hash}') = '' THEN
        RAISE EXCEPTION
            'SUPERADMIN_PASSWORD_HASH no está declarado. Un despliegue sin credencial inicial debe fallar, no arrancar con una conocida (Art. IX.5).';
    END IF;
    IF left('${superadmin_password_hash}', 9) <> '$argon2id' THEN
        RAISE EXCEPTION
            'SUPERADMIN_PASSWORD_HASH no parece un hash Argon2id. Si viene de un archivo .env, sus $ deben ir DUPLICADOS: Docker Compose los interpola y destruye el valor (ver .env.example).';
    END IF;
    IF btrim('${superadmin_email}') = '' THEN
        RAISE EXCEPTION
            'SUPERADMIN_EMAIL no está declarado.';
    END IF;
END
$guarda$;

-- IDENTIDAD DOCUMENTAL Y CONTACTO, desde el 23-09-2026 y por decisión del
-- responsable del proyecto. Hasta entonces el superadministrador nacía sin
-- documento y sin teléfono: las columnas admiten nulo (`V4`), de modo que la
-- fila era válida, y `DevelopmentSeedIT.documento` afirmaba justamente eso.
--
-- `ck_users_document_pair` exige LAS DOS o NINGUNA, y por eso el tipo y el
-- número entran juntos. El número va en mayúsculas y sin espacios porque
-- `ck_users_document_number_normalized` compara contra `upper(btrim(...))`, y el
-- teléfono sin prefijo internacional porque `ck_users_phone_format` lo admite
-- opcional —de siete a quince dígitos—.
--
-- SE EDITA `V9` EN EL SITIO Y NO SE AÑADE UNA MIGRACIÓN DE DATOS, por decisión
-- del responsable del proyecto el 23-09-2026: la alternativa deja el dato
-- dividido en dos sitios para siempre —quién nace y quién se corrige— cuando lo
-- que se quiere es que el superadministrador NAZCA así. El coste está a la
-- vista y es real: **toda base ya migrada falla la validación de suma de
-- comprobación de Flyway** hasta que se recree. Se aceptó porque la base se
-- reinicia, y CI levanta una nueva en cada corrida.
INSERT INTO users (
    id, username, email, first_name, last_name,
    password_hash, must_change_password, status, country_id,
    document_type_id, document_number, phone
) VALUES (
    '01a033a4-4a00-7001-9c4f-5e7ad4000001',
    'superadmin',
    '${superadmin_email}',
    'Super',
    'Administrador',
    '${superadmin_password_hash}',
    true,
    'ACTIVO',
    '01a07bbd-5200-7001-9c4f-5e7ad3000101',
    -- `CC`, sembrada unas líneas más arriba en este mismo archivo.
    '01a080e3-ae00-7001-9c4f-5e7ad6000001',
    '12345678910',
    '3001234567'
);

INSERT INTO user_roles (user_id, role_id, role_type)
SELECT '01a033a4-4a00-7001-9c4f-5e7ad4000001', id, role_type
  FROM roles
 WHERE code = 'SUPERADMIN';

-- RN-SP-018: toda persona tiene membresía, y quien no recibe una arranca en BECA.
INSERT INTO user_memberships (id, user_id, membership_id, started_at, ends_at)
VALUES ('01a033a4-4a00-7021-9c4f-5e7ad4000001',
        '01a033a4-4a00-7001-9c4f-5e7ad4000001',
        '01a04ad0-e800-7001-9c4f-5e7ad7000001',
        now(), NULL);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM user_roles WHERE user_id = '01a033a4-4a00-7001-9c4f-5e7ad4000001'
    ) THEN
        RAISE EXCEPTION
            'El rol SUPERADMIN no existe: la semilla habría dejado al superadministrador sin permisos';
    END IF;
END $$;

INSERT INTO audit_change_log (
    id, occurred_at, actor_id, correlation_id, ip_address, user_agent,
    module, entity, entity_id, action, changes
)
SELECT
    '01a033a4-4a00-7011-9c4f-5e7ad4000001'::uuid,
    now(), NULL, NULL, NULL, NULL,
    'SP', 'users', u.id, 'CREATE',
    jsonb_build_object(
        'username',             u.username,
        'email',                u.email,
        'first_name',           u.first_name,
        'last_name',            u.last_name,
        'status',               u.status,
        'must_change_password', u.must_change_password,
        'roles',                jsonb_build_array('SUPERADMIN'))
  FROM users u
 WHERE u.id = '01a033a4-4a00-7001-9c4f-5e7ad4000001';
