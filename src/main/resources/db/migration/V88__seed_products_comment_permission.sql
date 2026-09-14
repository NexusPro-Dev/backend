-- =============================================================================
-- Siembra de `products:comment` (`requirements/pm.md` §4, 14-09-2026).
--
-- EL PRIMER PERMISO DE ESCRITURA DE `PM` QUE NO ES DE ADMINISTRACIÓN. Gobierna
-- las tres operaciones sobre LA RESEÑA PROPIA —escribirla, corregirla,
-- retirarla— y la lectura de la propia (`RF-PM-009` a `RF-PM-011`,
-- `RF-PM-013`).
--
-- NO REUTILIZA `products:sale`, por decisión del responsable del proyecto: ver
-- qué se puede comprar y opinar sobre ello son dos capacidades, y quien
-- administre roles tiene que poder conceder una sin la otra.
--
-- HABILITA, NO AUTORIZA (`RN-PM-027`). Quien lo porta escribe LAS SUYAS; tocar
-- una ajena responde `403` aunque lo porte un administrador. Es la
-- verificación de propiedad del dato que security.md §6 deja a la capa de
-- aplicación, y la primera vez que `PM` la ejerce.
--
-- IDENTIFICADOR LITERAL, no generado (Art. V.11), por lo mismo que V40, V48 y
-- V60. UUID v7 con marca de tiempo 2026-09-14T00:00:00Z (01a09d97-2400),
-- versión 7 y variante RFC 9562, y CONTINÚA LA SERIE DE `PM` con el sufijo
-- `5e7ad5`: del `...000001` al `...000006` ya están; este es el SÉPTIMO.
--
-- SE ASOCIA A SUPERADMIN Y A ADMIN EN ESTA MISMA MIGRACIÓN, como security.md
-- §4.4 exige. Sin reserva: el catálogo comercial de `PM` es administración
-- ordinaria desde `V40`, y opinar sobre un producto menos todavía.
--
-- NO SE ASOCIA A `CLIENTE`, por lo mismo que `V48` y `V60`: `V30` siembra ese
-- rol SIN PERMISOS a propósito, y quien administre roles se lo concede a los
-- de tipo CONSUMIDOR por la vía normal, `RF-SP-006`.
--
-- ESTA MIGRACIÓN NO EMITE AUDITORÍA, igual que V3, V40, V48 y V60.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

('01a09d97-2400-7001-9c4f-5e7ad5000007', 'products:comment', 'products', 'comment',
 'Reseñar productos',
 'Escribir, corregir y retirar la reseña propia sobre un producto (RN-PM-025 a RN-PM-029).');


-- -----------------------------------------------------------------------------
-- Asociación a los dos roles de sistema. Por código y no con
-- `SELECT ... FROM permissions`, por lo mismo que V40, V48 y V60: ese atajo
-- asociaría también cualquier permiso sembrado antes que alguien hubiera
-- decidido no conceder.
-- -----------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7001-9c4f-5e7ad1000001', id
  FROM permissions
 WHERE code = 'products:comment';

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7002-9c4f-5e7ad1000002', id
  FROM permissions
 WHERE code = 'products:comment';


-- -----------------------------------------------------------------------------
-- Guarda: si alguna de las dos asociaciones no entró, la migración ABORTA.
-- Olvidar la fila de `ADMIN` no falla al aplicar la migración — deja a `ADMIN`
-- incapaz de conceder lo que no tiene.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE p.code = 'products:comment'
       AND rp.role_id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                          '01a02a33-4c00-7002-9c4f-5e7ad1000002');

    IF filas <> 2 THEN
        RAISE EXCEPTION
            'V88: products:comment debe quedar asociado a SUPERADMIN y a ADMIN; se insertaron %',
            filas;
    END IF;
END $$;
