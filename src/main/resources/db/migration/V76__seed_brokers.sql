-- =============================================================================
-- Los tres primeros brokers del catálogo.
--
-- `RF-SP-052`. Nombres dados por el responsable del proyecto el 08-09-2026:
-- IQOPTION, EXNOVA y EXOPTION. Con esto se cierra el bloqueo 1 de la tripleta —
-- la tabla dejaba de estar vacía en cuanto hubiera una decisión de negocio, y
-- esta es la decisión.
--
-- SE ESCRIBEN COMO SE DIERON. El nombre es la clave de negocio (`RN-SP-039`,
-- §10.17 de `requirements/sp.md`), de modo que la caja con la que entran es la
-- que el catálogo devuelve y la que el desplegable pinta. Cambiarla más
-- adelante es una migración, no una corrección de estilo.
--
-- IDENTIFICADORES LITERALES Y NO GENERADOS (Art. V.11): `user_brokers` va a
-- apuntar a estas filas por `id`, y un identificador distinto en cada entorno
-- haría que una cuenta declarada en desarrollo no se pudiera reproducir en
-- ningún otro. La marca de tiempo del UUID v7 —`01a081f0-6000`— corresponde al
-- 08-09-2026, versión 7 y variante RFC 4122.
--
-- ESTA MIGRACIÓN NO EMITE AUDITORÍA, igual que las demás siembras de catálogo:
-- un broker sembrado no tiene línea de tiempo que reconstruir.
-- =============================================================================

INSERT INTO brokers (id, name) VALUES
('01a081f0-6000-7101-9c4f-5e7adb000001', 'IQOPTION'),
('01a081f0-6000-7102-9c4f-5e7adb000002', 'EXNOVA'),
('01a081f0-6000-7103-9c4f-5e7adb000003', 'EXOPTION');


-- -----------------------------------------------------------------------------
-- Guarda: si el catálogo no quedó con los tres, la migración ABORTA.
--
-- Un catálogo a medias no falla en ningún sitio: deja el desplegable del
-- registro con menos opciones de las que hay, y quien lo mire dará por hecho
-- que el broker que falta no está soportado.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas FROM brokers;

    IF filas <> 3 THEN
        RAISE EXCEPTION
            'V76: se esperaban 3 brokers y hay %. Un catálogo a medias deja el '
            'registro con menos opciones de las que existen.', filas;
    END IF;
END $$;
