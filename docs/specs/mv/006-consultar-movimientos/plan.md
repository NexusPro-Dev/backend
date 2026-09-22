# PLAN — `RF-MV-006` Consultar los movimientos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-006` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| `spec.md` aprobada el | 17-09-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Enmendado el | 21-09-2026 — el séptimo filtro, `type` (§3, §4.1, §4.3, §9, §11) |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Es `RF-MV-008` con el alcance quitado y los filtros puestos**, y las dos cosas se hacen en sitios distintos. El alcance de `RF-MV-008` vivía **dentro de la sentencia** —`user_id = :actor OR EXISTS (…)`— porque su requerimiento era que no se escapara. Aquí no hay alcance que poner: **la puerta es el permiso**, declarado en el controlador, y la sentencia devuelve lo que los filtros dejen. Si un día alguien quita la anotación, `CA-MV-069` lo delata.

**Los filtros se escriben una vez y los usan la página y el conteo.** Es la forma que `RF-SP-011` fijó para la auditoría, y por lo mismo: un predicado que se copiara en las dos sentencias acabaría distinto en una de ellas, y entonces el total no correspondería a lo devuelto. Un predicado por filtro, todos opcionales, unidos con `AND`.

**El conteo es acotado** —`BoundedCount`, el mismo componente de los cuatro registros de auditoría— y no exacto como en `RF-MV-008`. La diferencia es qué se cuenta: allí, el conjunto de una persona; aquí, **la tabla entera** filtrada por algo que puede no seleccionar nada. El `COUNT(*)` exacto sobre `movements` sin filtros es un recorrido completo **por cada página**, y su síntoma no sería un fallo sino lentitud creciente.

**El identificador de las personas SÍ viaja en la petición**, y ahí está la diferencia con `RF-MV-008` que hay que leer bien: aquel registro no tenía forma de decir sobre quién; este la tiene, como **filtro**, porque el actor ya demostró con el permiso que puede ver a cualquiera. El agujero que allí se cerraba aquí no existe — lo que lo cierra es la anotación.

---

## 2. Cambios de esquema

**Ninguna tabla ni columna.** Todo lo que la fila devuelve ya está en `movements`, `movement_types`, `movement_details` y las tres que se cruzan para los nombres.

**Un índice nuevo, y hace falta.** El listado sin filtros —que es el primero que se pide— ordena la tabla entera por `occurred_at DESC` y se queda con veinte: sin índice es un recorrido completo con ordenamiento parcial en memoria, en cada página, y crece con el libro. Los dos índices de `RF-MV-008` no sirven porque los dos empiezan por una persona.

| Nombre | Definición | Por qué |
|---|---|---|
| `ix_movements_occurred_at` | `movements (occurred_at DESC, id DESC)` | Exactamente el `ORDER BY` de la página, con el desempate incluido: el motor lee el índice en orden y para en el `LIMIT`, sin ordenar nada |

**Los filtros que sí tienen índice, y los que no.** El sujeto lo responde `ix_movements_user` (`V12`) y el vendedor `ix_movement_details_seller` (`V12`) por el mismo `EXISTS` de `RF-MV-008`. **El código lo responde `uq_movements_code`**, y para que lo haga la comparación es por **igualdad sobre el valor en mayúsculas** —los comprobantes nacen en mayúsculas (`MovementCode`)— y no un `upper(m.code) = …` que el índice no podría usar. **El estado y el método de pago no llevan índice**: son columnas de cardinalidad baja, y un filtro por `PENDIENTE` sobre un libro donde casi todo está confirmado sería selectivo, pero un índice sobre `status` a secas no sirve para ordenar después. Queda como **disparador de revisión** (§10): el día que la pregunta «¿qué está pendiente?» tarde, el índice es `(status, occurred_at DESC)` parcial sobre `PENDIENTE`, y no hace falta decidirlo hoy.

La migración es `V15__mv_indice_movimientos_por_fecha.sql`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `ListMovementsRequest` | Nuevo | Página, tamaño y los seis filtros. Normaliza el estado y el código a mayúsculas. **Desde el 21-09-2026, siete: `type`**, normalizado como el estado |
| `application` | `MovementResponse` | Nuevo | La fila, **sin papel**: con `type`, `confirmedAt`, sujeto y vendedores |
| `domain/repository` | `MovementRepository` | Modificado | Gana `findAll`, `countAll` y el registro `MovementFilter` con los seis filtros; reutiliza `findSellersOf` |
| `domain/repository` | `JpaMovementRepository` | Modificado | Las dos sentencias sobre **un** predicado, y el conteo con `LIMIT techo + 1` |
| `domain/service` | `ListMovementsService` | Nuevo | Valida, pagina, cuenta acotado y mapea |
| `interfaces` | `MovementController` | Modificado | Un `GET` en la raíz, con `@PreAuthorize` |
| `db/migration` | `V15` | Nueva | El índice de §2 |

**`MovementResponse` es una fila nueva y no `MyMovementResponse` sin `role`.** Comparten sujeto, vendedores, moneda e importes, y aun así son dos contratos: aquella lleva el papel y esta lleva el tipo y la confirmación, y **cambian por motivos distintos** — el día que la fila propia gane algo que quien administra no debe ver, o al revés, no tiene que arrastrar a la otra. Lo que sí se comparte es lo que no es contrato: `findSellersOf` y el nombre completo.

**El séptimo filtro, `type`, entra el 21-09-2026 por los mismos cuatro sitios y no añade ninguno.** `ListMovementsRequest` lo recibe y lo normaliza a mayúsculas; `MovementFilter` lo lleva; `filtroGlobal` lo compara con `mt.code` —`movement_types` ya estaba en el `JOIN` para pintar el tipo de cada fila, de modo que filtrar por él no cuesta una tabla más—; y `ListMovementsService` lo valida **contra el catálogo**, con el `findTypeByCode` que `RF-MV-001` ya usa para resolver `VENTA`, **junto** con el estado y el rango. **Contra el catálogo y no contra una constante**: la spec lo trata como conjunto cerrado porque el sistema lo siembra (`RN-MV-017`), pero lo siembra **en una tabla**, y el día que una migración añada `DEPOSITO` el filtro tiene que admitirlo sin que nadie se acuerde de tocar una lista en Java. Es una consulta más por petición, solo cuando el parámetro viene, sobre una tabla de una fila.

**`MovementFilter` vive en el puerto y no en `application`.** Es lo que el repositorio necesita para escribir el predicado, y la petición HTTP lo produce; ponerlo en `application` obligaría al adaptador a conocer la forma de la petición, que es la dirección de dependencia que `architecture.md` no admite.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements` | `movements:read` |

**Es el `GET` del recurso que `POST /api/v1/movements` ya crea**, y por eso no lleva segmento: registrar y listar son los dos verbos del mismo nombre. `/mine` sigue siendo el listado propio, y el día que `RF-MV-007` traiga `/{id}` los tres conviven sin ambigüedad — la raíz, un literal y una variable.

### 4.1 Parámetros

| Parámetro | Tipo | Nota |
|---|---|---|
| `page`, `size` | | Los resuelve `Pagination`, como todo listado del sistema |
| `status` | `PENDIENTE` \| `CONFIRMADA` \| `RECHAZADA` \| `ANULADA` | Uno no admitido es `400`. Se valida contra `MovementStatus`, no contra una lista escrita a mano |
| `userId` | UUID | El sujeto. Uno inexistente da página vacía |
| `sellerId` | UUID | Vendedor de **alguna** línea. Uno inexistente da página vacía |
| `paymentMethodId` | UUID | Uno inexistente da página vacía |
| `code` | texto | Igualdad exacta sobre el valor en mayúsculas |
| `from`, `to` | instante ISO-8601 con zona | Sobre `occurred_at`. **Semiabierto**: `from <= occurred_at < to`. `from` posterior a `to` es `400` |
| `type` (21-09-2026) | código del catálogo: hoy, `VENTA` | Sin distinguir mayúsculas. Uno que no exista es `400` `VAL-005`, **junto** con los demás problemas. Se valida contra `movement_types`, no contra una constante |

**No hay parámetro de ordenamiento**, como en `RF-MV-008` y como en los cuatro listados de auditoría: el orden cronológico es parte del significado de un libro. **El desempate es `id` descendente**, y el índice de §2 lo lleva.

**Los identificadores malformados los rechaza el conversor global** con `VAL-001`, como en toda ruta con identificador desde `RF-SP-018` · `T-08`; el estado y el rango los valida el caso de uso, **juntos**, para que una petición con tres parámetros mal escritos falle una vez con los tres. La paginación la valida `Pagination` con sus propios códigos, y **la discrepancia de códigos con la spec** —`Pagination` emite `VAL-003` donde esta y otras specs escriben `VAL-001`— es la que `requirements.md` v0.43.0 dejó declarada y sin cerrar: se mantiene la uniformidad del componente compartido.

### 4.2 La respuesta

Un `PageResponse` con `totalIsExact`, que aquí **sí puede valer falso** (`FA-003`). Cada fila:

| Campo | Tipo | Nota |
|---|---|---|
| `id`, `code`, `status` | | |
| `type` | texto | El código del tipo de movimiento. Hoy, `VENTA` |
| `user` | objeto | El sujeto: identificador, nombre de usuario y nombre |
| `sellers` | lista, **nunca nula** | Los vendedores de sus líneas, sin repetir. Vacía cuando no hay ninguno |
| `currency`, `paymentMethod` | | |
| `totalAmount`, `discountAmount`, `payableAmount` | | |
| `occurredAt` | instante | Cuándo ocurrió |
| `confirmedAt` | instante **o nulo** | Cuándo entró el dinero. Nulo y presente en todo lo que no está confirmado |

**`confirmedAt` se declara nulable con `types = {"string","null"}` y no con `nullable`**: este contrato es OpenAPI 3.1 y springdoc descarta `nullable` en silencio, que es la trampa que `SaleLineResponse.seller` ya pagó.

**No hay `role`.** Quien administra no participa en lo que mira, y un papel que valiera siempre lo mismo sería un campo que miente por omisión.

### 4.3 Códigos de respuesta

| Código | Cuándo |
|---|---|
| `200` | La página, aunque esté vacía |
| `400` | Paginación, estado, tipo, identificador o rango inválidos |
| `401` | Sin token |
| `403` | Sin `movements:read`, tenga o no movimientos propios |

---

## 5. Autorización

**`@PreAuthorize("hasAuthority('movements:read')")` en el controlador, y nada más.** Es la única línea que separa esta operación de publicar el libro entero a cualquier autenticado, y por eso `CA-MV-069` la ejercita con un actor que **sí tiene movimientos propios**: el `403` tiene que salir aunque el listado, de responder, no le mostrara nada ajeno.

**No entra en la lista blanca de `EndpointPermissionsIT`**, al revés que `/mine`: esa lista es de las rutas que **no** declaran permiso, y esta lo declara. Si alguien lo quitara, la prueba fallaría por su cuenta — que es la segunda red, y la primera es `CA-MV-069`.

**Hoy solo lo tiene el superadministrador** (`requirements/mv.md` §6.1), y este plan no cambia eso: conceder `movements:read` a `ADMIN` es la migración de dos `INSERT` que allí queda descrita, y es una decisión del responsable del proyecto y no de un requerimiento de lectura.

---

## 6. Auditoría

**Ninguna.** `spec.md` §7 lo decide y dice por qué: consultar con permiso no es un evento, y es el mismo criterio de `RF-SP-025` y de los cuatro listados de auditoría.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)` en el caso de uso. **Tres sentencias** —la página, el conteo acotado y los vendedores de la página— en la misma transacción, para que no puedan describir estados distintos de la tabla.

---

## 8. Impacto sobre otros módulos

**Ninguno.** Se cruzan `users`, `currencies` y `payment_methods` para los nombres, exactamente como hace `RF-MV-008`, y `movement_types` para el tipo, que es de `MV`. No se pasa por `SP` para resolver a nadie: resolver cien sujetos de una página por el catálogo publicado son cien consultas.

**`requirements/mv.md` §4.1 y `requirements.md` cambian el nombre del requerimiento** (`spec.md` §2.1), y es la única enmienda que este plan declara. Se aplica en el mismo pase.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Un parámetro «solo lo mío» sobre `/mine`, o «todos» con permiso | Un endpoint con **dos modelos de seguridad**: el mismo argumento con el que `requirements/mv.md` §4.1 separó registrar de comprar, y `RF-MV-008` §9 ya lo descartó desde el otro lado |
| Reutilizar `MyMovementResponse` con `role` nulo | Un campo que siempre miente por omisión, y dos contratos atados que cambian por motivos distintos (§3) |
| Conteo exacto, como `RF-MV-008` | Allí es el conjunto de una persona; aquí la tabla entera. El síntoma sería lentitud creciente en cada página, que nadie descubre hasta que duele |
| Filtrar en Java lo que devuelve una consulta más amplia | El total no correspondería a lo devuelto, y el techo del conteo no acotaría nada |
| `LIKE` sobre el código, o buscar por texto | `LIKE` no usa `uq_movements_code`, y buscar por nombre de persona es `RF-SP-025`: el nombre es un dato de `SP` |
| `JOIN` con las líneas para filtrar por vendedor | Multiplica el movimiento por sus líneas y rompe `CA-MV-074`. El `EXISTS` deja una fila por movimiento, como en `RF-MV-008` |
| Devolver las líneas o una suma de importes | Las líneas multiplican la respuesta; la suma es un informe con reglas que este listado no decide (`spec.md` §2.2) |
| Parámetro de ordenamiento | El orden cronológico es el significado de un libro; ordenar por importe invita a construir informes sobre un listado |
| Índice sobre `status` desde hoy | Cardinalidad baja y sin evidencia de que haga falta. Queda como disparador de revisión (§10) |
| Validar `type` contra una constante `VENTA` en Java (21-09-2026) | El catálogo vive en una tabla y crece por migración: una constante sería una segunda copia del catálogo que alguien tendría que recordar. `findTypeByCode` ya existe y cuesta una consulta sobre una tabla de una fila |
| Página vacía para un tipo inexistente, como el sujeto (21-09-2026) | El tipo es un conjunto cerrado que el sistema declara (`RN-MV-017`), no un dato: es el argumento de `spec.md` §6.1 para el estado, y vale entero |
| Publicar el catálogo de tipos para que el frontend arme el filtro (21-09-2026) | Decisión del responsable del proyecto: hoy devolvería una lista de uno. Los códigos vigentes los documenta la prosa del parámetro; el día del segundo tipo, esa lectura tendrá un motivo |
| Índice sobre `movement_type_id` (21-09-2026) | Cardinalidad ínfima —un valor hoy, un puñado mañana— y el mismo argumento que `status`: no serviría para ordenar después |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Que se quite la anotación de permiso** — el defecto que importa | `CA-MV-069` con un actor que tiene movimientos propios, y `EndpointPermissionsIT` como segunda red |
| Que D-22 se cierre y este listado siga devolviendo todo a un director | Declarado en `spec.md` §14: cuando se cierre, es una restricción sobre esta consulta. Hasta entonces solo el superadministrador tiene el permiso |
| Recorrido completo de `movements` en el listado sin filtros | `ix_movements_occurred_at` (§2) y el conteo acotado |
| Que la pregunta «¿qué está pendiente?» tarde cuando el libro crezca | **Disparador de revisión**: índice parcial `(status, occurred_at DESC) WHERE status = 'PENDIENTE'`. No se adelanta |
| Que el predicado de la página y el del conteo diverjan | Escrito una vez y usado por los dos (§1) |
| Que `confirmedAt` se declare con `nullable` y el contrato no lo diga | `types = {"string","null"}` y la lectura del esquema generado |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Con el permiso se ven movimientos ajenos; sin él, `403` aunque haya propios; sin token, `401` | Integración | Es lo que sostiene el requerimiento, y solo se observa por HTTP |
| Cada uno de los seis filtros, y dos combinados | Integración | Dependen de la sentencia, no de una clase |
| El vendedor con varias líneas cuenta una vez | Integración | Es el caso que un `JOIN` multiplica |
| Estado inexistente `400`; `from` posterior a `to` `400`; los dos juntos en una respuesta | Integración | |
| Sujeto, vendedor y método inexistentes: página vacía | Integración | La diferencia con el estado es de negocio (`spec.md` §6.1) |
| Rango semiabierto: el de la medianoche cae en un solo periodo | Integración | |
| Forma de la fila: `type`, `confirmedAt` nulo y presente, `sellers` vacía y presente, sin `role` ni `lines` | Integración, sobre el JSON en crudo | «Nulo» tiene que distinguirse de «ausente» |
| Orden y estabilidad entre páginas | Integración | |
| Total acotado: por encima del techo, `totalIsExact` falso y el total es el techo | Integración, con el techo bajado por propiedad | Es la misma forma de `AuditBoundedCountIT` |
| Filtro por tipo: **discrimina** con un segundo tipo sembrado solo en la prueba, en mayúsculas o minúsculas y combinado con otro filtro; tipo inexistente `400` `VAL-005` **junto** con el estado (21-09-2026) | Integración | Con un solo tipo en el catálogo, filtrar por `VENTA` devuelve todo y no probaría nada. La prueba deja el catálogo como lo encontró |
