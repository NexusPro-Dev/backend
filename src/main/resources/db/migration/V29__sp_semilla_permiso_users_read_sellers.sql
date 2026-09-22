-- ---------------------------------------------------------------------------
-- V29 — El permiso `users:read-sellers` de RF-SP-059 (requirements/sp.md §9,
-- security.md §4.4, 21-09-2026).
--
-- RF-SP-059 se construyó el 18-09-2026 en su propia rama con `users:read`
-- sobre `GET /users/{id}/sellers`, un día ANTES de que RF-SP-060 decidiera un
-- permiso por operación (RN-SEG-014). V28 no lo sembró porque en su rama la
-- ruta no existía; su spec §6.2 dejó dicho que RF-SP-059 «nacerá con
-- users:read-sellers», y esta migración cumple el anuncio al integrar la rama.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la serie de SP continúa donde V28 la
-- dejó —`users:read-team` fue …5e7ad0000024—, en hex como V8: …5e7ad0000025.
-- `01a0c1432c00` es la marca v7 del 21-09-2026.
--
-- Se asocia a SUPERADMIN y a ADMIN EXPLÍCITAMENTE (la lección de V19: V8
-- asocia a ADMIN por exclusión solo sobre el catálogo de aquel día); a CLIENTE
-- no. No hay padre que repartir: `users:read` no gobernaba esta operación en
-- ningún entorno desplegado, de modo que ningún rol pierde nada. El catálogo
-- pasa de 111 a 112. SIN AUDITORÍA, como V8, V22 y V28.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0c143-2c00-7001-9c4f-5e7ad0000025', 'users:read-sellers', 'users', 'read-sellers',
 'Consultar los vendedores de un cliente',
 'Ver los vendedores de cualquier cliente —su principal y los vinculados por hotlink— por GET /users/{id}/sellers. Los propios los ve cada cliente sin permiso (RF-SP-059).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  CROSS JOIN permissions p
 WHERE r.id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',   -- SUPERADMIN
                '01a02a33-4c00-7002-9c4f-5e7ad1000002')   -- ADMIN
   AND p.code = 'users:read-sellers';

DO $$
DECLARE
    en_catalogo integer;
    asociadas   integer;
BEGIN
    SELECT count(*) INTO en_catalogo FROM permissions;
    IF en_catalogo <> 112 THEN
        RAISE EXCEPTION 'V29: el catálogo debe quedar en 112 permisos; hay %', en_catalogo;
    END IF;

    SELECT count(*) INTO asociadas
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE p.code = 'users:read-sellers';
    IF asociadas <> 2 THEN
        RAISE EXCEPTION 'V29: users:read-sellers debe quedar asociado a SUPERADMIN y ADMIN (2 filas); hay %', asociadas;
    END IF;
END $$;
