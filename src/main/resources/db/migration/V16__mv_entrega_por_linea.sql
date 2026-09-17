-- ---------------------------------------------------------------------------
-- V16 — la entrega es de la LÍNEA, y la implementación se copia por fin
-- (RN-MV-030 nueva, RN-MV-029 nueva, 17-09-2026, RF-MV-003).
--
-- Decisión del responsable del proyecto. Confirmar el pago de una venta
-- (RF-MV-003) tiene que ENTREGAR lo comprado, y los productos de una misma
-- venta no se entregan igual: un upgrade automático concede la membresía en el
-- acto, un bot manual espera a que alguien lo autorice (RF-MV-010), y un
-- upgrade que bajaría de nivel NO se entrega (RN-MV-029). Un quinto estado de
-- `movements` —«confirmada a medias»— decidiría por toda la venta lo que el
-- dato solo permite decidir por línea; por eso el estado vive AQUÍ.
--
-- `implementation` es la copia que mv.md §5.4 exigía desde el 07-09-2026:
-- decide si confirmar entrega, y RF-PM-004 la corrige, de modo que se copia
-- como el precio (RN-MV-002). Las ventas anteriores a esta migración se
-- registraron sin la copia; darles el valor que el producto tiene HOY es
-- asumir que no cambió, que es lo que se asume de toda copia que nace tarde
-- (V14 hizo lo mismo con el nombre). Por eso se añade nula, se rellena y
-- DESPUÉS se declara NOT NULL.
--
-- `delivery_status` es la ÚNICA columna de esta tabla que cambia después de
-- escribirse, y es la excepción acotada a RN-MV-001: PENDIENTE → ENTREGADA o
-- RETENIDA, y de ahí no se sale (lo sostiene el caso de uso; el esquema ata la
-- fecha a la entrega y el motivo a la retención, como ck_movements_confirmed).
-- El DEFAULT es lo que deja válidas las líneas existentes y las que las tres
-- entradas escriben sin tocarlas: nada se entrega antes de confirmar.
-- ---------------------------------------------------------------------------

ALTER TABLE movement_details
    ADD COLUMN implementation  varchar(20)  NULL,
    ADD COLUMN delivery_status varchar(20)  NOT NULL DEFAULT 'PENDIENTE',
    ADD COLUMN delivered_at    timestamptz  NULL,
    ADD COLUMN delivery_note   varchar(200) NULL;

UPDATE movement_details d
   SET implementation = p.implementation
  FROM products p
 WHERE p.id = d.product_id
   AND d.implementation IS NULL;

ALTER TABLE movement_details
    ALTER COLUMN implementation SET NOT NULL,
    ADD CONSTRAINT ck_movement_details_implementation
        CHECK (implementation IN ('AUTOMATICA', 'MANUAL')),
    ADD CONSTRAINT ck_movement_details_delivery_status
        CHECK (delivery_status IN ('PENDIENTE', 'ENTREGADA', 'RETENIDA')),
    ADD CONSTRAINT ck_movement_details_delivery
        CHECK ((delivery_status = 'ENTREGADA') = (delivered_at IS NOT NULL)
           AND (delivery_status = 'RETENIDA')  = (delivery_note IS NOT NULL));
