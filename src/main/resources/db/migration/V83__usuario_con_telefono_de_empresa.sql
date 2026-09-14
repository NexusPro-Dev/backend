-- =============================================================================
-- RF-SP-024 · T-63 — El teléfono de la empresa (`RN-SP-037`, enmendada el
-- 10-09-2026).
--
-- `users` gana UNA columna: el segundo teléfono, el del trabajo. El personal
-- —`phone`, de `V71`— NO SE TOCA, ni de nombre ni de significado, de modo que
-- esta migración no rompe nada que ya funcione.
--
-- -----------------------------------------------------------------------------
-- MISMA FORMA QUE EL PERSONAL, Y A PROPÓSITO. Mismo largo, misma normalización
-- en el dominio y la misma expresión en el `CHECK`. Aquí no hay ninguna
-- decisión de esquema que tomar: se copia la que `V71` ya tomó, y copiarla es
-- mejor que inventar una segunda forma de escribir un número de teléfono.
--
-- -----------------------------------------------------------------------------
-- NACE NULABLE, Y ESTA VEZ EL NULO NO ES UNA TRANSICIÓN. Conviene no confundirlo
-- con el de `V71`, porque llevan al sitio contrario.
--
-- Las seis columnas de `V71` nacieron nulables porque las filas ANTERIORES no
-- tenían el dato e inventarlo habría sido escribir algo falso; por eso aquella
-- migración dejó escrita la condición para endurecerlas: el día que ninguna fila
-- tenga el documento nulo, `NOT NULL`.
--
-- Aquí NO HAY CONDICIÓN QUE ESCRIBIR. `RN-SP-037` declara este teléfono
-- OPCIONAL PARA SIEMPRE: exigirlo bloquearía el alta de todo el que no tenga
-- empresa. De modo que `NOT NULL` no es un endurecimiento pendiente sino algo
-- QUE NUNCA DEBE OCURRIR, y el nulo no significa «falta por completar» sino
-- «esta persona no tiene teléfono de empresa».
--
-- SIN `DEFAULT` Y SIN RELLENO, por lo mismo: una cadena vacía o un guion
-- afirmarían que esa persona tiene un número que no tiene, y además chocarían
-- con el `CHECK` de abajo. El nulo es la verdad.
--
-- -----------------------------------------------------------------------------
-- NUMERACIÓN: `V83`. Se planificó como `V82` y el índice de búsqueda de
-- `user_brokers` se lo llevó el mismo día. La reserva de números por
-- requerimiento quedó muerta el 24-08-2026 (ver `V28`).
-- =============================================================================

ALTER TABLE users
    ADD COLUMN company_phone varchar(20);


-- -----------------------------------------------------------------------------
-- La forma, idéntica a la de `ck_users_phone_format`.
--
-- Dígitos con un `+` opcional. Quince es el máximo de E.164; el número se
-- persiste ya normalizado, sin espacios, guiones ni paréntesis.
--
-- NO SE VALIDA CONTRA EL PAÍS, por lo mismo que el personal: exigiría un
-- catálogo de prefijos que nadie ha pedido, y una validación a medias
-- rechazaría números legítimos. Y menos aún aquí, donde la empresa puede estar
-- en un país distinto del de la persona.
-- -----------------------------------------------------------------------------
ALTER TABLE users
    ADD CONSTRAINT ck_users_company_phone_format
        CHECK (company_phone IS NULL OR company_phone ~ '^\+?[0-9]{7,15}$');


COMMENT ON COLUMN users.company_phone IS
    'Teléfono de la empresa (RN-SP-037, 10-09-2026). OPCIONAL SIEMPRE: el nulo es un hecho, no un dato pendiente. Nunca NOT NULL.';


-- NO se crea índice, por lo mismo que `phone` y `city`: ninguna consulta filtra
-- ni ordena por él, y `RF-SP-025` no lo admite como criterio de búsqueda. Un
-- índice sin consumidor es una estructura que se mantiene sola en cada
-- escritura.
