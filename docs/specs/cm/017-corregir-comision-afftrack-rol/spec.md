# SPEC — `RF-CM-017` Corregir el límite o el valor de una comisión afftrack de rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-017` |
| Módulo | `CM` — Comisiones |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 29-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Cambiar **el límite o el valor por FTD** de un escalón de rol, sin retirarlo y registrarlo de nuevo.

---

## 2. Contexto

Hereda la forma de [`RF-CM-003`](../003-corregir-valor-tasa/spec.md): una corrección **parcial**, que rechaza —y no ignora— lo que no se puede corregir, que trata un escalón retirado como inexistente y que **no toca lo ya pagado**, porque cada comisión generada copia lo que aplicó (`RN-CM-008`).

**Sin vigencia, corregir reescribe lo que regirá en el próximo cierre**, igual que en la tasa de rol: los cierres pasados ya guardaron su valor y su límite en su liquidación y en su comisión (`cm.md` §7.11).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **El límite se puede corregir** | Es la mitad del escalón y se negocia igual que el valor; retirar y registrar para cambiarlo perdería la identidad del escalón en lo que ya pagó |
| **El rol y el producto, no** | Son lo que el escalón **es** (`RN-CM-037`) |
| **Cambiar el límite a uno que ya existe** | Se rechaza con `409`, como el alta (`RN-CM-039`) |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administración | Corrige (`afftrack-rates:update`) |

---

## 4. Alcance

### 4.1 Incluye

- Corregir el **límite**, el **valor por FTD** o los dos.

### 4.2 No incluye

- Cambiar el rol o el producto: se retira y se registra otro.
- **Recalcular cierres pasados**: lo liquidado no se recalcula (`RN-CM-029`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-037` | Rol y producto no se corrigen |
| `RN-CM-038` | El límite y el valor nuevos cumplen la misma forma que en el alta |
| `RN-CM-039` | El límite nuevo no puede chocar con otro escalón vivo del mismo rol y producto |
| `RN-CM-008`, `RN-CM-029` | Lo ya pagado no cambia |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Escalón | Sí | Cuál se corrige |
| Límite | No | Entero mayor que cero |
| Valor por FTD | No | Cero o más, en los decimales de la moneda del producto |

**Al menos uno de los dos.**

### 6.2 Salida

El escalón corregido, en la forma del alta (`RF-CM-015` §6.2).

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor porta el permiso; el escalón existe y está vivo |
| Postcondición | El siguiente cierre usa el límite y el valor nuevos |
| Postcondición | La auditoría de cambios tiene el antes y el después, **solo si algo cambió** |

---

## 8. Flujo principal

1. Se envían el escalón y lo que se corrige.
2. Se valida la forma de los datos.
3. Se comprueba que el escalón existe y está vivo.
4. Se comprueba el valor contra la moneda del producto, y que el límite nuevo no choque.
5. Se aplica, se audita y se devuelve.

---

## 9. Flujos alternativos

### FA-001 — Nada cambia

Se envían el mismo límite y el mismo valor —`8000` frente a `8000.0000` es el mismo valor—. Se responde con el escalón **sin escribir ni auditar**, como `RF-CM-003` `FA-002`.

---

## 10. Excepciones

| ID | Condición | Respuesta |
|---|---|---|
| `EX-001` | El escalón no existe o está retirado | `404`. Un retirado se trata como inexistente |
| `EX-002` | Se envía el rol o el producto | `400`: no se pueden corregir. **Se rechaza y no se ignora** |
| `EX-003` | No se envía nada corregible | `400` |
| `EX-004` | El límite nuevo ya lo tiene otro escalón vivo del mismo rol y producto | `409` |

---

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-001` | Límite entero y mayor que cero, si llega | El límite debe ser un número entero mayor que cero. |
| `VAL-002` | Valor no negativo, si llega; ninguno de los dos vacío explícito | El valor por FTD no puede ser negativo ni vaciarse. |
| `VAL-003` | Decimales del valor según la moneda del producto | El valor por FTD no admite más decimales que los de la moneda del producto. |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-221` | Se corrige el valor, el límite, o los dos, y la respuesta trae **cuánto paga al alcanzarse** recalculado |
| `CA-CM-222` | Corregir **no cambia** una comisión afftrack ya pagada con ese escalón, ni su liquidación |
| `CA-CM-223` | Un escalón retirado o inexistente responde `404` |
| `CA-CM-224` | Se rechaza enviar el rol o el producto, y una petición sin nada corregible |
| `CA-CM-225` | Se rechaza un límite que ya tiene otro escalón vivo del mismo rol y producto con `409`, y se admite si el otro está retirado |
| `CA-CM-226` | Una corrección que no cambia nada no escribe ni audita; una que cambia deja el antes y el después |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Se corrige entre un cierre y el siguiente | El siguiente cierre usa lo nuevo, también para los FTD que se activaron antes de la corrección: la escala se resuelve **el día del cierre** (`RN-CM-039`) |
| Dos correcciones simultáneas al mismo límite en dos escalones | Una entra y la otra choca con el índice: `409` |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 29-09-2026 | Primera versión, con la comisión afftrack ([`requirements/cm.md`](../../../requirements/cm.md) v0.22.0 §5.8). Criterios `CA-CM-221` a `CA-CM-226`. | Responsable del proyecto |
