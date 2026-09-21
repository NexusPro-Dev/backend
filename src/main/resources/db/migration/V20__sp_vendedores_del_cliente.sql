-- ---------------------------------------------------------------------------
-- V20 — `client_sellers`: los vendedores de un cliente, y el cliente SALE de
-- `user_supervisors` (RF-SP-059; requirements/sp.md §10.19, RN-SP-028
-- revertida y RN-SP-049 enmendadas el 18-09-2026).
--
-- Dos cosas en una migración, y van juntas a propósito:
--
--   1. La tabla. Una fila por pareja cliente-vendedor, con el origen del
--      vínculo —REGISTRO, quien lo registró; HOTLINK, quien le vendió por su
--      enlace— y la venta que lo creó. SIN clave sustituta, SIN fin: la pareja
--      ES la fila, y un vínculo es un hecho que no se cierra.
--
--   2. La mudanza. Entre el 01-09-2026 y hoy el cliente colgaba de su vendedor
--      en `user_supervisors`, la tabla de MANDO de la fuerza comercial. Se
--      copia la fila VIGENTE de cada cliente como su REGISTRO y se BORRAN
--      todas las suyas —vigentes y cerradas— de la tabla de mando. Se mueve,
--      no se duplica: dos tablas diciendo lo mismo es el estado que esta
--      decisión termina, y una lectura que siguiera mirando la vieja no
--      fallaría — devolvería DE MENOS.
--
-- Quién es «cliente» para la mudanza: quien porta un rol CONSUMIDOR y NINGUNO
-- VENDEDOR. Una persona con los dos —un cliente ascendido a agente— se queda
-- donde está: su fila vigente es mando, y tocarla la dejaría sin superior
-- siendo vendedora (RN-SP-019).
-- ---------------------------------------------------------------------------

CREATE TABLE client_sellers (
    client_id         uuid        NOT NULL,
    seller_id         uuid        NOT NULL,
    origin            varchar(20) NOT NULL,
    first_movement_id uuid        NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_client_sellers
        PRIMARY KEY (client_id, seller_id),
    CONSTRAINT fk_client_sellers_client
        FOREIGN KEY (client_id) REFERENCES users (id),
    CONSTRAINT fk_client_sellers_seller
        FOREIGN KEY (seller_id) REFERENCES users (id),
    -- Primera clave foránea de SP hacia una tabla de MV. ArchUnit vigila
    -- paquetes, no claves; y un vínculo que cita la venta que lo creó es más
    -- honesto que uno que la olvida. Sin ON DELETE: los movimientos no se
    -- borran (RF-MV-001).
    CONSTRAINT fk_client_sellers_movement
        FOREIGN KEY (first_movement_id) REFERENCES movements (id),
    CONSTRAINT ck_client_sellers_no_self
        CHECK (client_id <> seller_id),
    CONSTRAINT ck_client_sellers_origin
        CHECK (origin IN ('REGISTRO', 'HOTLINK'))
);

COMMENT ON TABLE client_sellers IS
    'Los vendedores de un cliente (RN-SP-049). REGISTRO = quien lo registró, su '
    'principal, uno por cliente e inmutable; HOTLINK = quien le vendió por su '
    'enlace. Sin fin: un vínculo es un hecho. El cliente NO tiene fila en '
    'user_supervisors desde V20 (RN-SP-028).';

COMMENT ON COLUMN client_sellers.first_movement_id IS
    'La venta que creó el vínculo. Nula solo en las filas que V20 trajo desde '
    'user_supervisors, cuya venta de registro no se puede reconstruir con certeza.';

-- «Un principal por cliente» dicho en una regla es una intención; dicho en un
-- índice es un rechazo. El día que RF-MV-011 escriba desde otra transacción,
-- la base lo garantiza sin que nadie tenga que acordarse.
CREATE UNIQUE INDEX uq_client_sellers_principal
    ON client_sellers (client_id) WHERE origin = 'REGISTRO';

-- La entrada de «los clientes de un vendedor»: RF-SP-060, la unión con el
-- equipo en RF-SP-056 y la hoja de la recursiva en RF-SP-057.
CREATE INDEX ix_client_sellers_vendedor
    ON client_sellers (seller_id);

-- ---------------------------------------------------------------------------
-- La mudanza.
-- ---------------------------------------------------------------------------

-- `created_at` toma `started_at` —desde cuándo cuelga de él— y no now(), que
-- sería desde cuándo se migró.
INSERT INTO client_sellers (client_id, seller_id, origin, first_movement_id, created_at)
SELECT us.user_id, us.supervisor_id, 'REGISTRO', NULL, us.started_at
  FROM user_supervisors us
 WHERE us.ended_at IS NULL
   AND EXISTS (SELECT 1
                 FROM user_roles ur
                WHERE ur.user_id = us.user_id
                  AND ur.role_type = 'CONSUMIDOR')
   AND NOT EXISTS (SELECT 1
                     FROM user_roles ur
                    WHERE ur.user_id = us.user_id
                      AND ur.role_type = 'VENDEDOR');

-- TODAS las filas del cliente, no solo la vigente: cerrarlas conservaría
-- clientes en una tabla que ya no los admite, y cada lectura tendría que
-- seguir filtrándolos.
DELETE FROM user_supervisors us
 WHERE EXISTS (SELECT 1
                 FROM user_roles ur
                WHERE ur.user_id = us.user_id
                  AND ur.role_type = 'CONSUMIDOR')
   AND NOT EXISTS (SELECT 1
                     FROM user_roles ur
                    WHERE ur.user_id = us.user_id
                      AND ur.role_type = 'VENDEDOR');

-- Sin guarda de recuento, y es deliberado: un cliente con SOLO tramos cerrados
-- —sin vigente— produce una fila borrada sin fila insertada, y abortar la
-- migración de producción por él sería peor que dejarlo sin principal, que es
-- lo que ya era.
