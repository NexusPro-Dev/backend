-- =============================================================================
-- V60 — Pagar varios lotes de una vez (RF-CM-025; requirements/cm.md v0.28.0
-- §6, RN-CM-049; 01-10-2026).
--
-- Solo el permiso: pagar varios es pagar cada uno con RF-CM-011, y no hay
-- esquema nuevo.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 de V59 —`01a0ef9c6800`—,
-- continuando la serie de permisos de CM: 000030 → 000031.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0ef9c-6800-7014-9c4f-5e7ad6000031', 'commission-batches:pay-batches',
 'commission-batches', 'pay-batches',
 'Pagar varios lotes de comisión',
 'Pagar de una vez los lotes PENDIENTES que se elijan, cada uno por su cuenta y abonado en la billetera de su persona, por POST /commission-batches/payments (RF-CM-025).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
               ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r (id)
  CROSS JOIN permissions p
 WHERE p.id = '01a0ef9c-6800-7014-9c4f-5e7ad6000031'
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;
