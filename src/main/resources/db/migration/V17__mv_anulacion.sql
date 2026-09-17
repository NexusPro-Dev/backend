-- ---------------------------------------------------------------------------
-- V17 — anular una venta pendiente deja escrito cuándo y por qué
-- (RF-MV-005, 17-09-2026).
--
-- Anular NO es borrar (RN-MV-001): la fila se queda con su código, sus líneas
-- y sus importes, y desde hoy con el MOTIVO, que es lo que separa «anulada» de
-- «desaparecida». Quien mire la venta dentro de un año lo lee aquí y no en la
-- auditoría. Quinientos caracteres, como el motivo de una eliminación.
--
-- Columnas propias y no un resolved_at genérico para rechazar y anular: un
-- genérico obligaría a decidir hoy si rechazar lleva motivo, y eso es de
-- RF-MV-004. El CHECK ata las dos al estado, como ck_movements_confirmed: una
-- anulada sin motivo o una pendiente con fecha de anulación son estados que el
-- código puede escribir y el negocio no admite.
-- ---------------------------------------------------------------------------

ALTER TABLE movements
    ADD COLUMN voided_at   timestamptz  NULL,
    ADD COLUMN void_reason varchar(500) NULL,
    ADD CONSTRAINT ck_movements_voided
        CHECK ((status = 'ANULADA') = (voided_at IS NOT NULL AND void_reason IS NOT NULL));
