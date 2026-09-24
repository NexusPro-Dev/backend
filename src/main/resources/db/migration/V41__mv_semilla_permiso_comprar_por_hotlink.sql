-- =============================================================================
-- V41 — Nace `products:buy-by-hotlink` (RF-MV-011, security.md §4.4 v0.73.0,
-- 24-09-2026).
--
-- Comprar un producto POR EL ENLACE de un vendedor: la compra en la que la venta
-- se le acredita a quien repartió el enlace y no a quien ya le vendía a ese
-- cliente (RN-MV-025).
--
-- PROPIO Y NO `products:buy`, y la distinción es el motivo de que exista. Son
-- dos puertas sobre el mismo producto con ATRIBUCIONES distintas, y quien
-- administra roles tiene que poder abrir una sin abrir la otra — con un solo
-- permiso esa decisión no se puede expresar. Es el criterio de RN-SEG-014, el
-- mismo que separó `users:revoke-membership` de `users:assign-membership`.
--
-- SE REPARTE POR TIPO DE ROL Y NO POR LISTA, como V31 con la familia de alcance
-- propio: un rol de consumidor nuevo tiene que poder comprar sin que nadie
-- toque una migración. Y a los tres tipos, no solo a CONSUMIDOR: un vendedor
-- también compra para sí mismo, y negárselo sería inventar una regla que nadie
-- pidió.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 del 24-09-2026 a medianoche
-- UTC —`01a0d0b64000`, la convención que siguen V28 y V29— continuando la serie
-- de `products:` (5e7ad5), donde V31 la dejó: 000026 → 000027.
--
-- SOBRE LA MARCA DE V37, para que no parezca un descuido: aquella
-- (`01a0d7f13800`) cae en el 25-09 y es POSTERIOR a esta aunque su migración sea
-- anterior. El orden de los identificadores no es el de las migraciones y nunca
-- lo fue; lo que Art. V.11 exige es que sean v7 y literales, y las pruebas
-- comprueban eso.
--
-- GUARDA POR CONJUNTO Y NO POR RECUENTO, como V40: el catálogo cambia de tamaño
-- en tres ramas a la vez, y un número fijo aquí fallaría por el trabajo de otro.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0d0b6-4000-7001-9c4f-5e7ad5000027', 'products:buy-by-hotlink', 'products', 'buy-by-hotlink',
 'Comprar un producto por el hotlink de un vendedor',
 'Comprar para uno mismo el producto que llego por el enlace de un vendedor (RF-MV-011). La venta se le acredita a quien reparte el enlace, aunque quien compra tenga otro agente principal (RN-MV-025), y nace el vinculo HOTLINK con ese vendedor. Separado de products:buy porque la atribucion es distinta (RN-SEG-014).');

-- ---------------------------------------------------------------------------
-- El reparto: a todo rol, por su tipo. `ON CONFLICT` por si alguien lo concedió
-- a mano entre dos arranques, que es la misma defensa de V31.
-- ---------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  CROSS JOIN permissions p
 WHERE p.code = 'products:buy-by-hotlink'
   AND r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- ---------------------------------------------------------------------------
-- Guardas.
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    sin_el     integer;
    sin_padre  integer;
BEGIN
    -- TODO rol de sistema lo porta. Es lo que distingue «se reparte por tipo» de
    -- «se le dio a los que habia»: si manana nace un rol y no lo recibe, esta
    -- guarda no lo ve, pero el reparto de arriba si lo alcanzara.
    SELECT count(*) INTO sin_el
      FROM roles r
     WHERE r.is_system = true
       AND NOT EXISTS (SELECT 1 FROM role_permissions rp
                         JOIN permissions p ON p.id = rp.permission_id
                        WHERE rp.role_id = r.id AND p.code = 'products:buy-by-hotlink');
    IF sin_el <> 0 THEN
        RAISE EXCEPTION 'V41: % roles de sistema se quedaron sin products:buy-by-hotlink', sin_el;
    END IF;

    -- Contencion (RN-SEG-003), que el reparto a todos no puede romper pero que
    -- se comprueba igual: es la guarda que convierte un reparto nuevo en seguro.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id
                          AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V41: % asociaciones rompen la contencion de RN-SEG-003', sin_padre;
    END IF;
END $$;
