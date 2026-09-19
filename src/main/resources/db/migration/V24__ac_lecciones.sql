-- ---------------------------------------------------------------------------
-- V24 — Las LECCIONES de un módulo (RF-AC-028, requirements/ac.md §8.7,
-- 18-09-2026).
--
-- Una lección es lo que se estudia: VIDEO o TEXTO (RN-AC-016), con UNA SOLA
-- columna `content` —la URL si es video, el Markdown si es texto— y no
-- `video_url` más `body`: el tipo manda sobre el contenido, y el backend
-- guarda el texto como llega y lo devuelve como se guardó (ac.md §5.2.4).
--
-- `ck_lessons_video_content` tiene tres ramas y ninguna evalúa a NULL: cuando
-- es video y hay contenido, es una URL; un TEXTO no se mira. Es la lección
-- de ck_deletion_reason (requirements.md v0.31.0).
--
-- `open` con DEFAULT false: la demostración se declara, no se hereda
-- (RN-AC-014). `duration_minutes` en minutos enteros mayores que cero en los
-- dos tipos (RN-AC-017). NACE EN UN MÓDULO Y NO SE MUEVE (RN-AC-019).
--
-- `uq_lessons_title` lleva module_id delante: único dentro del módulo, entre
-- las vivas. `ix_lessons_module` sostiene el árbol y las cuentas de
-- ofrecibilidad; parcial sobre vivas.
-- ---------------------------------------------------------------------------

CREATE TABLE lessons (
    id               uuid         PRIMARY KEY,
    module_id        uuid         NOT NULL,
    type             varchar(20)  NOT NULL,
    title            varchar(150) NOT NULL,
    description      text         NULL,
    content          text         NULL,
    duration_minutes integer      NOT NULL,
    display_order    integer      NOT NULL,
    open             boolean      NOT NULL DEFAULT false,
    status           varchar(20)  NOT NULL DEFAULT 'INACTIVO',
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    deleted_at       timestamptz  NULL,
    CONSTRAINT fk_lessons_module        FOREIGN KEY (module_id) REFERENCES course_modules (id),
    CONSTRAINT ck_lessons_type          CHECK (type IN ('VIDEO', 'TEXTO')),
    CONSTRAINT ck_lessons_video_content CHECK (type <> 'VIDEO' OR content IS NULL OR content ~ '^https?://[^[:space:]]+$'),
    CONSTRAINT ck_lessons_duration      CHECK (duration_minutes > 0),
    CONSTRAINT ck_lessons_status        CHECK (status IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT ck_lessons_display_order CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uq_lessons_title
    ON lessons (module_id, f_unaccent(lower(title)))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_lessons_module
    ON lessons (module_id, display_order, id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE lessons IS
    'Lo que se estudia (AC, RN-AC-016, RN-AC-017, RN-AC-019): VIDEO o TEXTO con una sola columna content. Nace INACTIVO y se activa con contenido (RN-AC-009); se ofrece si esta ACTIVO, viva y con contenido (RN-AC-015). Abierta (open) es la demostracion: se ensena a cualquier alumno con sesion (RN-AC-014).';

COMMENT ON COLUMN lessons.content IS
    'La URL del video si type = VIDEO (forma de RN-AC-005) o el Markdown si type = TEXTO, que el backend guarda y devuelve sin interpretar ni sanear (ac.md §5.2.4). Opcional al registrar, obligatorio para activar.';

COMMENT ON COLUMN lessons.open IS
    'La demostracion (RN-AC-014): abierta a cualquier alumno con sesion aunque su membresia no abra el curso. Falsa por omision. No exime de ofrecerse.';
