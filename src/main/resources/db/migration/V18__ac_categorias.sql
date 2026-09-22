-- ---------------------------------------------------------------------------
-- V18 — Nace el módulo AC (Academia) con su primera tabla: las CATEGORÍAS del
-- catálogo de cursos (RF-AC-001, requirements/ac.md §8.1, 17-09-2026).
--
-- Una categoría es un cajón: nombre, descripción, color, icono y orden. SIN
-- estado (RN-AC-008): está viva o retirada, y un cajón vacío sale vacío. SIN
-- código: no se teclea en ninguna venta ni se imprime en ningún comprobante.
--
-- `cover_image_id` NACE AQUÍ, nulable y SIN clave foránea: la tabla de las
-- portadas de Academia (`academy_images`) la crea RF-AC-006, y su migración
-- añadirá `fk_course_categories_cover_image` y `uq_course_categories_cover_image`.
-- Se declara ya para que el SELECT del detalle y el `coverImageUrl` de la
-- respuesta tengan su forma definitiva desde el primer día. Mientras no exista
-- la tabla, nadie la escribe.
--
-- `display_order` y no `order` —palabra reservada— ni `position` —función de
-- PostgreSQL que se lee mal en un SELECT—. NO es único (RN-AC-002): dos
-- categorías con el mismo número se desempatan por identificador, que es
-- cronológico (UUID v7).
--
-- El color tiene la forma del de la membresía (RN-SP-024) y NO su unicidad:
-- las categorías no son una cadena que haya que distinguir por el color. La
-- normalización a mayúsculas la hace el dominio ANTES de escribir; el CHECK
-- rechaza lo que llegue en minúsculas por cualquier otra vía.
--
-- El icono es un IDENTIFICADOR, no una imagen (RN-PM-016 por extensión), y
-- aquí es obligatorio: la categoría se pinta SIEMPRE con color e icono, y la
-- portada es un adorno. Por eso el CHECK de forma cabe en el esquema — en
-- `products` el icono es nulable y la forma solo vive en la validación.
-- ---------------------------------------------------------------------------

CREATE TABLE course_categories (
    id             uuid         PRIMARY KEY,
    name           varchar(150) NOT NULL,
    description    text         NULL,
    color          varchar(6)   NOT NULL,
    icon           varchar(50)  NOT NULL,
    display_order  integer      NOT NULL,
    cover_image_id uuid         NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz  NULL,
    CONSTRAINT ck_course_categories_color_format  CHECK (color ~ '^[0-9A-F]{6}$'),
    CONSTRAINT ck_course_categories_icon_format   CHECK (icon ~ '^[a-z][a-z0-9-]*$'),
    CONSTRAINT ck_course_categories_display_order CHECK (display_order >= 0)
);

-- Único entre las VIVAS, sin distinguir mayúsculas ni acentos (RN-AC-001):
-- una retirada libera el nombre, porque no hay código que conservar. Parcial,
-- y por parcial no admite DEFERRABLE: muerde en el INSERT, y el repositorio lo
-- traduce al mismo 409 que la comprobación previa.
CREATE UNIQUE INDEX uq_course_categories_name
    ON course_categories (f_unaccent(lower(name)))
    WHERE deleted_at IS NULL;

COMMENT ON TABLE course_categories IS
    'Los cajones del catalogo de cursos (AC, RN-AC-001 a RN-AC-004). SIN estado: viva o retirada (RN-AC-008). Retirarla NO arrastra cursos ni se rechaza por tenerlos: es un filtro (RN-AC-018). Se ordena por display_order, no unico, con desempate por id.';

COMMENT ON COLUMN course_categories.color IS
    'Seis digitos hexadecimales en mayusculas, sin #, con los que el frontend pinta la categoria (RN-AC-003; forma de RN-SP-024). NO es unico: dos cajones del mismo tono no confunden a nadie.';

COMMENT ON COLUMN course_categories.icon IS
    'El NOMBRE del icono con el que el frontend pinta la categoria (RN-AC-003), no una imagen. Obligatorio: la categoria se pinta siempre con color e icono, y la portada es opcional.';

COMMENT ON COLUMN course_categories.display_order IS
    'La posicion en que se ensena (RN-AC-002): entero >= 0, NO unico, desempate por id. Corregirlo no reordena a las demas.';

COMMENT ON COLUMN course_categories.cover_image_id IS
    'La portada de la categoria (RN-AC-004): la fila de academy_images cuyos bytes se sirven sin token en /api/v1/academy-images/{id}. NULL = no tiene, y entonces se pinta con color e icono. La clave foranea y la unicidad las anade la migracion que crea academy_images (RF-AC-006).';
