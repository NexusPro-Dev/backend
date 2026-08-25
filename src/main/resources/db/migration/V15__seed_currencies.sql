-- =============================================================================
-- RF-SP-019 · T-02 — Siembra del catálogo de monedas.
--
-- Se siembra UNA SOLA moneda, que es el estado esperado hoy y lo que
-- `requirements/sp.md` §10.5 declara: el catálogo «hoy contiene únicamente
-- USD». No se añade ninguna otra «por si acaso» — una moneda que existe en el
-- catálogo puede seleccionarse, y ofrecer una en la que no se opera es peor que
-- no tenerla. Cuando se opere en otra, será una migración de una fila, que es
-- exactamente para lo que el catálogo existe.
--
-- IDENTIFICADOR UUID v7 LITERAL (Art. V.11), generado una sola vez al redactar
-- esta migración. Debe ser el mismo en todos los entornos para que las pruebas y
-- cualquier dato financiero futuro puedan referenciarlo por constante. Marca de
-- tiempo 2026-08-24T10:00:00Z (01a03336-6d00).
-- =============================================================================

INSERT INTO currencies (id, code, name, symbol, decimal_places, is_default, is_active) VALUES
('01a03336-6d00-7001-9c4f-5e7ad3000001', 'USD', 'Dólar estadounidense', '$', 2, true, true);

-- `decimal_places = 2` es lo que ISO 4217 asigna al dólar estadounidense, y el
-- número no se elige por conveniencia de presentación: mostrar importes con más
-- o menos dígitos es decisión del frontend y es reversible, mientras que
-- redondear AL GUARDAR destruye información que ya no se recupera. Cambiarlo
-- mientras no exista ningún importe es una migración de una fila; después obliga
-- a revisar cada cálculo hecho hasta entonces.


-- -----------------------------------------------------------------------------
-- Auditoría de la siembra
--
-- ESTA MIGRACIÓN SÍ AUDITA, y la diferencia con `V3__seed_permissions.sql` es
-- deliberada. Allí se argumentó que un permiso «no tiene línea de tiempo, porque
-- RN-SP-004 lo hace inmutable por API». Una moneda SÍ la tiene: `RF-SP-023`
-- puede desactivarla, y ese evento aparecería en `RF-SP-011` como el segundo
-- capítulo de una historia cuyo primero faltaría.
--
-- Con actor, correlación e IP en NULL: lo creó el sistema, no una persona
-- (Art. V.15). Mismo criterio que `V7__seed_system_roles.sql`.
-- -----------------------------------------------------------------------------
INSERT INTO audit_change_log (
    id, occurred_at, actor_id, correlation_id, ip_address, user_agent,
    module, entity, entity_id, action, changes
)
SELECT
    '01a03336-6d00-7011-9c4f-5e7ad3000001'::uuid,
    now(),
    NULL, NULL, NULL, NULL,
    'SP',
    'currencies',
    c.id,
    'CREATE',
    jsonb_build_object(
        'code',           c.code,
        'name',           c.name,
        'symbol',         c.symbol,
        'decimal_places', c.decimal_places,
        'is_default',     c.is_default,
        'is_active',      c.is_active
    )
  FROM currencies c
 WHERE c.code = 'USD';
