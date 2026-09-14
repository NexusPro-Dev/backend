-- =============================================================================
-- RF-MV-009 · T-01 — DONDE NO VALE CADA METODO DE PAGO (`RN-MV-019`).
--
-- No todos los medios operan en todas partes: `PSE` es colombiano y no
-- significa nada en Mexico. Esta tabla declara las exclusiones y
-- `GET /api/v1/payment-methods` las publica.
--
-- LA RESTRICCION SE PUBLICA Y NO SE COMPRUEBA, y es la decision con mas
-- consecuencias de este requerimiento — del responsable del proyecto, el
-- 04-09-2026. Registrar una venta NO mira el pais: una venta con un metodo
-- excluido SE REGISTRA CON NORMALIDAD. Quien filtra es el cliente que consume
-- el catalogo.
--
-- QUIEN LEA ESTO BUSCANDO DONDE SE VALIDA, QUE NO SIGA BUSCANDO. No se valida.
-- Comprobarlo en el servidor exigiria antes decidir DE QUE PAIS SE TRATA, y hoy
-- nadie tiene pais: `users` no lo guarda (requirements/mv.md §5.3). Esa
-- pregunta sigue abierta y esta decision la aplaza en lugar de resolverla.
--
-- DECLARA LA EXCLUSION Y NO EL PERMISO, que es la postura CONTRARIA a la que
-- `RN-CM-012` tomo con las tasas —donde la ausencia significa «ninguno»—. Aqui
-- un metodo SIN FILAS VALE EN TODOS LOS PAISES, de modo que los tres sembrados
-- por V54 no exigen declarar nada y anadir un pais no obliga a revisar el
-- catalogo de medios. Lo que se paga a cambio: OLVIDAR UNA EXCLUSION NO FALLA,
-- OFRECE. Se acepta porque la lista de paises crece sola y la de metodos no, y
-- porque esto no bloquea un cobro — solo pinta un selector.
--
-- ESTA MIGRACION NO SIEMBRA NADA, y no es un olvido. Los tres metodos de hoy
-- valen en todos los paises, y `RN-MV-019` dice que la ausencia de filas
-- significa exactamente eso. Sembrar exclusiones de ejemplo seria inventar una
-- regla de negocio que nadie ha declarado.
--
-- NO EMITE AUDITORIA, igual que V54: esta tabla no se administra por API
-- todavia (mv.md §5.3), y lo que no cambia por API no tiene linea de tiempo que
-- reconstruir. El dia que haya pantalla de administracion habra que decidir si
-- retirar una exclusion es auditable; hoy no hay operacion que auditar.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- ES LA PRIMERA CLAVE FORANEA QUE ENTRA A `countries`
--
-- Ese catalogo existe desde `V16` y en veinte dias NO LO HABIA REFERENCIADO NI
-- UNA TABLA: se consultaba para pintar selectores y nada mas.
-- `modelo-datos.md` §6 lo tenia anotado como observacion abierta —«countries y
-- currencies son islas»— y se cierra aqui. Curiosamente, no entro por donde esa
-- observacion lo esperaba —«direcciones con pais»— sino por donde NO se puede
-- pagar.
--
-- LOS DOS BORRADOS NO SON SIMETRICOS, y es deliberado:
--
--   payment_methods -> CASCADE   una exclusion no significa nada sin su
--                                metodo. Si el metodo desapareciera, la fila
--                                sobraria.
--
--   countries       -> RESTRICT  un pais NO SE BORRA NUNCA: `RF-SP-022` cambia
--                                `is_active`, no elimina. El RESTRICT esta para
--                                que un DELETE hecho a mano no se lleve por
--                                delante una restriccion que alguien declaro.
--
-- Y POR ESO ESTA TABLA NO PUEDE ROMPER `RF-SP-022`: desactivar un pais es un
-- UPDATE, y una clave foranea no mira los UPDATE de columnas que no referencia.
-- La suite de paises tiene que seguir en verde sin un solo cambio.
-- -----------------------------------------------------------------------------
CREATE TABLE payment_method_exclusions (
    payment_method_id uuid        NOT NULL,
    country_id        uuid        NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),

    -- CLAVE PRIMARIA COMPUESTA, excepcion al Art. V.11 por lo mismo que
    -- `role_permissions`: la fila ES la relacion y no tiene identidad propia
    -- que valga la pena nombrar. Cierra ademas «un metodo no se excluye dos
    -- veces del mismo pais» sin que ninguna operacion tenga que comprobarlo.
    CONSTRAINT pk_payment_method_exclusions
        PRIMARY KEY (payment_method_id, country_id),

    CONSTRAINT fk_payment_method_exclusions_method
        FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id) ON DELETE CASCADE,

    CONSTRAINT fk_payment_method_exclusions_country
        FOREIGN KEY (country_id) REFERENCES countries (id) ON DELETE RESTRICT
);

COMMENT ON TABLE payment_method_exclusions IS
    'RN-MV-019: donde NO vale cada metodo de pago. SE PUBLICA Y NO SE COMPRUEBA: registrar una venta no mira el pais.';

COMMENT ON COLUMN payment_method_exclusions.country_id IS
    'Primera clave foranea entrante de `countries` (04-09-2026). Un metodo sin filas vale en todos los paises.';

-- NINGUN INDICE ADICIONAL. La clave primaria ya cubre la busqueda por metodo,
-- que es la unica que `RF-MV-009` hace; la busqueda por pais no la hace nadie
-- todavia, y anadirla por adelantado cuesta en cada escritura sin ahorrar en
-- ninguna lectura.
