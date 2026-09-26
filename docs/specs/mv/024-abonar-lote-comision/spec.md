# SPEC — `RF-MV-024` Abonar el pago de un lote de comisión

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-024` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que cuando `CM` da por pagado un lote de comisión, **el dinero aparezca en la billetera de la persona**, una sola vez, y que `CM` sepa con qué movimiento se abonó.

---

## 2. Contexto

**Hasta el 26-09-2026 pagar un lote era solo una marca** —«Finanzas pagó»— que no movía dinero (`RN-CM-030` en su primera redacción). **El responsable del proyecto decidió que pagar un lote es abonarlo** ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0 §4.3, `RN-MV-044`; [`requirements/cm.md`](../../../requirements/cm.md) v0.18.0, `RN-CM-030` enmendada): el importe entra en la billetera, y sale de la plataforma cuando la persona lo retira.

**Este requerimiento no tiene ruta ni actor humano.** Es la operación que `MV` **publica** para que `CM` la invoque desde `RF-CM-011` —marcar un lote como pagado—, **en la misma transacción**. Es la forma de **D-26**, la misma que `SP` usa para conceder el nivel comprado: quien sabe **cómo** se abona es `MV`; quien decide **si** se paga es `CM`.

**Y `MV` no sabe qué es un lote.** Recibe lo mínimo —a quién, cuánto, en qué moneda, y una identidad para no abonar dos veces— y responde qué movimiento quedó. La referencia del lote al movimiento la guarda `CM`, para que `MV` siga sin depender de `CM`.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Una vez por lote** | La misma orden dos veces **devuelve el mismo abono**. Lo sostiene el esquema, con la identidad del lote como clave |
| **Redondeo a la moneda** | El lote suma con cuatro decimales y el libro guarda dinero con los de la moneda: se redondea **a la mitad hacia arriba** |
| **Un lote de cero se abona igual** | Con un movimiento de importe cero **y sin asientos**, para que el lote pagado tenga su movimiento (`RN-MV-044`) |
| **Falla entero** | Si algo falla, no queda abono y `CM` no marca el lote |
| **La persona no se valida aquí** | El lote ya se liquidó a nombre de alguien que existía; si hoy está eliminado, el dinero sigue siendo suyo y la decisión de pagarlo es de `CM` |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| `CM`, desde `RF-CM-011` | Invoca la operación al marcar un lote como pagado. El permiso lo exige `RF-CM-011` (`commission-batches:pay`) |

---

## 4. Alcance

### 4.1 Incluye

- Registrar un movimiento de **pago de comisión**, confirmado, a nombre de la persona, con su comprobante y un concepto que cite el lote.
- Abonarlo en su billetera desde la cuenta de comisiones de la empresa.
- Reconocer la misma orden repetida.

### 4.2 No incluye

- **Marcar el lote** como pagado: es `RF-CM-011`.
- **Liquidar**: es `CM`.
- **Deshacer un abono.** `RN-CM-029` —lo liquidado no se revierte— lo hace innecesario hoy.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-044` | Pagar un lote es abonarlo, una vez, redondeado; un lote de cero, sin asientos |
| `RN-MV-042` | Dos asientos de `ABONO` que suman cero |
| `RN-MV-046` | Sin líneas, sin vendedor, sin paquete |
| `RN-MV-026` | El sujeto es la persona del lote |
| `RN-CM-030` | Del lado de `CM`: el lote pasa a pagado solo si el abono se escribe |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Descripción |
|---|---|
| Persona | A quién se le debe |
| Moneda | La del lote |
| Importe | El total del lote, con sus cuatro decimales |
| Identidad del lote | Para no abonar dos veces y para citarlo en el concepto |
| Código del lote | Para el concepto, que es lo que la persona lee en su historial |

### 6.2 Salida

**El movimiento que quedó**: su identificador, su comprobante y el importe **ya redondeado**.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | Se invoca dentro de una transacción; el importe no es negativo; la moneda existe |
| Postcondición | Existe un pago de comisión confirmado a nombre de la persona; su billetera subió en el importe redondeado y la cuenta de comisiones de la empresa bajó en lo mismo —salvo si es cero—; está auditado |

---

## 8. Flujo principal

1. `CM` indica persona, moneda, importe, identidad y código del lote.
2. `MV` redondea el importe a la moneda.
3. Si ese lote **ya se abonó**, devuelve ese movimiento y termina (`FA-001`).
4. Registra el pago de comisión confirmado, con el concepto «Comisión del lote `<código>`».
5. Si el importe no es cero, abona la billetera desde la cuenta de comisiones.
6. Audita y devuelve el movimiento.

---

## 9. Flujos alternativos

### FA-001 — La misma orden llega dos veces

Un reintento de `CM` **después** de la primera: recibe el mismo movimiento y no se abona nada más. **Si llegan a la vez**, la segunda **falla entera** y su transacción se revierte, porque esta operación corre dentro de la de `CM` y no puede abrir otra para releer (`plan.md` §1). Del lado de `CM`, marcar un lote ya pagado responde conflicto antes de llegar aquí; esto es la segunda defensa.

### FA-002 — El importe redondea a cero

Se registra el movimiento de importe cero, sin asientos. La billetera no cambia.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El importe es negativo, o falta algún dato | Error de programación: `CM` no debe pedirlo. La transacción se revierte entera |
| `EX-002` | La moneda no existe | Lo mismo |

**No hay excepciones de negocio**: quien decide si se paga es `CM`, y lo que llega aquí es una orden ya decidida.

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Todos los datos presentes; importe no negativo |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-269` | Una orden abona la billetera de la persona en el importe **redondeado a la moneda, a la mitad hacia arriba**, y baja en lo mismo la cuenta de comisiones de la empresa; el movimiento es un pago de comisión **confirmado**, con comprobante propio y el concepto que cita el lote |
| `CA-MV-270` | La **misma orden dos veces seguidas** devuelve **el mismo movimiento**; **dos a la vez** abonan **una sola vez** —la segunda falla y su transacción se revierte—. En los dos casos la billetera sube una sola vez |
| `CA-MV-271` | Un importe que **redondea a cero** produce un movimiento de importe cero **sin asientos** |
| `CA-MV-272` | Si la transacción de `CM` se revierte **después** de invocar la operación, **no queda ni movimiento ni asiento** |
| `CA-MV-273` | El abono aparece en el **historial de saldos** de la persona y puede **retirarse** |
| `CA-MV-274` | Queda **auditado** |

**`CA-MV-272` es el que sostiene la forma de D-26**: el abono y la marca del lote son un solo acto, o ninguno.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La persona no tiene billetera en esa moneda | Se crea |
| La persona está eliminada | Se abona igual (§2.1) |
| Un importe con exactamente dos decimales | Sin cambio |
| `0.005` en una moneda de dos decimales | Redondea a `0.01` |

---

## 14. Preguntas abiertas

Ninguna propia. **`RF-CM-011` no tiene tripleta todavía**: cuando se escriba, invocará esta operación y guardará el movimiento en el lote.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 26-09-2026 | Primera versión, con la etapa 6 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.46.0) y `RN-CM-030` enmendada ([`requirements/cm.md`](../../../requirements/cm.md) v0.18.0). **Una operación publicada, sin ruta**, que `CM` invoca en su transacción: abona **una vez por lote**, **redondeado a la moneda**, y un lote de cero sin asientos. Criterios `CA-MV-269` a `CA-MV-274`. | Responsable del proyecto |
