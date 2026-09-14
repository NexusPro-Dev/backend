# Modelo de Datos — Estado actual

| Campo | Valor |
|---|---|
| Versión | 0.42.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 21-08-2026 |
| Última actualización | 14-09-2026 |

!!! info "Qué va en este documento"

    Cómo va quedando el esquema con lo que hay especificado hoy: las tablas, sus columnas y las relaciones entre ellas, agrupadas por lo que resuelven.

    Es una **vista derivada**, no normativa. Sale de [`requirements/sp.md` §10](requirements/sp.md), [`security.md` §9](security.md) y [`architecture.md` §6.6](architecture.md). La fuente de verdad del esquema son las **migraciones Flyway** (Art. V.3), y donde ya existen mandan ellas.

!!! success "Veintiocho tablas escritas, y ninguna diseñada pendiente de escribir"

    `V49` cerró las tres de `CM` —creó `user_commission_rates` y `product_commission_rates`, y **rehízo `commission_rates`** quitándole el producto, la persona y la vigencia—, y el 04-09-2026 **`V54__create_movements.sql`** creó las cuatro de `MV`: `movements`, `movement_types`, `movement_details` y `payment_methods`, con sus dos catálogos sembrados en la misma migración (`RF-MV-001`).

    **`V55` añadió la quinta de `MV` el mismo día**: `payment_method_exclusions`, que declara **dónde NO vale cada método de pago** (`RN-MV-019`). Con ella `countries` **deja de ser una isla** — es su primera clave foránea entrante en los veinte días que lleva existiendo.

    **La forma de esas cuatro no se repite aquí**: vive en [`requirements/mv.md` §7](requirements/mv.md). Este documento es una **vista derivada** —lo dice justo arriba—, y dos copias del mismo esquema divergen sin que nadie lo note.

    **`movements` es la única tabla del sistema sin `updated_at` ni `deleted_at`** (`RN-MV-001`), y es deliberado: una venta no se edita y no se borra, solo avanza su `status`. Quien mantenga el gestor de auditoría de la aplicación tiene que saberlo, porque no puede tratar esta tabla como a las demás.

    **Y `seller_id` admite nulo desde el 04-09-2026** (`RN-MV-003`): comprar no es cosa solo de los clientes, y quien no cuelga de nadie —la cúspide de la fuerza comercial, que por `RN-SP-019` no declara superior— compra igual. Una venta sin vendedor es un estado legítimo y **no comisiona a nadie**, que es lo que quien construya la liquidación tiene que tratar.

    **`V50`** añadió las cuatro columnas del valor fijo —`rate_type` y `fixed_amount` en las dos tablas de tasas— y **`V52`** el `role_type` de `user_roles`, que es lo que hace declarable `RN-SP-025`.

    !!! warning "Las tablas de `MV` cambiaron de número TRES veces, y no es cosmético"

        Se numeran **por orden de aplicación**, y estas cuatro tablas son el caso que mejor lo enseña: pidieron el `51`, luego el `52`, luego el `53`, y acabaron en el **`54`**.

        | Reserva | Quién se llevó el hueco |
        |---|---|
        | `V51` | `V51__seed_movements_permissions.sql` — la siembra de los cuatro permisos `movements:`, que **no crea ninguna tabla** y estaba escrita antes ([`requirements/mv.md` §6.1](requirements/mv.md)) |
        | `V52` | El `role_type` de `user_roles`, que hace declarable `RN-SP-025` |
        | `V53` | `V53__products_source_membership.sql` — el origen del upgrade (`RF-PM-001`), fusionado el 03-09-2026 |
        | **`V54`** | **Las cuatro tablas de `MV`. Aplicada el 04-09-2026** |

        **La que se aplica primero es siempre la que está escrita.** Las otras eran reservas sin una línea de `SQL`, y esa es la situación en la que una reserva no vale nada: Flyway deja fuera, sin error y sin aviso, una migración con número por debajo del último aplicado.

        No es una preferencia de estilo. Flyway aplica **en orden**, y una migración añadida con un número **por debajo** del último aplicado se queda fuera — el esquema quedaría sin esas cuatro tablas y nada lo diría hasta la primera consulta.

    **`V49` es también la primera migración del proyecto que borra datos a propósito.** Vació `commission_rates` porque **ninguna de las cuatro formas del modelo anterior tiene traducción al nuevo**: dejarlas caer a «tasa de rol» las habría convertido en filas plausibles y falsas — con su porcentaje, sin asociación, y sin que nada dijera que significan otra cosa que el día que se escribieron.

---

## 1. Control de acceso

El núcleo del módulo `SP`, incluidas las tablas de usuarios que lo consumen. Es la única zona del modelo con relaciones densas.

```mermaid
erDiagram
    roles ||--o{ roles : "parent_role_id · acota privilegios"
    roles ||--o{ role_permissions : "declara"
    permissions ||--o{ role_permissions : "se declara en"
    users ||--o{ user_roles : "porta"
    roles ||--o{ user_roles : "se asigna a"
    users ||--o{ refresh_tokens : "abre sesión"
    users ||--o{ password_reset_permits : "pide restablecer · RF-SP-040"
    users ||--o{ user_supervisors : "está a cargo de alguien"
    users ||--o{ user_supervisors : "tiene gente a cargo"
    countries ||--o{ users : "está en · RN-SP-034 · exactamente uno, obligatorio"
    document_types ||--o{ users : "se identifica con · RN-SP-035 · columnas en §2"
    brokers        ||--o{ user_brokers : "tiene cuentas en · RN-SP-038"
    users          ||--o{ user_brokers : "declara su cuenta · columnas en §2"
    users ||--|{ user_memberships : "historial · exactamente una abierta · RN-SP-018"
    memberships ||--o{ user_memberships : "se asigna a"

    memberships {
        uuid id PK "v7"
        varchar code UK "50"
        varchar name "100"
        text description "NULL"
        varchar color "6 · hexadecimal sin # · en mayúsculas"
        uuid parent_membership_id FK,UK "NULL en la superior · UK impide bifurcar"
        smallint level "orden materializado · se recalcula al insertar"
        timestamptz created_at "now"
        timestamptz updated_at "now"
    }

    permissions {
        uuid id PK "v7"
        varchar code UK "100 · resource:action"
        varchar resource "50"
        varchar action "50"
        varchar name "100"
        text description "NULL"
        timestamptz created_at "now"
        timestamptz updated_at "now"
    }

    roles {
        uuid id PK "v7"
        varchar code "50 · único entre los no eliminados"
        varchar name "100 · único entre los no eliminados"
        text description "NULL · CHECK 500 caracteres"
        varchar role_type "20 · FUNCIONARIO VENDEDOR CONSUMIDOR"
        uuid parent_role_id FK "NULL solo en el rol raíz"
        varchar status "20 · ACTIVO INACTIVO · default ACTIVO"
        boolean is_system "default false · bloquea toda edición"
        timestamptz created_at "now"
        timestamptz updated_at "now"
        timestamptz deleted_at "NULL · borrado lógico"
    }

    role_permissions {
        uuid role_id PK,FK "PK compuesta · excepción al Art. V.11"
        uuid permission_id PK,FK "PK compuesta"
        timestamptz created_at "now"
    }

    users {
        uuid id PK "usuarios"
        varchar username UK "inmutable · sin @ · único incluidos los eliminados"
        varchar email UK "corregible · único incluidos los eliminados"
        varchar first_name "—"
        varchar last_name "—"
        uuid country_id FK "OBLIGATORIO · RN-SP-034 · columna y no tabla puente porque el pais no tiene vigencia"
        uuid document_type_id FK "NULL en las filas previas · obligatorio en la API · RN-SP-035"
        varchar document_number "30 · normalizado en mayusculas · unico CON el tipo, y no se libera al eliminar"
        varchar address_line1 "150 · NULL · opcional"
        varchar address_line2 "150 · NULL · complemento: opcional POR NATURALEZA"
        varchar city "100 · NULL · texto libre, no hay catalogo de ciudades"
        varchar phone "20 · NULL en las filas previas · obligatorio en la API · RN-SP-037"
        varchar company_phone "20 · el de la empresa · OPCIONAL · RN-SP-037 · 10-09-2026"
        varchar password_hash "Argon2id"
        boolean must_change_password "default false · lo fijan RF-SP-024 y RF-SP-038"
        timestamptz password_expires_at "NULL · caducidad de la credencial provisional · la exige RF-SP-038"
        varchar status "ACTIVO FTD_PENDIENTE INACTIVO BLOQUEADO · el segundo AUTENTICA"
        int failed_attempts "control de bloqueo · la crea RF-SP-034"
        timestamptz locked_until "NULL · nulo también en el bloqueo manual, que no expira · la crea RF-SP-034"
        timestamptz last_login_at "NULL · la crea RF-SP-034"
        timestamptz deleted_at "NULL · nace con la tabla en V18 · solo la escribe RF-SP-029"
    }

    user_roles {
        uuid user_id PK,FK "PK compuesta"
        uuid role_id PK,FK "PK compuesta"
        varchar role_type FK "COPIA de roles.role_type · RN-SP-025 en el esquema"
        timestamptz created_at "now"
    }

    user_memberships {
        uuid id PK "historial · user_id se repite"
        uuid user_id FK "uq_user_memberships_abierta · WHERE closed_at IS NULL"
        uuid membership_id FK "—"
        timestamptz started_at "now"
        timestamptz ends_at "NULL · indefinida · hasta cuando se pago"
        timestamptz closed_at "NULL · la fila abierta es la actual · cuando dejo de serlo"
        timestamptz created_at "now"
        timestamptz updated_at "now · RF-SP-032 corrige la fecha sin cerrar"
    }

    refresh_tokens {
        uuid id PK "sesiones revocables"
        uuid user_id FK "—"
        varchar token_hash UK "nunca el valor en claro"
        timestamptz expires_at "—"
        timestamptz revoked_at "NULL"
        varchar revoked_reason "NULL · ROTACION CIERRE ACCESO_RETIRADO · solo ROTACION alerta"
        uuid replaced_by_id FK "NULL · rotación"
        inet ip "—"
        text user_agent "—"
    }

    password_reset_permits {
        uuid id PK "permiso temporal de RF-SP-040 · sin plan.md"
        uuid user_id FK "—"
        varchar token_hash UK "nunca el valor en claro, como refresh_tokens"
        timestamptz expires_at "vigencia corta"
        timestamptz used_at "NULL · de un solo uso"
        timestamptz invalidated_at "NULL · FA-002 · lo invalida el siguiente"
        inet ip "—"
        timestamptz created_at "now"
    }

    user_supervisors {
        uuid id PK "estructura comercial"
        uuid user_id FK "subordinado O CLIENTE · uno vigente por persona"
        uuid supervisor_id FK "superior si es vendedor, QUIEN LO TRAJO si es cliente"
        timestamptz started_at "now"
        timestamptz ended_at "NULL mientras esté vigente"
        timestamptz created_at "now"
        timestamptz updated_at "now"
    }
```

**`countries` aparece aquí dibujada sin sus columnas a propósito**: es un catálogo, vive en §2 y repetir sus campos en dos diagramas es la forma más barata de que diverjan. Lo que hace en este es sostener la única arista nueva del 07-09-2026.

Nueve decisiones que el dibujo no explica solo:

- **`parent_role_id` hace dos trabajos**: acota los privilegios del hijo y expresa el orden de mando comercial (`RN-SP-011`). La consecuencia es permanente: un rol `VENDEDOR` nunca podrá tener un permiso que su superior no tenga, porque `RN-SEG-003` lo rechazaría.
- **`user_supervisors` es la única tabla que relaciona dos usuarios entre sí**, y responde a una pregunta que `parent_role_id` no puede responder: no *qué rol manda sobre qué rol*, sino **qué persona está a cargo de qué persona**. Lleva clave sustituta —al contrario que `user_roles`— porque el mismo par puede repetirse en el tiempo y lo que distingue una fila de otra es el periodo. Su unicidad es parcial, `WHERE ended_at IS NULL`: un solo superior vigente, historial ilimitado. **No concede acceso a ningún dato**; el modelo de alcance sigue pendiente como D-22.
- **La unicidad de `roles` es parcial**, no total: `WHERE deleted_at IS NULL`. Una restricción única corriente bloquearía para siempre el nombre de un rol borrado.
- **La de `users` es justo la contraria: total.** `username` y `email` son únicos entre **todos** los usuarios, incluidos los eliminados (`RN-SP-016`). Reutilizarlos permitiría que la actividad de dos personas distintas quedara bajo la misma etiqueta en la auditoría. La asimetría con `roles` es deliberada: un rol es una etiqueta, un usuario es una persona.
- **`username` y `email` sirven ambos para iniciar sesión**, y lo que impide que se confundan es que `username` no admite el carácter `@` (`RF-SP-024`). Sin esa restricción, las dos columnas necesitarían compartir un espacio de unicidad común.
- **`role_permissions` y `user_roles` no llevan clave sustituta.** La unicidad del par es la restricción que importa, y una columna sin significado no aportaría nada.
- **La identidad documental y el contacto son columnas de `users`, y la mayoría de edad NO es una comprobación.** `document_types` es un catálogo diminuto y `users` lo señala con `document_type_id`; lo que hace ese vínculo distinto de los demás es **lo que el catálogo NO contiene**. `RN-SP-035` exige que toda persona sea mayor de edad, y en lugar de una columna `acredita_mayoria` que alguien deba mirar, **el catálogo solo lleva documentos de adulto**: la tarjeta de identidad y el registro civil no están. Registrar a un menor deja de ser algo que se rechaza y pasa a ser algo que **no se puede escribir** — no hay identificador que poner, y `fk_users_document_type` no admite otra cosa. Es el patrón contrario al de `user_roles.role_type`, y conviene ver la simetría: allí se **trajo** un dato para que la regla cupiera en el motor; aquí se **quitó** una opción del catálogo para que la regla no hiciera falta.
- **`users.country_id` es la única cosa que una persona «tiene» y que no vive en una tabla propia**, y la asimetría con las otras dos es la que hay que leer. La membresía está en `user_memberships` y el superior en `user_supervisors` porque las dos **tienen vigencia**: se conceden, vencen, se sustituyen, y hay que poder decir cuál regía **entonces**. **El país no tiene periodo.** No se concede hasta una fecha y nadie pregunta dónde estaba alguien el mes pasado — lo que el sistema necesita saber es dónde está **hoy**, que es lo que decide qué medios de pago se le ofrecen (`RN-MV-019`). Una tabla puente para un dato sin vigencia añadiría un `join` a cada consulta de usuario a cambio de nada, y el rastro de cada cambio lo guarda ya `audit_change_log`. **La cardinalidad obligatoria está en el lado izquierdo, no en el derecho**: `||--o{` dice que cada usuario tiene **exactamente un** país —eso es el `NOT NULL`— y que un país puede tener **cero**, que es el estado de todo país recién registrado y del que se acaba de retirar de la circulación.

!!! danger "`user_roles.role_type` es la única columna desnormalizada del sistema, y es la que hace declarable `RN-SP-025`"

    Es una **copia** de `roles.role_type`, y está ahí porque «una persona no puede portar dos roles de tipo `VENDEDOR`» no se puede escribir de otra forma: un `CHECK` no consulta otra tabla, y un índice único no puede unir `user_roles` con `roles`.

    Copiar un dato es normalmente el error que este documento evita. **Aquí no lo es, y la diferencia se puede nombrar:** la copia está atada por una **clave foránea compuesta** `(role_id, role_type) → roles(id, role_type)`, de modo que **no puede divergir**; y su origen es **inmutable** —`RF-SP-004` corrige nombre y descripción, no el tipo—, de modo que nunca tendrá que actualizarse.

    Es el mismo patrón que `product_commission_rates` (§4), y la condición que lo hace legítimo es la misma en los dos sitios: **el dato copiado no cambia en su origen**. Donde cambie, la FK bloquearía la corrección legítima y el patrón deja de valer.

    Sobre ella, `uq_user_roles_vendedor` —parcial, `WHERE role_type = 'VENDEDOR'`— cierra la regla **en el motor**. Se decidió así el 02-09-2026 por un precedente y no por gusto: `RN-SP-018` se comprobaba en el caso de uso, **no aguantó la concurrencia**, y hubo que corregirla el 26-08-2026 sobre esta misma tabla.
- **La credencial provisional necesita dos columnas, no una.** `must_change_password` dice *que* hay que cambiarla; `password_expires_at` dice *hasta cuándo sirve*. `RF-SP-038` §7 exige ambas cosas —fija la marca y «el momento en que la credencial provisional caduca», superado el cual hay que restablecerla de nuevo— y hasta ahora el modelo solo declaraba la primera. La columna es nulable porque solo tiene sentido mientras la credencial sea provisional, y deja de tenerlo en cuanto la persona elige la suya: que `RF-SP-037` y `RF-SP-040` la limpien junto con la marca es la lectura natural, pero **ninguna de las dos lo dice** y es parte de lo que sus `plan.md` tendrán que fijar.
- **El permiso temporal de `RF-SP-040` es una tabla, no una columna.** Tiene vigencia propia, se consume de un solo uso y una solicitud nueva invalida la anterior (`FA-002`), de modo que necesita filas con estado y no un campo en `users`. Su forma copia la de `refresh_tokens` por el mismo motivo: **nunca se guarda el valor en claro**, solo su hash, porque quien leyera la tabla podría entrar como cualquiera. Es la tabla más provisional del modelo —`RF-SP-040` todavía no tiene `plan.md`—, y los nombres de sus columnas quedan sujetos a él.

---

## 2. Catálogos

Los tres catálogos del módulo `SP`, y **la primera tabla que cuelga de uno de ellos**.

!!! warning "Esta sección decía «no tienen ninguna clave foránea entrante», y dejó de ser cierto hace tiempo"

    Lo era el 21-08-2026 y se quedó escrito. `currencies` dejó de ser una isla con `products.currency_id` (`V39`) y `movements.currency_id` (`V54`); `countries`, con `payment_method_exclusions` (`V55`, 04-09-2026). §5.3 lo recogía y esta línea no.

    **Y el 07-09-2026 `currencies` recibe dos más de golpe**, las dos desde la misma tabla: `exchange_rates` apunta a ella **por partida doble** —origen y destino—, que es lo que obliga a que su restricción de no solapamiento compare **las dos** columnas y no una.

    **El mismo día `countries` recibe la suya segunda, y es de otra clase.** `payment_method_exclusions` la señalaba desde una regla de configuración; `users.country_id` la señala **desde una persona** (`RN-SP-034`), y es el primer sitio del modelo donde el país deja de ser un dato sobre el sistema para ser un dato sobre alguien. **Es además la única clave foránea entrante de este cuadro que es `NOT NULL`**: las demás admiten no apuntar a nada, y esta no — de modo que el catálogo **no puede estar vacío** mientras exista un solo usuario, que es lo que obliga a sembrar Colombia (`requirements/sp.md` §10.6).

```mermaid
erDiagram
    memberships ||--o| memberships : "parent_membership_id · una sola hija"
    currencies  ||--o{ exchange_rates : "de esta moneda sale"
    currencies  ||--o{ exchange_rates : "a esta moneda llega"
    countries   ||--o{ users : "aqui vive · columnas en §1"
    document_types ||--o{ users : "se identifica con · columnas en §1"
    brokers        ||--o{ user_brokers : "tiene cuentas en · RN-SP-038"
    users          ||--o{ user_brokers : "declara su cuenta"

    memberships {
        uuid id PK "v7"
        varchar code UK "50"
        varchar name "100"
        text description "NULL"
        varchar color "6 · hexadecimal sin # · en mayúsculas"
        uuid parent_membership_id FK,UK "NULL en la superior · UK impide bifurcar"
        smallint level "orden materializado · se recalcula al insertar"
        timestamptz created_at "now"
        timestamptz updated_at "now"
    }

    currencies {
        uuid id PK "v7"
        char code UK "3 · ISO 4217"
        varchar name "100"
        varchar symbol "10 · NULL"
        smallint decimal_places "default 2 · cero es legítimo"
        boolean is_default "índice único parcial · exactamente una en true"
        boolean is_active "default true · lo cambia RF-SP-023"
        timestamptz created_at "now"
    }

    document_types {
        uuid id PK "v7"
        varchar abbreviation UK "10 · ES el codigo: no hay columna code aparte"
        varchar name "100 · UK funcional sobre f_unaccent(lower(name))"
        boolean is_active "default true · SOLO lo cambia una migracion · RN-SP-036"
        timestamptz created_at "now"
        timestamptz updated_at "now"
    }

    countries {
        uuid id PK "v7"
        char code UK "3 · ISO 3166-1 alfa-3"
        varchar name "100 · UK funcional sobre f_unaccent(lower(name))"
        boolean is_active "default true · lo cambia RF-SP-022"
        timestamptz created_at "now"
        timestamptz updated_at "now · lo mueve RF-SP-022"
    }

    exchange_rates {
        uuid id PK "v7"
        uuid source_currency_id FK "de que moneda"
        uuid target_currency_id FK "a cual · CHECK: distinta del origen"
        numeric price "18,8 · NO es un importe: COP a USD ronda 0,00024"
        date valid_from "obligatoria"
        date valid_to "NULL = vitalicia"
        boolean is_active "default true"
        timestamptz deleted_at "logico con motivo · RN-SP-033"
    }

    brokers {
        uuid id PK "v7"
        varchar name "120 · UK funcional sobre f_unaccent(lower(name)) · ES la clave de negocio"
        boolean is_active "default true · SOLO lo cambia una migracion · RN-SP-039"
        timestamptz created_at "now"
        timestamptz updated_at "now"
    }

    user_brokers {
        uuid id PK "v7"
        uuid user_id FK "de quien es la cuenta"
        uuid broker_id FK "en que broker"
        varchar external_id "80 · el numero de cuenta EN EL BROKER"
        varchar broker_username "120 · NULL = el broker aun no lo confirmo · RN-SP-040"
        varchar status "20 · REGISTER | FIRST_DEPOSIT · nace en REGISTER · RN-SP-045"
        timestamptz created_at "now"
        timestamptz updated_at "now · lo movera el webhook RF-SP-054"
    }
```

- **`memberships` es una lista, no un árbol.** El índice único sobre `parent_membership_id` es lo que lo garantiza: sin él la cadena podría bifurcarse y el orden dejaría de estar definido.
- **`currencies.is_default`** exige un índice único parcial: exactamente una fila en `true`, declarado en el esquema y no solo en el dominio (Art. V.6).
- **`document_types` es el primer catálogo cuyo CONTENIDO es una regla de negocio**, y por eso es el único que no se administra por API ni siquiera para desactivar (`RN-SP-036`). `countries` y `currencies` tienen su requerimiento para cambiar `is_active` —`RF-SP-022` y `RF-SP-023`—; este no, y no es un olvido: **añadir una fila a este catálogo es abrir la puerta a los menores de edad**, de modo que la operación no puede existir como llamada. Lo que se retira, se retira con una migración revisada. Su `is_active` **sí se lee** desde el primer día —el listado publica solo los activos— y por eso no es una columna dormida de las que `RF-SP-024` §2 advierte; y **existe porque sin ella no hay retirada posible**: `fk_users_document_type` bloquea el borrado en cuanto una persona la referencie.
- **`user_brokers` es la primera tabla del sistema cuyo único NO incluye a la persona.** Va sobre `(broker_id, external_id)` y no sobre `(user_id, broker_id)`, y esa elección es el requerimiento entero (`RN-SP-038`): una persona puede tener **varias** cuentas en el mismo broker —lo normal en el ramo— y una cuenta **no puede ser de dos personas**. Con el único al revés se prohibiría lo primero y se permitiría lo segundo, que es exactamente el intercambio contrario al que hace falta.
- **`user_brokers.broker_username` admite nulo y su nulo significa algo**: «el broker todavía no lo ha confirmado» (`RN-SP-040`). Lo rellena un webhook (`RF-SP-054`, sin construir), no el alta — declararlo obligatorio obligaría a inventar un valor que sobreviviría a la confirmación.
- **`user_brokers.status` es la segunda columna de esta tabla que espera al mismo webhook** (`RN-SP-045`, 10-09-2026), y por eso conviene leerla junto a la de arriba: `REGISTER` o `FIRST_DEPOSIT`, con `CHECK` en el motor, **nace en `REGISTER`** y hoy nada la mueve. La diferencia con `broker_username` es que **esta sí se lee desde el primer día** —`RF-SP-055` y `RF-SP-056`—, y lo que devuelve es cierto: sin webhook no hay depósito confirmado. **Sus dos valores van en inglés**, únicos en todo el modelo —`users.status`, `products.status` y `movements.status` van en castellano—, porque son el vocabulario del broker que los va a escribir.
- **`user_supervisors` se lee por primera vez EN PROFUNDIDAD** (`RN-SP-047`, 10-09-2026). Hasta hoy toda consulta sobre esta tabla miraba **un nivel** —«quién está a cargo de esta persona», «quién depende de ella»—; `RF-SP-057` recorre la rama entera con una **recursiva**. Dos consecuencias de modelado que conviene tener escritas: **(1)** la terminación **no depende de que los datos sean acíclicos** —lo son, porque `RN-SP-020` ata esta cadena a la de roles, que sí lo es— sino de que la recursión acumule con **`UNION`** y no con `UNION ALL`, que es lo que impide reexpandir a quien ya se vio; **(2)** el recorrido entra por `ix_user_supervisors_supervisor_vigente`, el índice **parcial** de `V28`, y por eso el predicado `ended_at IS NULL` tiene que estar **en los dos brazos** de la recursiva — omitirlo en el recursivo haría descender por la estructura de ayer sin que nada fallara.
- **`user_brokers.status` no se deriva de `users.status` ni al revés.** Aquel dice si la cuenta del sistema opera (`FTD_PENDIENTE` autentica y no opera, `RN-SP-044`) y este dice qué pasó en el broker. Una persona con dos cuentas puede tener una depositada y otra no, de modo que **no hay función que lleve de un conjunto al otro** sin decidir antes qué significa ese caso — y esa decisión es de `RF-SP-054`, no de aquí.
- **`brokers` guarda solo el nombre**, por decisión del 08-09-2026, y de ahí sale que el **nombre sea la clave de negocio**: único funcional, como en `countries` y `document_types`. Renombrar un broker es, por tanto, una migración.
- **`countries` es el único catálogo del que cuelga una persona**, desde el 07-09-2026. `memberships` tiene su tabla puente y `currencies` no toca a nadie; el país es **una columna de `users`**, y el porqué —no tiene vigencia— está razonado en §1. Lo que este cuadro añade es la consecuencia sobre el catálogo: **`is_active` deja de ser inofensivo**. Desactivar un país lo retira de los selectores del alta y **no desasigna a nadie**, de modo que a partir de ahí pueden convivir usuarios en un país que ya no se ofrece. Es deliberado y es lo que `RF-SP-022` prometía desde el principio — lo que cambia es que ahora hay a quién afectar.
- **`countries` sí lleva `updated_at`**, incorporado el 21-08-2026 al aprobar el `plan.md` de `RF-SP-020`: el Art. V.7 lo obliga y `RF-SP-022` mueve la fila. `currencies` lo necesitará por el mismo motivo cuando se escriba el plan de `RF-SP-023`. Ninguna de las tres lleva borrado lógico.
- **La unicidad del nombre de `countries` es funcional**, sobre `f_unaccent(lower(name))` y no sobre `name` literal, porque `RN-SP-009` no admite edición y un `Panamá`/`Panama` duplicado sería permanente. Es la asimetría deliberada con `uq_roles_name`.
- `memberships` no tiene **ninguna** salida —ni baja ni indicador de activo— y `RN-SP-008` lo justifica: desactivar un eslabón dejaría un hueco en un orden lineal.
- **`memberships` gana `color`** el 26-08-2026: seis dígitos hexadecimales **sin `#`**, con los que el frontend pinta el nivel. Es el único campo de la tabla que no participa en ninguna regla del backend —nada se autoriza ni se ordena por él—, y aun así vive aquí y no en el navegador: si lo eligiera el frontend, dos pantallas del mismo sistema pintarían el mismo nivel de distinto color y nadie lo notaría hasta verlas juntas. Como `RN-SP-008` mantiene la membresía inmutable, **un color mal elegido no se puede corregir**; queda declarado en `requirements/sp.md` §5.1 con su condición de reapertura.

---

## 3. Auditoría

Cuatro tablas, no una. Cada una declara `NOT NULL` lo que en su contexto es obligatorio, algo que un registro único no permite. Comparten un núcleo común de seis columnas, repetido aquí en cada tabla porque así estará en el esquema.

```mermaid
erDiagram
    users |o..o{ audit_change_log : "actor_id · sin FK declarada"
    users |o..o{ audit_deletion_log : "actor_id · sin FK declarada"
    users |o..o{ audit_error_log : "actor_id · sin FK declarada"
    users |o..o{ audit_security_log : "actor_id y target_user_id"
    request_log |o..o{ audit_change_log : "correlation_id"
    request_log |o..o{ audit_deletion_log : "correlation_id"
    request_log |o..o{ audit_error_log : "correlation_id"
    request_log |o..o{ audit_security_log : "correlation_id"

    audit_change_log {
        uuid id PK "núcleo común"
        timestamptz occurred_at "núcleo · UTC"
        uuid actor_id "núcleo · NULL si es proceso"
        uuid correlation_id "núcleo · NULL"
        inet ip_address "núcleo · NULL"
        text user_agent "núcleo · NULL"
        varchar module "SP"
        varchar entity "roles users"
        uuid entity_id "registro afectado"
        varchar action "CREATE UPDATE · CHECK"
        jsonb changes "antes y después de lo que cambió"
    }

    audit_deletion_log {
        uuid id PK "núcleo común"
        timestamptz occurred_at "núcleo · UTC"
        uuid actor_id "núcleo · NULL"
        uuid correlation_id "núcleo · NULL"
        inet ip_address "núcleo · NULL"
        text user_agent "núcleo · NULL"
        varchar module "—"
        varchar entity "—"
        uuid entity_id "—"
        varchar deletion_type "LOGICAL PHYSICAL ASSOCIATION"
        text reason "obligatorio salvo en ASSOCIATION"
        jsonb snapshot "NOT NULL · estado completo al eliminar"
    }

    audit_error_log {
        uuid id PK "núcleo común"
        timestamptz occurred_at "núcleo · UTC"
        uuid actor_id "núcleo · NULL"
        uuid correlation_id "núcleo · NULL"
        inet ip_address "núcleo · NULL"
        text user_agent "núcleo · NULL"
        varchar resource "entidad o ruta"
        uuid entity_id "NULL"
        varchar operation "caso de uso o método y ruta"
        varchar error_code "del contrato o de la regla incumplida"
        varchar error_type "BUSINESS_RULE INTEGRATION UNHANDLED"
        smallint http_status "—"
        varchar severity "MEDIA ALTA"
        text message "saneado · sin trazas ni SQL"
    }

    audit_security_log {
        uuid id PK "núcleo común · solo inserción"
        timestamptz occurred_at "núcleo · UTC"
        uuid actor_id "núcleo · NULL si aún no hay identidad"
        uuid correlation_id "núcleo · NULL"
        inet ip_address "núcleo · clave para detectar fuerza bruta"
        text user_agent "núcleo · NULL"
        varchar event_type "CHECK sobre el catálogo de security.md 8.1"
        varchar severity "INFORMATIVA MEDIA ALTA"
        varchar outcome "SUCCESS FAILURE"
        uuid target_user_id "NULL · a quién se lo hicieron"
        jsonb detail "NULL · sujeto a enmascaramiento"
    }

    request_log {
        uuid id PK "uuid v7"
        timestamptz occurred_at "UTC"
        uuid correlation_id "NOT NULL · aquí no es nulable"
        uuid actor_id "NULL = anónimo · sin FK"
        varchar method "GET POST PUT PATCH DELETE"
        varchar path "ruta sin cuerpo ni cabeceras"
        text query_string "NULL · los parámetros"
        smallint status "NULL = abortada sin respuesta"
        integer duration_ms "umbrales p95 del Art XV.9"
        inet ip_address "NULL"
        text user_agent "NULL"
    }
```

- **Las tres columnas de origen son nulables a la vez**, con un `CHECK` que lo impone: `correlation_id` e `ip_address` van juntas. Una fila sin IP significa «no vino de la red», nunca «se olvidó registrarla».
- **`audit_security_log` es de solo inserción**, restringido a nivel de privilegios de base de datos y no por convención en el código. Un registro que la aplicación puede reescribir no prueba nada.
- Las cuatro se leen en conjunto por una **vista de solo lectura** sobre el núcleo común, que exige los cuatro permisos de lectura.
- **`request_log` ya no es un hueco**: existe desde `V35` (issue #23) y `architecture.md` §6.7 declara el porqué de cada columna. Dos diferencias con las cuatro de arriba, y ninguna es de estilo: su `correlation_id` **no** es nulable —aquellas admiten eventos de procesos internos, esto solo lo escribe una petición HTTP— y **no participa en la transacción de negocio**, de modo que una operación revertida deja su fila igual: que el negocio fallara no significa que nadie llamara.

---

## 4. Lo que se vende y cuánto se paga por venderlo

Las dos áreas que nacieron después de la primera versión de este documento y que **su mapa no conocía**: `PM` el 27-08-2026 y `CM` el 28-08-2026.

```mermaid
erDiagram
    memberships ||--o| products : "un upgrade lleva a"
    currencies  ||--o{ products : "el precio se expresa en"

    roles    ||--o{ commission_rates : "qué gana ese rol"
    commission_rates ||--o{ product_commission_rates : "rige sobre"
    products ||--o{ product_commission_rates : "paga esa tasa"
    users    ||--o{ user_commission_rates : "excepción de"
    user_commission_rates ||--o{ user_commission_rate_products : "rige sobre"
    products ||--o{ user_commission_rate_products : "paga esa excepción"
    products ||--o{ product_comments : "se reseña"
    users    ||--o{ product_comments : "escribe UNA por producto"

    products {
        uuid id PK
        varchar code UK "no se libera JAMAS"
        varchar type "UPGRADE_MEMBRESIA o BOT"
        varchar status "nace INACTIVO"
        varchar scope "TIENDA o HOTLINKS · ACUMULATIVO: el segundo incluye al primero"
        varchar implementation "AUTOMATICA o MANUAL · decide si confirmar una venta ENTREGA"
        uuid target_membership_id FK "obligatorio en upgrade, PROHIBIDO en bot · A DONDE lleva"
        uuid source_membership_id FK "obligatorio en upgrade, PROHIBIDO en bot · DE DONDE sale"
        numeric price "14,4 · EL QUE SE COBRA · la escala la decide la MONEDA"
        numeric purchase_price "14,4 · LO QUE PAGA NEXUS · NULL = no se conoce · solo administracion"
        integer validity_days "NULL = no caduca"
        timestamptz deleted_at "lógico · RN-PM-010"
    }

    commission_rates {
        uuid id PK
        uuid role_id FK "catálogo: qué gana ese rol"
        varchar rate_type "PORCENTAJE o FIJO · lo declara, no se deduce"
        numeric percentage "5,2 · NULL si es FIJO · cero es un VALOR"
        numeric fixed_amount "14,4 como products.price · NULL si es PORCENTAJE · SIN MONEDA"
        timestamptz deleted_at "lógico · RN-CM-005"
    }

    product_commission_rates {
        uuid product_id PK,FK "la PK es la regla:"
        uuid role_id PK,FK "un porcentaje por rol y producto"
        uuid commission_rate_id FK "FK COMPUESTA con role_id"
    }

    user_commission_rates {
        uuid id PK
        uuid user_id FK "SIN rol: es de la persona"
        varchar rate_type "PORCENTAJE o FIJO"
        numeric percentage "5,2 · NULL si es FIJO"
        numeric fixed_amount "14,4 · NULL si es PORCENTAJE · toma la moneda de SU producto"
        date valid_from "la ÚNICA tabla con vigencia"
        date valid_to "NULL = indefinidamente"
        timestamptz deleted_at "lógico"
    }

    user_commission_rate_products {
        uuid user_commission_rate_id PK,FK "la PK es la regla: no se asocia dos veces"
        uuid product_id PK,FK "y SIN role_id: aqui no hay nada copiado que pueda mentir"
        timestamptz created_at "sin retiro logico: desasociar BORRA"
    }

    product_comments {
        uuid id PK
        uuid product_id FK "activo y no retirado AL ESCRIBIR; la resena sobrevive al retiro"
        uuid user_id FK "el AUTOR, no el actor · solo el toca la fila"
        smallint rating "1 a 5 · el promedio NO se guarda: se cuenta"
        text comment "1 a 1000 sin espacios de los extremos"
        timestamptz deleted_at "logico · SIN motivo declarado: Art. V.13, contenido propio"
    }
```

!!! info "`product_comments` es la primera tabla de `PM` que apunta a `users`, y la primera del sistema que retira sin motivo declarado"

    Nace el 14-09-2026 (`requirements/pm.md` v0.24.0 §10.4). **`user_id` es el autor de la opinión y no el actor del cambio**: sin él la fila no significa nada, igual que `user_commission_rates.user_id`; quién corrigió o retiró sigue en la auditoría (Art. V.7), y coincide con el autor porque `RN-PM-027` lo obliga.

    **Una reseña por persona y producto entre las vivas** —`uq_product_comments_autor`, único y **parcial** sobre `(product_id, user_id) WHERE deleted_at IS NULL`—, de modo que retirada la suya la persona puede escribir otra, y **por parcial no admite `DEFERRABLE`**: la carrera muerde en el segundo `INSERT`.

    **Y el promedio no es una columna de `products`.** `rating.average` y `rating.count` se cuentan sobre las vivas en la misma sentencia que trae el producto; una columna desnormalizada obligaría a mantenerla en tres operaciones, y la que se quedara atrás no fallaría, mentiría.

!!! info "Las cuatro columnas de `rate_type` y `fixed_amount` las escribe `V50`"

    Las decidió el responsable del proyecto el 02-09-2026 (`requirements/cm.md` v0.7.0) y se construyeron **ese mismo día, después de rehacer las tripletas**. Una sola migración para las dos tablas: separarlas dejaría en el historial un estado en el que una pieza admite el valor fijo y la otra no.

**Tres cosas del dibujo que conviene leer despacio:**

- **`rate_type` es una columna y no algo deducido de qué campo esté lleno.** Sin ella, «una forma y solo una» sería una propiedad emergente de dos nulos, y una fila con los dos vacíos no permitiría saber **cuál** de las dos quiso declarar quien la insertó.
- **`fixed_amount` comparte forma con `products.price`, `numeric(14,4)`, y no por simetría.** El precio tiene esa forma porque **la escala real la decide la moneda** —`currencies.decimal_places` va de 0 a 4— y un importe de comisión es dinero en esa misma moneda. Con menos decimales, una comisión en una moneda de cuatro no se podría expresar.
- **Y no lleva moneda.** La toma del producto que se vende, de modo que **la misma fila paga cosas distintas** según a cuál se aplique. Es consecuencia aceptada (`cm.md` §1.1.1), no defecto, y **alcanza por igual a las dos clases de tasa** desde el 11-09-2026: las dos se asocian a varios productos, y un importe fijo se lee en la moneda de cada uno.

!!! danger "Y aparece una asimetría con `products` que ninguna restricción puede cerrar"

    `RN-PM-007` valida que los decimales de un precio casen con los de su moneda, y lo hace **en el dominio** porque un `CHECK` no consulta `currencies`.

    **El valor fijo no puede validarse igual**: cuando se declara, **no se sabe en qué moneda se pagará**. En el catálogo por rol el producto todavía no está asociado; en la personalizada no hay producto en absoluto.

    De modo que dos columnas con **la misma forma** tienen **garantías distintas**: el precio casa con su moneda, el importe de comisión **no lo comprueba nadie**.

!!! danger "`products` tiene DOS membresías desde el 02-09-2026, y la unicidad se movió con ellas"

    Un `UPGRADE_MEMBRESIA` declara **de dónde sale y a dónde lleva**. Hasta esa fecha solo declaraba el destino y quién podía comprarlo **se deducía** de la cadena — cualquiera por debajo—, y eso hacía **imposible el salto**: `BECA → ORO` no se podía distinguir de `PLATINO → ORO`, porque los dos eran «subir a ORO».

    **Lo que cambia en el esquema no es una columna, son dos cosas.** `uq_products_upgrade_target` pasa de ser único sobre `target_membership_id` a serlo sobre **la pareja**. La versión anterior prohibía exactamente lo que el origen existe para permitir: dos upgrades activos hacia `ORO`, uno desde `BECA` y otro desde `PLATINO`, **no son dos precios para lo mismo — son dos saltos distintos**.

    **Y `RN-PM-017` ya no cabe en el motor NI A MEDIAS, desde el 07-09-2026.** Aquel `CHECK` exigía que las dos membresías **no fueran la misma**, y eso es exactamente lo que la **renovación** admite: `V61` lo retira (`requirements/pm.md` §5.2.3). La mitad que sobrevive —«el origen no está por encima del destino»— **nunca** cupo aquí, porque obliga a leer el `level` de **dos filas de `memberships`** y un `CHECK` no consulta otra tabla. De modo que la regla pasa a vivir **entera en el caso de uso**, sin la red que tenía. Es el mismo reparto que `RN-PM-007` con los decimales de la moneda, y conviene tenerlo escrito: **una regla crítica sin una sola línea en el esquema** depende de que nadie inserte en esta tabla saltándose la aplicación.

!!! danger "`products` tiene DOS PRECIOS desde el 08-09-2026, y solo uno de ellos se cobra"

    `price` es **el que se cobra**: lo copia `movement_details.unit_price` y sobre él calcula `RN-CM-019`. `purchase_price` es **lo que NEXUS paga por el producto** cuando tiene que comprarlo —ahí se guarda lo que costó—, es **opcional**, **ninguna otra tabla lo lee** y **no sale de administración**: lo devuelven `RF-PM-002` y `RF-PM-003`, y la oferta y el hotlink **no lo seleccionan** (`RN-PM-024`, `requirements/pm.md` §5.2.6).

    **Nació como `public_price` el 08-09-2026 —lo que se anunciaba— y se renombró el 12-09-2026 al cambiar de significado.** El renombrado no es cosmético: cuando el número era un rótulo, publicarlo sin token era una decisión de forma; ahora que es el costo, publicarlo enseña el margen. La columna cambia de significado **vacía de él** — lo que el hotlink llegó a devolver era el rótulo, no un costo.

    **Comparten forma —`numeric(14,4)`— y también moneda**: no hay una segunda `currency_id`; si NEXUS paga en otra moneda, quien registra el costo lo convierte al declararlo, y una compra con su moneda, su tasa y su fecha es una tabla de compras, no una columna de esta. Lo que no comparten es la obligatoriedad, y ahí está toda la decisión: **el nulo de `purchase_price` significa «no se conoce»** —el producto no se ha comprado todavía, o no aplica—, no «costó cero». Los dos estados existen y son distintos, y por eso la columna admite nulo en lugar de llevar `DEFAULT 0`.

    **Y aparece la tercera columna de esta tabla cuya regla el esquema no puede sostener**, junto a `RN-PM-007` y `RN-PM-017`: que un importe **no se cobre** no es expresable en ninguna restricción. Lo único que lo sostiene es **dónde no aparece** — `movement_details` copia `price`, y el puerto que `PM` publica para vender (`ProductCatalog.saleViewOf`) no lleva el otro. Añadirlo ahí bastaría para que empezara a cobrarse sin que nada fallara.

    **`ck_products_price_positive` deja de existir con su nombre**: `V67` lo renombra a `ck_products_price_no_negativo` y lo relaja a `price >= 0` (`RN-PM-006`), porque una **renovación** de una membresía gratuita vale cero. El nombre cambia con el umbral a propósito — dejarle el viejo haría que quien lo leyera creyera que el cero sigue prohibido, que es justo lo que `ProductCommissionCapGuard` creía por escrito.

### 4.1 Lo que estas dos tablas le exigen a una que todavía no existe

Ninguna de las dos guarda una venta, y **las dos escribieron condiciones sobre quien la guarde**:

!!! success "Esa tabla ya existe en papel, y acepta las dos condiciones"

    Desde el 04-09-2026 la venta **existe**: `movements` y `movement_details` ([`requirements/mv.md` §7](requirements/mv.md)), creadas por `V54` con `RF-MV-001`. **Copia el precio unitario y la vigencia en la línea** —lo primero que esta sección exigía— y **no copia la membresía destino**, por el criterio que esta misma sección fijó: se copia lo que puede cambiar, y `RF-PM-004` `EX-004` rechaza cambiarla.

    Lo que **sigue sin dueño** es la segunda condición: copiar lo que la comisión valía. La venta no la copia porque **no devenga comisiones todavía** —es la etapa 5 de `MV`—, de modo que la deuda que §4.1 abrió sigue abierta y ahora se sabe **dónde** se pagará.

| Quién lo exige | Qué exige |
|---|---|
| `requirements/pm.md` §1.4 | Cada compra guardará **el importe que se pagó y la vigencia que compró**, en lugar de leerlos del producto |
| `RN-CM-008` | Cada liquidación guardará **la forma y el valor que aplicó** —el tipo, y el porcentaje o el importe—, en lugar de leerlos de la tasa |
| `RN-CM-017` | Y guardará además **la moneda en que se pagó**, que la tasa fija **no declara**: sale del producto de esa venta |
| `RN-CM-018` | Y **rechazará, no recortará**, lo que exceda el importe de la venta — la suma de la cadena, con una personalizada dentro, sigue sin acotar hasta que exista |

**Las dos primeras dicen lo mismo con distinto sujeto: copiar y no referenciar.** Sin ellas, corregir un precio reescribiría facturas ya emitidas y corregir una tasa reescribiría lo que alguien cobró.

!!! info "Desde el 03-09-2026, parte de esta deuda ya no espera a la liquidación (`RN-CM-019`)"

    Cuando lo que paga un producto sale entero de tasas de rol **asociadas** a él —sin ninguna personalizada en la cadena—, `RF-CM-007` y `RF-CM-003` ya rechazan configurarlo por encima de cien: suman el porcentaje de cada tasa de rol asociada, convirtiendo cada valor fijo a `fixed_amount ÷ price × 100` contra el precio **actual** del producto.

    **No nace ninguna columna ni migración.** La suma no se guarda: se calcula en cada asociación y en cada corrección, leyendo `product_commission_rates` y el precio que `PM` publica. Es la misma razón por la que `RN-CM-001`, `RN-CM-010` y la precedencia de `RN-CM-004` tampoco están en el esquema — un `CHECK` no consulta otra tabla, y menos aún **suma** filas hermanas.

    **Y por eso mismo sigue siendo un tope de configuración, no de venta.** Se calcula contra el precio de hoy: si el producto cambia de precio después (`RF-PM-004`), nadie repite la cuenta. Y una tasa **personalizada** en la cadena —que no se asocia a ningún producto— queda fuera de esta suma. Las dos deudas de esta tabla siguen abiertas para esos dos casos; `RN-CM-019` solo cierra el que se puede ver sin ellos.

!!! danger "El valor fijo hace crecer esta lista, y la parte nueva es la moneda"

    Con solo porcentajes, `RN-CM-008` se satisfacía copiando **un número**. Con el valor fijo hay que copiar **tres cosas** —el tipo, el valor y la moneda—, y **la tercera no está en ninguna tabla de `CM`**: la tasa fija no declara moneda, la toma del producto que se vende.

    De modo que la liquidación es **el primer sitio del sistema donde el importe de una comisión existe con su moneda**. Si no la copia ahí, no hay dónde ir a buscarla después: el producto puede haberse retirado, y la tasa nunca la tuvo.

!!! important "Pero no se copia todo, y la prueba es si el origen puede cambiar"

    **Se copia lo que puede cambiar; lo inmutable se referencia.** El **precio** y la **vigencia** se corrigen (`RF-PM-004`) y el **porcentaje** de una tarifa se corrige (`RF-CM-003`) — los tres habrá que copiarlos.

    **La membresía destino no.** `RF-PM-004` `EX-004` **rechaza cambiarla**, junto al tipo y al código, de modo que leerla del producto da siempre el mismo valor. Copiarla solo añadiría **un sitio más donde el dato pudiera discrepar de sí mismo**, que es el coste que toda desnormalización paga y que ahí no compra nada.

    Es la diferencia entre una copia que **protege el pasado** y una que **duplica el presente**.


## 5. Cómo queda la base de datos

**Veintiuna tablas escritas.** Ninguna se ha retirado nunca.

```mermaid
flowchart TB
    subgraph SP["SP · Sistema Principal · 19 tablas"]
        direction TB
        subgraph ACC["Control de acceso"]
            direction LR
            A1["roles"]
            A2["permissions"]
            A3["role_permissions"]
            C1["users"]
            C2["user_roles"]
        end
        subgraph CAT["Catálogos"]
            direction LR
            A4["memberships"]
            A5["currencies"]
            A6["countries"]
        end
        subgraph PER["Persona"]
            direction LR
            C6["user_memberships"]
            C4["user_supervisors<br/>vendedores Y CLIENTES"]
            C3["refresh_tokens"]
            C5["password_reset_permits"]
        end
        subgraph AUD["Auditoría · todos escriben"]
            direction LR
            B1["audit_change_log"]
            B2["audit_deletion_log"]
            B3["audit_error_log"]
            B4["audit_security_log"]
            OBS["request_log"]
        end
    end

    subgraph PM["PM · 1 tabla"]
        P1["products"]
    end

    subgraph CM["CM · 3 tablas · dos DISEÑADAS"]
        M1["commission_rates"]
        M2["user_commission_rates"]
        M3["product_commission_rates"]
    end


    A4 --> P1
    A5 --> P1
    A1 --> M1
    M1 --> M3
    P1 --> M3
    C1 --> M2
    V2 --> V5
    P1 --> V5
    C1 --> V2
    A5 --> V2
    V1 --> V2
    V3 --> V2
    V6 --> V2
    V4 -.-> V2

    classDef escrita fill:#e7eef0,stroke:#2d5a6b,color:#151b1e
    classDef disenada fill:#f6e6e2,stroke:#a33b2a,stroke-dasharray:3 3,color:#a33b2a
    class A1,A2,A3,A4,A5,A6,B1,B2,B3,B4,C1,C2,C3,C4,C5,C6,OBS,P1,M1 escrita
    class M2,M3 disenada
    class V1,V2,V3,V4,V5,V6 disenada
```

### 5.1 El inventario, con quién es dueño

| Módulo | Tablas | Estado |
|---|---|---|
| `SP` | `permissions`, `roles`, `role_permissions`, `users`, `user_roles`, `memberships`, `user_memberships`, `currencies`, `countries`, `document_types`, `user_supervisors`, `refresh_tokens`, `password_reset_permits`, `exchange_rates`, `brokers`, `user_brokers` | **16, escritas** |
| `SP` · auditoría | `audit_change_log`, `audit_deletion_log`, `audit_error_log`, `audit_security_log`, `request_log` | **5, escritas** |
| `PM` | `products`, `product_comments` | **2, escritas** (`V39`, `V87`) |
| `CM` | `commission_rates`, `user_commission_rates`, `product_commission_rates` | **3, escritas** (`V49`) |
| `MV` | `movements`, `movement_types`, `movement_details`, `payment_methods`, `payment_method_exclusions` | **5, escritas** (`V54`, `V55`) |

**Un módulo, una a cinco tablas.** `SP` tiene diecisiete y los otros tres juntos tienen diez, y eso no es desequilibrio: `SP` es dueño del acceso, de los catálogos transversales y de la auditoría entera, que es infraestructura que todos usan y nadie duplica.

### 5.2 Lo que cambió en `SP` el 01-09-2026, sin tabla nueva

Dos cambios que **no añaden ninguna tabla** y sí cambian lo que el modelo significa:

| Qué | Cambio |
|---|---|
| `users.status` | `PENDIENTE` —declarado y sin usar desde `V18`— es sustituido por **`FTD_PENDIENTE`**. Cambio de dominio **sin migración de datos**: ninguna fila llevaba el valor retirado |
| `user_supervisors` | **Deja de contener solo vendedores.** El cliente cuelga de su vendedor en la misma tabla, y una fila significa dos cosas según quién sea el subordinado: «reporta a» entre vendedores, «fue traído por» cuando es un cliente |

**El segundo es el más barato y el que más alcance tiene.** Cero columnas nuevas, cero migraciones — y `RN-SP-022` se endurece sin cambiar de texto, de modo que tres requerimientos ya construidos empiezan a rechazar más.

### 5.3 Dónde apunta cada clave foránea que cruza un módulo

Son las que siguen —**y desde el 14-09-2026 una de `PM` apunta a `users`**—, y todas van en la misma dirección: **hacia `SP` y hacia `PM`**, nunca al revés.

| Desde | Hacia | Módulo |
|---|---|---|
| `products.target_membership_id` | `memberships` | `PM` → `SP` |
| `products.source_membership_id` | `memberships` | `PM` → `SP` |
| `products.currency_id` | `currencies` | `PM` → `SP` |
| `commission_rates.role_id` | `roles` | `CM` → `SP` |
| `user_commission_rates.user_id` | `users` | `CM` → `SP` |
| `product_commission_rates.product_id` | `products` | `CM` → `PM` |
| `product_comments.user_id` | `users` | `PM` → `SP` — **la primera de `PM` hacia una persona** (14-09-2026) |



**Las claves foráneas sí cruzan; los repositorios no.** Es la distinción de D-25 y conviene tenerla clara mirando este cuadro: la integridad referencial la defiende el motor, y la frontera de código la defiende una regla de ArchUnit. Que una tabla apunte a otra de otro módulo **no autoriza a leerla desde Java**.

### 5.4 Sobre la numeración de las migraciones

La secuencia no es continua —falta el tramo `V8` a `V12`— y no es un descuido: son números consumidos por trabajo que se reorganizó. Un número de migración **no se reutiliza jamás**, porque Flyway lo registra en el historial de cada entorno.


## 6. Lo que el modelo deja pendiente

| # | Punto | Dónde se resuelve |
|---|---|---|
| ~~1~~ | ~~**`memberships` no tiene vínculo con nada.**~~ **Resuelto el 22-08-2026 al aprobar el `plan.md` de `RF-SP-024`:** la asociación vive en **`user_memberships`**, tabla puente con `user_id` como **clave primaria** —que es `RN-SP-014` declarada en el esquema: una membresía por persona—. No es `users.membership_id` porque la asignación lleva vigencia propia, ni una columna en `roles` porque el nivel es de la persona y no del rol. La restricción de que solo los consumidores la tengan (`RN-SP-013`, `RN-SP-018`) **no** es expresable en el esquema: depende de `user_roles` y `roles.role_type`, y PostgreSQL no admite subconsultas en `CHECK`. **Enmendado el 02-09-2026:** desde que `user_roles` copia el `role_type` (§1), **sí lo es** — la copia trae a la tabla el dato del que dependen, y es lo que permitió declarar `RN-SP-025` en el motor. **No se hace hoy**: es otro requerimiento y otra tripleta, y se anota para que no se pierda. **Enmendado otra vez el 05-09-2026:** `user_memberships` deja de tener `user_id` como clave primaria y pasa a ser un **historial** —una fila por membresía que alguien tuvo, con `id` propio—, de modo que `RN-SP-014` ya no la declara la clave primaria sino el par `uq_user_memberships_abierta` + `ex_user_memberships_sin_solape` (§6, `requirements/sp.md` §10.8). Lo que no cambia es el fondo de esta observación: la asociación sigue viviendo en la tabla puente y no en una columna de `users` | — |
| ~~2~~ | ~~**`countries` y `currencies` son islas.**~~ **Resuelto el 04-09-2026, y tardó veinte días.** `currencies` dejó de serlo con `products.currency_id` (`V39`) y `movements.currency_id` (`V54`); **`countries` recibe su primera clave foránea entrante hoy**, desde `payment_method_exclusions` (`V55`, `RN-MV-019`). La razón de ser que esta observación llamaba «futura» resultó ser la de la mitad izquierda —importes con moneda— y **no la de la derecha**: el país no entró por una dirección de nadie, sino por dónde **no** vale un medio de pago. Y queda una asimetría que conviene leer: el país sigue **sin colgar de ninguna persona**, de modo que el sistema sabe en qué países no se puede pagar de cierta forma y **no sabe en qué país está nadie**. **Cerrada del todo el 07-09-2026 (`RN-SP-034`):** `users.country_id` es esa arista que faltaba, y llega `NOT NULL` — no «el país de quien lo declare», sino el de **todos**. La asimetría que este pendiente describía era real y tenía un coste concreto: `RN-MV-019` sabía dónde no vale un medio de pago y no tenía contra qué contrastarlo. **Y el cierre cobra un precio que este documento no había anticipado**: el catálogo de países dejaba de nacer vacío, porque `V22` siembra un superadministrador al que hay que rellenar | **Cerrado** — `requirements/sp.md` §5.1 y §10.10 |
| ~~3~~ | ~~**`request_log` no tiene esquema.**~~ **Resuelto el 25-08-2026 (issue #23):** la tabla se crea en `V35` y sus columnas quedan declaradas en `architecture.md` §6.7 y en §3 de este documento. El hueco no era de documentación: la tabla **no existía**, y cinco secciones de la arquitectura la daban por escrita. Lo que se perdía mientras tanto era todo lo que el manejador global decide no auditar «porque `request_log` ya lo cubre» — los `404`, los `400` de formato y el barrido de rutas | `architecture.md` §6.7 |
| 4 | **`audit_*.actor_id` no declara clave foránea a `users`.** Está documentado como `uuid NULL` sin relación. Si es deliberado —para que eliminar un usuario no arrastre ni bloquee su auditoría— conviene decirlo; si no, falta la restricción. | `architecture.md` §6.6.1 |
| 5 | **Tres estrategias de baja distintas**: `roles` con `deleted_at`, `countries` y `currencies` con `is_active`, `memberships` con ninguna. Cada caso está justificado por separado, pero no hay una regla que diga cuándo se usa cada una. | `architecture.md` §6.4 |
| 6 | **`modelo_v1.mwb` está desactualizado.** Trae `roles.assigned_role_id`, que `security.md` §9 renombra a `parent_role_id`. El modelo gráfico es material de referencia, no autoridad sobre el esquema (Art. V.3). | `DB/modelo_v1.mwb` |
| 7 | **Qué ocurre con `role_permissions` cuando se elimina un rol.** El borrado de `roles` es lógico, y `RF-SP-009` §7 solo dice que sus asociaciones con permisos «dejan de tener efecto»: no declara si las filas se borran o sobreviven. `RF-SP-029` sí lo declara para las suyas —las de `user_roles` **desaparecen** y las de `user_memberships` **se cierran** desde el 05-09-2026—, de modo que dos eliminaciones del mismo módulo resuelven distinto la misma pregunta. Reutilizar el código de un rol eliminado con sus filas de permisos vivas dejaría un vínculo apuntando a un rol que ya no existe para nadie | `RF-SP-009` §7, migración de `roles` |
| ~~8~~ | ~~**`refresh_tokens` y `password_reset_permits` no tienen migración declarada.**~~ **Resuelto:** las crean `V29` y `V37` respectivamente. Lo que sigue — Las crean `RF-SP-034` y `RF-SP-040`, que todavía no tienen `plan.md`; hasta que lo tengan, sus columnas son derivación de la spec y no esquema fijado. Es también donde se decidirá dónde vive la **caducidad de la credencial provisional**, que aquí figura como `users.password_expires_at` | `plan.md` de `RF-SP-034` y `RF-SP-040` |
| 9 | ~~**Nadie purga los tokens.**~~ **Resuelto a medias el 25-08-2026 (issue #25):** `refresh_tokens` ya se purga —por familia entera, treinta días después de que **toda** ella caduque, con constancia auditada y un cerrojo que impide que tres réplicas purguen tres veces (`security.md` §5.5.2)—. Sigue abierto para **`password_reset_permits`**, que no se puede purgar porque todavía no existe: la crea `RF-SP-040`, bloqueado por **D-23**. Y sigue abierto para el `request_log` y los cuatro registros de auditoría, cuyo plazo depende de **D-10** | `security.md` §5.5.2, **D-10**, **D-23** |
| ~~10~~ | ~~**`users.status` declara `PENDIENTE` y ninguna operación entra ni sale de él.**~~ **Resuelto el 01-09-2026:** `RF-SP-045` lo sustituye por **`FTD_PENDIENTE`**, que sí tiene entrada —el registro por enlace— y salida —un depósito confirmado—. `V18` lo había dejado declarado justamente para que estrenarlo no costara alterar el `CHECK` de una tabla en uso, y ese día llegó. Lo que sigue — `security.md` §3.1 lo conserva para un flujo de activación que no existe, y el `CHECK` del dominio cerrado lo admitirá igual. O se retira del dominio hasta que ese flujo se especifique, o se declara qué requerimiento lo poblará | `security.md` §3.1 |

---

## 7. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 21-08-2026 | Creación inicial. Cuatro diagramas derivados de `requirements/sp.md` §10, `security.md` §9 y `architecture.md` §6.6, y seis puntos pendientes que el modelo deja a la vista. | Responsable técnico |
| 0.2.0 | 21-08-2026 | Consecuencias de aprobar `RF-SP-024`. `users` incorpora `first_name`, `last_name` y `must_change_password`; se anota que `username` es inmutable y sin `@`, que ambos identificadores sirven para iniciar sesión, y que su unicidad es **total** —incluidos los eliminados—, al contrario que la de `roles`. | Responsable técnico |
| 0.3.0 | 21-08-2026 | Consecuencias de aprobar `RF-SP-028`. `locked_until` queda nulo también en el bloqueo manual, que no expira y solo se levanta reactivando la cuenta. | Responsable técnico |
| 0.4.0 | 21-08-2026 | Consecuencias de aprobar el `plan.md` de `RF-SP-020`. `countries` gana `updated_at` y su unicidad de nombre pasa a ser funcional sobre `f_unaccent(lower(name))`. | Responsable técnico |
| 0.5.0 | 21-08-2026 | Consecuencias de aprobar `RF-SP-035`. `refresh_tokens` gana `revoked_reason`: solo la revocación por rotación indica robo, y sin ese dato cerrar sesión sería indistinguible de una reutilización. | Responsable técnico |
| 0.6.0 | 22-08-2026 | Entidad nueva `user_supervisors`, derivada de registrar `RF-SP-041` y `RF-SP-042`: la estructura comercial **persona → persona**, con historial y un solo superior vigente por persona. Es la primera tabla del modelo que relaciona dos usuarios entre sí. Se anota por qué lleva clave sustituta cuando las demás asociaciones no la llevan, y que **no concede alcance sobre los datos** —D-22 sigue abierta—. | Responsable técnico |
| 0.7.0 | 22-08-2026 | Consecuencias de aprobar los `plan.md` de `RF-SP-025` a `RF-SP-029`. `users` incorpora **`deleted_at`**, que nace con la tabla en `V18` y no con `RF-SP-029` —`architecture.md` §6.4 la declara obligatoria en toda tabla de negocio y diez requerimientos la leen antes de que alguien la escriba—, y se anota qué requerimiento crea cada una de las tres columnas de control de acceso: las tres son de `RF-SP-034`. §1 incorpora **`user_memberships`**, que faltaba en el diagrama pese a haberla creado `RF-SP-024` \(`V20`\), y con ella queda **cerrado el hueco 1** de §5: la asociación entre una persona y su nivel vive en esa tabla puente, con `user_id` como clave primaria. | Responsable técnico |
| 0.8.0 | 22-08-2026 | Revisión de completitud disparada por los flujos del módulo v0.3.0. §1 incorpora **`users.password_expires_at`** —`RF-SP-038` §7 exige fijar cuándo caduca la credencial provisional y el modelo solo declaraba la marca— y la tabla **`password_reset_permits`**, que el permiso temporal de un solo uso de `RF-SP-040` exige y que no puede ser una columna porque tiene vigencia, consumo e invalidación propios. §4 añade `user_memberships`, que faltaba en el mapa, retira la pregunta «¿quién apunta aquí?» de `memberships` —`user_memberships` la responde desde la v0.7.0— y anota qué tablas están escritas y cuáles no tienen sitio en la secuencia de migraciones. La advertencia de cabecera deja de decir que no hay ninguna migración escrita: de `V1` a `V7` lo están. §5 suma cuatro pendientes: `role_permissions` ante el borrado lógico de un rol, las dos tablas sin migración declarada, la purga que nadie ejecuta y `PENDIENTE` sin transiciones. | Responsable técnico |
| 0.9.0 | 25-08-2026 | **`request_log` deja de ser un hueco** (issue #23). §3 sustituye el marcador «esquema sin definir» por las once columnas reales que crea `V35`, §4 la marca como escrita en el mapa y §5 cierra el **pendiente 3**. Se anota lo que no se deduce del esquema: su `correlation_id` **no** es nulable al contrario que en las cuatro de auditoría —aquellas admiten eventos de procesos internos, esto solo lo escribe una petición HTTP—, un `status` nulo significa que la petición se abortó sin respuesta, y **no participa en la transacción de negocio**, de modo que una operación revertida deja su fila igual. Sigue faltando la purga, que depende de **D-10** (pendiente 9). | Responsable técnico |
| 0.10.0 | 25-08-2026 | §5 cierra **a medias el pendiente 9** (issue #25): `refresh_tokens` ya tiene quien la purgue. Queda abierto para `password_reset_permits` —que no se puede purgar porque no existe— y para el `request_log` y los cuatro registros de auditoría, cuyo plazo depende de **D-10**. El esquema no cambia: la purga no añade columnas, y su único rastro en el modelo es que la tabla deja de crecer sin techo. | Responsable técnico |
| 0.11.0 | 26-08-2026 | **`memberships` gana `color`**, seis dígitos hexadecimales sin `#` y en mayúsculas, con los que el frontend pinta el nivel (`RN-SP-024`). Es obligatorio: un color opcional obliga al navegador a inventarse uno de reserva, que es justo la decisión que este campo saca del frontend. Se anota en §4 la consecuencia de que `RN-SP-008` lo vuelve **incorregible** una vez creado. | Responsable técnico |
| 0.13.0 | 01-09-2026 | **`user_supervisors` cambia de significado sin cambiar de forma** (`RF-SP-045`). Hasta hoy relacionaba **vendedores entre sí**; desde ahora contiene también a los **clientes**, colgando del vendedor que los trajo. No hay columnas nuevas ni aristas nuevas que dibujar, y por eso este cambio **no se ve en el diagrama**: lo que cambia es qué significa una fila. Se propuso una tabla propia, `client_referrals`, y **el responsable del proyecto la descartó** a favor de reutilizar esta: con dos tablas, subir de un cliente hasta el manager que cobra por él exige un join y un caso especial en la hoja; con una, es un recorrido. | Responsable técnico |
| 0.14.0 | 01-09-2026 | **El documento deja de describir solo `SP`.** Su mapa llevaba **dos módulos de retraso**: no conocía `products` —de `PM`, escrita el 27-08-2026— ni `commission_rates` —de `CM`, el 28-08-2026—, y llamaba `password_reset_tokens` a una tabla que se llama **`password_reset_permits`** desde `V37`. Las tres derivas se corrigen. §4 incorpora las **dos áreas que nacieron después** con su diagrama entidad-relación, y §4.1 recoge **lo que esas dos tablas le exigen a una que todavía no existe** —la que guarde las ventas—: copiar el importe y la vigencia, copiar el porcentaje. Con el criterio que separa una copia que **protege el pasado** de una que **duplica el presente**: **se copia lo que puede cambiar; lo inmutable se referencia** — el precio y el porcentaje sí, la membresía destino no, porque `RF-PM-004` `EX-004` rechaza cambiarla. §5 se reescribe entera como **la vista de conjunto de la base**: diecinueve tablas, el inventario por dueño, **las cinco claves foráneas que cruzan un módulo** y la distinción que conviene tener a la vista mirándolas — **las claves foráneas sí cruzan y los repositorios no**, porque la integridad la defiende el motor y la frontera de código una regla de ArchUnit. §5.2 recoge los **dos cambios de este día que no añaden ninguna tabla**: el estado `FTD_PENDIENTE` y los clientes dentro de `user_supervisors`, que cuesta cero columnas y es el de más alcance. | Responsable técnico |
| 0.15.0 | 01-09-2026 | **`CM` se rehace y el modelo lo recoge.** Donde había **una** tabla ahora hay **tres**: `commission_rates` se queda como **catálogo por rol** —pierde el producto, la persona y la vigencia—, `user_commission_rates` guarda la **excepción por persona** con su vigencia y **sin rol**, y `product_commission_rates` es la **asociación** que decide sobre qué producto rige cada tasa. **Es la primera vez que este documento describe una tabla ya escrita que hay que rehacer**, y el mapa la marca como tal. Dos detalles del esquema merecen leerse: la **clave primaria de la asociación es `(product_id, role_id)`**, de modo que «un solo porcentaje por rol y producto» **no es una regla que alguien comprueba, es la forma de la tabla**; y `role_id` está ahí **copiado a propósito**, con una **clave foránea compuesta** hacia `commission_rates(id, role_id)` que hace **imposible**, no improbable, que diverja del rol que la tasa declara. **La vigencia queda en una sola tabla**, y con ello el `EXCLUDE` de no solapamiento vuelve a caber donde tiene que estar — sacar el producto fuera lo habría hecho cruzar dos tablas, que ningún índice hace. | Responsable técnico |
| 0.16.0 | 02-09-2026 | **Lo que este documento describía como diseñado pasa a estar escrito.** `V49` crea `user_commission_rates` y `product_commission_rates` y rehace `commission_rates`, de modo que el sistema llega a **veintiuna tablas** y **no queda ninguna pendiente de escribir** — la primera vez desde que existe este documento. **`V49` es además la primera migración del proyecto que borra datos a propósito**, y conviene que quede dicho por qué: **ninguna de las cuatro formas del modelo anterior tenía traducción al nuevo**, y dejarlas caer a «tasa de rol» las habría convertido en filas plausibles y falsas — con su porcentaje, sin asociación, y sin nada que dijera que ya no significan lo que el día que se escribieron. Se vacía para que la pérdida sea **visible** en vez de silenciosa. Al construirlo aparece además una restricción que el diseño no había previsto y que el esquema **no puede declarar**: `RN-CM-015` —una tasa asociada no se retira—, porque `product_commission_rates` no tiene retiro lógico y su fila sobreviviría apuntando a una tasa muerta; una clave foránea no distingue una fila viva de una retirada lógicamente, de modo que esto vive en el caso de uso y no en el motor. | Responsable técnico |
| 0.17.0 | 02-09-2026 | **Vuelve el valor directo a las comisiones** (`requirements/cm.md` v0.7.0) y el mapa lo recoge **antes de que exista la migración**: las dos tablas de tasas ganan `rate_type` y `fixed_amount`, cuatro columnas **diseñadas y sin escribir** que van marcadas en cabecera y en §4. Es la situación en la que estuvo este documento hasta `V49`, y vuelve a estarlo. **`fixed_amount` comparte forma con `products.price`, `numeric(14,4)`, y no por simetría**: el precio la tiene porque **la escala real la decide la moneda** —`currencies.decimal_places` va de 0 a 4— y un importe de comisión es dinero en esa misma moneda; con menos decimales, una comisión en una moneda de cuatro no se podría expresar. **Y el dibujo destapa una asimetría que ninguna restricción puede cerrar**: `RN-PM-007` valida que los decimales de un precio casen con su moneda, y **el valor fijo no puede validarse igual** porque al declararlo **no se sabe en qué moneda se pagará** — en el catálogo por rol el producto todavía no está asociado, y en la personalizada no hay producto en absoluto. Dos columnas con la misma forma, **garantías distintas**. §4.1 crece en consecuencia: con solo porcentajes, `RN-CM-008` se satisfacía copiando **un número**; ahora la liquidación tendrá que copiar **el tipo, el valor y la moneda**, y **la tercera no está en ninguna tabla de `CM`** — es el primer sitio del sistema donde el importe de una comisión existirá con su moneda, y si no la copia ahí no habrá dónde buscarla después. | Responsable técnico |
| 0.18.0 | 02-09-2026 | **`user_roles` gana `role_type`, la primera columna desnormalizada del sistema**, y con ella `RN-SP-025` deja de ser una regla declarada sin nadie que la sostenga. Copiar un dato es normalmente el error que este documento evita; §1 nombra la diferencia: la copia está atada por una **clave foránea compuesta** —de modo que no puede divergir— y su origen es **inmutable**, porque `RF-SP-004` corrige nombre y descripción y no el tipo. Es el mismo patrón que `product_commission_rates`, y la condición que lo hace legítimo es la misma en los dos sitios: **el dato copiado no cambia en su origen**. Sobre esa columna, un **índice único parcial** sobre `(user_id) WHERE role_type = 'VENDEDOR'` cierra la regla en el motor. Se decidió así por un precedente y no por gusto: `RN-SP-018` se comprobaba en el caso de uso, **no aguantó la concurrencia** y hubo que corregirla el 26-08-2026 **sobre esta misma tabla**. Y el hallazgo 1 queda **enmendado**: decía que `RN-SP-013` y `RN-SP-018` no son expresables en el esquema por depender de `user_roles` y `roles.role_type`, y desde hoy **sí lo son** — la copia trae a la tabla justo ese dato. No se hace en este pase, y se anota para que no se pierda. | Responsable técnico |
| 0.19.0 | 02-09-2026 | **Vuelve a haber tablas diseñadas y sin escribir**, y son cuatro: `movements`, `movement_types`, `movement_details` y `payment_methods`, del `MV` renacido. Las creará `V51` con `RF-MV-001`. **Su forma no se copia a este documento**, y eso es lo que cambia respecto del intento anterior: aquí es una **vista derivada** y allí, en [`requirements/mv.md` §7](requirements/mv.md), está la fuente — dos copias de un esquema que todavía se discute divergen sin que nadie lo note, y este mapa ya llegó una vez con **dos módulos de retraso**. Lo que sí se recoge es la respuesta a §4.1, que llevaba abierta desde el 01-09-2026: **la tabla que aquellas dos exigían ya existe en papel y acepta sus condiciones** — copia el precio y la vigencia en la línea, y **no copia la membresía destino** porque `RF-PM-004` `EX-004` rechaza cambiarla. **La segunda condición sigue sin dueño**: copiar lo que la comisión valía no lo hace la venta, porque no devenga comisiones todavía — es la etapa 5 de `MV`, y ahora al menos se sabe dónde se pagará esa deuda. | Responsable técnico |
| 0.20.0 | 02-09-2026 | **La primera de las tres migraciones que se disputaban el `50` está aplicada, y no es ninguna de las que lo habían reservado.** `V51__seed_movements_permissions.sql` siembra los cuatro permisos `movements:` de [`requirements/mv.md` §6](requirements/mv.md) y **no crea ninguna tabla**: la tarea que los siembra no depende de nada, y un permiso sin endpoint que lo exija no rompe nada, mientras que una tabla sin el caso de uso que la escribe promete algo que no existe. Con ello el reparto queda en **`V51` permisos de `MV`, `V52` el `role_type` de `RN-SP-025` y `V53` las cuatro tablas de `MV`** — el número lo toma quien se aplica primero, y las otras dos seguían siendo reservas sin una línea de `SQL`. **Lo que hay que leer del bloque de arriba no es el número sino la asociación**: esos cuatro permisos van **solo a `SUPERADMIN`**, por decisión del responsable del proyecto, de modo que por `RN-SEG-003` ningún rol bajo `ADMIN` podrá declararlos mientras la reserva siga en pie ([`security.md` §4.4](security.md)). El esquema no cambia: `role_permissions` recibe cuatro filas y nada más. | Responsable técnico |
| 0.21.0 | 02-09-2026 | **`products` gana `source_membership_id`**: un upgrade declara **de dónde sale**, no solo a dónde lleva (decisión del responsable del proyecto, `requirements/pm.md` v0.15.0). Hasta hoy quién podía comprarlo **se deducía** de la cadena —cualquiera por debajo del destino—, y esa deducción hacía **imposible el salto**: `BECA → ORO` y `PLATINO → ORO` eran el mismo producto. **Lo que cambia no es una columna, son dos cosas**: `uq_products_upgrade_target` pasa de ser único sobre el destino a serlo sobre **la pareja**, porque la versión anterior prohibía exactamente lo que el origen existe para permitir — dos upgrades activos hacia `ORO` desde sitios distintos **no son dos precios para lo mismo, son dos saltos**. Y `RN-PM-017` **solo cabe a medias en el motor**: un `CHECK` puede exigir que las dos membresías no sean la misma, y **no puede** exigir que el origen esté por debajo del destino — eso obliga a leer el `level` de dos filas de `memberships`, y un `CHECK` no consulta otra tabla. Esa mitad vive en el dominio, por el mismo motivo exacto que `RN-PM-007` con los decimales de la moneda. | Responsable del proyecto |
| 0.22.0 | 03-09-2026 | **Nace `RN-CM-019`** (`requirements/cm.md` v0.8.0) y **el esquema no cambia**: ninguna tabla gana columna, no hay migración. §4.1 recoge la consecuencia — un producto ya no se puede configurar para pagar más del 100 % de sí mismo por la vía de sus tasas de rol asociadas, comprobado al asociar (`RF-CM-007`) y al corregir (`RF-CM-003`) sumando `percentage` o `fixed_amount ÷ price × 100` de cada asociación viva de `product_commission_rates` contra el precio que `PM` publica **en ese instante**. **Es el mismo tipo de regla que `RN-CM-001`, `RN-CM-010` y la precedencia de `RN-CM-004`**: no cabe en un `CHECK` ni en un `EXCLUDE` — aquí, además, porque suma filas hermanas y lee otra tabla de otro módulo — y vive en el dominio. Dos deudas de §4.1 quedan explícitamente **fuera** de este cierre y siguen esperando la liquidación: la tasa **personalizada** de la cadena, que no se asocia a ningún producto, y el precio **cambiando después** de haberse comprobado la suma. | Responsable del proyecto |
| 0.23.0 | 04-09-2026 | **Las cuatro tablas de `MV` dejan de estar diseñadas y pasan a existir**, con `V54__create_movements.sql` y `RF-MV-001`. El sistema llega a **veinticinco tablas** y **no queda ninguna pendiente de escribir** — la segunda vez desde que existe este documento. `movement_types` y `payment_methods` se crean **y se siembran en la misma migración**: una tabla de tipos vacía deja el módulo sin poder registrar nada, y separar la siembra permitiría desplegar ese estado. **`movements` estrena algo que ninguna otra tabla del sistema tiene: no lleva `updated_at` ni `deleted_at`** (`RN-MV-001`), porque una venta no se edita y no se borra, solo avanza su `status` — y eso obliga a que el gestor de auditoría de la aplicación no la trate como a las demás, que es la consecuencia práctica de la regla. **El número cambió por tercera vez, y la lección merece quedarse escrita**: estas tablas reservaron el `51`, luego el `52`, luego el `53`, y acabaron en el `54`, porque cada vez alguien con el `SQL` ya escrito se llevó el hueco — la última, `V53__products_source_membership.sql` el 03-09-2026. **Una tabla reservada no está reservada**, y Flyway deja fuera sin error y sin aviso una migración con número por debajo del último aplicado. **Y queda declarada una tensión que este documento no puede resolver solo**: los cinco importes de `MV` son `numeric(14,2)` mientras `currencies.decimal_places` admite de cero a cuatro y `products.price` es `numeric(14,4)` justamente por eso, de modo que una moneda de tres o cuatro decimales **redondearía en silencio lo que alguien pagó**. Hoy no ocurre —la única moneda sembrada es `USD` con dos— y el caso de uso rechaza al registrar el precio que no quepa, en lugar de dejarlo pasar. Lo que hay que decidir es si los importes del libro suben a `numeric(14,4)` o si el sistema declara que no admitirá monedas de más de dos decimales. | Responsable técnico |
| 0.24.0 | 04-09-2026 | **`countries` deja de ser una isla, veinte días después de crearse**, y con ello se cierra la observación 2 de §6. La clave foránea entrante no llegó por donde esa observación la esperaba —«direcciones con país»— sino desde **`payment_method_exclusions`** (`V55`, `RN-MV-019`): la tabla que declara **dónde NO vale cada método de pago**. El sistema llega a **veintiséis tablas**. **La asimetría que queda merece leerse**: el sistema ya sabe en qué países no se puede pagar de cierta forma y **sigue sin saber en qué país está nadie** — `users` no guarda país, y no hace falta, porque la restricción **se publica y no se comprueba**; quien filtra es el cliente que consume el catálogo. **Declara la exclusión y no el permiso**, al revés que `RN-CM-012`: un método sin filas vale en todas partes, de modo que los tres sembrados no exigen declarar nada y añadir un país no obliga a revisar el catálogo de medios — el precio es que olvidar una exclusión **no falla: ofrece**. Clave primaria compuesta por las dos columnas, como `role_permissions`: la fila **es** la relación y no tiene identidad propia. **Y se corrige un hueco de ayer**: §5.1 no tenía fila para `MV` pese a que `V54` le había creado cuatro tablas — el inventario por módulo llevaba un día diciendo que el sistema tenía tres módulos con tablas cuando ya eran cuatro. | Responsable técnico |
| 0.25.0 | 04-09-2026 | **`movements.seller_id` pasa a admitir nulo** (`RN-MV-003`), por decisión del responsable del proyecto: **comprar no es cosa solo de los clientes**, y un agente también compra. La columna se declaró `NOT NULL` el mismo día suponiendo que toda venta tiene a quién atribuirse, y esa suposición **dejaba fuera a alguien que `sp.md` sabía que existe desde el principio**: `RN-SP-019` declara que la cúspide de la fuerza comercial **no declara superior**, de modo que esa persona no podía comprar nada — no por una decisión de negocio, sino porque la venta no sabía a quién atribuirla. Se retira la exigencia y **no la deducción**: quien tiene superior sigue produciendo una venta atribuida a él. **La consecuencia es de `CM` y hay que decirla aquí porque la columna es la que la produce**: `RN-CM-011` liquida por *override* recorriendo la cadena hacia arriba **desde el vendedor**, de modo que una venta sin vendedor **no comisiona a nadie**. Es correcto y es un caso que la liquidación tendrá que tratar; la alternativa era inventar una atribución, y una comisión pagada a quien no vendió **no se detecta**, porque el dinero sale y el número cuadra. Se enmienda `V54` en lugar de añadir una migración: no está fusionada, y una tabla que nace y se corrige el mismo día no merece dos entradas en el historial del esquema. | Responsable del proyecto |
| 0.26.0 | 05-09-2026 | **`user_memberships` deja de ser una foto y pasa a ser un historial**, por decisión del responsable del proyecto (`requirements/sp.md` v1.35.0, `V56`). §1 rehace la entidad: gana **`id`** como clave primaria —`user_id` se repite— y **`closed_at`**, y la cardinalidad con `users` pasa de `||--o|` a `||--o{`. **Las dos fechas de fin no son redundancia**: `ends_at` es hasta cuándo se **pagó** y `closed_at` cuándo dejó de ser la actual, de modo que una membresía de treinta días reemplazada el día doce conserva las dos y se puede distinguir **vencer** de **que te la sustituyan**. `RN-SP-014` ya no la declara la clave primaria, sino **dos** restricciones que hacen trabajos distintos —`uq_user_memberships_abierta`, único parcial sobre `WHERE closed_at IS NULL`, y `ex_user_memberships_sin_solape`, un `EXCLUDE` sobre el intervalo—, y la observación 1 de §6 queda enmendada con ese cambio sin que su fondo se mueva. **Y la observación 7 corrige un dato que dejó de ser cierto**: al eliminar una persona, las filas de `user_roles` siguen desapareciendo pero las de `user_memberships` **se cierran**, porque borrarlas destruiría el historial de alguien cuya fila en `users` sobrevive al borrado lógico. | Responsable del proyecto |
| 0.27.0 | 05-09-2026 | **La cardinalidad entre `users` y `user_memberships` deja de ser opcional**: pasa de `||--o{` a `||--|{`. `RN-SP-018` reescrita obliga a que **toda** persona tenga una membresía abierta —superadministrador y funcionarios incluidos—, y quien no recibe una al registrarse arranca en la de código `BECA` (`V46`). **No hay cambio de esquema**: `V57` **rellena** y no altera nada — una fila `BECA` para toda persona que no tuviera ninguna abierta. **Y el invariante no se puede declarar en el motor**, cosa que conviene tener escrita porque el Art. V.6 empuja a intentarlo: «toda fila de `users` tiene una fila abierta en `user_memberships`» es una comprobación **entre tablas** que ningún `CHECK` alcanza, y una clave foránea en sentido contrario no existe porque la fila de la membresía nace después que la persona. Lo sostienen el relleno de `V57` y las tres operaciones que crean personas. | Responsable del proyecto |
| 0.28.0 | 07-09-2026 | **`products` gana `scope` e `implementation`**: hasta dónde se muestra un producto y quién aplica lo que otorga (`requirements/pm.md` v0.17.0, `V59`). **Dos columnas obligatorias, sin `DEFAULT` y en los dos tipos**, que se apartan de todo lo que este mapa tenía en esa tabla: `RN-PM-002` y `RN-PM-016` obligan o prohíben **según el tipo**, y estas no distinguen — un bot también se muestra en algún sitio y también se entrega de alguna forma. **`scope` es una escala y no un reparto**: `HOTLINKS` **incluye** la tienda, de modo que no existe forma de publicar algo solo en hotlinks y el día que ese caso aparezca lo que entra es un **tercer valor**, no un cambio de significado de los dos que hay — cambiárselo reescribiría en silencio cada fila ya declarada. **`implementation` es lo primero de `PM` que gobierna a `MV`**: `RN-MV-020` deja de conceder la membresía en toda venta confirmada y la concede **solo** cuando el producto es automático (`requirements/mv.md` v0.9.0). **Y de ahí sale la deuda que este mapa tiene que registrar**: §4.1 declara desde el 01-09-2026 que **se copia lo que puede cambiar y lo inmutable se referencia**, la implementación **se corrige** (`RF-PM-004`), y **`movement_details` no tiene esa columna** (`V54`) — sin ella, corregir un producto reescribiría cómo se entregan ventas ya hechas, en los dos sentidos y sin fallar en ninguno. No se escribe hoy porque `RF-MV-003` —el único que leería el valor— sigue bloqueado por **D-26**, y queda dicho que la copia **debe existir antes** de que ese requerimiento se construya. Es la tercera cosa que `movement_details` tendrá que congelar, junto al código y el nombre del producto que `requirements/mv.md` v0.8.0 dejó anotados. **Ninguna tabla nueva**: el sistema sigue en veintiséis. | Responsable del proyecto |
| 0.29.0 | 07-09-2026 | **Cae `ck_products_origen_distinto` (`V61`)**, y con ella **la última línea de `RN-PM-017` que vivía en el esquema**. La retira la **renovación**: un upgrade puede declarar la misma membresía en los dos lados —`BECA → BECA`, `ORO → ORO`— porque lo que se vende ahí es **tiempo y no nivel** (`requirements/pm.md` v0.19.0 §5.2.3), y esa restricción prohibía exactamente eso. **Ninguna columna cambia**: es una restricción que se va, y el sistema sigue en veintiséis tablas. Lo que hay que leer es lo que queda al descubierto: la mitad superviviente de la regla —«el origen no está por encima del destino»— **nunca cupo en un `CHECK`**, porque obliga a leer el `level` de dos filas de `memberships`, de modo que a partir de hoy **una regla crítica de `PM` no tiene una sola línea declarada en el motor** y depende por completo del caso de uso. Es el mismo reparto que `RN-PM-007` tiene con los decimales de la moneda, con la diferencia de que aquel nunca tuvo red y este la pierde. **`uq_products_upgrade_target` no se toca**: `(BECA, BECA)` es una pareja como cualquier otra, y sigue admitiendo un solo producto activo por pareja. | Responsable del proyecto |
| 0.30.0 | 07-09-2026 | **Nace `exchange_rates`, la tabla veintisiete**: a cuánto se cambia una moneda por otra, con su vigencia (`requirements/sp.md` v1.37.0 §10.14, `V65`). **Es la primera tabla que cuelga de `currencies`, y lo hace por partida doble** —origen y destino apuntan a la misma tabla—, que es lo que obliga a que su restricción de no solapamiento compare **las dos** columnas: con una sola, `USD → COP` y `USD → EUR` no podrían convivir, y eso es justo lo que la regla admite. **La restricción es un `EXCLUDE USING gist` y no un `UNIQUE`**, y es la tercera vez que este modelo lo necesita —`V44`, `V49` y ahora—: lo que no puede repetirse **no es un valor, es un solapamiento de rangos**, y dos tasas con fechas distintas pasarían cualquier unicidad mientras comparten días. Va con `daterange(..., '[]')` —cerrado, o dos tasas que se tocan en un extremo no se verían— y **parcial sobre las vivas y activas**, o retirar dejaría el periodo bloqueado para siempre. **El precio es `numeric(18,8)` y rompe deliberadamente con `numeric(14,4)`**, que es la forma de todo importe del sistema: una tasa **no es un importe**, y con cuatro decimales `COP → USD` se guardaría redondeada y una moneda más devaluada se guardaría como **cero**. Es la primera columna numérica del modelo cuya escala la decide **la aritmética** y no `currencies.decimal_places`. **Y §2 se corrige de paso**: decía que sus tres catálogos «no tienen ninguna clave foránea entrante», y eso dejó de ser cierto el 27-08-2026 con `products.currency_id` — §5.3 ya lo recogía y esa línea no. | Responsable del proyecto |
| 0.31.0 | 07-09-2026 | **`users` gana `country_id`, `NOT NULL`** (`RN-SP-034`, `V64`, `requirements/sp.md` v1.38.0). Ninguna tabla nueva: siguen siendo veintisiete. §1 dibuja la arista `countries ||--o{ users` y §2 anota lo que le hace al catálogo. **La decisión que este documento tiene que dejar razonada es por qué el país es una columna y la membresía y el superior no lo son**: aquellas dos llevan tabla propia porque **tienen vigencia** —se conceden, vencen, se sustituyen, y hay que poder decir cuál regía *entonces*—; el país no tiene periodo, nadie pregunta dónde estaba alguien el mes pasado, y una tabla puente para un dato sin vigencia añadiría un `join` a cada consulta de usuario a cambio de nada. **Cierra el pendiente 2 de §6**, que llevaba abierto desde el 21-08-2026 y que el 04-09-2026 solo se había cerrado a medias: `payment_method_exclusions` sacó a `countries` de ser una isla, pero la asimetría que quedaba escrita —«el sistema sabe en qué países no se puede pagar de cierta forma y **no sabe en qué país está nadie**»— seguía viva. **Y el cierre cobra un precio que no estaba anticipado**: `countries` es ahora el destino de la **única clave foránea entrante `NOT NULL` del modelo**, de modo que el catálogo **no puede estar vacío mientras exista un usuario** — y `V22` siembra un superadministrador. El catálogo deja de nacer vacío y se siembra **Colombia** (`COL`), con UUID v7 literal, igual que `V15` con `USD`; `RN-SP-009` hace esa elección **incorregible**. **Y una advertencia sobre la numeración, que es exactamente el caso que §5.4 y la cabecera describen, y que este documento vivió dos veces el mismo día.** La v0.30.0 anotó `V65` para `exchange_rates` **sin una línea de `SQL` escrita**, de modo que la migración del país se planificó con ese número. Horas después las tripletas de `RF-SP-047` a `RF-SP-050` fijaron `V65` para la tabla y `V66` para sus permisos **en cuatro documentos aprobados**, y el país pasó a **`V64`**. La lección, escrita: **una reserva de una línea no vale nada; una planificada en cuatro documentos sí vale** — y ninguna de las dos garantiza el número, porque Flyway se lo da a quien se aplique primero. Las tablas de `MV` cambiaron de número tres veces por esto mismo. | Responsable técnico |
| 0.32.0 | 08-09-2026 | **`products` gana `public_price`: dos precios en la misma fila, y solo uno es dinero** (`RN-PM-023`, `RN-PM-024`, [`requirements/pm.md`](requirements/pm.md) v0.21.0, `V67`). `price` sigue siendo **el que se cobra** —lo copia `movement_details.unit_price` y sobre él calcula `RN-CM-019`—; el nuevo **solo se muestra**, y **ninguna otra tabla lo lee**. **Comparten forma y también moneda**: no hay una segunda `currency_id`, porque un importe en otra moneda no sería un rótulo sino un segundo precio con su tasa y su vigencia. **Lo que no comparten es la obligatoriedad, y ahí está la decisión**: el nulo significa «este producto no declara precio público» —se anuncia con `price`—, y no «vale cero»; los dos estados existen y son distintos, de modo que la columna admite nulo en lugar de llevar `DEFAULT 0`. **Aparece la tercera regla de esta tabla que el esquema no puede sostener**, junto a `RN-PM-007` y `RN-PM-017`: que un importe **no se cobre** no cabe en ninguna restricción — lo único que lo sostiene es **dónde no aparece**, y añadirlo al puerto de venta bastaría para que empezara a cobrarse sin que nada fallara. **Y `ck_products_price_positive` cambia de umbral y de nombre**: pasa a `ck_products_price_no_negativo` con `price >= 0`, porque `RN-PM-006` deja de exigir «mayor que cero» — una **renovación** de una membresía gratuita vale cero. El nombre se cambia con el umbral a propósito: dejarle el viejo haría creer que el cero sigue prohibido, que es exactamente lo que `ProductCommissionCapGuard` creía **por escrito** y lo que obliga a corregirlo. **Ninguna tabla nueva**: el sistema sigue en veintisiete. | Responsable del proyecto |
| 0.33.0 | 08-09-2026 | **Nace `document_types`, y `users` gana seis columnas** (`RN-SP-035` a `RN-SP-037`, `requirements/sp.md` v1.41.0): identidad documental —tipo y número— y contacto —dos líneas de dirección, ciudad y teléfono—. **Lo que hay que leer de este cambio no es la tabla, es lo que la tabla NO contiene.** Se pidió validar que toda persona sea mayor de edad, y la validación **no es una comprobación**: el catálogo lleva **solo documentos de adulto**, sin columna que marque cuáles sí y cuáles no. Con una columna, registrar a un menor sería posible y rechazado; sin ella es **inexpresable**, porque no hay identificador que poner y la clave foránea no admite otra cosa. **Es el patrón inverso al de `user_roles.role_type`** y merece verse junto a él: allí se **trajo** un dato a la tabla para que la regla cupiera en el motor; aquí se **quitó** una opción del catálogo para que la regla no hiciera falta. Los dos acaban igual — la regla la sostiene el esquema y no un `if`—, y el segundo es más barato. **La consecuencia sobre el catálogo es que no se administra por API ni siquiera para desactivar**, al revés que `countries` y `currencies`: añadir una fila aquí es abrir la puerta a los menores, y eso no puede ser una llamada. **Y la asimetría que este documento tiene que dejar dicha**: las seis columnas nacen **nulables** aunque documento y teléfono sean obligatorios en la API. Un país de relleno es neutro; **un número de documento de relleno es falso sobre la identidad de una persona**, y `V22` siembra un superadministrador que no tiene ninguno. El esquema admite la ausencia porque es la verdad sobre esas filas, y la API la prohíbe para que no se creen más. | Responsable técnico |
| 0.34.0 | 08-09-2026 | **Nacen `brokers` y `user_brokers`** (`RN-SP-038` a `RN-SP-040`, [`requirements/sp.md`](requirements/sp.md) v1.42.0), por decisión del responsable del proyecto: el catálogo de los brokers con los que opera la plataforma y la cuenta que cada persona tiene en cada uno. **`brokers` guarda solo el nombre**, y de ahí sale que el nombre **sea la clave de negocio** —índice único funcional sobre `f_unaccent(lower(name))`, como `countries` y `document_types`— con la consecuencia declarada de que **renombrar un broker es una migración**. **La decisión que carga el diseño está en el único de `user_brokers`**: va sobre **`(broker_id, external_id)`** y **no** sobre `(user_id, broker_id)`, de modo que una persona puede tener **varias cuentas en el mismo broker** —lo normal en el ramo— y una cuenta **no puede ser de dos personas**. Es la primera tabla del sistema cuyo único **no incluye a la persona**, y el intercambio contrario —el único obvio— prohibiría lo que sí se admite y permitiría lo que no. **`broker_username` admite nulo y su nulo significa «el broker todavía no lo ha confirmado»** (`RN-SP-040`): la cuenta se declara con el broker y el identificador, y el nombre de usuario lo rellena después un webhook. **Ninguna de las dos lleva `deleted_at`**: el catálogo se apaga con `is_active` desde una migración, y desvincular una cuenta **no está decidido** — poner la columna hoy sería declarar una operación que nadie implementa. El modelo pasa a **treinta tablas**. | Responsable del proyecto |
| 0.35.0 | 09-09-2026 | **`payment_methods` gana `visibility`, y con ella el sistema separa «¿sirve?» de «¿se ofrece?»** (`RN-MV-022`, `RN-MV-023`, `requirements/mv.md` v0.12.0). Ninguna tabla nueva. **La forma del esquema de `MV` no se copia aquí** —regla de la v0.19.0 de este documento, y sigue valiendo—, pero la decisión sí, porque es de modelado y alcanza a todo catálogo futuro: **`is_active` y `visibility` son dos ejes y no uno**. Fundirlos en un `status` de tres valores era la salida fácil, y las **cuatro combinaciones significan algo** — `ACTIVO`+`INTERNO` es el pago gratuito, que sirve para pagar y nadie elige; `INACTIVO`+`PUBLICO` es un método retirado que algún día vuelve. **Y hay una prueba de que el eje faltaba**: `requirements/mv.md` proponía sembrar `POINTS` con `is_active` en falso «para que siga en la tabla y no se ofrezca» — es decir, **usaba `is_active` para significar visibilidad**, que es exactamente la confusión que esta columna deshace. **Es enumerado y no booleano**, con el criterio de `products.scope` y no el de `countries.is_active`: el dominio **es candidato a crecer** —«solo para administración», «solo en un canal»— y con un booleano cada distinción nueva costaría una columna. **Lo que esto cierra es un agujero de un día**: `RN-PM-006` admitió el precio cero el 08-09-2026 y `movements.payment_method_id` es `NOT NULL`, de modo que toda compra gratuita estaba obligada a declarar tarjeta, PSE o puntos — y las tres son falsas. El esquema no fallaba: mentía. | Responsable del proyecto |
| 0.36.0 | 10-09-2026 | **`user_brokers` gana `status`** (`RN-SP-045`, [`requirements/sp.md`](requirements/sp.md) v1.49.0), por decisión del responsable del proyecto: `REGISTER` o `FIRST_DEPOSIT`, con `CHECK` en el motor como `users.status`. Ninguna tabla nueva; el modelo sigue en **treinta**. **Es la segunda columna de esta tabla que espera al webhook de `RF-SP-054`** —la primera es `broker_username`— y conviene leerlas juntas, porque **se comportan distinto**: aquella nace nula y **no se lee**, esta nace en `REGISTER` y **se lee desde el primer día** (`RF-SP-055`, `RF-SP-056`). Esa diferencia es lo que la separa del defecto de la columna dormida: mientras no exista el webhook, `REGISTER` **no es un valor de relleno, es la verdad** — ninguna cuenta tiene depósito confirmado. **Sus dos valores van en inglés y son los únicos del modelo**: `users.status`, `products.status` y `movements.status` van en castellano, y aquí se cede porque son el vocabulario del broker que los va a escribir. **Y queda escrito lo que no se puede hacer con ella**: `user_brokers.status` y `users.status` **no se derivan el uno del otro**; aquel dice si la cuenta del sistema opera y este qué pasó en el broker, y una persona con dos cuentas puede tener una depositada y otra no — el caso existe y **decidirlo es de `RF-SP-054`**, no de esta columna. | Responsable del proyecto |
| 0.37.0 | 10-09-2026 | **`user_supervisors` se lee por primera vez EN PROFUNDIDAD, y `user_brokers` gana su índice de búsqueda** (`RN-SP-047`, [`requirements/sp.md`](requirements/sp.md) v1.50.0). Ninguna tabla ni columna nueva; el modelo sigue en **treinta**. Lo que cambia es **cómo se lee** una tabla que lleva desde `V21` respondiendo siempre a un solo nivel: `RF-SP-057` recorre la rama entera con una **recursiva**, la primera del sistema. **Dos consecuencias de modelado quedan escritas por adelantado**: la terminación **no descansa en que los datos sean acíclicos** —lo son, porque `RN-SP-020` ata esta cadena a la de roles— sino en que la recursión acumule con **`UNION`** y no con `UNION ALL`; y el predicado **`ended_at IS NULL` tiene que ir en los DOS brazos**, porque el índice parcial de `V28` es el que sostiene el recorrido y omitirlo en el recursivo haría descender por la estructura **de ayer** sin que nada fallara. Se declara además **`ix_user_brokers_busqueda`**, gin de trigramas sobre `f_unaccent(lower(external_id))`, con las **expresiones del predicado** y no otras — la lección que `ix_users_busqueda` dejó escrita en `V29`: si divergen, el índice existe, el planificador no lo usa nunca, y el defecto no sale como error sino como lentitud que nadie relaciona con su migración. | Responsable del proyecto |
| 0.38.0 | 10-09-2026 | **`users` gana `company_phone`** \(`V83`\): el teléfono de la empresa, junto al personal que ya existía. Misma forma que `phone` —`varchar(20)`, normalizado a dígitos con `+` opcional, `ck_users_company_phone_format`— y **opcional**, al revés que aquel: `RN-SP-037` exige el personal y no este, porque exigirlo bloquearía el alta de quien no tenga empresa. **Sin índice**, como `phone`: ninguna consulta filtra ni ordena por él. | Responsable técnico |
| 0.39.0 | 11-09-2026 | **`user_commission_rates` gana `product_id` `NOT NULL`** \(`V84`\): la excepción por persona deja de regir sobre todo el catálogo y pasa a declarar **su** producto. **Columna propia y no tabla de asociación**, y la asimetría con la tasa de rol es deliberada: aquella es **catálogo reutilizable** —una fila que rige en muchos productos, y por eso tiene `product_commission_rates`—, mientras que una personalizada ya es de **una sola persona** y no hay nada que reutilizar; una tabla intermedia solo añadiría un salto. **El `EXCLUDE` se rehace con el producto dentro**: pasa de «una vigente por persona» a «una vigente por persona **y producto**», de modo que la misma persona puede tener varias a la vez sobre productos distintos. Se cierra además la consecuencia de no declarar moneda que este documento tenía anotada para esta tabla: al conocer un producto, conoce una moneda. **La migración ABORTA si hay filas vivas**: no hay dato que inventar —a qué producto pertenecía una tasa que valía para todos no se puede adivinar— y rellenarlas produciría filas plausibles y falsas, que es exactamente lo que `V49` evitó vaciando en lugar de traducir. | Responsable técnico |
| 0.40.0 | 11-09-2026 | **Corrige a v0.39.0 el mismo día, y las dos quedan para que el cambio se vea.** Aquella dio a la excepción por persona una **columna `product_id`**; el responsable del proyecto corrigió la forma: la personalizada debe asociarse a productos **con el mismo mecanismo que la de rol**. La columna se retira y nace **`user_commission_rate_products`** \(`V85`\), gemela de `product_commission_rates` — el sistema llega a **veintiséis tablas**. **Lo que la gemela tiene y esta no es el `role_id` copiado**: allí existe para que `RN-CM-013` pueda declararse en el esquema, con una clave foránea compuesta que le impide divergir; aquí no hay nada equivalente que copiar, porque la regla hermana habla de **persona y fechas** y las fechas no caben en una clave primaria. **Y por eso el `EXCLUDE` de `user_commission_rates` SE RETIRA sin sustituto**: `RN-CM-006` cruza ahora dos tablas, y este documento ya lo había anticipado en v0.15.0 — «sacar el producto fuera lo habría hecho cruzar dos tablas, que ningún índice hace». La regla pasa al caso de uso con un bloqueo consultivo. **No se sustituye por un `EXCLUDE` sobre `(user_id, daterange)`**, que es la tentación: prohibiría dos tasas simultáneas de la misma persona sobre productos **distintos**, que es justo lo que la enmienda existe para permitir. | Responsable técnico |
| 0.41.0 | 12-09-2026 | **`products.public_price` se renombra a `purchase_price` y cambia de significado: de lo que se anuncia a lo que NEXUS paga** (`RN-PM-023`, `RN-PM-024`, [`requirements/pm.md`](requirements/pm.md) v0.23.0 §5.2.6). Decisión del responsable del proyecto. La columna conserva forma, opcionalidad y `CHECK` —que se renombra con ella, `ck_products_purchase_price_no_negativo`—, y lo que cambia es **quién puede leerla**: solo las dos lecturas de administración; la oferta y el hotlink **no la seleccionan**, porque es el costo y publicarlo enseñaría el margen, en el hotlink sin token. **El nulo pasa de «se anuncia con `price`» a «no se conoce»**: un producto que todavía no se ha comprado no tiene costo que declarar, y por eso la migración sigue sin rellenar nada. **Desaparece «el importe que se muestra»**: fuera de administración `price` es lo único que se muestra y la conversión se calcula siempre sobre él. Ninguna tabla nueva: el sistema sigue en veintiséis. | Responsable del proyecto |
| 0.42.0 | 14-09-2026 | **Nace `product_comments`, la segunda tabla de `PM`** ([`requirements/pm.md`](requirements/pm.md) v0.24.0 §10.4): las reseñas de producto, con puntuación de uno a cinco, texto, autor y las tres marcas de tiempo. Por decisión del responsable del proyecto. **Tres cosas del dibujo**: `user_id` es el **autor y no el actor**, y por eso no infringe el Art. V.7; la unicidad «una por persona y producto» es un índice **parcial** sobre las vivas, que no admite `DEFERRABLE`; y **el promedio no se guarda en `products`**: se cuenta sobre las vivas en cada lectura, para que la copia que se quedara atrás no pueda mentir. **Es la primera tabla del sistema que se retira sin motivo declarado** —`Art. V.13` enmendado, `constitution.md` v0.8.0—, y **el esquema de la auditoría no cambia**: el `reason` de `audit_deletion_log` lleva un valor fijo que la especificación declara. §5.1 pasa `PM` a dos tablas; §5.3 registra la primera clave foránea de `PM` hacia `users`. | Responsable del proyecto |
