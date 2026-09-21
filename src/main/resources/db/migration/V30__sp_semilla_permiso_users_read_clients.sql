-- ---------------------------------------------------------------------------
-- V30 — El permiso `users:read-clients` de RF-SP-061 (requirements/sp.md §9,
-- security.md §4.4, 21-09-2026).
--
-- Gobierna `GET /users/{id}/clients` —la cartera de un vendedor: a quiénes
-- registró y a quiénes les vendió por hotlink— y nada más (RN-SEG-014). La
-- cartera propia, `GET /users/me/clients`, es alcance sobre uno mismo y no
-- exige permiso. Lo pidió el responsable del proyecto con la razón de fondo:
-- «cada endpoint debe tener un permiso único, con el fin de que el frontend se
-- pueda separar y saber qué vistas mostrar».
--
-- NO ES HIJO DE NADIE: la operación no existía y ningún rol la tenía bajo otro
-- nombre, de modo que no hay padre que repartir como hizo V28. Es la forma de
-- V29: a SUPERADMIN y a ADMIN explícitamente, a CLIENTE no.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 del 21-09-2026 que V29
-- estrenó (`01a0c1432c00`), secuencia 7002, y la serie de SP continuando en
-- hex donde `users:read-sellers` la dejó: …5e7ad0000026. El catálogo pasa de
-- 112 a 113. SIN AUDITORÍA, como V8, V22, V28 y V29.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0c143-2c00-7002-9c4f-5e7ad0000026', 'users:read-clients', 'users', 'read-clients',
 'Consultar los clientes de un vendedor',
 'Ver la cartera de cualquier vendedor —los clientes que registró y los que le compraron por hotlink— por GET /users/{id}/clients. La propia la ve cada vendedor sin permiso (RF-SP-061).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  CROSS JOIN permissions p
 WHERE r.id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',   -- SUPERADMIN
                '01a02a33-4c00-7002-9c4f-5e7ad1000002')   -- ADMIN
   AND p.code = 'users:read-clients';

DO $$
DECLARE
    en_catalogo integer;
    asociadas   integer;
BEGIN
    SELECT count(*) INTO en_catalogo FROM permissions;
    IF en_catalogo <> 113 THEN
        RAISE EXCEPTION 'V30: el catálogo debe quedar en 113 permisos; hay %', en_catalogo;
    END IF;

    SELECT count(*) INTO asociadas
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE p.code = 'users:read-clients';
    IF asociadas <> 2 THEN
        RAISE EXCEPTION 'V30: users:read-clients debe quedar asociado a SUPERADMIN y ADMIN (2 filas); hay %', asociadas;
    END IF;
END $$;
