-- =============================================================================
-- RF-SP-024 · T-42 — Toda persona pertenece a un país (`RN-SP-034`).
--
-- `users` gana `country_id`, OBLIGATORIA, con clave foránea a `countries`. Es la
-- primera columna de `users` que apunta a un catálogo, y la primera clave
-- foránea entrante de `countries` que viene de una persona: hasta hoy el sistema
-- sabía en qué países NO vale un medio de pago (`RN-MV-019`, `V55`) y no sabía
-- en qué país está nadie.
--
-- POR QUÉ LA COLUMNA NACE `NOT NULL` Y NO NULABLE. Una columna nulable obliga a
-- TODO consumidor futuro a contemplar la ausencia, y `RN-SP-034` dice justamente
-- que esa ausencia no significa nada: el estado «usuario sin país» no existe. El
-- precio de la obligatoriedad se paga UNA VEZ, aquí; el de la nulabilidad se
-- paga en cada consulta que se escriba a partir de mañana.
--
-- Y EL PRECIO ES CONCRETO: hay que rellenar las filas que ya existen. `V22`
-- siembra un superadministrador —siempre, en toda base, incluidas las de las
-- pruebas de integración—, de modo que no hay ningún entorno en el que este paso
-- se pueda saltar.
--
-- DE AHÍ QUE ESTA MIGRACIÓN SIEMBRE UN PAÍS, que es lo que la hace distinta de
-- un `ALTER` corriente. `V16` creó `countries` VACÍA a propósito —«los países se
-- dan de alta por la API a medida que la plataforma llega a ellos»
-- (`requirements/sp.md` §10.6)—, y esa decisión deja de ser sostenible en el
-- momento en que una columna obligatoria la referencia: un catálogo vacío haría
-- fallar el paso 4 en toda instalación nueva.
--
-- EL PAÍS ES COLOMBIA, por decisión del responsable del proyecto (07-09-2026).
-- Se siembra UNA SOLA FILA, la del mercado desde el que se opera, con el mismo
-- criterio con el que `V15` sembró `USD` y ninguna otra moneda: un país que
-- existe en el catálogo puede seleccionarse, y ofrecer uno en el que no se opera
-- es peor que no tenerlo.
--
-- LA ELECCIÓN NO TIENE CORRECCIÓN POSIBLE. `RN-SP-009` prohíbe editar y eliminar
-- un país: si el código o el nombre de abajo están mal, quedan mal PARA SIEMPRE
-- y lo único que se puede hacer es desactivar la fila. Es la misma
-- irreversibilidad que obligó a `V42` a levantar excepción en lugar de adivinar
-- equivalencias alfa-2 → alfa-3.
--
-- SOBRE EL NÚMERO DE MIGRACIÓN. Esta se planificó como `V62` y `V62`/`V63` se
-- los llevaron las tasas de cambio (`RF-SP-047`) el mismo día. Es el caso que
-- `modelo-datos.md` §5.4 describe: un número no se reserva anotándolo, se
-- reserva escribiéndolo, y Flyway deja fuera —SIN ERROR Y SIN AVISO— toda
-- migración con número por debajo del último aplicado.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. El país base
--
-- IDENTIFICADOR UUID v7 LITERAL (Art. V.11), generado una sola vez al redactar
-- esta migración y NO por `gen_random_uuid()`. Debe ser el mismo en todos los
-- entornos: las pruebas de integración de los seis requerimientos que esta
-- enmienda toca necesitan referir este país sin consultarlo, igual que refieren
-- el `USD` de `V15` y el superadministrador de `V22`. Marca de tiempo
-- 2026-09-07T12:00:00Z (01a07bbd-5200).
--
-- `ON CONFLICT DO NOTHING` sobre el código y no sobre el identificador: si una
-- base ya registró Colombia por la API, su fila tiene OTRO identificador y es la
-- buena — `uq_countries_code` es quien lo detecta. Sin esta cláusula, esa
-- migración fallaría en el único entorno donde alguien ya hizo lo correcto.
-- -----------------------------------------------------------------------------
INSERT INTO countries (id, code, name, is_active)
VALUES ('01a07bbd-5200-7001-9c4f-5e7ad3000101', 'COL', 'Colombia', true)
ON CONFLICT (code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2. La columna, todavía nulable
--
-- `ADD COLUMN … NOT NULL` sin defecto FALLA EN SECO sobre una tabla con filas, y
-- con un defecto dejaría el país cableado en el esquema para siempre — de modo
-- que toda fila futura que olvidara declararlo acabaría en Colombia sin que
-- nadie lo decidiera. Los pasos separados son lo que convierte el relleno en una
-- decisión legible en lugar de en el efecto lateral de una cláusula.
-- -----------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN country_id uuid;

-- -----------------------------------------------------------------------------
-- 3. El relleno
--
-- Se resuelve POR CÓDIGO y no por el identificador literal del paso 1,
-- justamente por el `ON CONFLICT` de arriba: en una base donde Colombia ya
-- existía, la fila buena es la suya.
-- -----------------------------------------------------------------------------
UPDATE users
   SET country_id = (SELECT id FROM countries WHERE code = 'COL')
 WHERE country_id IS NULL;

-- -----------------------------------------------------------------------------
-- 4. Y solo entonces, obligatoria
--
-- LA CLAVE FORÁNEA ES SIMPLE Y NO COMPUESTA, y esto es lo que más fácil sería
-- equivocar. `RN-SP-034` exige además que el país asignado esté ACTIVO, y el
-- patrón que `V52` estrenó para `user_roles.role_type` parece aplicable: copiar
-- `is_active` a `users` y declarar `(country_id, is_active) → countries(id,
-- is_active)`.
--
-- NO VALE, y falla en la condición que aquel mismo caso dejó escrita: el dato
-- copiado tiene que ser INMUTABLE EN SU ORIGEN. `role_type` no se corrige nunca;
-- `countries.is_active` es LO ÚNICO que `RN-SP-009` deja cambiar, y quien lo
-- cambia es `RF-SP-022`.
--
-- Lo que ocurriría es concreto: la clave compuesta HARÍA FALLAR `RF-SP-022`
-- sobre cualquier país con usuarios. Desactivar dejaría de ser «retirarlo de los
-- selectores» —lo que ese requerimiento promete— y pasaría a ser una operación
-- bloqueada por terceros, sobre gente a la que nadie estaba tocando. La
-- comprobación de país activo es DE ENTRADA y vive en el caso de uso.
--
-- Y NO SE DECLARA `ON DELETE` DE NINGÚN TIPO. `RN-SP-009` no admite borrar un
-- país, ni lógica ni físicamente, de modo que la fila apuntada no puede
-- desaparecer. El `NO ACTION` por omisión es aquí una red que nadie llegará a
-- tocar, y escribir `RESTRICT` sugeriría que existe un borrado del que
-- defenderse. Es la diferencia deliberada con `fk_user_roles_user`, que sí lo
-- declara porque `users` sí se borra.
-- -----------------------------------------------------------------------------
ALTER TABLE users ALTER COLUMN country_id SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_country
    FOREIGN KEY (country_id) REFERENCES countries (id);

-- EL ÍNDICE HACE DOBLE TRABAJO, y por eso se declara aquí y no en `RF-SP-025`,
-- que es quien estrena el filtro. Es la asimetría deliberada con
-- `ix_user_memberships_membership_id`, que sí vive en el plan de aquella
-- consulta: aquel índice SOLO sirve al listado, y este tiene un segundo
-- consumidor que no es ninguna consulta — sin él, el `NO ACTION` de arriba
-- recorre `users` ENTERA en cada intento de borrar un país.
--
-- TOTAL Y NO PARCIAL, al revés que los dos índices parciales de esta misma
-- tabla: aquellos excluyen historial cerrado, y aquí no hay historial que
-- excluir — el país es una columna del propio agregado y no una tabla puente.
CREATE INDEX ix_users_country_id ON users (country_id);

COMMENT ON COLUMN users.country_id IS
    'Dónde está la persona (RN-SP-034). Obligatorio. Solo se asigna un país activo, y esa mitad de la regla vive en el caso de uso: declararla en el esquema haría fallar RF-SP-022.';
