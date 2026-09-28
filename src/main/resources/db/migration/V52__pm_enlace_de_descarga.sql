-- =============================================================================
-- V52 — Un tercer tipo de enlace: DESCARGA (PM, 28-09-2026).
--
-- Donde descarga quien compro lo que compro (pm.md §5.2.15). Es ENTREGA y no
-- material de venta, como CUPON_BOT: no sale de la oferta ni de los hotlinks, y
-- en RF-MV-014 solo con la linea entregada (RN-PM-050, RN-MV-032).
--
-- Solo se reescribe el CHECK y el comentario: el CHECK y el enumerado
-- `ProductLinkType` admiten EXACTAMENTE lo mismo, a proposito. Ninguna fila
-- cambia.
-- =============================================================================

ALTER TABLE product_links DROP CONSTRAINT ck_product_links_type;

ALTER TABLE product_links ADD CONSTRAINT ck_product_links_type
    CHECK (type IN ('VIDEO_PRESENTACION', 'CUPON_BOT', 'DESCARGA'));

COMMENT ON COLUMN product_links.type IS
    'VIDEO_PRESENTACION (el video que presenta el producto, RN-PM-032), CUPON_BOT (donde registra su cuenta quien ya compro) o DESCARGA (donde descarga quien ya compro lo que compro, desde V52). El tipo decide DONDE SE PUBLICA: el video en las cuatro lecturas y el hotlink sin token; el cupon y la descarga SOLO en RF-MV-014 y SOLO en la linea ENTREGADA (RN-PM-050).';
