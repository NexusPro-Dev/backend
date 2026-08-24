-- =============================================================================
-- RF-SP-038 · T-01 — Caducidad de la credencial provisional.
--
-- NUMERACIÓN: el plan reservaba `V28`, número que ya está ocupado. Se toma el
-- siguiente libre; la reserva por requerimiento quedó muerta el 24-08-2026
-- (ver `V28`).
--
-- POR QUÉ EXISTE ESTA COLUMNA. Sin ella, una cuenta restablecida y nunca usada
-- conserva **indefinidamente** una credencial que otra persona conoce, y nadie
-- se entera porque no falla nada: la cuenta sigue activa, el registro dice que
-- se restableció, y la contraseña que alguien escribió en un papel sigue
-- abriendo la puerta meses después. La caducidad convierte ese estado silencioso
-- en un rechazo al iniciar sesión (`security.md` §3.2).
-- =============================================================================

ALTER TABLE users
    ADD COLUMN provisional_password_expires_at timestamptz NULL;

COMMENT ON COLUMN users.provisional_password_expires_at IS
    'Hasta cuándo vale la credencial que fijó otra persona (RF-SP-038). Nula = la credencial es del titular.';

-- -----------------------------------------------------------------------------
-- La caducidad va ATADA a la marca de cambio obligatorio
--
-- Las dos describen el mismo hecho —«esta contraseña la puso alguien que no es
-- su titular»— y separarlas admitiría dos estados que no significan nada: una
-- caducidad sobre una credencial propia, y una credencial ajena que no caduca.
--
-- La restricción es lo que impide que un camino futuro deje uno de los dos
-- valores sin el otro. Es barata: la comprobación es local a la fila.
-- -----------------------------------------------------------------------------
ALTER TABLE users
    ADD CONSTRAINT ck_users_provisional_expiry
    CHECK (provisional_password_expires_at IS NULL OR must_change_password);
