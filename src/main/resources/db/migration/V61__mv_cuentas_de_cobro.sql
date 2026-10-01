-- =============================================================================
-- V61 — Cuentas de cobro: a dónde se paga un retiro (RF-MV-032 a RF-MV-039, y
-- las enmiendas de RF-MV-019 y RF-MV-007; requirements/mv.md v0.62.0 §4.5,
-- §7.6 y §7.11 a §7.13; 01-10-2026).
--
-- La carga RF-MV-032 por todo el submódulo, como V58 la etapa 3. Trae:
--
--   1. `payout_institutions`: los bancos y las billeteras móviles, por país, que
--      administra la empresa (RN-MV-054). Se desactivan, no se borran.
--   2. `payout_accounts`: las cuentas de cada persona (RN-MV-055). Borrado
--      lógico; una sola principal y ningún número repetido entre las VIVAS, con
--      dos índices únicos PARCIALES.
--   3. `withdrawal_destinations`: la copia del destino de cada retiro, tal como
--      era al pedirlo (RN-MV-056). Una fila por retiro; no se edita.
--   4. Los ocho permisos: tres de administración a SUPERADMIN y ADMIN, y cinco
--      por tipo de rol, como V58.
--
-- NO SE SIEMBRA NINGUNA ENTIDAD: el catálogo lo llena administración.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 de V58 —`01a0ef9c6800`—,
-- continuando la serie de permisos de MV: 000047 → 000048 a 000055.
-- =============================================================================

CREATE TABLE payout_institutions (
    id          uuid         PRIMARY KEY,
    code        varchar(30)  NOT NULL,
    name        varchar(100) NOT NULL,
    kind        varchar(20)  NOT NULL,
    country_id  uuid         NOT NULL,
    is_active   boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_payout_institutions_code UNIQUE (code),
    CONSTRAINT ck_payout_institutions_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,29}$'),
    CONSTRAINT ck_payout_institutions_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_payout_institutions_kind CHECK (kind IN ('BANCO', 'BILLETERA_MOVIL')),
    CONSTRAINT fk_payout_institutions_country
        FOREIGN KEY (country_id) REFERENCES countries (id) ON DELETE RESTRICT
);

CREATE INDEX ix_payout_institutions_country ON payout_institutions (country_id, name);

COMMENT ON TABLE payout_institutions IS
    'RN-MV-054: los bancos y billeteras móviles a los que se paga un retiro. Código y tipo inmutables; se desactivan en vez de borrarse.';


CREATE TABLE payout_accounts (
    id              uuid         PRIMARY KEY,
    user_id         uuid         NOT NULL,
    institution_id  uuid         NOT NULL,
    account_type    varchar(20)  NULL,
    number          varchar(20)  NOT NULL,
    is_principal    boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    deleted_at      timestamptz  NULL,

    -- Que el tipo de cuenta vaya SOLO con un banco cruza a la entidad: lo
    -- sostiene el caso de uso. Aquí, el dominio y la forma del número.
    CONSTRAINT ck_payout_accounts_forma
        CHECK ((account_type IS NULL OR account_type IN ('AHORROS', 'CORRIENTE'))
               AND number ~ '^[0-9]{4,20}$'),
    CONSTRAINT ck_payout_accounts_baja CHECK (NOT (is_principal AND deleted_at IS NOT NULL)),
    -- CASCADE: en producción nadie borra personas, y las suites sí.
    CONSTRAINT fk_payout_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_payout_accounts_institution
        FOREIGN KEY (institution_id) REFERENCES payout_institutions (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_payout_accounts_numero
    ON payout_accounts (user_id, institution_id, number) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_payout_accounts_principal
    ON payout_accounts (user_id) WHERE is_principal AND deleted_at IS NULL;
CREATE INDEX ix_payout_accounts_user ON payout_accounts (user_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE payout_accounts IS
    'RN-MV-055: las cuentas de cobro de cada persona. El titular es el usuario y NO se copia aquí: se lee de users. Borrado lógico; una sola principal entre las vivas.';


CREATE TABLE withdrawal_destinations (
    movement_id             uuid          PRIMARY KEY,
    payout_account_id       uuid          NOT NULL,
    institution_code        varchar(30)   NOT NULL,
    institution_name        varchar(100)  NOT NULL,
    institution_kind        varchar(20)   NOT NULL,
    account_type            varchar(20)   NULL,
    number                  varchar(20)   NOT NULL,
    holder_name             varchar(201)  NOT NULL,
    holder_document_type    varchar(20)   NOT NULL,
    holder_document_number  varchar(30)   NOT NULL,
    created_at              timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_withdrawal_destinations_kind
        CHECK (institution_kind IN ('BANCO', 'BILLETERA_MOVIL')),
    CONSTRAINT fk_withdrawal_destinations_movement
        FOREIGN KEY (movement_id) REFERENCES movements (id) ON DELETE CASCADE,
    -- CASCADE solo por las suites: en producción una cuenta se da de baja, no
    -- se borra, y la copia sigue apuntando a ella.
    CONSTRAINT fk_withdrawal_destinations_account
        FOREIGN KEY (payout_account_id) REFERENCES payout_accounts (id) ON DELETE CASCADE
);

CREATE INDEX ix_withdrawal_destinations_account ON withdrawal_destinations (payout_account_id);

COMMENT ON TABLE withdrawal_destinations IS
    'RN-MV-056: a dónde se paga un retiro, copiado al pedirlo. No se edita: editar la cuenta, darla de baja o renombrar la entidad no lo cambia.';


INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0ef9c-6800-7015-9c4f-5e7ad7000048', 'movements:create-payout-institution', 'movements',
 'create-payout-institution',
 'Registrar una entidad de cobro',
 'Dar de alta un banco o una billetera móvil a los que se pagan los retiros, por POST /movements/payout-institutions (RF-MV-032).'),
('01a0ef9c-6800-7016-9c4f-5e7ad7000049', 'movements:read-payout-institutions', 'movements',
 'read-payout-institutions',
 'Consultar las entidades de cobro',
 'Ver los bancos y billeteras móviles entre los que se elige al registrar una cuenta de cobro, por GET /movements/payout-institutions (RF-MV-033).'),
('01a0ef9c-6800-7017-9c4f-5e7ad7000050', 'movements:update-payout-institution', 'movements',
 'update-payout-institution',
 'Editar una entidad de cobro',
 'Corregir el nombre de una entidad de cobro, o activarla y desactivarla, por PATCH /movements/payout-institutions/{id} (RF-MV-034).'),
('01a0ef9c-6800-7018-9c4f-5e7ad7000051', 'movements:create-own-payout-account', 'movements',
 'create-own-payout-account',
 'Registrar una cuenta de cobro propia',
 'Registrar una cuenta bancaria o una billetera móvil a nombre propio, por POST /movements/mine/payout-accounts (RF-MV-035).'),
('01a0ef9c-6800-7019-9c4f-5e7ad7000052', 'movements:list-own-payout-accounts', 'movements',
 'list-own-payout-accounts',
 'Consultar mis cuentas de cobro',
 'Ver las cuentas de cobro propias, la principal primero, por GET /movements/mine/payout-accounts (RF-MV-036).'),
('01a0ef9c-6800-701a-9c4f-5e7ad7000053', 'movements:update-own-payout-account', 'movements',
 'update-own-payout-account',
 'Editar una cuenta de cobro propia',
 'Corregir una cuenta de cobro propia o hacerla la principal, por PATCH /movements/mine/payout-accounts/{id} (RF-MV-037).'),
('01a0ef9c-6800-701b-9c4f-5e7ad7000054', 'movements:delete-own-payout-account', 'movements',
 'delete-own-payout-account',
 'Dar de baja una cuenta de cobro propia',
 'Retirar una cuenta de cobro propia, por DELETE /movements/mine/payout-accounts/{id} (RF-MV-038). Los retiros pedidos conservan su destino.'),
('01a0ef9c-6800-701c-9c4f-5e7ad7000055', 'movements:read-user-payout-accounts', 'movements',
 'read-user-payout-accounts',
 'Consultar las cuentas de cobro de una persona',
 'Ver las cuentas de cobro de cualquier persona, también las dadas de baja, por GET /movements/users/{userId}/payout-accounts (RF-MV-039).');

-- Administración: SUPERADMIN y ADMIN, explícitos.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
               ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r(id)
 CROSS JOIN (VALUES ('01a0ef9c-6800-7015-9c4f-5e7ad7000048'::uuid),
                    ('01a0ef9c-6800-7017-9c4f-5e7ad7000050'::uuid),
                    ('01a0ef9c-6800-701c-9c4f-5e7ad7000055'::uuid)) AS p(id)
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- Lo propio y el catálogo que se elige: por tipo de rol (RN-SEG-015).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
 CROSS JOIN permissions p
 WHERE p.id IN ('01a0ef9c-6800-7016-9c4f-5e7ad7000049',
                '01a0ef9c-6800-7018-9c4f-5e7ad7000051',
                '01a0ef9c-6800-7019-9c4f-5e7ad7000052',
                '01a0ef9c-6800-701a-9c4f-5e7ad7000053',
                '01a0ef9c-6800-701b-9c4f-5e7ad7000054')
   AND r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;


DO $$
DECLARE
    faltan     integer;
    sin_admin  integer;
    sin_padre  integer;
BEGIN
    SELECT count(*) INTO faltan
      FROM unnest(ARRAY['movements:create-payout-institution',
                        'movements:read-payout-institutions',
                        'movements:update-payout-institution',
                        'movements:create-own-payout-account',
                        'movements:list-own-payout-accounts',
                        'movements:update-own-payout-account',
                        'movements:delete-own-payout-account',
                        'movements:read-user-payout-accounts']) AS esperado(code)
     WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = esperado.code);
    IF faltan <> 0 THEN
        RAISE EXCEPTION 'V61: faltan % de los ocho permisos de las cuentas de cobro', faltan;
    END IF;

    SELECT count(*) INTO sin_admin
      FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
                   ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r(id)
     CROSS JOIN permissions p
     WHERE p.code LIKE 'movements:%payout%'
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.id AND x.permission_id = p.id);
    IF sin_admin <> 0 THEN
        RAISE EXCEPTION 'V61: SUPERADMIN o ADMIN no portan % de los ocho', sin_admin;
    END IF;

    -- Contención (RN-SEG-003).
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id
                          AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V61: % asociaciones rompen la contencion de RN-SEG-003', sin_padre;
    END IF;
END $$;
