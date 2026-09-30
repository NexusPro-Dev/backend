# PLAN — `RF-MV-030` Pagar una compra con puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-030` |
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

**Nada cambia hasta que el pago existe.** Cada entrada registra la venta y abre su pago `PENDIENTE` como hoy (`RF-MV-018`). **Si el método es `POINTS`**, a continuación y en la misma transacción:

1. `PointsRates.vigente(moneda)` —la de `RF-MV-025`—; sin ella, `409`.
2. `N = ceil(payable_amount × tasa, 2)`.
3. `Ledger.apply(venta, pago, PAGO, [PUNTOS de quien compra −N, PUNTOS_EMITIDOS +N])`. **Vacío = no alcanza**: se lanza el `409` y la transacción entera se revierte, con la venta y el pago (`CA-MV-337`). El bloqueo de la fila que hace el `Ledger` es lo que resuelve `CA-MV-340`.
4. **La confirmación de siempre**: la transición de la venta, el pago a `CONFIRMADO`, la entrega de cada línea y el aviso a `CM`.

**La confirmación se extrae de `ConfirmSaleService`** a un componente compartido, `SaleConfirmation`, como `RF-MV-010` extrajo `LineDelivery`: los pasos 1 a 4 de `confirm` (transición, líneas, auditoría, aviso). `RF-MV-003` lo llama igual que antes, y este requerimiento lo llama con el pago recién descontado. **Un solo sitio que confirma** es la única forma de que `CA-MV-332` y `CA-MV-334` no dependan de haber copiado bien.

**La idempotencia no necesita nada nuevo**: la clave va en el pago, y con la misma clave `uq_payments_idempotency_key` impide el segundo pago antes de llegar al `Ledger` (`CA-MV-341`).

---

## 2. Cambios de esquema

**En la migración de la etapa** (`RF-MV-025` · `T-01`), después de ampliar los `CHECK`:

```sql
UPDATE payments p
   SET status = 'RECHAZADO', rejected_at = now(),
       rejection_reason = 'Pagar con puntos no descontaba saldo antes del 30-09-2026: '
                       || 'vuelve a pagar la compra con otro método o con tus puntos.'
  FROM payment_methods pm
 WHERE pm.id = p.payment_method_id AND pm.code = 'POINTS' AND p.status = 'PENDIENTE';
```

**La venta no se toca**: sigue `PENDIENTE` y sin pago pendiente, que es exactamente el estado que `RF-MV-018` sabe volver a pagar. **Una guarda** al final: ningún pago `POINTS` queda `PENDIENTE`. **No se audita**, como ninguna migración; el motivo queda en el propio pago y lo lee quien compró en el detalle de su compra.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/service` | `SaleConfirmation` | Nuevo, **extraído** de `ConfirmSaleService` | Transición, líneas (`LineDelivery`), auditoría, aviso a `CM` |
| `domain/service` | `ConfirmSaleService` | Delega en `SaleConfirmation` | Sin cambio de comportamiento: sus suites lo prueban |
| `domain/service` | `PointsPayment` | Nuevo | Pasos 1 a 3 de §1, y llama a `SaleConfirmation` |
| `domain/models` | `PointsAmount` | Gana `costo(importe, tasa)` con `CEILING` | El de `RF-MV-027` redondea hacia abajo |
| `domain/service` | `BuyByHotlinkService`, `BuyPackageService`, `RetryPaymentService` | Tras abrir el pago, si es `POINTS`, `PointsPayment` | Y la compra de `RF-MV-002` cuando se construya |
| `domain/service` | `RegisterSaleService`, `PublishedRegistrationSaleRegistrar` | **Rechazan `POINTS`** con `409` (`EX-003`) | Quien registra no es quien compra |
| `application` | `PaymentResponse` | Gana `points`, nulo salvo en un pago con puntos | Se lee del asiento `PAGO` de ese pago, no se guarda en `payments` |

---

## 4. Contrato de API

**Ninguna ruta nueva.** Las entradas afectadas mantienen su cuerpo, su permiso y sus códigos, y ganan:

| Código | Cuándo |
|---|---|
| `201` | Con `POINTS`: la venta **ya confirmada**, con su pago confirmado y `points` |
| `409` | No alcanza (`EX-001`); sin tasa vigente (`EX-002`); `POINTS` en el registro de un funcionario o en el alta por enlace (`EX-003`) |

**La prosa de cada `@Operation` afectada se reescribe**: hoy ninguna dice que una compra puede volver confirmada.

---

## 5. Autorización

**Ninguna nueva**: la de cada entrada. Pagar con puntos no es una operación (`requirements/mv.md` §6).

---

## 6. Auditoría

La de registrar la venta y la de confirmarla, **las dos**, como si se hubiera confirmado a mano un instante después; el asiento de confirmar lleva los puntos descontados.

---

## 7. Transaccionalidad

**Una sola transacción** para venta, pago, asientos y confirmación. El `409` por saldo se lanza **después** de haber insertado la venta, y la reversión se la lleva: se comprueba en `CA-MV-337` leyendo la base, no la respuesta.

---

## 8. Impacto sobre otros módulos

**`CM`**: recibe el aviso de siempre; la venta con puntos comisiona sobre el valor de sus líneas, como cualquier otra (`requirements/mv.md` §4.4). **`SP`**: el alta por enlace (`RF-SP-045`) ofrece los métodos de `RF-MV-009`, entre ellos `POINTS`; **elegirlo pasa a rechazarse**, y la respuesta del registro lo dirá. Es un cambio de comportamiento del registro, declarado. **El frontend**: una compra con puntos vuelve confirmada, y debería ocultar `POINTS` en el formulario de registro.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Descontar antes de registrar la venta | El asiento cuelga del movimiento y del pago (`RN-MV-042`): tienen que existir antes |
| Dejar la venta pendiente y descontar al confirmar | Obligaría a alguien a confirmar una compra cuyo dinero ya está en la plataforma |
| Copiar la confirmación en el nuevo servicio | Dos sitios que confirman, y el primero que cambie deja al otro atrás |
| Guardar los puntos en `payments` | Ya están en el asiento, con su saldo; una columna sería una copia |
| Ocultar `POINTS` del catálogo público | `RF-MV-009` lo ofrece a quien compra para sí mismo, que sí puede usarlo |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que extraer `SaleConfirmation` cambie `RF-MV-003` | Sus suites (`ConfirmSaleIT`, `ConfirmSaleConcurrencyIT`) pasan sin tocarlas |
| Gastar el mismo saldo dos veces | El `Ledger` bloquea la fila; `CA-MV-340` con dos hilos |
| Un pago `POINTS` pendiente que sobreviva a la migración | La guarda; `CA-MV-343` |

---

## 11. Estrategia de prueba

Integración, `PayWithPointsIT`: `CA-MV-332` a `CA-MV-342`, por el enlace, el paquete y el reintento; `CA-MV-340` con dos hilos; `CA-MV-337` leyendo la base tras el `409`. `CA-MV-343` en una prueba de la migración que siembra un pago `POINTS` pendiente antes de aplicarla, o, si no cabe en el arnés, comprobando la sentencia sobre la base de desarrollo y la guarda.
