# PLAN — `RF-MV-027` Comprar puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-027` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 30-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Una venta sin líneas**: la cabecera en `movements` y su primer pago en `payments`, en la misma transacción, como registra `RF-MV-001` desde `RF-MV-018`. Lo que cambia es el tipo —`COMPRA_PUNTOS`, `REGISTRADO`—, que no hay líneas, y que la cabecera lleva `points_rate_id` y `points_amount`.

**La clave va en el pago**, como en la venta (`RN-MV-040`): la compra tiene pago, y `uq_payments_idempotency_key` es quien resuelve la carrera. La traducción es la de `RF-MV-018` · `plan.md` §7 —relectura en transacción nueva; mismos datos, `200`; otros, `409`—.

**El método se resuelve con las reglas de la venta** (`SaleRules.resolverMetodoDePago`), y a continuación se rechazan `POINTS` y **todo método `INTERNO`**: la regla de la venta admite `POINTS` y solo rechaza por su código el interno `GRATIS`; aquí se comprueba la visibilidad, para que `MANUAL` —el del retiro— tampoco valga.

---

## 2. Cambios de esquema

Ninguno. Lo trae `RF-MV-025` · `T-01`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/models` | `Movement` | Gana `compraDePuntos(persona, moneda, importe, tasa, puntos, instante)` | Sin líneas; `total = payable = importe`, descuento cero |
| `domain/models` | `PointsAmount` | Nuevo | `desde(importe, tasa)`: redondeo `FLOOR` a dos decimales; cero es inválido |
| `domain/service` | `BuyPointsService` | Nuevo | Clave, validación, estado de la cuenta (interfaz de `SP`), moneda, tasa (`PointsRates`), método, `INSERT` de cabecera y pago, traducción, auditoría |
| `domain/repository` | `JpaMovementRepository` | Inserta la cabecera con las dos columnas nuevas | El código con prefijo `PTS` sale del generador de siempre (`RN-MV-016`) |
| `application` | `BuyPointsRequest`, `PointsPurchaseResponse` | Nuevos | La respuesta: `id`, `code`, `status`, `currency`, `amount`, `pointsRate` (id y valor), `points`, `occurredAt`, `confirmedAt`, `rejectedAt`, `rejectionReason`, `payments`. **La reutilizan `RF-MV-028`, `RF-MV-029` y `RF-MV-031`** |
| `interfaces` | `PointsPurchaseController` | Nuevo | `POST /movements/mine/points-purchases` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/mine/points-purchases` | `movements:buy-points` |

**Bajo `/mine`**, porque es sobre lo propio; `RF-MV-031` lista en la misma ruta con `GET`. **Cabecera `Idempotency-Key`**, obligatoria. **Cuerpo**: `{ "currencyId", "amount", "paymentMethodId" }`.

| Código | Cuándo |
|---|---|
| `201` | Compra registrada, pendiente |
| `200` | La misma petición repetida (`FA-001`) |
| `400` | Datos ausentes o malformados, **todos juntos**; clave ausente o malformada; importe que da cero puntos (`EX-001`) |
| `401` / `403` | Sin token / sin `movements:buy-points` |
| `409` | Moneda inactiva o sin tasa (`EX-003`); método desactivado, interno o `POINTS` (`EX-005`); cuenta que no opera (`EX-006`); clave de otra petición (`EX-008`) |
| `422` | Moneda (`EX-002`) o método (`EX-004`) inexistentes |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:buy-points')")`. **El sujeto es siempre el actor**: la petición no lleva persona.

---

## 6. Auditoría

Un `ChangeEvent` sobre `movements`, `INSERT`, con la moneda, el importe, la tasa, los puntos y el pago —en el mismo asiento, como la venta: son un solo hecho—. **La clave no se audita.**

---

## 7. Transaccionalidad

`@Transactional`: cabecera y pago. Relectura tras clave repetida, en transacción nueva. **La tasa se lee dentro de la transacción**, y es la que se guarda (`spec.md` §13).

---

## 8. Impacto sobre otros módulos

**`SP`**: el estado de la cuenta del actor, por la interfaz que usa el retiro (`RF-MV-019`), y la moneda por `CurrencyCatalog`. **`CM`**: ninguno, y a propósito: no se publica ningún aviso de líneas comisionables (`RN-MV-049`), porque no hay líneas. **Los listados de administración** (`RF-MV-006`) la muestran con su tipo; **«mis compras»** (`RF-MV-008`) no, porque fija `VENTA` (`RN-MV-047`) y la persona las ve en `RF-MV-031`.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Comprar por puntos y calcular el importe | El cobro es lo que tiene que ser exacto; redondear el importe cobraría de más o de menos |
| La clave en `movements.idempotency_key` | Esa es la de los movimientos **sin pago** (`RN-MV-045`); este tiene pago, y la clave va en él |
| Una línea con un producto «puntos» | Comisionaría, y no hay producto (`requirements/mv.md` §4.4) |
| Abonar al registrar y retirar si el pago falla | Los puntos existirían antes de pagarse y podrían gastarse (`RN-MV-004`) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que un listado de ventas la trate como venta | `RF-MV-008`, `RF-MV-015` y `RF-MV-017` fijan `VENTA` en la sentencia; `CA-MV-309` comprueba que no aparece en las líneas |
| Doble compra por doble clic | `uq_payments_idempotency_key`; `CA-MV-310` con dos hilos |

---

## 11. Estrategia de prueba

Integración, `BuyPointsIT`: `CA-MV-306` a `CA-MV-317`; `CA-MV-310` con dos hilos y la misma clave; `CA-MV-307` con una tasa de cuatro decimales. Unitarias de `PointsAmount`.
