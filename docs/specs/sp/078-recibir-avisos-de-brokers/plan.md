# PLAN — `RF-SP-078` Recibir los avisos de los brokers

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-078` |
| Especificación | [`spec.md`](spec.md) v0.7.0 |
| `spec.md` aprobada el | 08-10-2026 |
| Versión | 0.7.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 08-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Un solo tiempo y una sola transacción**, al revés que las pasarelas de pago (`RF-MV-041` §1): allí hay un segundo tiempo porque hay algo que aplicar, y aquí no lo hay. Comprobar, guardar y responder `200`.

**El cuerpo se lee del flujo de la petición, como bytes, y no con `@RequestBody`.** Es la decisión que más pesa, y sale de no saber qué mandará cada broker: con un formulario (`application/x-www-form-urlencoded`), el contenedor y Spring **reconstruyen el cuerpo a partir de los parámetros**, mezclando los de la dirección con los del cuerpo, y cualquier `getParameter` previo lo consume. Leer `getInputStream()` directamente da los bytes exactos que llegaron, y por la misma razón **la dirección se lee de `getQueryString()` y se desarma a mano** —nunca con `@RequestParam`, que llama a `getParameter` y vaciaría el cuerpo de un formulario—. El orden de las comprobaciones es el de la spec §8: broker, secreto configurado, secreto, tamaño; nada se lee del cuerpo antes de saber que el aviso es del broker.

**El secreto se compara con `MessageDigest.isEqual`** sobre los bytes UTF-8 (`VAL-001`). El valor de `token` **sale de la dirección antes de guardarla**, y un `token` repetido es `EX-001`.

---

## 2. Los secretos

!!! warning "Superado el 13-10-2026 (§16)"

    Los secretos por broker y `notification-tokens` se retiraron con la ruta por nombre. Queda solo el secreto común, `nexus.brokers.notification-token` (§13).

**En el entorno, uno por broker, y por identificador**:

```yaml
nexus:
  brokers:
    notification-tokens:
      "[01a081f0-6000-7101-9c4f-5e7adb000001]": ${BROKER_TOKEN_IQOPTION:}
      "[01a081f0-6000-7102-9c4f-5e7adb000002]": ${BROKER_TOKEN_EXNOVA:}
      "[01a081f0-6000-7103-9c4f-5e7adb000003]": ${BROKER_TOKEN_EXOPTION:}
```

Los identificadores son los que fija `V9`, iguales en todos los entornos, y por eso pueden ir escritos en `application.yml`; **el secreto no**, que llega por variable y vacío por omisión. **Vacío es «sin configurar»** (`EX-002`): la suite corre así salvo donde la prueba lo fija. Un broker que se añada al catálogo necesitará su línea aquí y su variable; sin ellas responde `503`, que es el comportamiento seguro.

**No van a la base**, que sería lo natural para algo «por broker»: el catálogo se puebla por migración (`RN-SP-039`), y un secreto en una migración acaba en el repositorio. Es la misma razón por la que las credenciales de Stripe viven en el entorno.

---

## 3. Cambios de esquema

**`V85__sp_avisos_de_brokers.sql`** crea `broker_notifications` con la forma de [`requirements/sp.md` §10.25](../../../requirements/sp.md): `ck_broker_notifications_method` (`GET`, `POST`), `fk_broker_notifications_broker` sin `ON DELETE` e `ix_broker_notifications_broker (broker_id, received_at DESC)`. Comentarios en la tabla y en las columnas que lo necesitan. **Ningún permiso**: el catálogo no se mueve.

---

## 4. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `infrastructure` | `BrokerNotificationSettings` | Nuevo | `@ConfigurationProperties("nexus.brokers")`, `Map<UUID, String> notificationTokens`; `tokenOf(id)` devuelve vacío si falta o está en blanco |
| `application` | `BrokerNotice` | Nuevo | Lo que llegó, sin tipos del servlet: método, cadena de consulta cruda, cabeceras, cuerpo, tipo de contenido, IP |
| `domain/repository` | `BrokerNotificationRepository`, `JpaBrokerNotificationRepository` | Nuevos | `findActiveByName(nombre)` —`f_unaccent(lower(name))`, la expresión de `uq_brokers_name`— e `insert(...)`, SQL nativo con `CAST(:x AS jsonb)`, como `JpaGatewayEventRepository` |
| `domain/service` | `ReceiveBrokerNotificationService` | Nuevo | Las comprobaciones de §1, desarmar la consulta, filtrar cabeceras y guardar. Identificador con `UuidV7Generator` |
| `interfaces` | `BrokerNotificationController` | Nuevo | `GET` y `POST /api/v1/brokers/{name}/notifications`; lee el flujo con tope de 64 KiB + 1 y la IP de `RequestContext` |
| `shared/security` | `SecurityConfig` | `"/api/v1/brokers/*/notifications"` en `RUTAS_PUBLICAS` | Fuera de `RateLimitFilter` —que solo cubre las rutas que nombra— y sin CORS |
| `shared/observability` | `RequestLogFilter` | Oculta el valor de todo parámetro `token` de la cadena de consulta | `token=[OCULTO]`; el resto de la cadena, igual |
| `resources` | `application.yml` | El bloque de §2 | |

**Las cabeceras que no se guardan**: `authorization`, `proxy-authorization` y `cookie`. Los nombres van en minúsculas —HTTP no distingue— y los valores repetidos, en lista. **La consulta**: cada par `nombre=valor` se descodifica como `application/x-www-form-urlencoded` (UTF-8), en el orden en que llegó, y un nombre sin `=` vale cadena vacía.

---

## 5. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/brokers/{name}/notifications?token=…` | **Ninguno: pública**, autenticada por el secreto del broker |
| `POST` | `/api/v1/brokers/{name}/notifications?token=…` | **Ninguno: pública**, autenticada por el secreto del broker |

`POST` admite **cualquier tipo de contenido** (`consumes = "*/*"`), y los dos admiten **cualquier otro parámetro** en la dirección.

| Código | Cuándo |
|---|---|
| `200` | Guardado; cuerpo vacío |
| `400` | Cuerpo de más de 64 KiB (`EX-004`) |
| `401` | Sin `token`, con `token` repetido o con uno que no es el de ese broker (`EX-001`) |
| `404` | Broker inexistente o inactivo (`EX-003`) |
| `503` | El broker no tiene secreto configurado (`EX-002`) |

**Se documenta en el contrato como ruta de los brokers**, con una etiqueta propia, para que nadie la llame desde el frontend.

---

## 6. Autorización

**Ninguna de las del sistema**, y es deliberado ([`security.md`](../../../security.md) §6). Lo autentica el secreto, y **`EndpointPermissionsIT` declara las dos firmas en `PUBLICAS`**, con el motivo.

---

## 7. Auditoría

**El aviso mismo es la constancia**, en `broker_notifications`, y **no se audita aparte**: no cambia nada que auditar. Un rechazo deja su fila en `request_log` —con el `token` oculto— como cualquier petición. **El cuerpo no va al log de aplicación**: puede traer datos de las personas.

---

## 8. Enmiendas a otros documentos

Aplicadas el 08-10-2026, antes que esta tripleta: [`requirements/sp.md`](../../../requirements/sp.md) v1.107.0 (`RN-SP-066`, la ficha, §8, §9, §10.25, y `RF-SP-054` partido), [`modelo-datos.md`](../../../modelo-datos.md) v0.106.0, [`security.md`](../../../security.md) v0.121.0 y la matriz de [`requirements.md`](../../../requirements.md) v0.324.0. **Con el código**: [`deployment.md`](../../../deployment.md) gana las tres variables y cómo configurar el panel, y [`api/index.md`](../../../api/index.md) su fila.

---

## 9. Impacto sobre otros módulos

**Ninguno.** Nada lee la tabla; `RF-SP-054` será quien lo haga.

---

## 10. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Interpretar ya con un formato supuesto | Es lo que la decisión del 08-10-2026 evita: un supuesto equivocado completa la cuenta de otra persona |
| El broker por identificador en la ruta (`/brokers/01a081f0-…/…`) | Fue la primera forma, del 08-10-2026, y el responsable del proyecto la cambió el mismo día: la dirección del panel no se lee. El nombre se renombra por migración y entonces hay que cambiar la dirección; se acepta, sin añadir un `code` (§10.17 de `requirements/sp.md`) |
| El secreto en una cabecera | Los paneles de afiliados no dejan añadir cabeceras |
| Un solo secreto para los tres | El de uno abriría la ruta de los otros, y cambiarlo obligaría a tocar los tres paneles |
| Descartar repetidos con un resumen del contenido | Dos depósitos iguales del mismo cliente se parecen tanto como una reentrega; descartar es decidir sin saber (`FA-001`) |
| Leer el cuerpo con `@RequestBody` y la dirección con `@RequestParam` | Con un formulario, Spring mezcla los dos y el cuerpo llega reconstruido o vacío (§1) |
| Guardar el cuerpo como `jsonb` | Solo vale si es JSON, y no se sabe si lo será |

---

## 11. Riesgos

| Riesgo | Mitigación |
|---|---|
| El secreto se filtra por la dirección, que queda en registros | No se guarda con el aviso; `request_log` lo oculta. El borde de la plataforma puede registrarla igual: por eso el secreto es uno por broker y se cambia sin tocar código: la variable y la dirección del panel |
| Que alguien con el secreto llene la tabla | El secreto es solo del broker; el tope de 64 KiB por aviso; y si pasa, se cambia el secreto. La cota de tasa estorbaría a la reentrega |
| Que un filtro previo consuma el cuerpo de un formulario | `CA-SP-899` lo prueba de punta a punta, con la cadena de filtros real |

---

## 12. Estrategia de prueba

Integración, **`BrokerNotificationsIT`**, con `MockMvc` y la cadena de filtros real, y los secretos de `IQOPTION` y `EXNOVA` fijados por `@TestPropertySource` —`EXOPTION` queda sin secreto para `CA-SP-902`—: `CA-SP-897` a `CA-SP-907`, leyendo `broker_notifications` y `request_log` con SQL. `CA-SP-903` desactiva un broker en la prueba y lo repone al terminar, para no dejar el catálogo distinto a las suites que vienen detrás. `CA-SP-907` declara una cuenta y comprueba que no se mueve. **La tabla se vacía al empezar y al terminar** cada prueba. `EndpointPermissionsIT` gana las dos entradas.

---

## 13. Enmienda 0.3.0 — la dirección común (`RN-SP-069`, 09-10-2026)

**`V93`** añade a `brokers` dos columnas nulas: `advertiser varchar(60)`, con `uq_brokers_advertiser` sobre `lower(advertiser)`, y `url varchar(500)` con `ck_brokers_url` (`^https?://`). Carga `advertiser = 'iq_option'` en `IQOPTION` —el valor de sus avisos reales del 09-10-2026—; los otros dos y todas las `url` quedan nulos. Guarda: `IQOPTION` tiene su `advertiser`.

**El secreto común** va en `nexus.brokers.notification-token: ${BROKER_NOTIFICATION_TOKEN:}`, junto a los tres por broker, que siguen para la ruta obsoleta. `BrokerNotificationSettings` gana `commonToken()`.

**El servicio gana `receiveCommon(aviso)`**, con este orden: secreto común configurado (`EX-002`) → `token` presente, único y correcto (`EX-001`) → tope del cuerpo (`EX-004`) → `advertiser` (`EX-003`) → guardar. **El `advertiser` va al final a propósito**: antes del secreto dejaría sondear el catálogo. Se busca en la consulta; si no está, en el cuerpo **solo si** su tipo es `application/x-www-form-urlencoded` o `application/json`, y nunca se reescribe: el aviso se guarda tal cual llegó. `BrokerNotificationRepository.findActiveByAdvertiser` compara `lower(advertiser) = lower(:valor)` con `is_active`.

**El controlador** gana `GET` y `POST /api/v1/brokers/notifications`; las dos por nombre llevan `deprecated = true`. `/api/v1/brokers/notifications` entra en `RUTAS_PUBLICAS`. **No choca con `GET /api/v1/brokers`** ni con la ruta por nombre: son dos y tres segmentos, y un `{name}` nunca se llama `notifications` porque ningún broker se llama así.

**El catálogo**: `BrokerItem` gana `url`, leído en `JpaBrokerQueryRepository` (tripleta de `RF-SP-052` v0.3.0).

| Capa | Componente | Cambio |
|---|---|---|
| `db/migration` | `V93__sp_url_y_advertiser_de_brokers.sql` | Las dos columnas, la carga de `iq_option` |
| `infrastructure` | `BrokerNotificationSettings`, `application.yml` | El secreto común |
| `domain/repository` | `BrokerNotificationRepository`, `JpaBrokerNotificationRepository` | `findActiveByAdvertiser` |
| `domain/service` | `ReceiveBrokerNotificationService` | `receiveCommon` |
| `interfaces` | `BrokerNotificationController` | Las dos rutas comunes; las viejas, obsoletas |
| `shared/security` | `SecurityConfig` | La ruta en `RUTAS_PUBLICAS` |

**Riesgo**: `EXNOVA` y `EXOPTION` no pueden usar la dirección común hasta que una migración cargue su `advertiser`. Por eso la ruta por nombre no se retira todavía.

---

## 14. Enmienda 0.4.0 — el aviso de registro (`RN-SP-072`, 09-10-2026)

**Después de guardar el aviso**, en la misma transacción, `ReceiveBrokerNotificationService` llama a `BrokerRegistrationNotices.apply(brokerId, campos)` si el campo del evento vale lo configurado. Los campos se leen **de la consulta**, y si no están, del cuerpo de formulario o JSON, como el `advertiser`.

| Configuración | Variable | Por omisión |
|---|---|---|
| `nexus.brokers.registration-event` | `BROKER_REGISTRATION_EVENT` | vacío: no se interpreta nada |
| `nexus.brokers.fields.event` | `BROKER_FIELD_EVENT` | `postback_name` |
| `nexus.brokers.fields.account` | `BROKER_FIELD_ACCOUNT` | `trader_id` |
| `nexus.brokers.fields.afftrack` | `BROKER_FIELD_AFFTRACK` | `afftrack` |

**`apply`** busca la `VENDEDOR` por `afftrack` (`lower`, en ese broker) y hace `INSERT … ON CONFLICT ON CONSTRAINT uq_user_brokers_cuenta DO NOTHING` con `kind = 'CONSUMIDOR'`, sin titular y con el origen; si no insertó, `UPDATE … SET referrer_account_id` **solo donde es nulo** y la cuenta es `CONSUMIDOR`. Audita `CREATE` o `UPDATE` sobre `user_brokers`, sin actor —lo hizo el broker—. **Nunca falla el aviso**: lo que no se entiende se queda solo guardado.

**Por las dos direcciones**, la común y la obsoleta por nombre: el broker ya está resuelto cuando se interpreta.

---

## 15. Enmienda 0.5.0 — el depósito y la operación (`RN-SP-073`, `RN-SP-074`, 10-10-2026)

**`V98`** añade a `user_brokers` `first_deposit_at`, `operations_count` (`0`), `first_operation_at` y `last_operation_at`, con `ck_user_brokers_deposito_con_estado` y `ck_user_brokers_operaciones`; y a `broker_notifications` `event_id varchar(100)`, rellenado desde `query_params`, con `ix_broker_notifications_evento (broker_id, event_id)` **sin único**.

| Configuración | Variable | Por omisión |
|---|---|---|
| `nexus.brokers.deposit-event` | `BROKER_DEPOSIT_EVENT` | vacío: ningún aviso confirma un FTD |
| `nexus.brokers.operation-event` | `BROKER_OPERATION_EVENT` | vacío: no se cuenta ninguna |
| `nexus.brokers.fields.event-id` | `BROKER_FIELD_EVENT_ID` | `event_id` |

**Al guardar**, el servicio copia el `event_id` del aviso —hasta 100 caracteres— a su columna, y **después** despacha por el evento: registro (§14), depósito u operación. Los dos nuevos **bloquean la cuenta del número** (`lockByNumber`, `FOR UPDATE`) y **solo entonces** miran si otro aviso del mismo broker trae ese `event_id` (`otherWithEventId`): con la fila bloqueada, dos reentregas simultáneas se esperan, y la segunda —en `READ COMMITTED`, sentencia nueva— ve la fila de la primera. Sin `event_id` no hay forma de reconocer una reentrega, y el aviso se aplica.

**Depósito**: solo `CONSUMIDOR`. `markFirstDeposit` es un `UPDATE … WHERE status = 'REGISTER'` que devuelve si movió la cuenta; si la movió, se audita el `UPDATE` de `status`. Con titular, **`ConfirmFirstDepositService.confirm(userId)`** (`users`, `MANDATORY`): bloquea a la persona, y **solo si está en `FTD_PENDIENTE` y tiene venta del alta**, la pasa a `ACTIVO` y llama a `FirstDepositActivation.activate` —la de `RF-SP-028`—, con la auditoría de cambio y el evento de seguridad `USER_STATUS_CHANGED` con motivo `FIRST_DEPOSIT`. **Si la activación falla, el aviso entero se revierte** (`500`): es un defecto de integridad, no un caso de negocio, y el broker reenvía.

**Operación**: `countOperation` suma uno, fija `first_operation_at` si era nulo y `last_operation_at` a `now()`. **No se audita**: el aviso guardado es la constancia.

**La asociación posterior activa**: `ManageBrokerAccountsService` llama a `confirm` tras `claim` si la persona tiene alguna cuenta en `FIRST_DEPOSIT`, y tras `assignHolder` si la cuenta lo está. **En el registro por enlace se llama al final**, después de `attachFirstMovement`: la venta del alta no existe cuando se asocia la cuenta, y activar sin venta dejaría a la persona `ACTIVO` sin lo que compró. La respuesta lleva el estado resultante.

**Contrato**: `BrokerAccountItem` y `TeamBrokerAccountItem` ganan `activity` (`BrokerAccountActivity`), leída en las tres consultas de `JpaBrokerAccountQueryRepository`.

| Capa | Componente | Cambio |
|---|---|---|
| `db/migration` | `V98__sp_deposito_y_operaciones_de_broker.sql` | Las columnas, las restricciones y el relleno |
| `infrastructure` | `BrokerNotificationSettings`, `application.yml` | Los dos eventos y `fields.event-id` |
| `domain/repository` | `BrokerAccountWriter`, `BrokerNotificationRepository`, `BrokerAccountRegistrar` | `lockByNumber`, `markFirstDeposit`, `countOperation`; `event_id` y `otherWithEventId`; `hasFirstDeposit` |
| `domain/service` | `ReceiveBrokerNotificationService`, `ConfirmFirstDepositService` (nuevo), `ManageBrokerAccountsService`, `RegisterClientByLinkService` | Lo de arriba |
| `application` | `BrokerAccountActivity` (nuevo), `BrokerAccountItem`, `TeamBrokerAccountItem` | `activity` |

**Pruebas**: `BrokerNotificationsIT` (`CA-SP-996` a `CA-SP-1000`) y `SelfRegistrationIT` (`CA-SP-995`, `CA-SP-997`, `CA-SP-1001`, `CA-SP-1002`), esta con la venta del alta real.

---

## 16. Enmienda 0.6.0 — una sola dirección, y ningún aviso se pierde (`RN-SP-076`, 13-10-2026)

**`V103`** cambia `broker_notifications`: `broker_id` **admite nulo** —la clave foránea sigue—, y nace **`advertiser varchar(100)`**, nula. La rellena en los avisos ya guardados **desde `query_params`**, como `V98` hizo con `event_id`: el valor recortado si viene exactamente uno y mide de 1 a 100. **No mira el cuerpo**: todos los avisos reales hasta hoy llegan por `GET`. Crea `ix_broker_notifications_sin_broker (lower(advertiser), received_at, id) WHERE broker_id IS NULL`, el índice de la pregunta del arranque. **Ningún permiso**: el catálogo no se mueve.

**`receiveCommon`** conserva el orden —secreto configurado (`EX-002`) → `token` (`EX-001`) → tope (`EX-004`)— y **ya no lanza `EX-003`**: lee el `advertiser` con el mismo `campo(...)` de siempre, lo recorta a lo sumo a 100 —más largo cuenta como ausente—, busca el broker con `findActiveByAdvertiser`, y guarda **con broker o sin él**. **Sin broker no se interpreta**: `interpretar` no se llama. El `advertiser` se guarda en su columna en los dos casos. **Por qué el `advertiser` ya no se mira al final por seguridad**: ya no hay respuesta distinta que delate el catálogo; todo aviso con secreto responde `200`.

**Se retira la ruta por nombre**: `receive(name, aviso)`, los dos métodos del controlador, `findActiveByName`, `notificationTokens` y `tokenOf` de `BrokerNotificationSettings`, el bloque `notification-tokens` de `application.yml`, los tres `BROKER_TOKEN_*` de `.env.example`, `docker-compose.yml` y `deployment.md`, y `"/api/v1/brokers/*/notifications"` de `RUTAS_PUBLICAS`. **Una petición a la ruta vieja ya no llega a ningún controlador**: no es pública, y sin sesión responde `401` como cualquier ruta protegida; con sesión, `404`. En ningún caso se guarda (`CA-SP-1015`).

**La clasificación al arrancar** es un componente nuevo, **`BrokerNotificationClassifier`** (`ApplicationRunner`, en `domain/service`, como `FloorMembershipStartupCheck`). Pide la lista de avisos pendientes —`unclassifiedWithKnownAdvertiser()`: sin broker y cuyo `lower(advertiser)` es el de un broker activo, por `received_at, id`— y, **uno a uno y cada uno en su transacción**, llama a `ReceiveBrokerNotificationService.classify(id)`:

1. `lockUnclassified(id)`: el aviso con `FOR UPDATE SKIP LOCKED` y `broker_id IS NULL`, junto con el broker de su `advertiser`. **Si otro arranque lo tomó, o ya tiene broker, no se hace nada**: dos instancias que arrancan a la vez se reparten la lista sin pisarse.
2. `assignBroker(id, brokerId)`: `UPDATE … SET broker_id … WHERE broker_id IS NULL`. **Es la única escritura sobre un aviso ya guardado.**
3. `interpretar(...)` con lo guardado: `query_params` se lee de vuelta como nombre → lista con el `ObjectMapper`, y `body` como bytes UTF-8, con su `content_type`. **Es la misma función que el aviso que llega**, así que la reentrega por `event_id` se reconoce igual: el aviso anterior ya tiene broker cuando se mira el siguiente.

**Un fallo no tumba el arranque**: si `classify` lanza —la activación de `RN-SP-073` es lo único que puede—, su transacción se revierte, el aviso queda sin broker, se escribe un `WARN` con su `id` y se sigue con el siguiente. El siguiente arranque lo reintenta. **Sin auditoría propia**: lo que la interpretación cambia ya se audita como siempre.

**El contrato**: las dos rutas comunes cambian su prosa —sin `404`— y desaparecen las dos por nombre. `EndpointPermissionsIT` pierde las dos firmas de `PUBLICAS`.

| Capa | Componente | Cambio |
|---|---|---|
| `db/migration` | `V103__sp_avisos_de_brokers_sin_broker.sql` | `broker_id` nulo, `advertiser`, su relleno y el índice parcial |
| `infrastructure` | `BrokerNotificationSettings`, `application.yml` | Fuera `notificationTokens` y `tokenOf` |
| `domain/repository` | `BrokerNotificationRepository`, `JpaBrokerNotificationRepository` | Fuera `findActiveByName`; `insert` con `advertiser` y broker nulo; `unclassifiedWithKnownAdvertiser`, `lockUnclassified`, `assignBroker` |
| `domain/service` | `ReceiveBrokerNotificationService` | Fuera `receive`; `receiveCommon` sin `EX-003`; `classify` |
| `domain/service` | `BrokerNotificationClassifier` (nuevo) | El recorrido del arranque |
| `interfaces` | `BrokerNotificationController` | Fuera las dos rutas por nombre |
| `shared/security` | `SecurityConfig` | Fuera la ruta por nombre de `RUTAS_PUBLICAS` |
| `test` | `IntegrationTestBase` | Fuera los secretos por broker |

**Alternativas descartadas**

| Alternativa | Por qué no |
|---|---|
| Clasificar dentro de la migración que carga el `advertiser` | Una migración puede poner el broker, pero **no interpretar**: crear la cuenta, activar el FTD y entregar lo comprado es código, y duplicarlo en SQL se desincronizaría |
| Una ruta de administración para reclasificar a mano | Es un permiso, una ruta y una prueba más para algo que solo ocurre cuando cambia el catálogo, que solo cambia al desplegar |
| Una tarea periódica | El `advertiser` solo cambia con una migración, y las migraciones solo corren al arrancar: mirar cada minuto es mirar 1 440 veces al día algo que no cambia |
| Guardar en otra tabla los avisos sin broker | Dos tablas con la misma forma, y mudar la fila al reconocerla; con el nulo y el índice parcial la pregunta es igual de barata |

**Riesgo**: un aviso nuevo de un broker recién reconocido puede llegar mientras el arranque todavía asocia los viejos, y se interpreta antes que ellos (`spec.md` §13). El depósito y la operación solo miran que la cuenta exista, así que el orden solo importaría si un depósito viejo llegara antes que el registro que crea su cuenta, y eso ya pasaba igual con los avisos en vivo.

**Pruebas**, en `BrokerNotificationsIT`: `CA-SP-1009` a `CA-SP-1012` y `CA-SP-1015` por `MockMvc`; `CA-SP-1013` y `CA-SP-1014` guardan avisos de un `advertiser` desconocido, se lo cargan a `EXNOVA` con SQL, llaman a `BrokerNotificationClassifier.run(null)` y lo reponen a nulo al terminar —como `CA-SP-903` reponía el broker—. Se retiran las pruebas de `CA-SP-901` a `CA-SP-903`, `CA-SP-908`, `CA-SP-948` y `CA-SP-952`; **las que usaban la ruta por nombre para otros criterios pasan a la común**.

## 17. Enmienda 0.7.0 — lo que trae el aviso, en la cuenta (`RN-SP-077`, 15-10-2026)

**`V113`** cambia `user_brokers`: **borra `broker_username`** —y con ella `ix_user_brokers_busqueda_usuario`, el índice de trigramas de `V100`, que cae con la columna— y añade cinco columnas, todas nulas salvo el total:

| Columna | Tipo | Regla |
|---|---|---|
| `registered_at` | `timestamptz` | El `received_at` del aviso de registro, **no `now()`**: un aviso que se clasifica al arrancar (`RN-SP-076`) se interpreta días después de llegar |
| `country_code` | `varchar(2)` | `ck_user_brokers_pais`: `^[A-Z]{2}$` |
| `first_deposit_amount` | `bigint` | Centésimas (ADR-006). `ck_user_brokers_primer_deposito`: mayor que cero, y solo con `first_deposit_at` |
| `deposits_total` | `bigint NOT NULL DEFAULT 0` | Centésimas. `ck_user_brokers_total_depositado`: no negativo, y no menor que el primero |
| `deposit_currency` | `varchar(3)` | `ck_user_brokers_moneda`: `^[A-Z]{3}$` |

**Sin relleno**: los avisos de registro ya guardados podrían dar la fecha y el país de las cuentas que crearon, pero **reinterpretar lo ya aplicado** es otra decisión, y nadie la pidió.

**La configuración** gana tres campos en `nexus.brokers.fields`: `country` (`country_code`), `amount` (`amount`) y `currency` (`currency`), con sus variables `BROKER_FIELD_COUNTRY`, `BROKER_FIELD_AMOUNT` y `BROKER_FIELD_CURRENCY`.

**`interpretar`** lee el país una vez —el mismo `campo(...)` de siempre, en mayúsculas, y vacío si no son dos letras— y lo pasa a las tres ramas:

- **Registro**: tras crear la cuenta o completar su origen, `lockByNumber` y `recordRegistration(cuenta, aviso, país)`: `registered_at = coalesce(registered_at, (SELECT received_at FROM broker_notifications WHERE id = :aviso))` y `country_code = coalesce(country_code, :pais)`.
- **Depósito**: lee importe y moneda. El importe es un `BigDecimal` mayor que cero, redondeado `HALF_UP` a dos decimales y con tope en lo que cabe en un `bigint` de centésimas; si no, nulo. La moneda, tres letras en mayúsculas o nula. Tras `markFirstDeposit`, `recordDeposit(cuenta, centésimas, moneda, primero)` en **un solo `UPDATE`**: la moneda se fija con `coalesce`, el primero solo si `primero`, y el total suma si hay importe y **la moneda no contradice la guardada**. `fillCountry` antes.
- **Operación**: `fillCountry(cuenta, país)` antes de contar, y **después `avisos.delete(aviso)`** (`RN-SP-074` enmendada): `BrokerNotificationRepository` gana `delete`. La operación **deja de mirar la reentrega**: con el aviso contado borrado ya no hay con qué compararla, y mirarla solo impediría contar un aviso que llegó cuando el número no tenía cuenta. En la clasificación al arrancar es la misma función, de modo que el aviso de operación se borra allí también al contarse.

**Sin auditoría**: el aviso guardado es la constancia, como en `RN-SP-074`. La auditoría del alta por aviso sigue con lo que tenía.

**El contrato**: `BrokerAccountActivity` gana `registeredAt`, `countryCode`, `firstDepositAmount`, `depositsTotal` y `depositCurrency`; `BrokerAccountItem` y `TeamBrokerAccountItem` **pierden `brokerUsername`**, y `search` deja de mirar la columna. **Es una ruptura del contrato** —un campo menos en cuatro respuestas—, admitida porque el campo nunca llevó un valor: siempre fue nulo.

| Capa | Componente | Cambio |
|---|---|---|
| `db/migration` | `V113__sp_lo_que_traen_los_avisos_de_broker.sql` | Fuera `broker_username`; las cinco columnas y sus `CHECK` |
| `infrastructure` | `BrokerNotificationSettings`, `application.yml`, `.env.example` | Tres campos más |
| `domain/repository` | `BrokerAccountWriter`, `JpaBrokerAccountWriter` | Fuera `brokerUsername` de `LockedAccount`; `recordRegistration`, `fillCountry`, `recordDeposit` |
| `domain/repository` | `JpaBrokerAccountQueryRepository` | Fuera la columna y su búsqueda; las cinco en `actividad` |
| `domain/service` | `ReceiveBrokerNotificationService` | País, importe y moneda; las tres escrituras |
| `domain/service` | `ManageBrokerAccountsService` | Fuera `broker_username` de la instantánea de auditoría |
| `application` | `BrokerAccountActivity`, `BrokerAccountItem`, `TeamBrokerAccountItem` | Los campos |

**Alternativas descartadas**

| Alternativa | Por qué no |
|---|---|
| Una tabla de depósitos, una fila por aviso | Los avisos **ya son** esa tabla: cada uno guardado tal como llegó. La cuenta guarda la cifra que se consulta, y el detalle se reconstruye de los avisos |
| Sumar al total aunque la moneda cambie | Una suma de dos monedas no es una cifra de ninguna |
| Tomar el país del último aviso | El responsable eligió el primero: el país de origen de la cuenta, no dónde opera hoy |
| Guardar el importe en `numeric` | Todo el dinero del sistema va en centésimas `bigint` (ADR-006) |

**Pruebas**, en `BrokerNotificationsIT`: `CA-SP-1026` a `CA-SP-1030` por `MockMvc` contra la dirección común, leyendo la fila; `CA-SP-1031` en la respuesta de `RF-SP-057`. `UserBrokerAccountSchemaIT` afirma los `CHECK` nuevos y que la columna ya no existe; las demás suites pierden `brokerUsername`.
