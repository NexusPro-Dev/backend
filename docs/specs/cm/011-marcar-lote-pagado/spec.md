# SPEC — `RF-CM-011` Marcar un lote como pagado

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-011` |
| Módulo | `CM` — Comisiones |
| Versión | 0.4.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |
| Enmendada el | 30-09-2026 — **un lote sin comisiones vivas no se paga** (`RN-CM-048`) |
| Enmendada el | 05-10-2026 — el lote ya no puede tener cuatro decimales ([`ADR-006`](../../../architecture/ADR-006-importes-en-unidades-minimas.md)); §13 |
| Enmendada el | 08-10-2026 — **un pago que se hace borra después todos los pendientes vacíos** (`RN-CM-052` enmendada): `CA-CM-378` y `CA-CM-379` |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que Finanzas, tras revisar un lote cerrado, **lo dé por pagado**, y que en el mismo acto **el importe entre en la billetera** de su persona.

---

## 2. Contexto

**Es la compuerta humana del flujo** (`requirements/cm.md` v0.19.0, §5.7): el devengo y el cierre son automáticos, y **el pago no**, por decisión del responsable del proyecto — sin nadie que mire antes de mover dinero, una tasa mal puesta llegaría directa al saldo del vendedor.

**Desde el 26-09-2026 pagar es abonar** (`RN-CM-030`, `RN-MV-044`): la marca invoca la operación que `MV` publica (`RF-MV-024`) **en la misma transacción**, y el lote pasa a pagado **solo si** el abono se escribe.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo un lote pendiente** | Un lote abierto sigue creciendo y no se paga; uno pagado no se vuelve a pagar (`RN-CM-030`) |
| **Un acto o ninguno** | El abono y la marca se escriben juntos o no se escribe ninguno |
| **Sin datos de entrada** | Se paga el total del lote, entero. No hay pagos parciales |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Finanzas o administración | Marca el lote (`commission-batches:pay`) |

---

## 4. Alcance

### 4.1 Incluye

- Pasar un lote pendiente a pagado, con la fecha del pago y el movimiento que lo abonó.
- Abonar su total en la billetera de la persona, por `MV`.

### 4.2 No incluye

- **Pagos parciales**, ni pagar varios lotes de una vez.
- **Deshacer un pago** (`RN-CM-029`, `RN-CM-030`).
- **Sacar el dinero de la plataforma**: es un retiro de `MV`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-030` | Solo `PENDIENTE` → `PAGADO`; pagar es abonar, una vez, en la misma transacción |
| `RN-MV-044` | `MV` redondea a la moneda y abona desde la cuenta de comisiones; un lote de cero, sin asientos |
| `RN-CM-029` | Lo pagado no se toca |

---

## 6. Datos

### 6.1 Entrada

| Dato | Descripción |
|---|---|
| Lote | Su identificador |

### 6.2 Salida

**El lote**, como lo devuelve el detalle de `RF-CM-010`, ya pagado: con la fecha del pago y el movimiento del abono, y el **importe abonado** —redondeado a la moneda—.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El lote existe y está pendiente |
| Postcondición | El lote está pagado, con fecha y movimiento; la billetera de su persona subió en el total redondeado; queda auditado |

---

## 8. Flujo principal

1. Finanzas pide pagar un lote.
2. Se comprueba que está pendiente.
3. Se abona su total en la billetera de la persona.
4. El lote pasa a pagado, con la fecha y el movimiento del abono.
5. Se audita y se devuelve.

---

## 9. Flujos alternativos

### FA-001 — Dos pagos del mismo lote a la vez

Uno lo paga; el otro recibe conflicto. **La billetera sube una sola vez.**

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El lote no existe | No encontrado |
| `EX-002` | El lote está **abierto** | Conflicto: «El lote sigue abierto: se paga después del cierre» |
| `EX-003` | El lote ya está **pagado** | Conflicto: «El lote ya está pagado» |
| `EX-004` | El abono falla | El lote **no** cambia; error |
| `EX-005` | El lote **no tiene comisiones vivas** —se retiraron o revirtieron todas— (30-09-2026) | Conflicto: «El lote no tiene nada que pagar». No se abona nada |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Identificador bien formado |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-189` | Pagar un lote **pendiente** lo deja **pagado**, con fecha y movimiento, y la billetera de su persona sube en el total **redondeado a la moneda** |
| `CA-CM-190` | Un lote **abierto** responde conflicto y **no** se abona nada |
| `CA-CM-191` | Un lote **ya pagado** responde conflicto y **no** se abona otra vez |
| `CA-CM-192` | **Dos pagos simultáneos** del mismo lote: uno paga, el otro conflicto, y la billetera sube **una vez** |
| `CA-CM-193` | Si el abono **falla**, el lote sigue **pendiente** y no queda movimiento |
| `CA-CM-194` | Un lote de total que redondea a **cero** se paga, con su movimiento de importe cero |
| `CA-CM-195` | Un lote que no existe responde **no encontrado**; sin `commission-batches:pay`, se rechaza |
| `CA-CM-196` | Queda **auditado**, con el lote, el importe y el movimiento |
| `CA-CM-301` | Un lote pendiente **sin comisiones vivas** responde conflicto y **no** se abona nada; en cuanto se le devuelve una, se paga. **Un lote con una comisión viva de importe cero se sigue pagando** (`CA-CM-194`) (30-09-2026) |
| `CA-CM-378` | Pagar un lote **borra después todos los pendientes sin comisiones**, de cualquier persona, auditados; los abiertos vacíos y los pendientes con comisiones **no se tocan**, y lo retirado de un pendiente borrado pierde su origen (08-10-2026) |
| `CA-CM-379` | Un pago que **no se hace** —el lote está abierto, pagado o vacío, o no existe— **no borra** ningún lote |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La persona del lote está eliminada | Se paga igual: el dinero es suyo (`RF-MV-024` §2.1) |
| ~~El lote tiene total en cuatro decimales~~ | ~~Se abona redondeado; el lote conserva sus cuatro~~ **Deja de poder ocurrir el 05-10-2026**: el total se guarda en centésimas y es la suma exacta de comisiones ya redondeadas al guardarse (`RF-CM-013`, `CA-CM-337`), de modo que el abono es el total tal cual. El redondeo de `RN-MV-044` se queda, sin efecto |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 28-09-2026 | Primera versión ([`requirements/cm.md`](../../../requirements/cm.md) v0.20.0). Solo se paga un lote pendiente, y pagar es abonar por `RF-MV-024` en la misma transacción. Criterios `CA-CM-189` a `CA-CM-196`. | Responsable del proyecto |
| 0.2.0 | 30-09-2026 | **Un lote sin comisiones vivas no se paga** (`RN-CM-048`, [`requirements/cm.md`](../../../requirements/cm.md) v0.26.0 §5.10): desde que se pueden retirar y revertir comisiones, un pendiente puede quedarse vacío, y abonarlo dejaría un `PAGO_COMISION` que no paga nada. **Lo que cuenta son las comisiones vivas, no el total**: `CA-CM-194` sigue en pie. `EX-005`, `CA-CM-301`. | Responsable del proyecto |
| 0.3.0 | 05-10-2026 | **El lote ya no puede tener total en cuatro decimales** ([`ADR-006`](../../../architecture/ADR-006-importes-en-unidades-minimas.md), [`requirements/cm.md`](../../../requirements/cm.md) v0.31.0): las comisiones se guardan en centésimas, redondeadas al guardarse, y el lote suma enteros. El caso límite de §13 se tacha, sin borrarlo, porque explica por qué `RN-MV-044` redondea. Ningún criterio cambia. | Responsable del proyecto |
| 0.4.0 | 08-10-2026 | **Un pago que se hace borra después todos los pendientes vacíos** ([`requirements/cm.md`](../../../requirements/cm.md) v0.42.0, `RN-CM-052` enmendada, «Quinta enmienda»), por decisión del responsable del proyecto: de todas las personas, en una transacción aparte; si ese borrado falla, el pago queda hecho. Nacen `CA-CM-378` y `CA-CM-379`. | Responsable del proyecto |
