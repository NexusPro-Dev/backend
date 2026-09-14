-- =============================================================================
-- RF-SP-024 · T-53 — Identidad documental y datos de contacto (`RN-SP-035`,
-- `RN-SP-037`).
--
-- `users` gana seis columnas: el tipo y el número de documento, dos líneas de
-- dirección, la ciudad y el teléfono.
--
-- DEPENDE DE `V70`, que crea y siembra `document_types`: la clave foránea de
-- abajo apunta a una tabla que aquella migración crea.
--
-- -----------------------------------------------------------------------------
-- LAS SEIS COLUMNAS NACEN NULABLES, Y DOS DE ELLAS SON OBLIGATORIAS EN LA API.
-- Es la asimetría con `country_id` (`V64`), que sí nació `NOT NULL`, y la
-- diferencia está en QUÉ SIGNIFICA EL RELLENO.
--
-- Un país de relleno es una afirmación NEUTRA: poner «Colombia» al
-- superadministrador no dice nada falso sobre nadie. Un NÚMERO DE DOCUMENTO de
-- relleno es otra cosa — es una afirmación sobre la IDENTIDAD de una persona, y
-- CUALQUIER VALOR QUE SE INVENTE ES FALSO. Lo mismo el teléfono.
--
-- `V22` siembra un superadministrador que no tiene ni uno ni otro, y la semilla
-- de desarrollo crea decenas de personas que tampoco. De modo que el esquema
-- ADMITE LA AUSENCIA —que es la verdad sobre esas filas— y LA API LA PROHÍBE en
-- toda alta nueva (`RN-SP-035`, `RN-SP-037`).
--
-- LA CONSECUENCIA SE ACEPTA ENTERA: existen y seguirán existiendo personas sin
-- documento, y todo consumidor tiene que contemplarlo. Lo que no puede ocurrir
-- es que se creen más.
--
-- LA CONDICIÓN PARA ENDURECERLO QUEDA ESCRITA: el día que ninguna fila tenga el
-- documento nulo —porque se completaron todas—, una migración puede poner
-- `NOT NULL`. Antes no, y no por prudencia: hacerlo obligaría a inventar los
-- datos que faltan.
-- =============================================================================

ALTER TABLE users
    ADD COLUMN document_type_id uuid,
    ADD COLUMN document_number  varchar(30),
    ADD COLUMN address_line1    varchar(150),
    ADD COLUMN address_line2    varchar(150),
    ADD COLUMN city             varchar(100),
    ADD COLUMN phone            varchar(20);

-- -----------------------------------------------------------------------------
-- La clave foránea, que es DONDE VIVE LA VALIDACIÓN DE MAYORÍA DE EDAD.
--
-- El catálogo de `V70` no ofrece documentos de menor, y esto es lo que impide
-- apuntar a uno. No hay comprobación de edad en ningún caso de uso porque no
-- hay nada que comprobar.
--
-- SIN `ON DELETE` DE NINGÚN TIPO, como `fk_users_country`: `RN-SP-036` no
-- admite borrar una fila del catálogo, de modo que no hay borrado del que
-- defenderse. Declarar `RESTRICT` sugeriría que existe uno.
-- -----------------------------------------------------------------------------
ALTER TABLE users
    ADD CONSTRAINT fk_users_document_type
        FOREIGN KEY (document_type_id) REFERENCES document_types (id);

-- -----------------------------------------------------------------------------
-- EL TIPO Y EL NÚMERO VAN JUNTOS O NO VAN.
--
-- Es la restricción que más fácil se olvida y la que más barata sale. Con las
-- dos columnas nulables, ES LO ÚNICO QUE IMPIDE MEDIA IDENTIDAD: un número sin
-- decir de qué documento es, y un tipo sin número. Ninguno de los dos estados
-- significa nada, y los dos pasarían el `NOT NULL` que estas columnas no
-- tienen.
-- -----------------------------------------------------------------------------
ALTER TABLE users
    ADD CONSTRAINT ck_users_document_pair
        CHECK ((document_type_id IS NULL) = (document_number IS NULL));

-- El documento se persiste NORMALIZADO —recortado y en mayúsculas—, con el
-- mismo criterio que el correo y por lo mismo: sin esto, `uq_users_document`
-- deja de significar lo que dice en cuanto entre un `abc123` por INSERT
-- directo, y `abc123` y `ABC123` serían dos personas distintas.
ALTER TABLE users
    ADD CONSTRAINT ck_users_document_number_normalized
        CHECK (document_number IS NULL OR document_number = upper(btrim(document_number)));

-- Comprobación de FORMA MÍNIMA, no una validación de documento: los formatos
-- reales dependen del país y del tipo, y una validación a medias rechazaría
-- documentos legítimos. Alfanumérico, admite punto y guion, sin espacios.
ALTER TABLE users
    ADD CONSTRAINT ck_users_document_number_format
        CHECK (document_number IS NULL OR document_number ~ '^[A-Z0-9][A-Z0-9.-]{2,29}$');

-- Dígitos con un `+` opcional. Quince es el máximo de E.164; el número se
-- persiste ya normalizado, sin espacios, guiones ni paréntesis.
--
-- NO SE VALIDA CONTRA EL PAÍS: eso exigiría un catálogo de prefijos que nadie
-- ha pedido, y una validación a medias rechazaría números legítimos.
ALTER TABLE users
    ADD CONSTRAINT ck_users_phone_format
        CHECK (phone IS NULL OR phone ~ '^\+?[0-9]{7,15}$');

-- Los tres campos de dirección son OPCIONALES, de modo que el nulo es legítimo;
-- lo que no lo es son los que vienen y vienen en blanco. Un campo de un solo
-- espacio pasa el `NOT NULL` que no tiene y ensucia la ficha sin que nada falle.
ALTER TABLE users
    ADD CONSTRAINT ck_users_contacto_not_blank
        CHECK ((address_line1 IS NULL OR length(btrim(address_line1)) > 0)
           AND (address_line2 IS NULL OR length(btrim(address_line2)) > 0)
           AND (city          IS NULL OR length(btrim(city))          > 0));

-- -----------------------------------------------------------------------------
-- LA UNICIDAD DEL DOCUMENTO.
--
-- PARCIAL SOBRE EL NULO Y **TOTAL** SOBRE LOS ELIMINADOS, y la segunda mitad es
-- la que importa: no lleva `deleted_at IS NULL`. `RN-SP-035` NO LIBERA el
-- documento al eliminar, con el mismo criterio que `uq_users_username` —
-- reutilizarlo permitiría que la actividad de dos personas quedara bajo la
-- misma identidad en la auditoría.
--
-- El `WHERE document_number IS NOT NULL` NO ES UNA EXCEPCIÓN A LA REGLA: es lo
-- que permite que convivan las filas anteriores a esta migración. Sin él, dos
-- personas sin documento colisionarían entre sí — PostgreSQL trata los nulos
-- como distintos en un UNIQUE, sí, pero la pareja `(NULL, NULL)` repetida es
-- exactamente el caso en que ese comportamiento salva por accidente y no por
-- diseño. Declararlo parcial lo hace explícito.
--
-- LA UNICIDAD VA SOBRE EL PAR Y NO SOBRE EL NÚMERO: dos catálogos distintos
-- pueden numerar igual, y un índice sobre `document_number` solo rechazaría
-- documentos legítimos.
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX uq_users_document
    ON users (document_type_id, document_number)
 WHERE document_number IS NOT NULL;

COMMENT ON COLUMN users.document_type_id IS
    'Tipo de documento (RN-SP-035). El catálogo solo lleva los de mayor de edad: esta FK ES la validación.';
COMMENT ON COLUMN users.document_number IS
    'Normalizado en mayúsculas. Único CON el tipo, y no se libera al eliminar (RN-SP-035).';
COMMENT ON COLUMN users.phone IS
    'Obligatorio en la API (RN-SP-037), nulable en el esquema por las filas anteriores a V71.';
COMMENT ON COLUMN users.address_line2 IS
    'Complemento. Opcional POR NATURALEZA: una dirección puede no tenerlo, y eso no es un dato que falte.';

-- NO se crea índice sobre `phone` ni sobre `city`: ninguna consulta filtra por
-- ellos hoy, y un índice sin consumidor es una estructura que se mantiene sola
-- en cada escritura. Mismo criterio con el que `V18` dejó fuera
-- `ix_users_busqueda`.
