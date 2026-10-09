-- =============================================================================
-- V93 — El enlace de registro y el nombre en los avisos de cada broker
-- (RF-SP-052 y RF-SP-078 · T-11; requirements/sp.md v1.116.0, RN-SP-069;
-- modelo-datos.md v0.111.0; 09-10-2026).
--
-- url: el enlace de registro del broker, a donde se manda a la persona para
-- abrir su cuenta. Lo publica el catálogo. NULL = todavía no se cargó.
--
-- advertiser: cómo se nombra el broker en sus propios avisos. La dirección
-- común de los avisos (/api/v1/brokers/notifications) reconoce al broker por
-- él. Único sin distinguir mayúsculas: un aviso lleva a UN broker. NULL = no
-- se ha visto un aviso suyo, y ese broker no puede usar la dirección común.
--
-- CARGA: solo IQOPTION = 'iq_option', el valor de sus avisos reales del
-- 09-10-2026. EXNOVA y EXOPTION quedan en NULL: no se inventan.
--
-- SIN AUDITORÍA, como el resto del catálogo, que se puebla por migración
-- (RN-SP-039).
-- =============================================================================

ALTER TABLE brokers
    ADD COLUMN url        varchar(500) NULL,
    ADD COLUMN advertiser varchar(60)  NULL,
    ADD CONSTRAINT ck_brokers_url CHECK (url IS NULL OR url ~ '^https?://');

CREATE UNIQUE INDEX uq_brokers_advertiser ON brokers (lower(advertiser));

COMMENT ON COLUMN brokers.url IS
    'Enlace de registro del broker (RF-SP-052). NULL = no cargado. Lo publica el catálogo.';
COMMENT ON COLUMN brokers.advertiser IS
    'Cómo se nombra el broker en sus avisos (RN-SP-069): la dirección común lo reconoce por él. NULL = no se sabe todavía.';

UPDATE brokers SET advertiser = 'iq_option', updated_at = now()
 WHERE id = '01a081f0-6000-7101-9c4f-5e7adb000001';

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM brokers WHERE id = '01a081f0-6000-7101-9c4f-5e7adb000001')
     AND NOT EXISTS (SELECT 1 FROM brokers
                      WHERE id = '01a081f0-6000-7101-9c4f-5e7adb000001'
                        AND advertiser = 'iq_option') THEN
    RAISE EXCEPTION 'V93: IQOPTION sin su advertiser';
  END IF;
END $$;
