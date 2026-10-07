-- =============================================================================
-- V82 — La marca de una línea reatribuida (RN-CM-051; RF-CM-024 · T-12;
-- requirements/cm.md v0.37.0 §7.13, modelo-datos.md v0.104.0; 07-10-2026).
--
-- Al corregir el vendedor de una línea, RF-CM-024 borra su cadena vieja y deja
-- aquí una fila; el devengo (RF-CM-013) la lee para llevar la cadena nueva al
-- lote MÁS RECIENTE SIN PAGAR de cada persona —ABIERTO o PENDIENTE— en vez de
-- al abierto, y la borra en cuanto la línea queda DEVENGADA o SIN_COMISION.
--
-- ON DELETE CASCADE, al revés que las otras claves de CM hacia la línea: la
-- marca no es dinero, y no debe obligar a nadie a limpiarla antes que la venta.
-- =============================================================================

CREATE TABLE commission_reattributions (
    movement_detail_id  uuid         NOT NULL,
    released_at         timestamptz  NOT NULL,
    CONSTRAINT pk_commission_reattributions PRIMARY KEY (movement_detail_id),
    CONSTRAINT fk_commission_reattributions_detail FOREIGN KEY (movement_detail_id)
        REFERENCES movement_details (id) ON DELETE CASCADE
);

COMMENT ON TABLE commission_reattributions IS
    'Líneas cuyo vendedor se corrigió y cuya cadena nueva aún no se devengó (RN-CM-051): '
    'su comisión va al lote más reciente sin pagar de cada persona.';
