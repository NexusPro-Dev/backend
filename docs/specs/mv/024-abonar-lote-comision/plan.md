# PLAN — `RF-MV-024` Abonar el pago de un lote de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-024` |
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

**Una interfaz de aplicación de escritura, como `MembershipGrant`** ([`architecture.md` §15.2](../../../architecture.md)): `CommissionPayout`, en `modules/movements/application`, con una sola operación que **recibe una orden** y **responde lo que quedó**, sin entidades ni agregados. La implementa un servicio de `domain/service` que reutiliza `Movement` y el `Ledger` de `RF-MV-019`.

**La idempotencia la da el esquema**: el movimiento lleva `idempotency_key = 'lote-' || batchId`, y `uq_movements_idempotency_key` (`RF-MV-023`) rechaza el segundo. **Aquí no se relee en una transacción nueva**, al revés que en `RF-MV-018`: la operación corre **dentro** de la transacción de `CM`, y abrir otra rompería justo lo que `CA-MV-272` exige. Por eso **se consulta la clave antes de insertar**, con `SELECT … FOR UPDATE` sobre el movimiento si existe; y si dos llamadas simultáneas pasan las dos la consulta, la segunda choca con el índice y **falla entera** —`CM` recibe un error y el lote no se marca dos veces—. El caso simultáneo lo evita antes la transición de `CM` (`PENDIENTE` → `PAGADO` condicionada), y esto es la segunda línea.

---

## 2. Cambios de esquema

**Ninguno propio**: el tipo `PAGO_COMISION` lo siembra `RF-MV-019` y `movements.idempotency_key` `RF-MV-023`. **Sin permiso**: no hay ruta.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `CommissionPayout` | Nuevo — **interfaz publicada** | `PayoutResult pay(PayoutOrder)`; `PayoutOrder(userId, currencyId, amount, batchId, batchCode)` y `PayoutResult(movementId, code, amount)`, records |
| `domain/models` | `Movement` | Gana `pagoDeComision(...)` | Sin líneas, `REGISTRADO`, `CONFIRMADA` |
| `domain/service` | `CommissionPayoutService` | Nuevo, implementa `CommissionPayout` | Redondeo con `RoundingMode.HALF_UP` a la escala de la moneda; consulta de la clave; `INSERT`; `Ledger` si no es cero; auditoría. **`@Transactional(propagation = MANDATORY)`**: sin transacción de quien llama, falla |

**`MANDATORY` y no `REQUIRED`** porque la operación **no tiene sentido fuera** de la transacción de `CM`: abonar sin marcar el lote es el estado que `RN-CM-030` prohíbe, y `MANDATORY` lo convierte en un error de programación visible en lugar de un abono huérfano.

---

## 4. Contrato de API

**Ninguno.** No hay ruta HTTP. El contrato es la interfaz Java de §3, y se documenta en su Javadoc como `MembershipGrant`.

---

## 5. Autorización

**Ninguna propia**: la exige `RF-CM-011` (`commission-batches:pay`) antes de invocar. ArchUnit ya impide que otro módulo llegue a los servicios de `MV` salvo por su `application`.

---

## 6. Auditoría

Un `ChangeEvent` sobre `movements`, `INSERT`, con la persona, el importe redondeado, la moneda, el lote citado y el evento `ABONO`.

---

## 7. Transaccionalidad

**La de `CM`**, por `MANDATORY` (§3). Todo lo que escribe esta operación se revierte si `CM` se revierte (`CA-MV-272`).

---

## 8. Impacto sobre otros módulos

**`CM`** la invocará desde `RF-CM-011` y guardará `movementId` en `commission_batches.movement_id` (`requirements/cm.md` v0.18.0). **No hay dependencia de `MV` hacia `CM`**: `MV` no lee ni referencia nada de `CM`.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Que `MV` lea los lotes y los abone | `MV` dependería de `CM` y cerraría el ciclo |
| Un evento que `MV` escuche | Abonar dejaría de ser inmediato, y un lote pagado sin abono es la avería que nadie reporta (el argumento de **D-26**) |
| `REQUIRES_NEW` para la relectura | Rompería la atomicidad con `CM` |
| No redondear | El libro guarda dinero con los decimales de la moneda; un importe de cuatro no cabe |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Abonar dos veces el mismo lote | La clave única; la transición de `CM` delante |
| Un abono sin lote marcado | `MANDATORY`; `CA-MV-272` |

---

## 11. Estrategia de prueba

Integración, `CommissionPayoutIT`, **invocando la interfaz** dentro de una transacción de prueba: `CA-MV-269` a `CA-MV-274`. `CA-MV-272` abre la transacción, invoca, la revierte y comprueba que no quedó nada; y una prueba sin transacción comprueba que `MANDATORY` falla.
