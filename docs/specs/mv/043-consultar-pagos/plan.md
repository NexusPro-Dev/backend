# PLAN — `RF-MV-043` Consultar los pagos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-043` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 01-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Es `RF-MV-006` con la fila cambiada de tabla**, y se construye con sus mismas piezas: la puerta es **el permiso** en el controlador, los filtros se escriben **una vez** para la página y el conteo —un predicado por filtro, añadido solo cuando su valor viene, la clase `Filtro` que `RF-MV-006` · `tasks.md` §3 copió de la auditoría—, y el conteo es **acotado** con `BoundedCount`. Se lee [`006-consultar-movimientos/plan.md`](../006-consultar-movimientos/plan.md) §1 y no se repite.

**Lo que cambia es de dónde parte la sentencia: de `payments`, no de `movements`.** Cada fila es un intento (`spec.md` §2.2), de modo que la sentencia **recorre los pagos** y **une** su movimiento —uno por pago, por `fk_payments_movement`—, y no al revés. Partir del movimiento y unir sus pagos daría el mismo conjunto, pero el orden sería el del movimiento y el índice que lo sirve no existiría.

**Depende de `V62`** (`RF-MV-040`): la fila publica la incidencia de la pasarela, y sus tres columnas nacen allí. Este requerimiento se construye **después** de la tarjeta (§10).

---

## 2. Cambios de esquema

**Ninguna tabla ni columna.** Todo lo que la fila devuelve ya está en `payments`, `movements`, `movement_types`, `payment_methods`, `currencies` y `users`.

**Un índice nuevo, y hace falta**, por el argumento de `RF-MV-006` · `plan.md` §2: el listado sin filtros ordena la tabla entera por cuándo se intentó y se queda con veinte. Los índices que `payments` ya tiene empiezan por el movimiento (`ix_payments_ultimo`), el método (`ix_payments_metodo`) o la referencia (`ix_payments_provider_reference`), y ninguno sirve para ese orden.

| Nombre | Definición | Por qué |
|---|---|---|
| `ix_payments_occurred_at` | `payments (occurred_at DESC, id DESC)` | Exactamente el `ORDER BY` de la página, con el desempate: el motor lee en orden y para en el `LIMIT` |

**Los filtros que sí tienen índice, y los que no.** El medio lo responde `ix_payments_metodo` (`V48`). La persona se resuelve por el movimiento —`m.user_id`, con `ix_movements_user` (`V12`)—, y el comprobante por fragmento con los trigramas de `ix_movements_codigo_busqueda` (`V39`), exactamente como en el libro (`RN-MV-037`). **El estado y el tipo no llevan índice**: cardinalidad baja, el mismo argumento que `RF-MV-006` · `plan.md` §2 y el mismo **disparador de revisión** (§10): si «¿qué está pendiente?» tarda, el índice es `(status, occurred_at DESC)` parcial sobre `PENDIENTE`.

**La migración es `V64__mv_listado_de_pagos.sql`**: el índice y el permiso de §5. `V62` es de la tarjeta; el número lo reservó este plan el 01-10-2026 —era `V63`, y lo cedió el mismo día a la conciliación por el pago (`RF-MV-044`), que se construye antes: Flyway no aplica fuera de orden—, de acuerdo con quien construye la tarjeta.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `ListPaymentsRequest` | Nuevo | Página, tamaño y los seis filtros. Normaliza estado y tipo a mayúsculas |
| `application` | `PaymentListItemResponse` | Nuevo | La fila: el pago y su movimiento. `@Schema(name = "PaymentListItem")` |
| `domain/repository` | `PaymentRepository` | Modificado | Gana `findAll`, `countAll` —que devuelve `BoundedCount`— y el registro `PaymentFilter` |
| `domain/repository` | `JpaPaymentRepository` | Modificado | Las dos sentencias sobre **un** predicado, y el conteo con `LIMIT techo + 1` |
| `domain/service` | `ListPaymentsService` | Nuevo | Valida estado, tipo y rango **juntos**, pagina, cuenta acotado y mapea |
| `interfaces` | `PaymentController` | Modificado | Un `GET /payments` bajo `/api/v1/movements`, con `@PreAuthorize` |
| `db/migration` | `V64` | Nueva | El índice de §2 y el permiso de §5 |

**`PaymentListItemResponse` es una fila nueva y no `PaymentResponse` con el movimiento añadido.** `PaymentResponse` es el pago **dentro** del detalle de un movimiento —allí el movimiento es el contenedor, y repetirlo en cada pago sería ruido—, y este es el pago **suelto**, que sin su movimiento no se entiende. Comparten forma y **cambian por motivos distintos**, que es el argumento de `RF-MV-006` · `plan.md` §3 para no reutilizar la fila propia. **Lleva nombre de esquema explícito y único**: springdoc funde en uno los registros con el mismo nombre simple, y `PaymentResponse` ya publica `MovementPayment` y `MovementPaymentMethod`. El medio de la fila **reutiliza** `PaymentResponse.Method`, que es el mismo dato con el mismo esquema.

**El tipo se valida contra el catálogo** con `findTypeByCode`, como `RF-MV-006` · `plan.md` §3 decidió y por lo mismo: el catálogo crece por migración. **El estado, contra `PaymentStatus`**, no contra una lista escrita a mano.

**`PaymentFilter` vive en el puerto**, como `MovementFilter`: el adaptador no conoce la petición HTTP.

**Los nombres se resuelven en la misma sentencia** —`users`, `currencies`, `payment_methods`, `movement_types` por `JOIN`—, como el libro: pasar por `SP` para cada persona de una página serían veinte consultas (`RF-MV-006` · `plan.md` §8).

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/payments` | `movements:list-payments` |

**Bajo `/movements` y no en un `/payments` propio**: el pago es del módulo `MV` y de su recurso —todas sus rutas de pago ya viven ahí: `/mine/{id}/payments`, `/{id}/rejection`—, y un prefijo nuevo aparentaría un recurso de primer nivel que no existe sin su movimiento. **No choca con el detalle**: `/{id}` está acotado por la expresión regular de un UUID (`RF-MV-007`), y `payments` no la cumple.

### 4.1 Parámetros

| Parámetro | Tipo | Nota |
|---|---|---|
| `page`, `size` | | Los resuelve `Pagination` |
| `status` | `PENDIENTE` \| `CONFIRMADO` \| `RECHAZADO` | Del **pago**. Uno no admitido es `400` `VAL-002` |
| `paymentMethodId` | UUID | Uno inexistente da página vacía |
| `type` | código del catálogo: hoy `VENTA`, `COMPRA_PUNTOS`, `RETIRO`… | Sin distinguir mayúsculas. Uno que no exista es `400` `VAL-005`. Los tipos sin pagos —`PAGO_COMISION`, `BONO`— se admiten y dan página vacía |
| `userId` | UUID | A nombre de quién está el movimiento. Uno inexistente da página vacía |
| `code` | texto | **Fragmento** del comprobante, sin distinguir mayúsculas, con `%` y `_` escapados (`RN-MV-037`) |
| `from`, `to` | instante ISO-8601 con zona | Sobre `p.occurred_at`. **Semiabierto**. `from` posterior a `to` es `400` `VAL-004` |

**Sin parámetro de ordenamiento** y con desempate por `id` descendente, como el libro. Los identificadores malformados los rechaza el conversor global con `VAL-001`, y la discrepancia de códigos de `Pagination` es la que `RF-MV-006` · `plan.md` §4.1 ya declara.

### 4.2 La respuesta

Un `PageResponse` con `totalIsExact`, que **puede valer falso** (`FA-003`). Cada fila:

| Campo | Tipo | Nota |
|---|---|---|
| `id`, `status` | | El pago |
| `amount` | número | |
| `currency` | objeto | La del movimiento |
| `paymentMethod` | objeto | `PaymentResponse.Method`: identificador, código y nombre |
| `occurredAt` | instante | Cuándo se intentó |
| `confirmedAt`, `rejectedAt` | instante **o nulo** | |
| `rejectionReason` | texto **o nulo** | |
| `providerReference` | texto **o nulo** | |
| `incident`, `incidentAt`, `refundedAmount` | **o nulo** | `RN-MV-060`. `refundedAmount` solo con `REEMBOLSADO` |
| `movement` | objeto | `id`, `code`, `type`, `status` y `user` —identificador, nombre de usuario y nombre— |

**Cada nulable se declara con `types = {…, "null"}` y no con `nullable`**, que springdoc descarta en silencio en OpenAPI 3.1 (`RF-MV-006` · `plan.md` §4.2). **`@JsonInclude(ALWAYS)`** para que lo nulo viaje presente (`CA-MV-492`). **Sin `idempotencyKey` ni `points`**: la clave no le dice nada a quien administra, y los puntos de un pago con puntos están en el detalle del movimiento.

### 4.3 Códigos de respuesta

| Código | Cuándo |
|---|---|
| `200` | La página, aunque esté vacía |
| `400` | Paginación, estado, tipo, identificador o rango inválidos |
| `401` | Sin token |
| `403` | Sin `movements:list-payments`, tenga o no pagos propios |

---

## 5. Autorización

**`@PreAuthorize("hasAuthority('movements:list-payments')")` en el controlador, y nada más**, con el argumento de `RF-MV-006` · `plan.md` §5: es la única línea que separa esta operación de publicar todos los pagos a cualquier autenticado, y `CA-MV-481` la ejercita con un actor que **sí tiene pagos propios**.

**Permiso propio, y no `movements:read`.** Ver los pagos y ver el libro son dos operaciones, y `RN-SEG-014` no deja que un permiso gobierne dos: `EndpointPermissionsIT.ningunPermisoGobiernaDosOperaciones` lo rechazaría, como rechazó la primera tripleta de `RF-MV-007`.

**`V64` lo siembra a `SUPERADMIN` y `ADMIN`, explícitos**, con el patrón de `V61` y su bloque de comprobación al final. **No va por tipo de rol**: es la lectura de administración (`spec.md` §3), como `movements:read-user-payout-accounts`. Identificador literal `01a0ef9c-6800-701e-9c4f-5e7ad7000057`, el siguiente de la marca de `V61` tras el de `V62`. **El catálogo pasa de 181 a 182**, y `ADMIN` de 179 a 180.

---

## 6. Auditoría

**Ninguna** (`spec.md` §7).

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)` en el caso de uso. **Dos sentencias** —la página y el conteo acotado— en la misma transacción. **Una menos que el libro**: no hay lista de vendedores que pedir aparte, porque todo lo que la fila lleva es uno por pago y sale por `JOIN`.

---

## 8. Impacto sobre otros módulos

**Ninguno.** `users`, `currencies` y `payment_methods` se cruzan para los nombres, como hace el libro.

**Las enmiendas que este plan declara**, aplicadas en el mismo pase: [`requirements/mv.md`](../../../requirements/mv.md) §4.1 (la fila), §4.7 (el requerimiento), §6 (el permiso), §7.7 (el índice) y su control de cambios; [`security.md`](../../../security.md) §4.4 (el permiso, sin sembrar) y su control de cambios; [`modelo-datos.md`](../../../modelo-datos.md) (el índice) y su control de cambios; y la matriz de [`requirements.md`](../../../requirements.md), con sus indicadores.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Un parámetro en el libro que devuelva intentos en vez de movimientos | Una fila que cambia de significado según la petición, y un total que unas veces cuenta ventas y otras intentos (`spec.md` §2.2) |
| Reutilizar `movements:read` | `RN-SEG-014`: dos operaciones, dos permisos. La prueba de permisos lo rechaza |
| Reutilizar `PaymentResponse` con el movimiento añadido | Un dato redundante en el detalle, y dos contratos atados que cambian por motivos distintos (§3) |
| Partir de `movements` y unir sus pagos | Mismo conjunto, pero el orden sería el del movimiento y el índice de §2 no lo serviría |
| `/api/v1/payments` | El pago no existe sin su movimiento, y todas sus rutas ya viven bajo `/movements` (§4) |
| Filtros por incidencia y por referencia de la pasarela | Decisión del responsable del proyecto: la incidencia se filtra en el libro, y las dos viajan en la fila (`spec.md` §2.4) |
| Conteo exacto | La tabla crece más deprisa que el libro: al menos un pago por movimiento pagado |
| Sumas por medio o por estado | Un informe con reglas que este listado no decide, y que aquí mezclaría lo que entra con lo que sale (`spec.md` §2.3) |
| Índice sobre `status` desde hoy | Sin evidencia de que haga falta; queda como disparador de revisión (§10) |
| Sembrarlo por tipo de rol | Es la lectura de administración, no algo sobre uno mismo (`RN-SEG-015` no aplica) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Que se quite la anotación de permiso** | `CA-MV-481` con un actor que tiene pagos propios, y `EndpointPermissionsIT` como segunda red |
| Construirlo antes que la tarjeta | Las columnas de la incidencia nacen en `V62`. La rama sale de `feature/stripe-tarjeta`, o de `develop` cuando la tarjeta esté mezclada |
| Que la prueba de la incidencia dependa de la pasarela | La incidencia se siembra en la prueba con un `UPDATE`, como las demás suites de pagos; no hace falta un evento firmado |
| Que el predicado de la página y el del conteo diverjan | Escrito una vez y usado por los dos (§1) |
| Que «¿qué está pendiente?» tarde cuando crezca la tabla | **Disparador de revisión**: índice parcial `(status, occurred_at DESC) WHERE status = 'PENDIENTE'`. No se adelanta |
| Que el nombre del esquema se funda con otro | Nombre explícito y diferente, y se mira el diff de `openapi.json` |
| Que los recuentos del catálogo queden en 181 | `grep -rnE "\b181\b"` en `src/test` además de `isEqualTo`; las suites que lo cuentan están en `tasks.md` |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Con el permiso se ven pagos ajenos; sin él, `403` aunque haya propios; sin token, `401` | Integración | Es lo que sostiene el requerimiento |
| Un movimiento con un rechazado y un confirmado da **dos filas**, y el filtro por rechazado devuelve una de una venta confirmada | Integración | Es la diferencia con el libro (`CA-MV-483`, `CA-MV-484`) |
| Cada filtro, y dos combinados; tipo y estado inexistentes `400` **juntos** con el rango | Integración | Dependen de la sentencia |
| Medio y persona inexistentes: página vacía | Integración | |
| El comprobante por fragmento, en minúsculas, y con `%` como texto | Integración | `RN-MV-037` |
| Rango semiabierto sobre cuándo se intentó, no sobre cuándo se confirmó | Integración | Con un pago intentado antes del corte y confirmado después |
| Forma de la fila sobre el JSON en crudo: nulos presentes, sin `idempotencyKey`, el movimiento anidado | Integración | «Nulo» tiene que distinguirse de «ausente» |
| Un pago reembolsado sale `CONFIRMADO` con la incidencia | Integración | Sembrado con un `UPDATE` (§10) |
| Orden y estabilidad entre páginas | Integración | |
| Total acotado con el techo bajado por propiedad | Integración | La forma de `MovementsBoundedCountIT` |
| El catálogo de permisos en 180 —tras `V63`, que lo deja en 179— | Integración | Las suites que lo cuentan |
