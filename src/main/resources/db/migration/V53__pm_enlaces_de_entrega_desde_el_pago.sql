-- =============================================================================
-- V53 — El comentario de `product_links.type`, con la frontera nueva (28-09-2026).
--
-- V52 lo escribio diciendo que el cupon y la descarga se ven "SOLO en la linea
-- ENTREGADA". El mismo dia la frontera paso al PAGO (RN-MV-032, mv.md v0.51.0):
-- en RF-MV-014 viajan desde que la venta esta CONFIRMADA, para que el cupon
-- sirva para activar el bot. Ningun esquema ni ninguna fila cambia.
-- =============================================================================

COMMENT ON COLUMN product_links.type IS
    'VIDEO_PRESENTACION (el video que presenta el producto, RN-PM-032), CUPON_BOT (donde registra su cuenta quien ya compro) o DESCARGA (donde descarga quien ya compro lo que compro, desde V52). El tipo decide DONDE SE PUBLICA: el video en las cuatro lecturas y el hotlink sin token; el cupon y la descarga SOLO en RF-MV-014 y SOLO desde que la venta esta CONFIRMADA (RN-PM-050, RN-MV-032, desde V53).';
