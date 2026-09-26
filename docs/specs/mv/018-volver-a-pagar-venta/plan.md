# PLAN — `RF-MV-018` Volver a pagar una venta pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-018` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 26-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 26-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Dos trabajos en uno, y el primero es el grande.** Antes de poder abrir un segundo pago hay que **sacar el método de `movements`** y darle a cada venta su primer pago; eso toca las cinco entradas de compra, confirmar, anular, los tres listados y el detalle. Después, lo propio de este requerimiento es pequeño: un `INSERT` en `payments` que el esquema deja pasar o no.

**El esquema decide, no una lectura previa.** Los dos índices únicos parciales de `RN-MV-039` y el único de la clave (`RN-MV-040`) son los que resuelven las carreras: el servicio **intenta insertar** y traduce la violación, como `RF-MV-016` traduce las suyas. Leer primero y escribir después dejaría pasar dos peticiones simultáneas, que es justo `CA-MV-210`.

**El «último pago» se resuelve en la consulta**, con un `LEFT JOIN LATERAL (… ORDER BY occurred_at DESC, id DESC LIMIT 1)` sobre `payments`. No se copia el método en la cabecera: una copia es un segundo sitio donde el dato puede discrepar de sí mismo, y es exactamente lo que acaba de salir de `movements`.

---

## 2. Cambios de esquema

**La siguiente migración libre al construir** —`V48` a 26-09-2026, porque `V47` la tomó `AC` ese día—, `V48__mv_pagos.sql`:

| Elemento | Definición | Por qué |
|---|---|---|
| `payments` | Las columnas de [`requirements/mv.md` §7.7](../../../requirements/mv.md) | `RN-MV-039` |
| `fk_payments_movement` | `movement_id` → `movements(id)` **`ON DELETE CASCADE`** | Un pago no sobrevive a su movimiento. **`CASCADE` y no `RESTRICT`** por la lección de `product_links`: una FK sin `ON DELETE` rompe las suites que limpian con `DELETE FROM movements`, lejos de aquí. En producción nadie borra movimientos (`RN-MV-001`) |
| `fk_payments_method` | `payment_method_id` → `payment_methods(id)` `RESTRICT` | La que tenía `movements` |
| `uq_payments_idempotency_key` | `UNIQUE (idempotency_key)` | `RN-MV-040` |
| `uq_payments_uno_pendiente` | `UNIQUE (movement_id) WHERE status = 'PENDIENTE'` | `RN-MV-039` |
| `uq_payments_uno_confirmado` | `UNIQUE (movement_id) WHERE status = 'CONFIRMADO'` | `RN-MV-039` |
| `ck_payments_status`, `ck_payments_confirmed`, `ck_payments_rejected`, `ck_payments_amount` | Los de `requirements/mv.md` §7.6 | |
| `ix_payments_ultimo` | `(movement_id, occurred_at DESC, id DESC)` | El `LATERAL` del «último pago», en los tres listados |
| `ix_payments_metodo` | `(payment_method_id)` | El filtro por método |

**El traslado de lo que ya existe**, en la misma migración y en este orden —**crear, copiar, y solo entonces borrar**, como `V12` con el vendedor—:

```sql
INSERT INTO payments (id, movement_id, payment_method_id, status, amount, idempotency_key,
                      occurred_at, confirmed_at, rejected_at, rejection_reason, created_at)
SELECT gen_random_uuid(), m.id, m.payment_method_id,
       CASE m.status WHEN 'CONFIRMADA' THEN 'CONFIRMADO'
                     WHEN 'PENDIENTE'  THEN 'PENDIENTE'
                     ELSE 'RECHAZADO' END,
       m.payable_amount, 'migracion-' || m.id,
       m.occurred_at, m.confirmed_at,
       CASE WHEN m.status = 'ANULADA' THEN m.voided_at END,
       CASE WHEN m.status = 'ANULADA' THEN 'Venta anulada: ' || m.void_reason END,
       m.created_at
  FROM movements m;

ALTER TABLE movements DROP CONSTRAINT fk_movements_payment_method;
ALTER TABLE movements DROP COLUMN payment_method_id;
```

**`gen_random_uuid()` da un UUID v4**, no el v7 que genera la aplicación: se acepta en las filas trasladadas —nadie ordena por el identificador de un pago, se ordena por `occurred_at`— y queda escrito en la cabecera de la migración. **Ninguna venta está `RECHAZADA`** (`RF-MV-004` nunca se construyó), y el `ELSE` las cubriría igual. **Se comprueba al aplicarla**: la migración termina con un `DO $$ … $$` que levanta excepción si algún movimiento queda sin exactamente un pago, como `V78` hace con `GRATIS`.

**Y el permiso**: `movements:retry-payment`, sembrado como `V31` sembró `movements:list-own` —a `SUPERADMIN`, a `ADMIN` explícito y a todo rol por su tipo—, porque es una operación sobre lo propio que tiene que poder hacer cualquiera que compre.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/models` | `Payment`, `PaymentStatus` | Nuevos | `Payment.pendiente(movimiento, método, importe, clave, instante)`; los estados en masculino (`RN-MV-039`) |
| `domain/models` | `IdempotencyKey` | Nuevo | `VAL-002`; `IdempotencyKey.generada()` para las compras que no la mandan: `srv-` + UUID v7 |
| `domain/models` | `Movement` | **Pierde `paymentMethodId`**; gana el pago con el que nace | `RN-MV-001` intacta: la venta sigue sin editarse |
| `domain/repository` | `PaymentRepository`, `JpaPaymentRepository` | Nuevos | `insert`, `findByIdempotencyKey`, `findByMovement` (ordenados), `confirmPending`, `rejectPending` |
| `domain/repository` | `JpaMovementRepository` | Inserta la venta **y** su primer pago en la misma transacción; los listados y el detalle toman el método del `LATERAL`; los tres filtros por método van sobre él | La sentencia de `RF-MV-015` y la de `RF-MV-017` también (`RF-MV-017` no filtra por método, pero publica el de la venta) |
| `domain/service` | `SaleRules.resolverMetodoDePago` | **Sin cambios**, se reutiliza | `RN-MV-018`, `RN-MV-022`, `EX-010` |
| `domain/service` | `RetryPaymentService` | Nuevo | Clave, venta propia, estado, método, `INSERT`, traducción de violaciones, auditoría |
| `domain/service` | `RegisterSaleService`, `BuyByHotlinkService`, `BuyPackageService`, `PublishedRegistrationSaleRegistrar` | Reciben la clave opcional y crean el primer pago | Con clave repetida devuelven **la venta que ya existe** (`CA-MV-216`) |
| `domain/service` | `ConfirmSaleService` | Confirma el pago pendiente y la venta, en la misma transacción | Ver `RF-MV-003` · `spec.md` v0.2.0 |
| `domain/service` | `VoidSaleService` | Rechaza el pago pendiente con el motivo | Ver `RF-MV-005` · `spec.md` v0.2.0 |
| `application` | `PaymentResponse` | Nuevo | Método (id, código, nombre), estado, importe, `providerReference`, `occurredAt`, `confirmedAt`, `rejectedAt`, `rejectionReason`. **Sin la clave** (`RN-MV-047`) |
| `application` | `SaleResponse` | Gana `payments` | El método de cabecera sigue, y es el del último pago |
| `application` | `RetryPaymentRequest` | Nuevo | `paymentMethodId`, nulable |
| `interfaces` | `MovementController` | `POST /mine/{id}/payments`; `POST` de registrar lee `Idempotency-Key` opcional | |
| `interfaces` | `HotlinkPurchaseController`, `PackagePurchaseController`, y la compra de `RF-MV-002` | Leen `Idempotency-Key` opcional | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/mine/{id}/payments` | `movements:retry-payment` |

**Bajo `/mine`**, junto al detalle propio, porque es una operación sobre lo propio y responde `404` a lo ajeno como él. **Un recurso `payments` y no una acción con nombre** como `…/confirmation`: aquí se **crea** una cosa que antes no existía —un pago—, y eso es un `POST` a su colección.

**Cabecera `Idempotency-Key`**, obligatoria. **En la cabecera y no en el cuerpo**, que es donde la ponen las pasarelas y el borrador del IETF (`draft-ietf-httpapi-idempotency-key-header`): el día que una pasarela llame a este sistema, ya sabrá dónde mirar.

**Cuerpo**: `{ "paymentMethodId": "…" }`, o `{}` en una venta de importe cero.

**Respuesta**: `SaleResponse` con `payments`.

| Código | Cuándo |
|---|---|
| `201` | Pago abierto. `Location` apunta al detalle propio |
| `200` | **La misma petición repetida** (`FA-001`): la misma respuesta, sin crear nada |
| `400` | Identificador malformado, clave ausente o malformada (`EX-007`), método presente en una venta de importe cero o ausente en una cobrada (`VAL-003`) |
| `401` / `403` | Sin token / sin `movements:retry-payment` |
| `404` | La venta no existe, no es del actor, o no es una venta (`EX-001`, `EX-002`) |
| `409` | No está pendiente (`EX-003`), tiene un pago pendiente (`EX-004`), el método está desactivado (`EX-005`) o la clave es de otra petición (`EX-006`) |
| `422` | El método no existe (`EX-005`) |

**En las cinco entradas de compra**, la cabecera `Idempotency-Key` es **opcional**, y su única consecuencia visible es que repetir la compra con la misma clave responde `200` con la venta ya registrada, en lugar de `201` con otra. **Ningún cuerpo cambia.**

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:retry-payment')")`, y el alcance en la consulta: `user_id = :actor`. **Un permiso por operación** (`RN-SEG-014`): `EndpointPermissionsIT` lo vigila.

---

## 6. Auditoría

Un `ChangeEvent` de `MV` sobre `payments`, `INSERT`, con el movimiento, el método, el importe y el estado. **La clave no se audita**: es del cliente y no explica nada. Crear el primer pago al registrar va **en el mismo asiento** que la venta: son un solo hecho.

---

## 7. Transaccionalidad

`@Transactional`. El `INSERT` puede violar tres restricciones, y cada una se traduce por **nombre de restricción** —PostgreSQL lo da en un único; a diferencia del `EXCLUDE` de `CM`, aquí sí llega—:

| Restricción | Traducción |
|---|---|
| `uq_payments_idempotency_key` | Releer el pago de esa clave: misma venta y mismo método → `200` con él (`FA-001`); si no → `409` (`EX-006`) |
| `uq_payments_uno_pendiente` | `409` (`EX-004`) |
| `uq_payments_uno_confirmado` | No puede ocurrir: solo se inserta en `PENDIENTE`. Si ocurre, es un defecto y sale `500` |

**La relectura tras la violación ocurre en una transacción nueva**: la que violó la restricción está abortada en PostgreSQL y no admite más sentencias. El servicio lo hace con `REQUIRES_NEW` o fuera del `@Transactional`, como decida la construcción.

---

## 8. Impacto sobre otros módulos

**`SP`**: `PublishedRegistrationSaleRegistrar` —la venta del alta de `RF-SP-045`— crea su primer pago; la interfaz publicada no cambia.

**El frontend**: ningún cuerpo cambia. Gana `payments` en las respuestas de la venta y la cabecera opcional en las compras. **El contrato OpenAPI se regenera y la prosa de las `@Operation` se relee**.

**`CM`**: ninguno. Liquida las líneas de las ventas confirmadas, y eso no cambia.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Copiar el método del último pago en `movements` | Un segundo sitio para el mismo dato; es lo que acaba de salir |
| Registrar otra venta para reintentar | Es lo que había, y deja ventas pendientes huérfanas y dos compras de lo mismo en el registro del comprador (`spec.md` §2) |
| Clave de idempotencia opcional también aquí | Sin clave, un reintento tras una caída abre un segundo pago; aquí eso es cobrar dos veces (`spec.md` §2.1) |
| Obligatoria también en las compras | Rompería a todos los clientes de esas rutas por un daño menor —una venta pendiente de más— (`spec.md` §2.2) |
| Comprobar «no hay pago pendiente» con un `SELECT` antes del `INSERT` | Dos peticiones simultáneas pasarían las dos (`CA-MV-210`) |
| `payments` con `RESTRICT` hacia `movements` | Rompería las suites que limpian ventas, lejos de aquí |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Perder el método de alguna venta al trasladarlo | Crear, copiar y comprobar **antes** de borrar la columna; el `DO $$` final aborta la migración |
| Que un listado siga leyendo la columna retirada | Falla al compilar la sentencia, y las suites de `RF-MV-006`, `RF-MV-008`, `RF-MV-015` y `RF-MV-017` la ejercitan |
| Que los fixtures de las suites escriban `movements.payment_method_id` | Buscarlo en `src/test` y pasarlo a `payments` en la misma tarea (`T-09`) |
| Dos pagos abiertos por una carrera | El índice único parcial; `CA-MV-210` con dos hilos |
| Un `target/` con migraciones de otra rama al cambiar de rama | `mvn clean verify`, nunca `verify` a secas |

---

## 11. Estrategia de prueba

**Integración.** `RetryPaymentIT` —`CA-MV-206` a `CA-MV-215`, con dos hilos para `CA-MV-210`—; `PaymentsOnRegistrationIT` —`CA-MV-216`, por las cinco entradas—; y `CA-MV-217` en la suite de cada listado. **Las suites de confirmar y anular ganan sus criterios nuevos** (`RF-MV-003` y `RF-MV-005`). **La migración** se comprueba sobre la base de desarrollo antes de subirla: cada movimiento con un pago, y los estados casados. **Se cuentan las sentencias** del listado con las estadísticas de Hibernate: el `LATERAL` no puede convertirse en una consulta por fila.
