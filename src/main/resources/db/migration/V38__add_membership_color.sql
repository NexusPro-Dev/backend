-- =============================================================================
-- RF-SP-016 · T-19 — El color de la membresía (`RN-SP-024`).
--
-- Seis dígitos hexadecimales SIN `#`, en mayúsculas, con los que el frontend
-- pinta el nivel. El `#` no se guarda porque es notación de CSS y no parte del
-- valor: devolverlo obligaría a todo consumidor que no sea una hoja de estilos
-- —una app móvil, un informe— a quitárselo.
--
-- NO SE EDITA `V13`, que es donde nace la tabla: ya está aplicada, y Flyway
-- valida por suma de comprobación. Editarla haría fallar el arranque de toda
-- base que la tenga, con un mensaje que no dice «alguien editó V13» sino
-- «validación fallida». Misma corrección que ya se hizo con `V30` sobre `V7`.
--
-- EL ORDEN DE LOS CUATRO PASOS NO ES NEGOCIABLE, y por dos motivos distintos:
--
--   1. `ADD COLUMN … NOT NULL` sin `DEFAULT` falla en cuanto la tabla tenga una
--      sola fila, de modo que el `NOT NULL` va DESPUÉS del relleno.
--   2. Poner `ck_memberships_color_format` antes del relleno NO fallaría —un
--      `CHECK` sobre `NULL` evalúa a `NULL`, y una fila que evalúa a `NULL` se
--      ACEPTA—, que es peor que fallar: la restricción quedaría declarada sin
--      haber comprobado nada. Es exactamente el defecto de `ck_deletion_reason`
--      (`requirements.md` v0.31.0).
-- =============================================================================

-- 1. La columna, todavía nullable.
ALTER TABLE memberships ADD COLUMN color varchar(6);

-- 2. Relleno de lo que ya existiera. En una base sin membresías no toca nada.
--
-- EL VALOR SE DERIVA DE `level` Y NO DE UN HASH DEL `id`: `uq_memberships_color`
-- se crea tres líneas más abajo y no admite repetidos, de modo que un relleno
-- con probabilidad de colisión convertiría el despliegue en una lotería. `level`
-- es único por `uq_memberships_level`, así que este cálculo lo es también.
--
-- El módulo mantiene el resultado dentro de los seis dígitos que admite la
-- columna, sea cual sea el nivel.
--
-- `updated_at` NO se toca a propósito: esa marca dice cuándo cambió la
-- membresía como hecho de negocio, y rellenar una columna nueva no lo es.
-- Moverla haría que la auditoría de `RF-SP-011` mostrara una modificación que
-- nadie hizo.
--
-- ESTOS COLORES SON DE RELLENO Y NADIE PODRÁ CORREGIRLOS: `RN-SP-008` mantiene
-- la membresía inmutable. Es la condición de reapertura que
-- `requirements/sp.md` §5.1 declara para `RF-SP-043`.
UPDATE memberships
   SET color = upper(lpad(to_hex((level * 1237 + 1193046) % 16777216), 6, '0'));

-- 3. Ahora sí, obligatoria.
ALTER TABLE memberships ALTER COLUMN color SET NOT NULL;

-- 4. Las dos restricciones.
--
-- El formato se declara aquí y no solo en el dominio (Art. V.6): el dominio
-- normaliza a mayúsculas antes de escribir, y esta restricción rechaza lo que
-- llegue por cualquier otra vía, incluida una migración posterior.
ALTER TABLE memberships
    ADD CONSTRAINT ck_memberships_color_format
    CHECK (color ~ '^[0-9A-F]{6}$');

-- Dos niveles del mismo color son indistinguibles justo en lo que el color
-- existe para distinguir. Atrapa el valor REPETIDO y no dos tonos que un ojo
-- humano no separa: contra eso no hay restricción que valga.
CREATE UNIQUE INDEX uq_memberships_color ON memberships (color);

COMMENT ON COLUMN memberships.color IS
    'Color del nivel para el frontend: seis dígitos hexadecimales en mayúsculas, sin #.';
