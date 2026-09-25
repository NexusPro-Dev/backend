-- =============================================================================
-- V45 — Nace `course_memberships` (RF-AC-020, RN-AC-012, ac.md §8.5,
-- 25-09-2026).
--
-- Qué MEMBRESÍAS abren un curso: una lista explícita y no un nivel mínimo
-- (ac.md §5.2.2). Se SUMA a los servicios de `course_products` (V43): el curso
-- se abre con una de sus membresías O uno de sus servicios (RN-AC-020).
--
-- `fk_course_memberships_membership` CRUZA HACIA SP y se declara, como
-- `products.target_membership_id`. La membresía es inmutable y no se retira
-- (RN-SP-008), de modo que la fila no tiene otro lado que pueda morir.
--
-- SIN `ON DELETE`, y aquí importa por las pruebas: varias suites de MV montan
-- su propia cadena con `DELETE FROM memberships`; la suite de academia borra
-- sus filas de esta tabla al terminar cada prueba.
--
-- `ix_course_memberships_membership` sostiene el aula: qué cursos abre la
-- membresía vigente de quien mira (RF-AC-033).
--
-- El plan la numeraba V26; el número se asigna al construir.
-- =============================================================================

CREATE TABLE course_memberships (
    course_id     uuid        NOT NULL,
    membership_id uuid        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_course_memberships PRIMARY KEY (course_id, membership_id),
    CONSTRAINT fk_course_memberships_course     FOREIGN KEY (course_id)     REFERENCES courses (id),
    CONSTRAINT fk_course_memberships_membership FOREIGN KEY (membership_id) REFERENCES memberships (id)
);

CREATE INDEX ix_course_memberships_membership ON course_memberships (membership_id);
