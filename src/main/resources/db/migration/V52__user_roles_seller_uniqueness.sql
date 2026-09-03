-- =============================================================================
-- RF-SP-030 · RN-SP-025 — UN SOLO ROL VENDEDOR POR PERSONA, EN EL MOTOR.
--
-- La regla nacio el 28-08-2026 porque la pidio `CM`: sin ella, «el rol vendedor
-- de una persona» NO ES UNA PREGUNTA CON UNA SOLA RESPUESTA, y la resolucion de
-- comisiones elegiria en silencio. SE DECLARO Y NO SE CONSTRUYO, de modo que
-- durante cinco dias una persona pudo portar dos. Lo unico que lo delataba era
-- que `SellerRoleCatalog` reventara con `AmbiguousSellerRoleException` en lugar
-- de elegir.
--
-- `modules.md` afirmaba que NO SE PODIA declarar en el esquema: que un CHECK no
-- consulta otra tabla y que un indice unico no puede unir `user_roles` con
-- `roles`. Las dos frases son ciertas y LA CONCLUSION NO LO ERA: el dato que
-- falta no hay que consultarlo, HAY QUE COPIARLO.
--
-- COPIAR UN DATO ES NORMALMENTE EL ERROR QUE ESTE PROYECTO EVITA, y aqui no lo
-- es por dos motivos que conviene poder nombrar:
--
--   1. La copia esta atada por una CLAVE FORANEA COMPUESTA, de modo que no
--      puede divergir. Es el patron que `V49` valido en
--      `product_commission_rates`.
--   2. `roles.role_type` NO ES EDITABLE —`RF-SP-004` corrige nombre y
--      descripcion—, de modo que la copia NUNCA habra que actualizarla.
--
-- Donde el origen cambia, este patron no vale: la FK bloquearia la correccion
-- legitima, y la regla tendria que volver al dominio.
--
-- LO QUE DECIDIO NO FUE LA ELEGANCIA SINO UN PRECEDENTE. `RN-SP-018` se
-- comprobaba en el caso de uso, NO SE SOSTUVO BAJO CONCURRENCIA y hubo que
-- corregirla el 26-08-2026 — misma tabla, misma clase de comprobacion.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. El destino de la clave foranea compuesta.
--
-- REDUNDANTE CON LA CLAVE PRIMARIA DE `roles`, Y ESA ES TODA SU FUNCION.
-- PostgreSQL exige que el destino de una FK COMPUESTA sea una restriccion unica
-- sobre EXACTAMENTE esas columnas; sin esto, la de abajo no se puede declarar.
-- Mismo caso que `uq_commission_rates_id_role` en `V49`.
-- -----------------------------------------------------------------------------

ALTER TABLE roles
    ADD CONSTRAINT uq_roles_id_role_type UNIQUE (id, role_type);

-- -----------------------------------------------------------------------------
-- 2. La copia.
--
-- Se anade NULA, se rellena desde `roles`, y solo entonces se pone NOT NULL.
-- Anadirla NOT NULL de golpe exigiria un DEFAULT, y cualquier valor por defecto
-- seria MENTIRA en la mitad de las filas.
-- -----------------------------------------------------------------------------

ALTER TABLE user_roles
    ADD COLUMN role_type varchar(20) NULL;

UPDATE user_roles ur
   SET role_type = r.role_type
  FROM roles r
 WHERE r.id = ur.role_id;

ALTER TABLE user_roles
    ALTER COLUMN role_type SET NOT NULL;

-- -----------------------------------------------------------------------------
-- 3. Lo que impide que la copia mienta.
--
-- La FK vieja apuntaba solo a `roles(id)`. La nueva lleva el tipo dentro, de
-- modo que una fila con un `role_type` que su rol no tiene NO SE PUEDE
-- ESCRIBIR. Sin esto, `role_type` seria un dato suelto que nadie mantiene.
--
-- Y con ella, `roles.role_type` queda ademas protegido: cambiarlo con gente
-- portando ese rol lo rechaza el motor. Hoy no se puede cambiar por la API
-- (`RF-SP-004`), de modo que la restriccion no le quita nada a nadie.
-- -----------------------------------------------------------------------------

ALTER TABLE user_roles
    DROP CONSTRAINT fk_user_roles_role;

ALTER TABLE user_roles
    ADD CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id, role_type)
        REFERENCES roles (id, role_type)
        ON DELETE RESTRICT;

-- -----------------------------------------------------------------------------
-- 4. `RN-SP-025`.
--
-- PARCIAL, como `uq_user_supervisors_vigente`: solo mira las filas de tipo
-- VENDEDOR. Un funcionario y un consumidor pueden convivir con lo que sea.
--
-- SI ESTA SENTENCIA FALLA, ALGUIEN PORTA YA DOS ROLES VENDEDORES, y la
-- migracion se detiene. NO SE LIMPIA AQUI: `V49` borro datos a proposito porque
-- ninguno tenia traduccion al modelo nuevo; estos si la tienen —alguien decidio
-- esos roles— y ELEGIR CUAL SOBREVIVE ES UNA DECISION DE NEGOCIO QUE UNA
-- MIGRACION NO PUEDE TOMAR. Que se detenga es lo correcto: obliga a mirar los
-- datos antes de imponer una regla que llevaba cinco dias declarada sin
-- sostener.
-- -----------------------------------------------------------------------------

CREATE UNIQUE INDEX uq_user_roles_vendedor
    ON user_roles (user_id)
    WHERE role_type = 'VENDEDOR';

COMMENT ON COLUMN user_roles.role_type IS
    'COPIA de roles.role_type, atada por la FK compuesta (role_id, role_type). '
    'Existe para que `RN-SP-025` viva en el motor: un CHECK no consulta otra '
    'tabla y un indice unico no puede unir user_roles con roles. Es legitima '
    'porque roles.role_type NO ES EDITABLE, de modo que nunca hay que '
    'actualizarla.';
