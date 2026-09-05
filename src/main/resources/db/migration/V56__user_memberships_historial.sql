-- =============================================================================
-- RF-SP-024 · T-22 y T-23 — `user_memberships` deja de ser una foto y pasa a
-- ser un HISTORIAL.
--
-- `RN-SP-014` decía «una membresía por usuario» y la declaraba la clave
-- primaria: `user_id`. Pasa a decir «una membresía VIGENTE por usuario, y todas
-- las que tuvo conservadas», y eso la clave primaria ya no lo puede expresar.
--
-- LO QUE SE GANA NO ES UNA COLUMNA, ES UNA RESPUESTA. Hasta hoy, «¿en qué nivel
-- estaba esta persona el 12 de marzo?» no se podía contestar desde `SP`:
-- `RF-SP-032` sustituía con un UPDATE y el nivel anterior desaparecía.
-- `requirements/mv.md` §5.4 llegó a declarar que quien lo necesitara «tendrá
-- que leerlo de las ventas confirmadas» — es decir, que `MV` sostuviera la
-- memoria de `SP`. Esa dependencia invertida se cierra aquí.
--
-- DOS FECHAS DE FIN Y NO UNA, que es la decisión que carga la migración:
--
--   `ends_at`   — hasta cuándo se PAGÓ. Nula, indefinida. No la escribe nadie
--                 al cerrar.
--   `closed_at` — cuándo dejó de ser la actual.
--
-- Una membresía de treinta días reemplazada el día doce termina con las dos
-- puestas y distintas, y LAS DOS SON CIERTAS: se pagó un mes y se usó menos de
-- medio. Con una sola columna se pierde la diferencia entre VENCER y QUE TE LA
-- SUSTITUYAN, que es justo la que responde un reclamo.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1 · Las dos columnas nuevas.
-- -----------------------------------------------------------------------------

ALTER TABLE user_memberships
    ADD COLUMN id        uuid        NULL,
    ADD COLUMN closed_at timestamptz NULL;

COMMENT ON COLUMN user_memberships.closed_at IS
    'Nula = es la fila ABIERTA (la actual). Poblada = historial. NO es una marca de caducado: nadie la escribe por el paso del tiempo, solo al conceder otra o al retirar (RN-SP-014).';
COMMENT ON COLUMN user_memberships.ends_at IS
    'Nula = indefinida. Hasta cuando se PAGO, no cuando se cerro. Retirar NO la toca (RF-SP-033).';


-- -----------------------------------------------------------------------------
-- 2 · `T-23` — el relleno de `id` construye UUID v7 DESDE `started_at`.
--
-- NO ES `gen_random_uuid()`, y hay dos motivos. El Art. V.11 exige v7 y `V3`
-- deja escrito que en este proyecto los identificadores de migración son
-- literales o construidos, NUNCA aleatorios. Y hay uno propio de esta tabla:
-- derivar el identificador de `started_at` deja el historial ORDENADO POR SU
-- CLAVE, que es exactamente lo que un v7 promete y lo que un v4 rompería fila a
-- fila — el primer historial del sistema no debería nacer desordenado.
--
-- La forma del v7: 48 bits de milisegundos desde la época, cuatro bits de
-- versión a 0111, dos bits de variante a 10, y el resto aleatorio.
-- -----------------------------------------------------------------------------

UPDATE user_memberships
   SET id = (
           -- 48 bits de milisegundos, en 12 dígitos hexadecimales.
           lpad(to_hex((extract(epoch FROM started_at) * 1000)::bigint), 12, '0')
           -- Version 7 en el primer nibble del tercer grupo.
        || '7' || substr(md5(random()::text || started_at::text || user_id::text), 1, 3)
           -- Variante RFC 4122: los dos bits altos a 10 -> el nibble cae en 8..b.
        || substr('89ab', 1 + (random() * 3)::int, 1)
        || substr(md5(random()::text || membership_id::text), 1, 3)
        || substr(md5(random()::text || user_id::text || membership_id::text), 1, 12)
       )::uuid
 WHERE id IS NULL;

ALTER TABLE user_memberships ALTER COLUMN id SET NOT NULL;


-- -----------------------------------------------------------------------------
-- 3 · La clave primaria se muda.
--
-- NINGUNA FILA EXISTENTE SE CIERRA. Toda membresía que hoy existe es la actual
-- de su persona: dejarlas con `closed_at` nulo no es una omisión, es que ya son
-- la fila abierta.
-- -----------------------------------------------------------------------------

ALTER TABLE user_memberships DROP CONSTRAINT pk_user_memberships;
ALTER TABLE user_memberships ADD  CONSTRAINT pk_user_memberships PRIMARY KEY (id);


-- -----------------------------------------------------------------------------
-- 4 · `RN-SP-014`, ahora repartida entre DOS restricciones.
--
-- Y NINGUNA DE LAS DOS SOBRA, que es la parte que se piensa mal:
--
--   * El unico parcial NO VE EL HISTORIAL: dos filas cerradas con periodos
--     solapados lo satisfacen sin problema.
--
--   * El EXCLUDE NO VE DOS FILAS ABIERTAS QUE NO SE SOLAPAN: una vencida en
--     enero y otra nueva en marzo no se pisan, y son aun asi dos filas
--     actuales.
--
-- De ahi sale la obligacion que menos se ve y que el codigo tiene que respetar:
-- CONCEDER CIERRA SIEMPRE, aunque la anterior ya estuviera vencida.
-- -----------------------------------------------------------------------------

-- UNA SOLA FILA ABIERTA POR PERSONA. Su segundo trabajo es menos visible y no
-- menor: `RF-SP-025` y `RF-SP-026` cruzan esta tabla con un LEFT JOIN, y sin
-- esta garantia ese cruce REPETIRIA PERSONAS en cuanto alguien tuviera una
-- segunda membresia — y `totalElements` contaria asignaciones en lugar de
-- gente, que es el defecto que aquellas consultas evitan en `user_roles` con
-- EXISTS.
CREATE UNIQUE INDEX uq_user_memberships_abierta
    ON user_memberships (user_id)
 WHERE closed_at IS NULL;

-- DOS PERIODOS NO SE PISAN. `btree_gist` no se declara aqui: `V44` ya la
-- instalo para `ex_commission_rates_sin_solape`, que es este mismo patron y por
-- el mismo motivo — comprobar un solape con un SELECT previo es una carrera.
--
-- `LEAST` da el fin REAL: vence o la cierran, lo que ocurra antes. Ignora los
-- nulos, de modo que devuelve la fecha que haya; el COALESCE cubre que las dos
-- sean nulas, que es la membresia indefinida y viva.
ALTER TABLE user_memberships
    ADD CONSTRAINT ex_user_memberships_sin_solape
    EXCLUDE USING gist (
        user_id WITH =,
        tstzrange(started_at, COALESCE(LEAST(ends_at, closed_at), 'infinity'::timestamptz)) WITH &&
    );

-- `>=` Y NO `>`, a proposito: conceder y cerrar ocurren en la misma transaccion
-- y producen EL MISMO INSTANTE. Con `>` la operacion normal de `RF-SP-032`
-- fallaria cada vez que alguien cambia de nivel dos veces sin que pase tiempo
-- entre medias — en una prueba, siempre.
ALTER TABLE user_memberships
    ADD CONSTRAINT ck_user_memberships_cierre
    CHECK (closed_at IS NULL OR closed_at >= started_at);


-- -----------------------------------------------------------------------------
-- 5 · El indice del filtro por membresia pasa a ser PARCIAL.
--
-- `RF-SP-025` pregunta quienes tienen HOY esa membresia. El historial cerrado
-- nunca forma parte de esa respuesta y creceria indefinidamente dentro del
-- indice. Mismo criterio con el que `ix_user_supervisors_supervisor_vigente` es
-- parcial desde `RF-SP-028`.
-- -----------------------------------------------------------------------------

DROP INDEX IF EXISTS ix_user_memberships_membership_id;

CREATE INDEX ix_user_memberships_membership_id
    ON user_memberships (membership_id)
 WHERE closed_at IS NULL;


COMMENT ON TABLE user_memberships IS
    'HISTORIAL de membresias. Una fila por membresia que alguien tuvo; la abierta (closed_at IS NULL) es la actual y hay como mucho una. Vigente = abierta Y dentro de fecha — no son lo mismo (RN-SP-014).';
