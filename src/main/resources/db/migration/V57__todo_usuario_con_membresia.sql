-- =============================================================================
-- RF-SP-024 · T-33 — TODA PERSONA TIENE MEMBRESIA.
--
-- `RN-SP-018` decia «todo CONSUMIDOR tiene membresia» y pasa a decir «todo
-- USUARIO la tiene». Quien no recibe una al registrarse arranca en la mas baja.
-- Decision del responsable del proyecto.
--
-- MUEREN DOS REGLAS CRITICAS CON ELLA, y conviene saberlo al leer esta
-- migracion: `RN-SP-013` —membresia solo para consumidores— y `RN-SP-015`
-- —quedarse sin rol consumidor retira la membresia—. Sostenian las dos mitades
-- de una atadura entre el rol y el nivel que ya no existe.
--
-- ESTA MIGRACION RELLENA Y NO ALTERA NADA. Ni una columna, ni una restriccion.
--
-- Y NO PORQUE FALTE GANAS: el invariante NO ES EXPRESABLE EN EL ESQUEMA. «Toda
-- fila de `users` tiene una fila abierta en `user_memberships`» es una
-- comprobacion ENTRE TABLAS que ningun CHECK alcanza, y la clave foranea
-- inversa no existe porque la fila de la membresia nace DESPUES que la persona.
-- Lo sostienen este relleno y las tres operaciones que crean personas. Queda
-- escrito porque el Art. V.6 empuja a intentarlo y conviene no perder el rato.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- EL SUELO SE RESUELVE POR `code = 'FREE'`, Y NO POR LA FORMA DE LA CADENA.
--
-- La tentacion es `parent_membership_id IS NULL` —la que no tiene padre—, y es
-- un blanco movil: `RN-SP-007` permite REGISTRAR UNA POR DEBAJO de Free, y
-- entonces el suelo se mueve y con el cambiaria, EN SILENCIO, el nivel con el
-- que arranca todo el mundo.
--
-- El codigo no se mueve: `uq_memberships_code` lo hace unico, `RN-SP-008`
-- impide borrar la fila y `V46` la siembra en TODOS los entornos.
--
-- EL PRECIO QUEDA ESCRITO: si alguien registra un nivel por debajo, el SUELO DE
-- LA CADENA y el NIVEL DE ARRANQUE dejan de ser el mismo. Es a proposito.
-- -----------------------------------------------------------------------------

-- Guarda: sin `FREE` esta migracion no rellenaria nada Y NO FALLARIA, que es la
-- peor de las dos salidas — el sistema arrancaria con el invariante roto y
-- nadie se enteraria hasta que alguien consultara un perfil.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM memberships WHERE code = 'FREE') THEN
        RAISE EXCEPTION
            'V57 exige la membresia de codigo FREE, que V46 siembra. No existe: %',
            'revise que V46 se haya aplicado antes que esta migracion';
    END IF;
END
$$;


-- -----------------------------------------------------------------------------
-- El relleno.
--
-- ALCANZA TAMBIEN A LAS PERSONAS ELIMINADAS, y es deliberado: `users` conserva
-- su fila tras el borrado logico, y dejarlas fuera obligaria a que toda
-- consulta futura del invariante llevara `AND deleted_at IS NULL` — una
-- excepcion que se olvida. Darles Free no les concede nada: una cuenta
-- eliminada no autentica.
--
-- Y ALCANZA AL SUPERADMINISTRADOR sembrado en `V22`, que hasta hoy no tenia
-- ninguna. No se corrige `V22` en el sitio: esta aplicada, Flyway la valida por
-- suma de comprobacion y editarla haria fallar el arranque de toda base
-- existente. Ademas no PODRIA hacerlo aunque se editara — las membresias no
-- existen hasta `V46`.
--
-- El `id` se construye como en `V56`: UUID v7 con el prefijo temporal de
-- `now()` (Art. V.11), no `gen_random_uuid()`.
-- -----------------------------------------------------------------------------

INSERT INTO user_memberships (id, user_id, membership_id, started_at, created_at, updated_at)
SELECT (
           lpad(to_hex((extract(epoch FROM now()) * 1000)::bigint), 12, '0')
        || '7' || substr(md5(random()::text || u.id::text), 1, 3)
        || substr('89ab', 1 + (random() * 3)::int, 1)
        || substr(md5(random()::text || m.id::text), 1, 3)
        || substr(md5(random()::text || u.id::text || m.id::text), 1, 12)
       )::uuid,
       u.id, m.id, now(), now(), now()
  FROM users u
  CROSS JOIN memberships m
 WHERE m.code = 'FREE'
   AND NOT EXISTS (SELECT 1 FROM user_memberships um
                    WHERE um.user_id = u.id AND um.closed_at IS NULL);


-- Guarda de salida. Si algo dejo a alguien fuera, ES AHORA cuando hay que
-- saberlo: el invariante que este relleno establece no lo sostiene ninguna
-- restriccion, de modo que si nace roto nada volvera a comprobarlo.
DO $$
DECLARE
    sin_nivel bigint;
BEGIN
    SELECT count(*) INTO sin_nivel
      FROM users u
     WHERE NOT EXISTS (SELECT 1 FROM user_memberships um
                        WHERE um.user_id = u.id AND um.closed_at IS NULL);

    IF sin_nivel > 0 THEN
        RAISE EXCEPTION 'V57 dejo % persona(s) sin membresia abierta (RN-SP-018)', sin_nivel;
    END IF;
END
$$;
