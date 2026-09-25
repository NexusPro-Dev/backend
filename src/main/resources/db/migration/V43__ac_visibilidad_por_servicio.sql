-- =============================================================================
-- V43 — Nace `course_products` (RF-AC-037, RN-AC-020, ac.md §8.5.1,
-- 25-09-2026).
--
-- Qué SERVICIOS abren un curso: los productos `BOT` de PM que, tenidos
-- vigentes, dan acceso a su contenido. Se SUMA a las membresías: el curso se
-- abre con una de sus membresías O uno de sus servicios. Lo decidió el
-- responsable del proyecto: es EL CURSO quien declara quién lo puede ver.
--
-- LA PRIMERA CLAVE FORÁNEA DE AC HACIA PM. Cruza y se declara, como las de CM
-- y SP hacia `products`: la frontera que D-25 defiende es la del código, y una
-- clave foránea es integridad en el motor. AC lee el producto por
-- `ProductCatalog` al escribir, y por JOIN al leer.
--
-- QUE SEA `BOT` NO CABE AQUÍ: el tipo vive en `products` y ningún CHECK de esta
-- tabla puede mirarlo. Lo comprueba el dominio al añadir, y como el tipo de un
-- producto no cambia nunca (RN-PM-001), lo comprobado sigue siendo cierto.
--
-- SIN `ON DELETE`: el producto se retira en lógico y no se borra. Un servicio
-- retirado DEJA SU FILA, porque quien lo compró lo tiene hasta que venza. Toda
-- suite que limpie con `DELETE FROM products` borra antes estas filas.
--
-- `ix_course_products_product` sostiene la pregunta del aula al revés: con los
-- productos vigentes de una persona, qué cursos se le abren.
--
-- Numerada al construir: `cm.md` v0.17.0 anotaba V43 para la liquidación, que
-- no tiene código y pasa a la siguiente libre.
-- =============================================================================

CREATE TABLE course_products (
    course_id  uuid        NOT NULL,
    product_id uuid        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_course_products PRIMARY KEY (course_id, product_id),
    CONSTRAINT fk_course_products_course  FOREIGN KEY (course_id)  REFERENCES courses (id),
    CONSTRAINT fk_course_products_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX ix_course_products_product ON course_products (product_id);
