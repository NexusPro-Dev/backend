-- =============================================================================
-- RF-SP-024 · T-01 — Personas del sistema.
--
-- Es la tabla que CREA EL SUJETO DEL MÓDULO: hasta aquí `SP` tenía roles,
-- permisos y catálogos, y el `actor_id` de los cuatro registros de auditoría no
-- resolvía a ninguna fila.
--
-- CADA PERSONA LLEVA DOS IDENTIDADES y con cualquiera de las dos inicia sesión:
-- el correo, que puede corregirse, y el nombre de usuario, inmutable. La
-- diferencia de trato entre ambas —una se normaliza al guardarse y la otra no—
-- está justificada más abajo, en cada restricción.
-- =============================================================================

CREATE TABLE users (
    id                   uuid         PRIMARY KEY,
    username             varchar(50)  NOT NULL,
    email                varchar(255) NOT NULL,
    first_name           varchar(100) NOT NULL,
    last_name            varchar(100) NOT NULL,
    password_hash        varchar(255) NOT NULL,
    must_change_password boolean      NOT NULL DEFAULT false,
    status               varchar(20)  NOT NULL DEFAULT 'ACTIVO',
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),

    -- `deleted_at` NACE CON LA TABLA y no con `RF-SP-029`, corregido el
    -- 22-08-2026 (Art. I.7). `architecture.md` §6.4 la declara columna
    -- obligatoria de toda tabla de negocio, y DIEZ requerimientos la leen antes
    -- de que alguien la escriba: `RF-SP-003` y `RF-SP-009` ya la daban por
    -- existente, y `RF-SP-025` a `RF-SP-027` no serían implementables sin ella,
    -- porque tratar al eliminado como inexistente es la mitad de su contrato.
    --
    -- Lo que sigue siendo de `RF-SP-029` es ESCRIBIRLA: es el único
    -- requerimiento que la pone a un valor distinto de nulo.
    deleted_at           timestamptz  NULL,

    -- El correo se persiste YA NORMALIZADO, de modo que una restricción única
    -- corriente basta y el dato almacenado es el comparable.
    CONSTRAINT uq_users_email UNIQUE (email),

    -- Sin este CHECK, un INSERT directo —una migración, una corrección manual—
    -- mete `Juan@X.com` y `uq_users_email` deja de significar lo que dice.
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(btrim(email))),

    -- Comprobación de FORMA mínima, no una validación de correo: la buena está
    -- en el DTO. Aquí solo impide que entre por INSERT directo algo que no es
    -- una dirección.
    CONSTRAINT ck_users_email_format
        CHECK (email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),

    -- ES LA RESTRICCIÓN QUE SOSTIENE EL INICIO DE SESIÓN CON AMBAS IDENTIDADES.
    -- Sin ella, un nombre de usuario podría parecerse a un correo y `RF-SP-034`
    -- tendría que decidir cuál de las dos columnas consultar.
    CONSTRAINT ck_users_username_no_at CHECK (position('@' in username) = 0),

    -- Alfabeto sin espacios ni acentos. Un nombre de usuario con un espacio al
    -- final es indistinguible del mismo sin él en cualquier pantalla, y es
    -- permanente: `RN-SP-016` lo hace inmutable.
    CONSTRAINT ck_users_username_format CHECK (username ~ '^[A-Za-z0-9._-]{3,50}$'),

    CONSTRAINT ck_users_names_not_blank
        CHECK (length(btrim(first_name)) > 0 AND length(btrim(last_name)) > 0),

    -- Los CUATRO estados del catálogo de `security.md` §9, aunque este
    -- requerimiento solo produzca ACTIVO: `PENDIENTE` queda declarado y sin uso
    -- a propósito, y `BLOQUEADO` lo estrenan `RF-SP-034` y `RF-SP-028`.
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVO', 'INACTIVO', 'BLOQUEADO', 'PENDIENTE'))
);

COMMENT ON TABLE users IS
    'Personas del sistema. Dos identidades —nombre de usuario y correo— y ambas sirven para entrar.';
COMMENT ON COLUMN users.username IS
    'Inmutable y sin arroba (RN-SP-016). Se guarda TAL COMO SE ESCRIBIÓ; la unicidad ignora la caja.';
COMMENT ON COLUMN users.password_hash IS
    'Argon2id. Nunca en texto plano ni con hash reversible (security.md §3.2).';

-- LA UNICIDAD DEL NOMBRE DE USUARIO VA SOBRE `lower(username)` Y LA DEL CORREO
-- NO, y la diferencia es real.
--
-- El correo TIENE forma canónica: todo proveedor trata el buzón como insensible
-- a mayúsculas, de modo que se normaliza al recibirlo y se guarda normalizado.
--
-- El nombre de usuario NO la tiene: `JPerez` es como esa persona quiere que la
-- vean, y la auditoría lo mostrará durante años. Se guarda tal cual y es la
-- unicidad la que ignora la caja.
--
-- Consecuencia para `RF-SP-034`, y es una obligación: el inicio de sesión debe
-- comparar el nombre de usuario SIN DISTINGUIR MAYÚSCULAS, o alguien podrá
-- registrarse como `JPerez` y no poder entrar escribiendo `jperez`.
--
-- Los dos son TOTALES y no parciales, aunque `deleted_at` exista: `RN-SP-016` no
-- libera nada al eliminar, porque reutilizar una identidad permitiría que la
-- actividad de dos personas se confundiera en la auditoría. Es la asimetría
-- deliberada con `uq_roles_code`.
CREATE UNIQUE INDEX uq_users_username ON users (lower(username));

-- NO se crea `ix_users_busqueda`: la búsqueda por fragmento es de `RF-SP-025`,
-- que es quien decide su forma. Mismo reparto que con `ix_countries_busqueda`.
--
-- NO se crean `failed_attempts`, `locked_until` ni `last_login_at`: las crea
-- `RF-SP-034`, que es quien las lee y las escribe. Una columna disponible antes
-- de que exista la regla que la gobierna acaba usándose por un camino que nadie
-- diseñó.
