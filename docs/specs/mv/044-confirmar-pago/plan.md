# PLAN — `RF-MV-044` Confirmar un pago pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-044` |
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

**Un servicio nuevo, `PaymentResolutionService`, que no confirma nada por sí mismo: localiza, bloquea, comprueba y delega.** El efecto de cada tipo ya existe y está probado —`ConfirmSaleService` para la venta, `PointsPurchaseService` para la compra de puntos—, y la notificación de la pasarela (`RF-MV-041`) ya lo invoca por dentro. Reescribirlo aquí sería tener dos copias de la entrega y del abono; este plan solo cambia **la puerta**.

**El orden de los bloqueos no cambia: la cabecera del movimiento primero.** Es la fila que serializa desde el 26-09-2026 (`RF-MV-003` · `JpaMovementRepository.confirmIfPending`; `RF-MV-004`, `RF-MV-018` y la pasarela bloquean la misma), y un camino que bloqueara el pago antes que el movimiento podría cruzarse con ellos en un interbloqueo. Por eso se resuelve en **dos sentencias**:

1. `PaymentRepository.lockMovementOf(paymentId)`: `SELECT m.id … FROM movements m WHERE m.id = (SELECT p.movement_id FROM payments p WHERE p.id = :pago) FOR UPDATE`. El `movement_id` de un pago no cambia nunca, de modo que leerlo sin bloqueo es seguro. Cero filas → `EX-001`.
2. `PaymentRepository.findTarget(paymentId)`: una lectura **nueva**, ya con el movimiento bloqueado —en `READ COMMITTED` cada sentencia toma su instantánea—, del estado del pago, el código del tipo del movimiento y si tiene cobro abierto. Hacerlo en una sola sentencia con `FOR UPDATE OF m` dejaría el estado del pago leído **antes** de esperar el bloqueo: la reevaluación de PostgreSQL relee la fila bloqueada, no las demás de la reunión.

**Después, las comprobaciones, en el orden de `spec.md` §10**: tipo (`EX-002`), estado del pago (`EX-003`, con el estado en el mensaje), cobro abierto (`EX-004`, la comprobación de `CardPayment.exigirSinCobroAbierto`). **Y la delegación**, con el movimiento ya bloqueado por esta transacción: `ConfirmSaleService.confirmPayment(movimiento, referencia)` o `PointsPurchaseService.confirmPayment(movimiento, referencia)`, dos métodos **de paquete** nuevos que entran en la misma transición condicionada que ya tienen. Como el pago pendiente es a lo sumo uno (`RN-MV-039`) y el movimiento está bloqueado, **el pago que confirma la transición es el que se nombró**.

**La respuesta es `GetMovementService.get(movimiento)`**, la del detalle (`RF-MV-007`), leída al final en la misma transacción.

---

## 2. Cambios de esquema

**Ninguno en las tablas.** El permiso lo trae **`V63__mv_conciliar_por_el_pago.sql`**:

- Inserta `movements:confirm-payment` (`01a0ef9c-6800-701f-9c4f-5e7ad7000058`).
- Se lo da a **todo rol que porte `movements:confirm` o `movements:confirm-points-purchase`** —hoy `SUPERADMIN` y `ADMIN`—, y `movements:reject-payment` a todo rol que porte `movements:reject-points-purchase` (`RF-MV-045` · `plan.md` §2). **Por posesión y no por nombre de rol**: un rol personalizado que hubiera recibido alguno de los retirados no pierde la facultad.
- Corrige el nombre y la descripción de `movements:reject-payment`.
- Borra de `role_permissions` y de `permissions` los tres retirados.
- Un bloque de comprobación al final, con el patrón de `V61` y `V62`: el nuevo existe y lo portan `SUPERADMIN` y `ADMIN`; los tres retirados no existen; el catálogo cuenta **179**.

`V63` era de `RF-MV-043`, que no está construido: pasa a `V64` (`requirements/mv.md` v0.67.0 §4.7). Flyway no aplica migraciones fuera de orden, y la que se construya primero toma el número menor.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `db/migration` | `V63__mv_conciliar_por_el_pago.sql` | Nueva | §2 |
| `domain/repository` | `PaymentRepository` | Gana `lockMovementOf(pago)` y `findTarget(pago)` → `PaymentTarget(paymentId, movementId, movementType, status, openCharge)` | §1 |
| `domain/repository` | `MovementRepository.confirmIfPending` | Gana la referencia: `provider_reference = COALESCE(:referencia, provider_reference)` en el pago | La venta no la guardaba; la compra de puntos sí (`confirmPendingPayment`) |
| `domain/service` | `PaymentResolutionService` | Nuevo: `confirm(pago, Confirmation)` | Y `reject` (`RF-MV-045`) |
| `domain/service` | `ConfirmSaleService` | `confirm(movimiento)` **se retira**; nace `confirmPayment(movimiento, referencia)`, de paquete. `confirmByGateway` e `confirmInternal` no cambian | |
| `domain/service` | `PointsPurchaseService` | `confirm(compra, Confirmation)` **se retira**; nace `confirmPayment(compra, referencia)`, de paquete | La validación de la referencia sube al servicio nuevo |
| `application` | `PaymentRequests.Confirmation(providerReference)` | Nuevo | Sustituye a `PointsRequests.Confirmation` |
| `interfaces` | `PaymentController` | `POST /movements/payments/{paymentId}/confirmation` | |
| `interfaces` | `MovementController` | **Se retira** `POST /movements/{id}/confirmation` | |
| `interfaces` | `PointsController` | **Se retira** `POST /movements/{id}/points-purchase-confirmation` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/payments/{paymentId}/confirmation` | `movements:confirm-payment` |

**Cuerpo opcional**: `{ "providerReference": "…" }`. Sin cuerpo es lo mismo que sin referencia.

**`/movements/payments/…` y no `/payments/…`**: los pagos son un submódulo de `MV` (`requirements/mv.md` §2) y el listado de `RF-MV-043` ya reservó `GET /movements/payments`. **No choca con `GET /movements/{id}`**: aquella variable exige forma de UUID (`RF-MV-007` · `plan.md`), y `payments` no la tiene.

| Código | Cuándo |
|---|---|
| `200` | El movimiento, con la forma de `GET /movements/{id}` (`SaleResponse`) |
| `400` | Identificador malformado (`VAL-001`) o referencia de más de 120 caracteres (`VAL-002`) |
| `401` / `403` | Sin token / sin `movements:confirm-payment` |
| `404` | El pago no existe (`EX-001`) |
| `409` | Es de un retiro (`EX-002`), no está pendiente (`EX-003`, con el estado) o tiene cobro abierto (`EX-004`) |
| `500` | Lo de cada tipo falló; nada quedó escrito (`EX-007`) |

**Cambio rompedor**: `POST /movements/{id}/confirmation` y `POST /movements/{id}/points-purchase-confirmation` responden `404` desde que esto se despliegue. **La prosa de la `@Operation`** dice lo que pasa con cada tipo —la de `RF-MV-003` sobre las líneas, resumida, y la de `RF-MV-028` sobre el abono— y lo que la pasarela resuelve sola.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:confirm-payment')")`. **Sin alcance**: quien concilia ve el extracto de toda la empresa. `EndpointPermissionsIT.PERMISO_DE_CADA_OPERACION` gana la ruta y pierde las dos retiradas.

---

## 6. Auditoría

**La de cada tipo, sin cambios**: `RF-MV-003` audita la venta con el resultado de cada línea y `RF-MV-028` la compra con el pago, la referencia y los puntos. **La venta gana la referencia** en su `after` cuando la hay. El servicio nuevo **no audita por su cuenta**: escribiría dos veces el mismo hecho.

---

## 7. Transaccionalidad

`@Transactional` en `PaymentResolutionService.confirm`; los métodos delegados se suman a ella. **El bloqueo del movimiento es lo primero que se escribe**, y todo —transición, pago, entrega o abono, auditoría y la lectura de la respuesta— va dentro. Si algo falla, nada queda (`EX-007`). **El aviso a `CM`** sigue siendo el evento `AFTER_COMMIT` de `RF-MV-003`: solo sale si esta transacción confirma.

---

## 8. Impacto sobre otros módulos

**`CM`**: ninguno; el evento es el mismo. **La pasarela (`RF-MV-041`)**: ninguno; `GatewayEventProcessor` sigue llamando a `confirmByGateway`. **El frontend**: las pantallas de conciliación de ventas y de compras de puntos llaman a la ruta nueva con el `id` del pago, que publican el detalle (`RF-MV-007`, `RF-MV-008`) y el listado de compras de puntos (`RF-MV-031`). **El contrato OpenAPI se regenera y la prosa se relee.**

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Mantener las rutas por movimiento como alias | Descartado por el responsable: dos rutas para una operación rompen `RN-SEG-014` y duplican las pruebas |
| `PATCH /movements/payments/{id}` con `status` | Un cuerpo que elige el efecto tendría dos permisos en un endpoint; el precedente es la acción con nombre (`…/confirmation`, `…/voiding`) |
| Copiar la entrega y el abono en el servicio nuevo | Dos copias de lo mismo, que la pasarela ya comparte. Se delega |
| Bloquear el pago y luego el movimiento | Orden inverso al de los demás caminos: interbloqueo posible (§1) |
| Responder el pago y no el movimiento | Quien concilia necesita ver qué se entregó; el pago solo ya está dentro del detalle |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Confirmar un pago distinto del nombrado | El movimiento bloqueado antes de leer el pago, y a lo sumo un pendiente (§1); `CA-MV-503`, `CA-MV-504` |
| Un rol personalizado pierde la facultad al retirar el permiso | `V63` reparte por posesión (§2) |
| Pruebas de otras suites que confirmaban por la ruta vieja | Se cambian todas en esta rama, con un ayudante común de prueba (`tasks.md` `T-06`) |

---

## 11. Estrategia de prueba

Integración, **`ConfirmPaymentIT`**: `CA-MV-495` a `CA-MV-506`. `CA-MV-502` con el doble `FakeCardGateway`, encendido. `CA-MV-503` y `CA-MV-504` con `ConcurrencyHarness`, como `ConfirmSaleConcurrencyIT`. **El recuento del catálogo** baja a 179 en todas las suites que lo cuentan (`requirements/mv.md` §6 y la nota de `RF-MV-007`).
