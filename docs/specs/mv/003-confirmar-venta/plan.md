# PLAN — `RF-MV-003` Confirmar una venta pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-003` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 17-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |

!!! warning "Enmendado el 23-09-2026 — confirmar escribe lo que la persona TIENE, y no solo su nivel"

    `RN-MV-036` ([`requirements/mv.md`](../../../requirements/mv.md) v0.40.0) y `RN-SP-056` ([`requirements/sp.md`](../../../requirements/sp.md) v1.84.0), por decisión del responsable del proyecto. **Toda línea que pasa a `ENTREGADA` escribe ahora una posesión** en `user_products` —también la de un bot, que hasta hoy no dejaba constancia en ninguna parte—, con el producto, los `validity_days` **copiados de la línea** y la vigencia contada desde la confirmación.

    **Lo que esto cambia en §3 y §6.** `PublishedMembershipGrant` deja de invocarse **solo** para las líneas de upgrade y pasa a invocarse para **cada línea entregada**; su orden gana dos datos —`productId` y `movementDetailId`— y `membershipId` **pasa a poder ser nulo**, que es el caso del bot: entonces la operación escribe la posesión y **no toca nivel alguno**. El asiento de auditoría de `SP` pasa a nombrar `user_products`.

    **Lo que NO cambia, y es la mitad que importa.** `RN-MV-029` sigue decidiendo *si* se concede antes de llamar, y una línea **retenida no escribe posesión**: lo que no se entregó no se tiene. El bloqueo sobre la fila de la persona, el orden venta→persona y el `MANDATORY` de la escritura publicada siguen exactamente igual. Y `RN-MV-020` sigue diciendo lo mismo del **nivel**: de todas las posesiones que confirmar escribe, solo la del upgrade concede algo.

    **La repetición deja de ser un riesgo del caso de uso y pasa a serlo del esquema**: `uq_user_products_linea` hace que una línea produzca **como mucho una** posesión, de modo que una confirmación repetida no puede duplicar lo que alguien tiene.

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Una transición condicionada, un recorrido de líneas y una escritura publicada, en una sola transacción.** Toda la dificultad del requerimiento está en dos sitios: que **confirmar dos veces conceda una vez**, y que **la membresía no se conceda sin confirmar ni se confirme sin conceder**.

**La transición es un `UPDATE … WHERE status = 'PENDIENTE'`, y su cuenta de filas es la decisión.** No es un `SELECT` seguido de un `UPDATE`: entre los dos puede entrar otra confirmación, y las dos leerían «pendiente». Con la escritura condicionada, la segunda afecta cero filas y responde `EX-002` **sin haber leído nada antes** — que es lo que hace que un webhook reentregado no conceda otra vez. Es el mismo recurso con el que `RF-MV-001` absorbe la colisión del comprobante: declarar el conflicto como esperado en lugar de descubrirlo por excepción.

**La entrega es un bucle por líneas, y la única rama que escribe fuera del módulo es la del upgrade automático.** Manual → nada (queda `PENDIENTE`); automático sin upgrade → `ENTREGADA`; automático con upgrade → comparar el nivel (`RN-MV-029`) y, si no baja, invocar la operación que `SP` publica y marcar `ENTREGADA`. **El nivel se compara con el puerto que ya existe** —`CurrentMembershipLookup`, el mismo de `RN-MV-006`— y no con uno nuevo, por la lección que `architecture.md` §15.2 dejó escrita el 04-09-2026.

**La escritura de `SP` no abre transacción propia**: se une a la de confirmar. Si lanza, todo se deshace, incluida la transición. Es la norma que §15.2.1 fija hoy para toda escritura entre módulos, y aquí es lo que sostiene el «todo o nada» de `spec.md` §7.

---

## 2. Cambios de esquema

**`V16__mv_entrega_por_linea.sql`**, sobre `movement_details`:

| Columna | Definición | Por qué |
|---|---|---|
| `implementation` | `varchar(20) NOT NULL`, `CHECK IN ('AUTOMATICA','MANUAL')` | La copia que `requirements/mv.md` §5.4 exigía desde el 07-09-2026. Se rellena para lo ya vendido con `products.implementation` de ese día, y después se declara `NOT NULL` |
| `delivery_status` | `varchar(20) NOT NULL DEFAULT 'PENDIENTE'`, `CHECK IN ('PENDIENTE','ENTREGADA','RETENIDA')` | El estado de la entrega (`RN-MV-030`). El `DEFAULT` es lo que deja válidas las líneas existentes y las que escriben las tres entradas sin tocarlas |
| `delivered_at` | `timestamptz NULL` | Desde cuándo se tiene lo comprado; desde aquí corre la vigencia |
| `delivery_note` | `varchar(200) NULL` | Por qué se retuvo, escrito para una persona |
| `ck_movement_details_delivery` | `(delivery_status = 'ENTREGADA') = (delivered_at IS NOT NULL) AND (delivery_status = 'RETENIDA') = (delivery_note IS NOT NULL)` | Ata la fecha a la entrega y el motivo a la retención, en el esquema y no en el caso de uso — como `ck_movements_confirmed` |

**El relleno de `implementation` lee el catálogo, y es lo mejor que se puede hacer.** Las ventas anteriores a `V16` se registraron sin la copia; darles el valor que el producto tiene el día de la migración es asumir que no cambió, que es lo que se asume de toda copia que nace tarde (`V14` hizo lo mismo con el nombre).

**Ningún índice**: la transición y la entrega van por clave primaria.

**`movements.confirmed_at` ya existe** (`V7`), atado por `ck_movements_confirmed` al estado: la transición lo escribe en la misma sentencia.

---

## 3. Componentes afectados

### 3.1 En `SP` — la escritura publicada (D-26)

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `system/users/application` | `MembershipGrant` | **Nueva interfaz** | `grant(GrantOrder)` → `GrantedMembership`. La orden: persona, membresía, días de vigencia (nulo = indefinida), instante. La respuesta: qué quedó vigente y hasta cuándo |
| `system/users/domain/service` | `PublishedMembershipGrant` | Nuevo | Bloquea la fila de la persona (`findNotDeletedByIdForUpdate`, como `RF-SP-032`), cierra la vigente e inserta la nueva con `assignMembership`, audita como `user_memberships` |

**Siempre cierra e inserta, también en la renovación del mismo nivel**, al revés que `RF-SP-032`, que en ese caso solo mueve la fecha. La diferencia es qué hecho se registra: corregir hasta cuándo vale un nivel es una corrección administrativa; **una compra es un periodo nuevo pagado**, y `requirements/mv.md` §5.4 lo fija así — el historial tiene que decir cuántas veces se pagó.

**No compara niveles.** Si lo hiciera, sería la segunda definición de `RN-MV-029`, que es de `MV`. Recibe una orden y la cumple; si la persona no existe o está eliminada, **lanza**, porque eso no puede ocurrir desde una venta registrada y si ocurre es un fallo (`spec.md` §13).

**`@Transactional(propagation = MANDATORY)`**: no abre transacción y falla si no hay una. Es la forma de que la norma de §15.2.1 —«se une a la del que llama»— no sea una convención sino algo que el arranque comprueba.

### 3.2 En `PM` — la implementación viaja con lo que se vende

| Componente | Cambio |
|---|---|
| `ProductCatalog.SaleView` | Gana `implementation`. Es la vista con la que se venden los productos sueltos **y** los de un paquete (`PackageSaleLine.product`), de modo que las tres entradas la reciben por el mismo camino |
| `PublishedProductCatalog` | La proyecta desde `products.implementation` |

### 3.3 En `MV`

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/models` | `MovementLine` | Gana `implementation` y el estado de entrega; `copiarDe` exige la implementación | Una línea sin implementación no se puede entregar |
| `domain/models` | `DeliveryStatus` | Nuevo | `PENDIENTE`, `ENTREGADA`, `RETENIDA` |
| `domain/repository` | `MovementRepository` | Gana `confirmIfPending`, `findLinesForDelivery`, `markDelivered`, `markRetained`; las lecturas de detalle proyectan las columnas nuevas | El `INSERT` de líneas escribe `implementation` |
| `domain/service` | `ConfirmSaleService` | Nuevo | La transición, el bucle y la auditoría |
| `domain/service` | `RegisterSaleService`, `BuyPackageService` | Copian `implementation` en cada línea | El registro por enlace pasa por `RegisterSaleService` y lo hereda |
| `application` | `SaleResponse`, `SaleLineResponse` | `confirmedAt` en la cabecera; `implementation`, `deliveryStatus`, `deliveredAt`, `deliveryNote` en la línea | Compatible: solo añade |
| `application` | `PurchaseResponse`, `PurchaseLineResponse`, `MyMovementResponse` | Lo mismo que arriba donde aplica | El comprador ve si ya tiene lo suyo |
| `interfaces` | `MovementController` | `POST /{id}/confirmation` | |

**`ConfirmSaleService` lee las líneas DESPUÉS de la transición, no antes.** Si las leyera antes y la transición fallara por `EX-002`, habría leído para nada; y si dos confirmaciones leyeran a la vez, las dos tendrían las líneas en la mano. Con la transición primero, solo quien la ganó recorre las líneas.

**La membresía destino y el nivel se leen del producto en ese momento** (`requirements/mv.md` §5.4: «no se relee del catálogo» vale para la vigencia, que se copia; la membresía destino se referencia porque `RF-PM-004` rechaza cambiarla). `findLinesForDelivery` cruza `products` por eso.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/{id}/confirmation` | `movements:confirm` |

**`POST …/confirmation` y no `PATCH …/status`**, aunque seis recursos del sistema cambian de estado con `PATCH /{id}/status`. Aquellos tienen **un** permiso para todas sus transiciones; aquí confirmar y rechazar comparten permiso y anular tiene el suyo, y un `PATCH /status` que aceptara los tres valores tendría **dos modelos de seguridad** en un endpoint — el argumento de `requirements/mv.md` §4.1. El precedente que sí encaja es `POST /{id}/deletion`: una acción con nombre y con su permiso. `RF-MV-004` y `RF-MV-005` seguirán la misma forma (`…/rejection`, `…/voiding`).

**Sin cuerpo.** `spec.md` §6.1: confirmar es un hecho.

### 4.1 La respuesta

`200` con `SaleResponse`, la misma forma que registrar y que el detalle, con lo nuevo:

| Campo | Dónde | Tipo | Nota |
|---|---|---|---|
| `confirmedAt` | cabecera | instante **o nulo** | Nulo en toda venta no confirmada. `types = {"string","null"}`, como `MovementSummary.confirmedAt` |
| `implementation` | línea | `AUTOMATICA` \| `MANUAL` | Lo copiado |
| `deliveryStatus` | línea | `PENDIENTE` \| `ENTREGADA` \| `RETENIDA` | |
| `deliveredAt` | línea | instante **o nulo** | Desde cuándo se tiene |
| `deliveryNote` | línea | texto **o nulo** | Solo en `RETENIDA` |

**`200` y no `204`**: quien confirma necesita ver qué se entregó y qué se retuvo, y hacerlo pedir el detalle después sería una segunda petición para el dato que este acto acaba de producir.

### 4.2 Códigos

| Código | Cuándo |
|---|---|
| `200` | Confirmada; el cuerpo dice qué pasó con cada línea |
| `400` | Identificador malformado (`VAL-001`) |
| `401` | Sin token |
| `403` | Sin `movements:confirm` |
| `404` | No existe (`EX-001`) |
| `409` | No está pendiente (`EX-002`, `EX-003`), con el estado actual en el mensaje |
| `500` | Conceder falló (`EX-004`); nada quedó escrito |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:confirm')")`. El permiso existe desde `V51` y solo lo tiene `SUPERADMIN` (`requirements/mv.md` §6.1); este plan no lo concede a nadie más.

**No hay alcance**: quien confirma confirma cualquiera. Es lo mismo que `RF-MV-006` y por lo mismo.

---

## 6. Auditoría

**Un `ChangeEvent` de `MV`** sobre `movements`, `UPDATE`, con `before` `{status: PENDIENTE}` y `after` `{status: CONFIRMADA, confirmed_at, lines: [{product_code, delivery_status, delivery_note}]}`. El resultado de cada línea va en el evento porque es **lo que este acto decidió** y lo que alguien querrá reconstruir: qué se entregó, qué se retuvo y por qué.

**Y el de `SP`** sobre `user_memberships`, escrito por la operación publicada exactamente como lo escribe `RF-SP-032`, con la misma correlación. Dos hechos, dos asientos.

---

## 7. Transaccionalidad

`@Transactional` en `ConfirmSaleService`; `MANDATORY` en la operación de `SP`. La transición condicionada **bloquea la fila de la venta** hasta el `COMMIT`, de modo que la segunda confirmación simultánea espera y, al despertar, afecta cero filas: `EX-003` se resuelve sin bloqueo explícito.

**La persona se bloquea dentro de `SP`** (`findNotDeletedByIdForUpdate`), y en ese orden —venta y luego persona— siempre: es el mismo orden en que `RF-SP-032` no participa, así que no hay ciclo de bloqueos posible entre una asignación manual y una confirmación.

---

## 8. Impacto sobre otros módulos

| Módulo | Qué cambia |
|---|---|
| `SP` | Publica su **primera escritura** (`MembershipGrant`). `requirements/sp.md` v1.58.0 §8 lo recoge; `architecture.md` v0.31.0 §15.2.1 fija la norma |
| `PM` | `SaleView` gana `implementation`. Ninguna regla cambia |
| `CM` | Nada todavía. **Es aquí donde la etapa 5 devengará**: confirmar es el hecho que comisiona, y el bucle por líneas es donde se enganchará |

**Las enmiendas que este plan declara están aplicadas** en el mismo pase: `requirements/mv.md` v0.24.0 (D-26, `RN-MV-029`, `RN-MV-030`, §5.4, §7.3), `requirements/sp.md` v1.58.0, `architecture.md` v0.31.0, `modelo-datos.md` v0.60.0.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| `SELECT … FOR UPDATE` y luego `UPDATE` | Funciona, y obliga a dos sentencias donde una condicionada basta; la cuenta de filas ya dice quién ganó |
| Que `MV` emita un evento y `SP` conceda al recibirlo | Conceder dejaría de ser inmediato y «pagó y no subió» sería la avería que nadie reporta. Descartado al cerrar D-26 |
| Que la operación de `SP` compare niveles | Segunda definición de `RN-MV-029` fuera de su módulo (§15.2.1) |
| Tabla aparte para la entrega, con quién autorizó | Lo que hay que responder es «¿se entregó, y desde cuándo?»; quién lo hizo va a la auditoría. `RN-MV-030` lo fija |
| Un quinto estado de `movements` («confirmada a medias») | Decidiría por toda la venta lo que el dato solo permite decidir por línea (`requirements/mv.md` §5.4) |
| `PATCH /{id}/status` | Dos modelos de seguridad en un endpoint (§4) |
| Responder `204` | Quien confirma se quedaría sin saber qué se retuvo |
| Leer las líneas antes de la transición | Dos confirmaciones simultáneas tendrían las líneas en la mano; leerlas después deja el recorrido solo a quien ganó |
| Relleno de `implementation` con `AUTOMATICA` fijo | Entregaría sin revisión lo que se vendió con revisión prometida. El valor del catálogo ese día es lo mejor que se sabe |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Conceder dos veces** — el defecto que importa | Transición condicionada; `CA-MV-084` y `CA-MV-097` (concurrente) |
| **Confirmar sin conceder** | `MANDATORY` en `SP`, sin `REQUIRES_NEW` en ningún punto; `EX-004` deshace todo |
| Que `SaleView` no lleve la implementación en alguna entrada nueva | `copiarDe` la exige: una línea sin ella no se construye |
| Que un `409` de `EX-002` no diga el estado y la pasarela no sepa si ya se procesó | El mensaje lleva el estado; `CA-MV-085` lo comprueba |
| Bloqueo cruzado entre confirmar y `RF-SP-032` | Orden fijo venta → persona; `RF-SP-032` solo toma persona |
| Que la retención pase inadvertida | Va en la respuesta, en el detalle, en `RF-MV-014` y en la auditoría |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Transición, `confirmedAt`, segunda confirmación `409` sin cambios | Integración | Es observable solo con la base |
| Rechazada/anulada `409` con el estado; inexistente `404`; `403`; `401` | Integración | |
| Upgrade automático: membresía vigente, anterior cerrada, `ends_at` desde la confirmación, línea entregada | Integración, leyendo `user_memberships` | Es el efecto del requerimiento |
| Sin vigencia → `ends_at` nulo; renovación → periodo nuevo | Integración | |
| Inferior al vigente → retenida con motivo, membresía intacta | Integración | `RN-MV-029` |
| Manual → pendiente; bot automático → entregada; paquete → cada línea por su regla | Integración | |
| Auditoría: el asiento de `MV` con las líneas y el de `SP` | Integración, leyendo `audit_change_log` | |
| Dos confirmaciones simultáneas: una `200`, una `409`, una membresía | Integración concurrente, con el arnés del proyecto | Es lo que una pasarela hace |
| `MANDATORY` sin transacción falla | Unitaria sobre el `Published…` de `SP` | Fija que la norma no es convención |
| `copiarDe` sin implementación lanza | Unitaria | |
