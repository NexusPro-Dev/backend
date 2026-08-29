-- =============================================================================
-- RF-SP-016 · T-20 — Siembra del catálogo de membresías.
--
-- Los CUATRO NIVELES COMERCIALES, por decisión del responsable del proyecto:
-- Free, VIP, Platino y Oro, en ese orden y con ORO ARRIBA.
--
-- POR QUÉ ESTO ES UNA MIGRACIÓN Y NO UN SCRIPT DE DESARROLLO. Es la misma
-- pregunta que separó `V15` —las monedas— de cualquier dato de prueba: estos
-- cuatro niveles son **catálogo del negocio**, no fixtures. Existen en todos los
-- entornos, nadie tiene que crearlos a mano en producción, y `PM` no puede
-- vender un upgrade hacia un nivel que no exista. Las personas de prueba, en
-- cambio, viven en `scripts/semilla-desarrollo.sql` y NO llegan a ningún
-- entorno desplegado.
--
-- IDENTIFICADORES UUID v7 LITERALES (Art. V.11), generados una sola vez al
-- redactar esta migración, con marca de tiempo 2026-08-29T00:00:00Z
-- (01a04ad0-e800), versión 7 y variante RFC 9562. Deben ser los mismos en todos
-- los entornos para que las pruebas los referencien por constante — mismo
-- criterio que `V3`, `V7`, `V15`, `V40` y `V45`.
--
-- EL NIVEL EMPIEZA EN 1 Y NO EN 0: `ck_memberships_level_positive` exige
-- `level >= 1`. No es una elección de esta migración, es del esquema.
--
-- LA CADENA ES LINEAL Y LO OBLIGA EL ESQUEMA: `uq_memberships_parent` impide
-- que dos membresías declaren el mismo padre, de modo que esto es una escalera
-- y no un árbol. Free no tiene padre porque es el suelo.
--
-- EL ORDEN NO ES COSMÉTICO. `level` decide hacia dónde se puede subir:
-- `RF-PM-007` ofrece a cada persona solo los upgrades cuyo destino está POR
-- ENCIMA de su nivel vigente. Cambiar este orden después de vender un upgrade
-- reescribiría qué compró quien lo compró, y por eso se fija aquí y con
-- identificadores estables.
--
-- LOS COLORES son los seis dígitos hexadecimales SIN `#` que `RN-SP-024` exige,
-- en mayúsculas, y son ÚNICOS por `uq_memberships_color`. Se eligen distinguibles
-- entre sí: un gris neutro para el nivel gratuito y tres con identidad propia.
-- =============================================================================

INSERT INTO memberships (id, code, name, description, parent_membership_id, level, color) VALUES

('01a04ad0-e800-7001-9c4f-5e7ad7000001', 'FREE', 'Free',
 'Nivel de entrada, sin costo. Es el suelo de la cadena y no tiene padre.',
 NULL, 1, '9E9E9E'),

('01a04ad0-e800-7002-9c4f-5e7ad7000002', 'VIP', 'VIP',
 'Primer nivel de pago.',
 '01a04ad0-e800-7001-9c4f-5e7ad7000001', 2, '7E57C2'),

('01a04ad0-e800-7003-9c4f-5e7ad7000003', 'PLATINO', 'Platino',
 'Nivel intermedio.',
 '01a04ad0-e800-7002-9c4f-5e7ad7000002', 3, 'B0BEC5'),

('01a04ad0-e800-7004-9c4f-5e7ad7000004', 'ORO', 'Oro',
 'Nivel más alto de la cadena.',
 '01a04ad0-e800-7003-9c4f-5e7ad7000003', 4, 'FFB300');


-- ESTA MIGRACIÓN NO EMITE AUDITORÍA, igual que `V3`, `V15` y `V40`: el catálogo
-- nace con el sistema y no tiene una línea de tiempo que reconstruir. Las
-- membresías que se registren después por `RF-SP-016` sí la emiten.
