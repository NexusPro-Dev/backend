# PLAN — `RF-MV-008` Consultar los movimientos propios

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-008` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 05-09-2026 |
| Versión | 0.6.0 |
| Estado | **Aprobado** |
| Enmendado el | 16-09-2026 — la mitad «lo que vendí» se resuelve por `movement_details.seller_id` (§2.1, §4.1) |
| Enmendado el | 21-09-2026 — el filtro `type` y el campo `type` en la fila (§3, §4.1, §4.3, §11) |
| Enmendado el | 21-09-2026 (segunda del día) — `paymentMethodId`, `code`, `from` y `to`, con la forma de `RF-MV-006` (§4.3, §11) |
| Enmendado el | 22-09-2026 — el listado se acota al sujeto y la fila pierde `role` (§2.2, §3, §4.1, §9, §11); el detalle no cambia |
| Enmendado el | 22-09-2026 (segunda del día) — el listado se muda a `GET /movements/mine/shopping` (§4, §9, §10) |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 05-09-2026 |
| Enmendado | 21-09-2026 — exige **`movements:list-own` (el listado) y `movements:read-own` (el detalle)** (`RF-SP-062`, `RN-SEG-015`: autenticarse no autoriza nada); lo siembra `V31` |

!!! warning "Enmendado el 24-09-2026 — el filtro `code` busca por FRAGMENTO"

    `RN-MV-037` ([`requirements/mv.md`](../../../requirements/mv.md) v0.41.0), a petición del responsable del proyecto: «por si solo me sé una parte». El filtro `code` de lo comprado propio **deja de exigir el comprobante entero** y pasa a devolver todo el que lo **contenga**, sin distinguir mayúsculas.

    **Es una ampliación y no un cambio de contrato**: el código completo sigue encontrando lo que encontraba, porque un comprobante se contiene a sí mismo. Lo que cambia para quien lo pinta es que la respuesta puede traer **más de una fila** donde antes traía como mucho una.

    **Tres cosas que NO cambian, y conviene que no se den por hechas.** `type` y `typeStatus` **siguen siendo exactos**: se eligen de un conjunto cerrado, no se teclean, y un `LIKE` ahí haría que pedir `VENTA` arrastrara cualquier tipo que la contenga. Los comodines `%` y `_` que escriba el usuario se **escapan** —son texto y no patrón—, que es la misma defensa que `RF-SP-025` ya tenía escrita. Y **el alcance no se ensancha**: va en la misma sentencia y **antes** que este predicado, de modo que quien solo ve lo suyo sigue viendo lo suyo.

    **Se indexa con trigramas** (`ix_movements_codigo_busqueda`, `V39`), como `ix_users_busqueda`: `uq_movements_code` no puede responder por un fragmento del medio —un B-tree solo responde por el principio— y sin el índice nuevo la consulta recorrería la tabla entera.

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

!!! note "Enmienda de Art. I.7 — 21-09-2026, `RF-SP-062`"

    Esta operación exige **`movements:list-own` (el listado) y `movements:read-own` (el detalle)** desde el 21-09-2026, por `RF-SP-062` —**autenticarse no autoriza nada**, `RN-SEG-015` ([`security.md` §4.3](../../../security.md#43-reglas-de-negocio))—, por decisión del responsable del proyecto: «cada endpoint debe tener su propio permiso, ya que uso esto para saber qué vista o consulta mostrar en el front; no basta con solo tener el token». Hasta entonces se atendía con solo el token, y las líneas que abajo dicen «sin permiso» o «autenticado a secas» hablan de esa decisión original y se conservan como historia: el alcance sobre uno mismo sigue siendo exactamente el mismo, lo que cambia es que ahora tiene nombre. `V31` siembra el permiso y lo da a todo rol por su tipo.



## 1. Enfoque

**Dos consultas de lectura y ninguna escritura.** Es el requerimiento más simple del módulo, y toda su dificultad está en un sitio: **que el alcance no se escape**. La condición «es mío» no es un filtro que se añade al final, es lo único que separa esta operación de `RF-MV-006`.

**El alcance se aplica en la sentencia, no después.** No se traen los movimientos y se descartan los ajenos en Java: se pide a la base solo los propios. Traer de más y filtrar después es la forma de que un día alguien mueva el filtro de sitio y no falle nada visible — y de que el conteo total cuente lo que no debe.

**El identificador de quien pregunta NO viaja en la petición**, y ese es el punto. Sale de `AuthenticatedActor`, que es el mismo componente que resuelve el actor en `RF-PM-007` y `RF-SP-039`. Un parámetro que dijera sobre quién sería exactamente el agujero que este requerimiento existe para no abrir.

---

## 2. Cambios de esquema

**Ninguno.** `movements`, `movement_details` y sus vecinas ya existen: las crean `V54` (`RF-MV-001`) y `V55` (`RF-MV-009`).

**Un índice nuevo, y hace falta.** La condición es `client_id = ? OR seller_id = ?`, y **ningún índice sirve a un `OR` sobre dos columnas distintas**: el planificador recorre la tabla entera. `movements` crece sin límite —una fila por venta del sistema— y el síntoma de no tenerlo **no sería un fallo sino lentitud creciente**, que es la clase de defecto que nadie descubre hasta que duele.

| Nombre | Definición | Por qué |
|---|---|---|
| `ix_movements_client` | `movements (client_id, occurred_at DESC)` | La mitad «lo que compré», ya ordenada. La segunda columna evita el ordenamiento en memoria de la página |
| `ix_movements_seller` | `movements (seller_id, occurred_at DESC) WHERE seller_id IS NOT NULL` | La mitad «lo que vendí». **Parcial**: una venta sin vendedor nunca forma parte de esta respuesta y no tiene por qué ocupar sitio en el índice — mismo criterio que `ix_user_supervisors_supervisor_vigente` |

**Dos índices y no uno, porque son dos accesos.** Un índice compuesto sobre `(client_id, seller_id)` no responde a la mitad del `OR`: PostgreSQL solo puede usar la primera columna como prefijo. Con los dos, el planificador puede resolverlo como un `BitmapOr` de dos recorridos de índice.

La migración es `V58__index_movements_por_participante.sql`.

### 2.1 Desde el 16-09-2026 la segunda mitad está en otra tabla

`V12` (`RF-MV-001` · `plan.md` §2.4) renombra `client_id` a `user_id` —con el índice, que pasa a `ix_movements_user`— y **retira `seller_id` de `movements`**: el vendedor es de la línea (`RN-MV-003`). Con él se va `ix_movements_seller`, y lo sustituye **`ix_movement_details_seller`** sobre `movement_details (seller_id, movement_id) WHERE seller_id IS NOT NULL` — parcial por lo mismo de antes, ahora porque los tipos de movimiento que no venden nada llevarán la columna en nulo.

**La condición deja de ser un `OR` sobre dos columnas de la misma fila** y pasa a ser `m.user_id = ? OR EXISTS (SELECT 1 FROM movement_details d WHERE d.movement_id = m.id AND d.seller_id = ?)`. El `EXISTS` y no un `JOIN`: una venta con varias líneas del mismo vendedor **es un movimiento**, y un `JOIN` lo multiplicaría — que es exactamente lo que `FA-002` y `CA-MV-040` prohíben. El índice de la línea responde al `EXISTS` por su primera columna, y la segunda evita volver a la tabla para casar `movement_id`.

**El papel se calcula igual, con el `EXISTS` dentro del `CASE`**: sujeto y vendedor de alguna línea → `BOTH`; solo sujeto → `BUYER`; solo vendedor → `SELLER`. El caso que se olvida sigue siendo `BOTH`, y desde hoy lo produce **toda compra de quien no cuelga de nadie**, que se vende a sí mismo.

### 2.2 Desde el 22-09-2026 el listado mira UNA columna

**El predicado del listado pasa de `m.user_id = ? OR EXISTS (… d.seller_id = ?)` a `m.user_id = ?`**, por la decisión que el aviso de `spec.md` §2 recoge: el listado trae solo lo comprado. Es la simplificación más grande que ha tenido este requerimiento, y trae tres consecuencias que conviene leer juntas:

- **El `CASE` del papel desaparece de la proyección**, y con él el parámetro `:actor` en las consultas que solo lo necesitaban para calcularlo — `findById`, la del detalle **sin alcance** de `RF-MV-003`, lo ataba a nulo únicamente por eso. `MyMovementRow` pierde `role` y `MovementRole` se **retira**: sin la fila que lo llevaba, es código muerto.
- **`ix_movement_details_seller` sigue haciendo falta**, aunque este listado deje de usarlo: lo usan `RF-MV-006` y `RF-MV-015` para el mismo `EXISTS`. No se toca el esquema.
- **El detalle conserva el `OR`**: `findMineById` mantiene `m.user_id = :actor OR EXISTS (…)`, y por eso `CABECERA_PROPIA` —que las dos comparten— deja de ser una proyección con papel para ser una proyección a secas. Lo que se acota es **una** de las dos consultas, y eso hay que verlo en el código sin tener que deducirlo: el Javadoc de cada una lo dice.

**El riesgo de esta enmienda no es lo que se quita sino lo que se olvida**: si alguien acota también `findMineById` «por coherencia», un vendedor se queda sin ninguna vía para abrir lo que vendió. `CA-MV-138` existe exactamente para que eso falle.

**Los vendedores del listado se leen aparte y sin repetir**: una segunda consulta por los movimientos de la página —`SELECT DISTINCT movement_id, seller_id …`— y no un agregado dentro de la primera, para que la sentencia paginada siga siendo la que era. Hoy cada venta trae uno.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `MyMovementsRequest` | Nuevo | Página, tamaño y estado. **No lleva identificador de persona**, y esa ausencia es el contrato. Desde el 21-09-2026, también `type`, normalizado como el estado |
| `application` | `MyMovementResponse` | Nuevo | La fila del listado, con **el sujeto y sus vendedores** (desde el 16-09-2026; antes, las dos partes). **Desde el 22-09-2026 sin `role`** |
| `application` | `MovementRole` | Nuevo; **retirado el 22-09-2026** | `BUYER`, `SELLER`, `BOTH`. Sin la fila que lo llevaba es código muerto |
| `domain/repository` | `MovementRepository` | Modificado | Gana `findMine`, `countMine` y `findMineById` |
| `domain/repository` | `JpaMovementRepository` | Modificado | Las tres sentencias |
| `domain/service` | `ListMyMovementsService` | Nuevo | Resuelve el actor, pagina y mapea |
| `domain/service` | `GetMyMovementService` | Nuevo | El detalle, con el alcance dentro de la consulta |
| `interfaces` | `MovementController` | Modificado | Dos `GET` nuevos |

**`SaleResponse` NO se toca, y el detalle lo reutiliza tal cual.** Quien registró una venta y quien la consulta después ven **la misma forma**, que es lo que `spec.md` §6.3 exige. Añadirle el papel obligaría a cambiar el contrato de `RF-MV-001` por una necesidad que no es suya.

**`MovementRole` es un tipo y no una cadena suelta.** Los tres valores son cerrados, y declararlos hace que el contrato publicado los enumere en lugar de decir «texto».

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/mine` | Autenticado |
| `GET` | `/api/v1/movements/mine/{id}` | Autenticado |

**`/mine` va declarado antes que cualquier `/{id}` del controlador.** Hoy no hay ninguno, pero `RF-MV-007` traerá `GET /api/v1/movements/{id}`, y entonces `mine` empezaría a parecerse a un identificador. Spring resuelve por especificidad y el segmento literal gana, de modo que **funcionará**; lo que se declara aquí es que se escriba en ese orden para que quien lea el archivo lo entienda, y una prueba lo fija — igual que hizo `RF-PM-007` con `/available`.

**`mine` y no `me`.** `SP` usa `/users/me` porque el recurso **es** la persona. Aquí el recurso son los movimientos y `me` no es uno de ellos: `mine` dice «los míos», que es lo que la ruta devuelve.

### 4.0 Las rutas, desde el 22-09-2026

| Verbo | Ruta | Qué devuelve | Permiso |
|---|---|---|---|
| `GET` | `/api/v1/movements/mine/shopping` | **Mis compras**: los movimientos a nombre del actor | `movements:list-own` |
| `GET` | `/api/v1/movements/mine/{id}` | El detalle de un movimiento del actor, **comprado o vendido** | `movements:read-own` |
| `GET` | `/api/v1/movements/mine/products` | Los productos comprados (`RF-MV-014`) | `movements:read-own-products` |

**`GET /api/v1/movements/mine` deja de existir**, y no se deja como alias: `RN-SEG-014` exige que cada operación tenga un permiso que ninguna otra tenga, y dos rutas con `movements:list-own` hacen fallar `EndpointPermissionsIT`. Darle un permiso propio al alias sería peor — un permiso para no decidir el nombre de una ruta.

**`shopping` es un literal y `{id}` una variable, y conviven porque Spring resuelve antes el literal** — es lo que `/mine/products` ya hacía desde `RF-MV-014`. La prueba que vigilaba que `mine` no lo capturase una variable de ruta se muda con la ruta.

### 4.1 El listado

Devuelve un `PageResponse` con las filas. Cada una:

| Campo | Tipo | Nota |
|---|---|---|
| `id`, `code`, `status` | | |
| ~~`role`~~ | | **Retirado el 22-09-2026**: el listado trae solo lo comprado, de modo que valdría siempre `BUYER` |
| `user` | objeto | Identificador, nombre de usuario y nombre del **sujeto**. Hasta el 16-09-2026 se llamó `client` |
| `sellers` | lista de objetos, **nunca nula** | Los vendedores de sus líneas, **sin repetir**. Vacía cuando el tipo de movimiento no vende nada. Hasta el 16-09-2026 fue `seller`, un objeto o nulo |
| `type` | texto | El código del tipo de movimiento. Hoy, `VENTA`. **Desde el 21-09-2026**: es el mismo campo de `MovementResponse`, y entra en la fila propia el día que se puede filtrar por él |
| `currency`, `paymentMethod` | | |
| `totalAmount`, `discountAmount`, `payableAmount` | | |
| `occurredAt` | | |

**`sellers` es una lista y no un objeto nulable, y es a propósito.** `RN-MV-003` admite que las líneas de una venta lleven vendedores distintos, y un objeto obligaría a elegir uno o a mentir. La lista dice la verdad con un elemento hoy y con varios el día que exista el caso, y **vacía** dice «este movimiento no tiene vendedor» sin que ningún consumidor tenga que interpretar un nulo. La trampa de la nulabilidad —`types = {"object","null"}` y no `nullable`, porque este contrato es **OpenAPI 3.1** y springdoc descarta `nullable` en silencio— sigue viva en `SaleLineResponse.seller`, que es donde el nulo todavía significa algo.

### 4.2 El detalle

Devuelve un `SaleResponse`, idéntico al de `RF-MV-001`.

**No se acota con el listado** (22-09-2026): abre un movimiento en el que el actor participa **de cualquiera de las dos formas**, también lo que vendió. La asimetría es deliberada y su motivo está en `spec.md` §2; `CA-MV-138` la fija.

**`404` para el ajeno y para el inexistente**, sin distinguirlos (`EX-002`). No es un `403`: un `403` diría que existe.

### 4.3 Parámetros

| Parámetro | Nota |
|---|---|
| `page`, `size` | Los resuelve `Pagination`, como todo listado del sistema |
| `status` | Opcional. Un valor no admitido es `400` `VAL-003` |
| `type` (21-09-2026) | Opcional; código del catálogo, sin distinguir mayúsculas. Uno que no exista es `400` `VAL-004`. **Se valida contra `movement_types` con `findTypeByCode`** y no contra una constante, por lo que `RF-MV-006` `plan.md` §3 y §9 dicen; entra en `SELECCION_PROPIA` con la misma forma que el estado —`CAST(:tipo AS varchar) IS NULL OR mt.code = …`— y `movement_types` se une a la sentencia, que hasta hoy no la necesitaba porque la fila no decía su tipo |
| `paymentMethodId` (21-09-2026) | Opcional, UUID. Igualdad sobre `m.payment_method_id`; uno inexistente da página vacía. Malformado, `400` `VAL-006` por el conversor global |
| `code` (21-09-2026) | Opcional; en mayúsculas para que la igualdad la responda `uq_movements_code`, como en `RF-MV-006`. El alcance del actor va en el mismo `WHERE`: un código ajeno no devuelve nada |
| `from`, `to` (21-09-2026) | Opcionales, instantes ISO-8601 con zona, sobre `occurred_at`, semiabierto. `from` posterior a `to` es `400` `VAL-005` |

**Los cuatro entran en `SELECCION_PROPIA` con la misma forma que el estado y el tipo** —`CAST(:x AS …) IS NULL OR …`—, porque esa sentencia se escribe una vez para la página y el conteo y ya usa esa forma; no se cambia a la clase `Filtro` del listado global para no reescribir una consulta que funciona. `MyMovementsRequest` normaliza el código a mayúsculas y `ListMyMovementsService` valida el rango como valida el estado y el tipo: uno a uno, que es la forma de este caso de uso.

**No hay parámetro de ordenamiento**, y es una decisión: el orden es fijo, del más reciente al más antiguo. Ofrecer ordenar por importe o por estado invitaría a construir informes sobre un endpoint que existe para que alguien mire lo suyo.

**El desempate es `id` descendente.** Sin él, dos movimientos del mismo instante pueden repetirse en una página y faltar en la siguiente sin que nada falle (`CA-MV-041`).

---

## 5. Autorización

**Ninguna anotación de permiso**, y es lo único que hay que hacer bien: la operación queda cubierta por la regla general —todo exige token salvo tres rutas públicas—, y el alcance lo pone la consulta.

**Las dos rutas entran en la lista blanca de `EndpointPermissionsIT`.** Esa prueba exige que todo endpoint declare permiso salvo los declarados a mano, y sin la entrada fallaría. Declararlas ahí **es la decisión escrita**: la ausencia de permiso queda registrada en un sitio que alguien revisa, en lugar de parecer un olvido.

**`CA-MV-038` se ejercita con `movements:read` puesto.** Tener el permiso de administración **no** debe ampliar lo que esta ruta devuelve; si algún día alguien lo conecta, la prueba lo delata.

---

## 6. Auditoría

**Ninguna.** `spec.md` §7 lo declara: consultar lo propio no es acceso a datos ajenos, y registrarlo llenaría el registro de ruido.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)` en los dos casos de uso. **Dos sentencias en el listado** —la página y el total—, dentro de la misma transacción para que no puedan describir estados distintos de la tabla.

---

## 8. Impacto sobre otros módulos

**Ninguno.** No se lee ninguna tabla ajena a `MV` salvo `users`, `currencies` y `payment_methods`, que ya se cruzan para resolver los nombres, exactamente como hace `RF-MV-001` al devolver la venta que acaba de crear.

**Y no se pasa por `SP` para resolver quién es quién.** Los nombres de las partes salen del mismo `JOIN` que ya existe; pedirlos por el catálogo publicado costaría una consulta por fila.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Filtrar en Java lo que devuelve una consulta más amplia | El total contaría movimientos ajenos, y el día que alguien mueva el filtro no fallaría nada visible |
| `UNION` de dos consultas en lugar de un `OR` | Duplicaría el movimiento en que alguien es las dos cosas, y `FA-002` exige que aparezca una vez. Un `UNION` sin `ALL` lo deduplicaría, a cambio de un ordenamiento completo antes de paginar |
| Calcular el papel en el cliente de la API | `spec.md` §6.2: acabaría escrito en cada consumidor, y distinto en cada uno. **Sin objeto desde el 22-09-2026**: no hay papel que calcular |
| **Dejar `role` valiendo siempre `BUYER`** (22-09-2026) | Un campo constante en el contrato: no rompe a nadie hoy y miente mañana. Es el argumento de `RF-MV-006` §6.2 |
| **Acotar también el detalle** (22-09-2026) | Dejaría a un vendedor sin ninguna vía para abrir lo que vendió: `RF-MV-007` no existe y `RF-MV-015` es solo listado. Cerrar una puerta sin abrir otra |
| **Un parámetro `role` para elegir el papel** (22-09-2026) | Lo pidió el frontend (R-46) y el responsable del proyecto lo descartó: la mitad de vendedor no se filtra, se va a `RF-MV-015` |
| **Dejar `GET /movements/mine` como alias de `/mine/shopping`** (22-09-2026) | Dos operaciones con el mismo permiso: rompe `RN-SEG-014` y lo delata `EndpointPermissionsIT`. Y un permiso nuevo para sostener el alias sería inventar autorización para no decidir un nombre |
| **Mudar también el detalle a `/mine/shopping/{id}`** (22-09-2026) | Ese detalle abre lo comprado **y lo vendido** (§4.2): meterlo bajo «compras» lo nombraría mal, que es justo el defecto que esta mudanza corrige |
| Reutilizar `RF-MV-006` con un parámetro «solo lo mío» | Daría un endpoint con **dos modelos de seguridad** — el mismo argumento con el que §4.1 de `requirements/mv.md` separó registrar de comprar |
| Devolver las líneas en el listado | Multiplica la respuesta por un dato que solo se mira al abrir uno |
| **Un solo `seller` en la fila, el de la primera línea** (16-09-2026) | Elegiría uno cuando puede haber varios, y el consumidor no sabría que hay más. La lista sin repetir cuesta una consulta por página y no miente |
| **`JOIN` con las líneas en la sentencia paginada** (16-09-2026) | Multiplica el movimiento por sus líneas y rompe `CA-MV-040`. El `EXISTS` deja una fila por movimiento |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Que el alcance se escape** — el defecto que importa | El filtro va en la sentencia; `CA-MV-038` lo ejercita con el permiso de administración puesto |
| Que `/mine/shopping` sea capturado por `/mine/{id}` | Prueba propia, la misma que vigilaba `/mine` y que se muda con la ruta (22-09-2026). Spring resuelve antes el literal, como con `/mine/products` |
| **Que un consumidor siga llamando a `GET /movements/mine`** (22-09-2026) | Recibe `404`. Cambio incompatible declarado en `api/index.md`; se avisa al frontend, que es su único consumidor conocido |
| Recorrido secuencial de `movements` al crecer | Los dos índices de §2 — desde el 16-09-2026, `ix_movements_user` e `ix_movement_details_seller` (§2.1). El síntoma sería lentitud y no un fallo |
| Que el papel salga mal cuando alguien es las dos cosas | `CA-MV-037` lo fijaba, y era el caso que se olvidaba al escribir el `CASE`. **Sin objeto desde el 22-09-2026**: no hay papel |
| **Que alguien acote también el detalle** «por coherencia» (22-09-2026) | `CA-MV-138`: el vendedor que no ve la venta en su listado la abre por su identificador |
| **Que un consumidor siga esperando `role`** (22-09-2026) | Cambio rompedor **declarado**: `api/index.md` lo dice y se avisa al frontend, que es su único consumidor conocido |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Los tres papeles, incluido `BOTH` | Integración | Depende de la sentencia, no de una clase |
| Que no aparezca ningún movimiento ajeno, **con `movements:read`** | Integración | Es el criterio que sostiene el requerimiento |
| El detalle ajeno responde `404` y no `403` | Integración | La distinción es observable solo por HTTP |
| Paginación, orden y estabilidad entre páginas | Integración | |
| Filtro por estado, y estado no admitido → `400` | Integración | |
| Filtro por tipo: **discrimina** con un segundo tipo sembrado solo en la prueba, combinado con el estado; tipo inexistente `400`; y `type` en cada fila (21-09-2026) | Integración | Con un solo tipo en el catálogo, filtrar por `VENTA` no probaría nada. La prueba deja el catálogo como lo encontró |
| Método de pago, código en minúsculas y ajeno, periodo semiabierto combinado con el estado, y rango invertido `400` (21-09-2026) | Integración | `CA-MV-133` a `CA-MV-135`; el código ajeno es el que protege el alcance |
| Página vacía para quien no participó | Integración | |
| `401` sin autenticar | Integración | |
| `/mine` no lo captura una variable de ruta | Integración | La declara `MovementRoutingIT` o la propia clase |
