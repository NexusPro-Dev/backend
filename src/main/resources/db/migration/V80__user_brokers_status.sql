-- =============================================================================
-- `user_brokers.status` — en qué punto está una cuenta de broker.
--
-- `RF-SP-055` · `T-01`. Decisión del responsable del proyecto del 10-09-2026:
-- una cuenta vive en `REGISTER` —declarada, sin depósito confirmado— o en
-- `FIRST_DEPOSIT` —el primer depósito está confirmado— (`RN-SP-045`).
--
-- SE NUMERÓ COMPROBANDO EL MÁXIMO APLICADO, que era `V79`. La `V62` y la `V67`
-- se planificaron y las tomó otra rama el mismo día, dos veces en dos días.
--
-- LOS DOS VALORES VAN EN INGLÉS y el resto de enumerados del sistema no
-- —`users.status` es `ACTIVO | INACTIVO | BLOQUEADO | FTD_PENDIENTE`,
-- `products.status` y `movements.status` van igual—. No es un descuido: son EL
-- VOCABULARIO DEL BROKER, y quien va a escribir esta columna es la integración
-- con él (`RF-SP-054`). Traducirlos aquí obligaría a mantener un diccionario
-- entre lo que llega por el webhook y lo que se guarda.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- NOT NULL CON DEFAULT, y el `DEFAULT` es lo que hace ciertas las filas viejas.
--
-- `RF-SP-045` lleva declarando cuentas desde el 09-09-2026 y NINGUNA de ellas
-- tiene depósito confirmado —el webhook que lo confirmaría no existe—, de modo
-- que `REGISTER` no es un relleno: es su valor correcto.
--
-- Una columna anulable dejaría un TERCER estado —«no se sabe»— que `RN-SP-045`
-- no admite y que cada lector tendría que decidir cómo pintar. Es el defecto
-- contrario al de `broker_username`, donde el nulo SÍ significa algo y por eso
-- se admite.
-- -----------------------------------------------------------------------------
ALTER TABLE user_brokers
    ADD COLUMN status varchar(20) NOT NULL DEFAULT 'REGISTER';

-- -----------------------------------------------------------------------------
-- EL CONJUNTO DE VALORES VIVE EN EL MOTOR (Art. V.6), como `ck_users_status`.
--
-- El enumerado de Java gobierna lo que entra por la API, y no lo que entra por
-- una migración de datos o por una corrección a mano: sin este `CHECK`, un
-- `register` en minúscula se guardaría sin que nada fallara y las consultas
-- filtradas por estado dejarían de verlo, en silencio.
-- -----------------------------------------------------------------------------
ALTER TABLE user_brokers
    ADD CONSTRAINT ck_user_brokers_status
    CHECK (status IN ('REGISTER', 'FIRST_DEPOSIT'));

COMMENT ON COLUMN user_brokers.status IS
    'REGISTER | FIRST_DEPOSIT (RN-SP-045). Nace en REGISTER; lo mueve el webhook de RF-SP-054, que no existe todavía.';
