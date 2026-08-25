-- =============================================================================
-- RF-SP-016 · T-01 — Cadena de membresías.
--
-- Campos de `requirements/sp.md` §10.4, restricciones de §10.7 más las cuatro
-- que el plan añade.
--
-- LA CADENA NO ES UN ÁRBOL. Cada membresía tiene una sola hija: es una lista
-- ordenada, no una jerarquía ramificada. Insertar una membresía en medio
-- reencadena a su hija y desplaza los niveles siguientes.
--
-- `level` ES LA DISTANCIA HASTA LA CIMA: la membresía superior tiene `level = 1`
-- y el número crece hacia abajo. Lo fija `plan.md` §2, y de ello depende cuántas
-- filas toca cada alta: con esta numeración, el alta más común —al extremo
-- inferior, `FA-002`— no toca ninguna otra fila. Con la numeración inversa
-- habría que renumerar la cadena entera justo en el caso que la especificación
-- describe como el que no reordena nada.
--
-- Cuidado con el vocabulario: en `RN-SP-006` —«toda membresía está sujeta a una
-- de MAYOR NIVEL»— *mayor* es jerárquico, no numérico. La superior de una
-- membresía tiene siempre un `level` MENOR.
--
-- NO SE DECLARA `deleted_at`: `RN-SP-008` hace que una membresía no se elimine
-- nunca. Sí `created_at` y `updated_at` por Art. V.7, y `updated_at` no es
-- decorativo aquí: cambia cada vez que el reordenamiento toca la fila, que es lo
-- único que puede cambiar en ella.
-- =============================================================================

CREATE TABLE memberships (
    id                   uuid         PRIMARY KEY,
    code                 varchar(50)  NOT NULL,
    name                 varchar(100) NOT NULL,
    description          text         NULL,
    parent_membership_id uuid         NULL,
    level                smallint     NOT NULL,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),

    -- Restricción TOTAL y no parcial, a diferencia de `roles`: aquí no hay
    -- borrado lógico, de modo que no existe estado en el que un código deba
    -- poder repetirse.
    CONSTRAINT uq_memberships_code UNIQUE (code),

    -- `VAL-006`. Mismo formato ya aprobado para `roles`, y en el esquema por la
    -- misma razón: la garantía vale también para las migraciones de poblado y
    -- para cualquier punto de entrada futuro.
    CONSTRAINT ck_memberships_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),

    -- `RN-SP-006`. RESTRICT y no CASCADE: `RN-SP-008` prohíbe eliminar, y una
    -- eliminación física accidental debe fallar en lugar de vaciar la cadena
    -- entera.
    CONSTRAINT fk_memberships_parent
        FOREIGN KEY (parent_membership_id) REFERENCES memberships (id) ON DELETE RESTRICT,

    -- LA RESTRICCIÓN CENTRAL DE LA TABLA.
    --
    -- `NULLS NOT DISTINCT` no es un adorno. PostgreSQL trata los nulos como
    -- distintos entre sí, de modo que una UNIQUE corriente sobre esta columna
    -- admitiría tantas filas con `parent_membership_id IS NULL` como se
    -- quisiera: es decir, admitiría VARIAS MEMBRESÍAS SUPERIORES, que es justo
    -- la bifurcación que se quiere impedir y en el peor sitio.
    --
    -- Con la cláusula, una sola restricción garantiza las dos mitades de
    -- `CA-SP-114`: a lo sumo una fila con la superior en nulo —existe como mucho
    -- una membresía superior— y a lo sumo una fila apuntando a cada membresía
    -- —cada una tiene como mucho una hija—.
    --
    -- DIFERIDA porque insertar en medio deja transitoriamente dos filas
    -- apuntando a la misma superior: la nueva ya apunta a ella y la hija todavía
    -- no se ha reencadenado. Con la restricción inmediata, el orden de las
    -- sentencias tendría que ser exacto y el caso de insertar por encima de la
    -- superior no tendría orden válido alguno.
    --
    -- LO QUE CUESTA, y hay que asumirlo: la violación se detecta en el COMMIT,
    -- fuera del bloque donde el adaptador podría capturarla. La traduce
    -- `GlobalExceptionHandler` a `EX-003`.
    CONSTRAINT uq_memberships_parent UNIQUE NULLS NOT DISTINCT (parent_membership_id)
        DEFERRABLE INITIALLY DEFERRED,

    -- En un orden lineal cada posición es única. Sin esta restricción, un
    -- recálculo defectuoso deja dos membresías en el mismo nivel y el listado de
    -- `RF-SP-017` devuelve un orden arbitrario entre ambas.
    --
    -- Diferida por el mismo motivo: PostgreSQL verifica la unicidad fila a fila
    -- dentro de una misma sentencia, de modo que `SET level = level + 1`
    -- colisiona consigo mismo aunque el estado final sea correcto. No hay orden
    -- de ejecución que lo evite; la solución no es ordenar las sentencias sino
    -- diferir la comprobación.
    CONSTRAINT uq_memberships_level UNIQUE (level) DEFERRABLE INITIALLY DEFERRED,

    -- El nivel 1 es la cima. Un cero o un negativo solo pueden venir de un
    -- recálculo defectuoso.
    CONSTRAINT ck_memberships_level_positive CHECK (level >= 1),

    -- Cadena de longitud uno. Cuesta una línea y no depende de que el dominio
    -- esté bien.
    CONSTRAINT ck_memberships_parent_not_self
        CHECK (parent_membership_id IS NULL OR parent_membership_id <> id),

    -- Mismo límite y mismo motivo que en `roles` y en `permissions`: la columna
    -- es `text` y `RF-SP-017` devuelve la cadena entera sin paginar, de modo que
    -- sin cota el tamaño de la respuesta es impredecible.
    CONSTRAINT ck_memberships_description_length
        CHECK (description IS NULL OR length(description) <= 500)
);

COMMENT ON TABLE memberships IS
    'Cadena lineal de niveles de consumidor. Inmutable salvo el reordenamiento de RN-SP-007.';
COMMENT ON COLUMN memberships.level IS
    'Distancia hasta la cima: 1 es la membresía superior y el número crece hacia abajo.';
COMMENT ON COLUMN memberships.parent_membership_id IS
    'Membresía de MAYOR nivel jerárquico, es decir, de level MENOR. Nulo solo en la superior.';

-- `VAL-004`. Sobre la forma normalizada y no sobre el texto: `RN-SP-008` hace la
-- membresía inmutable, de modo que `Plata` y `plata` convivirían para siempre.
--
-- Es un índice único FUNCIONAL y no una restricción de tabla, porque una
-- restricción no admite expresión. `f_unaccent` existe desde `V1` y está
-- declarada IMMUTABLE precisamente para poder indexarse.
CREATE UNIQUE INDEX uq_memberships_name ON memberships (f_unaccent(lower(name)));
