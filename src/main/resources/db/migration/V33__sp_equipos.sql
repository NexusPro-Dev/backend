-- ---------------------------------------------------------------------------
-- V33 — Nace el submódulo EQUIPOS de SP, con sus dos tablas (RF-SP-063,
-- requirements/sp.md §10.20 y §10.21, 22-09-2026).
--
-- Un equipo agrupa a la CÚSPIDE de la fuerza comercial —quienes portan el rol
-- vendedor de mayor rango, los que RN-SP-019 exime de superior—, y con cada
-- uno entra, por su cadena de mando en `user_supervisors`, toda la red que
-- cuelga de él. UN EQUIPO NO MANDA: agrupa. Debajo de un manager la
-- organización ya está dicha; lo que faltaba era cómo se agrupan quienes no
-- tienen a nadie encima.
--
-- NO ES UNA TERCERA JERARQUÍA. `roles.parent_role_id` acota privilegios,
-- `user_supervisors` dice quién está a cargo de quién, y esto PARTICIONA LAS
-- RAÍCES de ese bosque. Por eso no hay `parent_team_id`: encima de la cúspide
-- no hay nada que ordenar.
--
-- Y NO CONCEDE ACCESO A NINGÚN DATO: D-22 sigue abierta, y el alcance de las
-- ventas que RF-MV-015 resolvió recorre `user_supervisors`, no los equipos.
--
-- SIN CÓDIGO (RF-SP-063 §14.1): el código de un rol existe porque el código lo
-- referencia —`hasAuthority`, SellerRoleCatalog—; nada del sistema referencia a
-- un equipo por un nombre estable, y un código que nadie usa es una columna más
-- que mantener única. Lo que identifica es el nombre, y de ahí `uq_teams_name`.
--
-- `team_members` NACE AQUÍ aunque nadie la escriba hasta RF-SP-069, con el
-- precedente de `course_categories.cover_image_id` en V18: crear el esquema del
-- submódulo entero de una vez evita que el detalle de RF-SP-065 cambie de forma
-- a mitad del bloque, y `memberCount` puede contarse desde el primer día.
-- ---------------------------------------------------------------------------

CREATE TABLE teams (
    id          uuid         PRIMARY KEY,
    name        varchar(100) NOT NULL,
    description text         NULL,
    status      varchar(20)  NOT NULL DEFAULT 'ACTIVO',
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted_at  timestamptz  NULL,
    CONSTRAINT ck_teams_name_not_blank      CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_teams_description_length  CHECK (description IS NULL OR length(description) <= 500),
    CONSTRAINT ck_teams_status              CHECK (status IN ('ACTIVO', 'INACTIVO'))
);

-- Único entre los NO ELIMINADOS, sin distinguir mayúsculas ni acentos
-- (RN-SP-050). Junta por primera vez en el repositorio las dos formas:
-- FUNCIONAL como uq_brokers_name —el nombre es lo único que identifica, y
-- «Equipo Norte» y «equipo norte» serían dos opciones indistinguibles en
-- cualquier selector— y PARCIAL como uq_roles_name —la baja es lógica y libera
-- el nombre, porque no hay código que conservar—.
-- Por parcial no admite DEFERRABLE: muerde en el INSERT, y el repositorio lo
-- traduce al mismo 409 que la comprobación previa.
CREATE UNIQUE INDEX uq_teams_name
    ON teams (f_unaccent(lower(name)))
    WHERE deleted_at IS NULL;

-- Búsqueda por nombre de RF-SP-064, por contenido y sin acentos. La expresión
-- es LA DEL PREDICADO y no otra: si divergieran, el índice existiría y el
-- planificador no lo usaría nunca, y el defecto saldría como lentitud que nadie
-- relaciona con esta migración (la lección de ix_users_busqueda).
CREATE INDEX ix_teams_busqueda
    ON teams USING gin (f_unaccent(lower(name)) gin_trgm_ops);

CREATE TABLE team_members (
    id         uuid        PRIMARY KEY,
    team_id    uuid        NOT NULL,
    user_id    uuid        NOT NULL,
    started_at timestamptz NOT NULL DEFAULT now(),
    ended_at   timestamptz NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_team_members_team  FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_team_members_user  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_team_members_periodo CHECK (ended_at IS NULL OR ended_at > started_at)
);

-- RN-SP-052 declarada en el motor, con la construcción exacta de
-- uq_user_supervisors_vigente: UN equipo vigente por persona, historial
-- ilimitado. Una restricción única corriente sobre user_id haría imposible el
-- historial, que es justo lo que la regla existe para conservar.
CREATE UNIQUE INDEX uq_team_members_vigente
    ON team_members (user_id)
    WHERE ended_at IS NULL;

-- «¿Quiénes forman este equipo HOY?», que es lo que preguntan el recuento de
-- RF-SP-064, el detalle de RF-SP-065 y la comprobación de RN-SP-054 antes de
-- eliminar. Parcial por lo mismo que ix_user_supervisors_supervisor_vigente: el
-- historial cerrado nunca forma parte de esa respuesta y crecería dentro del
-- índice.
CREATE INDEX ix_team_members_team_vigente
    ON team_members (team_id)
    WHERE ended_at IS NULL;

COMMENT ON TABLE teams IS
    'Los equipos en que se organiza la CUSPIDE de la fuerza comercial (SP, RN-SP-050 a RN-SP-055). Agrupa managers; NO manda —el mando es user_supervisors— y NO concede acceso a ningun dato (D-22). No se anidan: encima de la cuspide no hay nada.';

COMMENT ON COLUMN teams.name IS
    'Lo unico que identifica a un equipo: unico entre los no eliminados, sin distinguir mayusculas ni acentos (RN-SP-050). No hay codigo porque nada del sistema referencia a un equipo por nombre.';

COMMENT ON COLUMN teams.status IS
    'ACTIVO o INACTIVO (RN-SP-053). Un equipo INACTIVO no recibe miembros nuevos y CONSERVA los que tiene: cerrarlos moveria la atribucion de una red entera sin decision explicita.';

COMMENT ON COLUMN teams.deleted_at IS
    'Baja logica con motivo (RN-SP-054). Solo se elimina un equipo SIN miembros vigentes; las pertenencias cerradas sobreviven y el nombre queda libre.';

COMMENT ON TABLE team_members IS
    'A que equipo pertenece cada manager y desde cuando (RN-SP-051, RN-SP-052). Tiene la forma de user_supervisors con un equipo al otro lado. SOLO la cuspide tiene fila: un director o un agente pertenece al equipo de su manager POR RECORRIDO de user_supervisors, no por dato, para que no pueda figurar en un equipo distinto del de quien lo manda. NO concede acceso a ningun dato (D-22).';

COMMENT ON COLUMN team_members.ended_at IS
    'NULL mientras la pertenencia esta vigente. Cerrada es historial y NO se borra: decide a que equipo se atribuia lo que esa red producia, y las comisiones lo necesitaran.';
