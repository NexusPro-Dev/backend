-- =============================================================================
-- V42 — Nace `course_category_items` (RF-AC-016, ac.md §8.3, 25-09-2026).
--
-- La clasificación de un curso en una categoría: libre y sin repetir
-- (RN-AC-010). Un curso está en cero o más categorías, una categoría reúne cero
-- o más cursos, y la pareja no se repite.
--
-- SIN `id` Y SIN `deleted_at`: no es una entidad, es el valor de una relación.
-- Desclasificar (RF-AC-017) BORRA la fila y lo registra como `ASSOCIATION`, la
-- tercera clase de eliminación del Art. V.13, como el paquete de PM.
--
-- LA CLAVE PRIMARIA ES LA UNICIDAD: el caso de uso comprueba la pareja bajo el
-- bloqueo del curso, y `pk_course_category_items` es la red si dos peticiones
-- llegaran a la vez; el repositorio la traduce al mismo 409.
--
-- SIN `ON DELETE`: nada se borra físicamente ni del curso ni de la categoría —
-- retirar es una marca—, y las filas PERMANECEN tras el retiro de cualquiera de
-- los dos lados (RN-AC-018). Por eso las suites que limpian con
-- `DELETE FROM courses` o `DELETE FROM course_categories` tienen que borrar
-- ANTES estas filas.
--
-- `ix_course_category_items_category` sostiene el filtro por categoría del
-- listado (RF-AC-009) y los cursos de la categoría (RF-AC-003); para «las
-- categorías de un curso» ya sirve la clave primaria, que empieza por él.
--
-- Plan de RF-AC-016 §2 la numeró V25; el número se asigna al construir, y la
-- siguiente libre era esta.
-- =============================================================================

CREATE TABLE course_category_items (
    course_id   uuid        NOT NULL,
    category_id uuid        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_course_category_items PRIMARY KEY (course_id, category_id),
    CONSTRAINT fk_course_category_items_course   FOREIGN KEY (course_id)   REFERENCES courses (id),
    CONSTRAINT fk_course_category_items_category FOREIGN KEY (category_id) REFERENCES course_categories (id)
);

CREATE INDEX ix_course_category_items_category ON course_category_items (category_id);
