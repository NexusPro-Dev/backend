-- =============================================================================
-- RF-PM-001 · RN-PM-017 — UN UPGRADE PUEDE IR DE UNA MEMBRESIA A SI MISMA.
--
-- Cae `ck_products_origen_distinto`, que exigia
-- `source_membership_id <> target_membership_id`. Eso es EXACTAMENTE lo que la
-- RENOVACION admite (`requirements/pm.md` §5.2.3).
--
-- QUE SE VENDE EN UNA RENOVACION: tiempo, no nivel. Quien esta en ORO y compra
-- `ORO -> ORO` sigue en ORO, con la vigencia que acaba de pagar. `RN-MV-020` lo
-- entrega sin una linea nueva — cierra la membresia abierta e inserta la
-- comprada, y aqui las dos son del mismo nivel.
--
-- LA REGLA TENIA DOS MITADES METIDAS EN UNA: «no bajes» y «no repitas». Solo la
-- primera protegia algo; la segunda impedia cobrar por tiempo, que es un
-- producto legitimo y de los mas comunes que existen.
--
-- =============================================================================
-- LO QUE ESTA MIGRACION DEJA AL DESCUBIERTO, Y HAY QUE LEER ENTERO
-- =============================================================================
--
-- De `RN-PM-017` NO QUEDA NADA DECLARADO EN EL ESQUEMA. Esta restriccion era la
-- unica mitad que el motor podia sostener; la que sobrevive —«el origen no esta
-- por encima del destino»— obliga a leer el `level` de DOS FILAS de
-- `memberships`, y un CHECK no consulta otra tabla: nunca cupo aqui y no va a
-- caber.
--
-- De modo que a partir de hoy una REGLA CRITICA de este modulo vive entera en
-- `RegisterProductService`, SIN RED. Es el mismo reparto que `RN-PM-007` tiene
-- con los decimales de la moneda, con una diferencia que conviene no olvidar:
-- aquel NUNCA tuvo una restriccion detras, y este la pierde.
--
-- Un INSERT directo —una migracion, una correccion a mano— puede meter desde
-- hoy un descenso vendido como upgrade, y nada lo impedira.
--
-- NO SE TOCA `uq_products_upgrade_target`, y no es un olvido: `(FREE, FREE)` es
-- una pareja como cualquier otra. La unicidad sigue siendo UN PRODUCTO ACTIVO
-- POR PAREJA origen->destino, de modo que no pueden coexistir dos renovaciones
-- activas de la misma membresia — que es justo lo que `RN-PM-004` existe para
-- evitar: dos precios simultaneos para lo mismo.
--
-- NO SE EDITA `V53`, que es donde nacio la restriccion: ya esta aplicada, y
-- Flyway valida por suma de comprobacion. Mismo criterio que `V43` sobre `V39`.
-- =============================================================================

ALTER TABLE products DROP CONSTRAINT ck_products_origen_distinto;

COMMENT ON COLUMN products.source_membership_id IS
    'De que membresia sale el upgrade. Obligatoria en UPGRADE_MEMBRESIA y '
    'prohibida en BOT (`RN-PM-002`). NO tiene por que ser la inmediatamente '
    'inferior al destino: saltar niveles es legitimo (`RN-PM-018`). PUEDE SER '
    'LA MISMA que el destino desde el 07-09-2026: entonces el producto es una '
    'RENOVACION y lo que vende es tiempo (`RN-PM-017`).';
