# SPEC — `RF-CM-015` Registrar una comisión afftrack de rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-015` |
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

Declarar **cuánto cobra un rol vendedor al reunir un número de FTD** de un producto: un **escalón** —«al reunir 50 FTD, 8.000 por cada uno»— que cada cierre comparará con los FTD de cada persona de ese rol.

---

## 2. Contexto

La comisión afftrack nace el 29-09-2026 por decisión del responsable del proyecto ([`requirements/cm.md`](../../../requirements/cm.md) v0.22.0, §5.8). **No paga una línea: paga un número de líneas reunidas en un periodo**, y por eso no es una tasa más sino una pieza propia.

**Un FTD es una línea de venta de la membresía gratuita a la gratuita** —`BECA → BECA`— **ya activada** (`RN-CM-036`). El producto de un escalón **tiene que ser de esa clase**: un escalón sobre un producto que no es FTD no contaría nunca nada.

**Una escala son varios escalones.** Un producto puede tener, para el mismo rol, uno de 50, otro de 60 y otro de 100; cada cierre pagará **el mayor que la persona alcance**, una sola vez, y guardará lo que sobre (`RN-CM-041`). Esta operación registra **uno** de ellos.

**Hereda la forma de `RF-CM-001`** —la tasa de rol—: nace con su producto, que no se corrige; el rol tiene que ser vendedor; y el importe no lleva moneda, porque es la de su producto. **Lo que no hereda** es la forma del valor: un escalón **siempre** paga un importe por FTD, y no hay porcentaje ni tope, porque el producto FTD vale cero y no hay precio contra el que medir (`RN-CM-038`).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **El producto tiene que ser FTD** | Se rechaza cualquier otro, y se distingue de uno que no existe o está retirado |
| **Un valor por límite** | Dos escalones vivos del mismo rol, producto y límite se rechazan: harían indeterminado cuál se paga |
| **La respuesta dice cuánto paga el escalón entero** | `límite × valor`, para que quien lo registra vea lo que cuesta alcanzarlo sin hacer la cuenta |
| **Registrar es poner en vigor** | Como la tasa de rol (`cm.md` §5.4): el escalón rige desde el siguiente cierre, sin paso de asociación |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administración | Registra el escalón (`afftrack-rates:create`) |

---

## 4. Alcance

### 4.1 Incluye

- Registrar un escalón **de un rol vendedor sobre un producto FTD**, con su **límite** y su **valor por FTD**.
- Verificar el rol, el producto y que el producto sea FTD.
- Verificar que el valor cabe en los decimales de la moneda del producto.
- Dejar constancia del alta en la auditoría de cambios.

### 4.2 No incluye

- **El escalón de una persona**: es `RF-CM-019`.
- **Liquidarlo**: lo hace el cierre (`RF-CM-020`).
- **Corregir o retirar**: `RF-CM-017` y `RF-CM-018`.
- **Registrar varios escalones de una vez.** Cada escalón es una fila con su identidad; una escala de tres escalones son tres altas.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-001` | El rol es de tipo vendedor |
| `RN-CM-002`, `RN-CM-010` | El producto existe y no está retirado |
| `RN-CM-036`, `RN-CM-037` | El producto es FTD, y no se corrige después |
| `RN-CM-038` | Límite entero mayor que cero; valor mayor o igual que cero, en los decimales de la moneda del producto |
| `RN-CM-039` | Un solo escalón vivo por producto, rol y límite |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Rol | Sí | Qué rol cobra | Existe y es vendedor (`RN-CM-001`) |
| Producto | Sí | Qué producto FTD cuenta | Existe, no está retirado y **es FTD** (`RN-CM-037`) |
| Límite | Sí | Cuántos FTD hay que reunir | Entero **mayor que cero** |
| Valor por FTD | Sí | Cuánto se paga por cada FTD del límite | **Cero o más**, en los decimales de la moneda del producto |

**El cero en el valor se admite**, como en las tasas (`RN-CM-007`): es la forma de declarar que un límite no paga, y **no es lo mismo que no tener escalón** — un escalón de cero **consume** los FTD al alcanzarse y deja el remanente en lo que sobra.

### 6.2 Salida

El escalón registrado: su identificador; el **rol** —identificador, código y nombre—; el **producto** —identificador, código, nombre y moneda—; el **límite**; el **valor por FTD**; y **cuánto paga al alcanzarse**, `límite × valor`.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor porta el permiso |
| Postcondición | El escalón existe y **rige desde el siguiente cierre** |
| Postcondición | La auditoría de cambios tiene su alta con el estado completo |

---

## 8. Flujo principal

1. Se envían el rol, el producto, el límite y el valor.
2. Se validan la forma de los datos.
3. Se comprueba el rol: que exista y sea vendedor.
4. Se comprueba el producto: que exista, no esté retirado **y sea FTD**.
5. Se comprueba que el valor cabe en los decimales de la moneda del producto.
6. Se comprueba que no haya ya un escalón vivo del mismo rol, producto y límite.
7. Se registra, se audita y se devuelve.

---

## 9. Flujos alternativos

### FA-001 — Otro escalón del mismo rol y producto, con otro límite

Se registra con normalidad: **es la escala**. No hay ninguna regla que ordene los valores —un escalón mayor puede pagar menos por FTD que uno menor— y el sistema no la inventa: cada cierre paga **el mayor límite alcanzado**, valga lo que valga (§13).

### FA-002 — El mismo límite, retirado el anterior

Se registra: la unicidad es **entre los vivos**.

---

## 10. Excepciones

| ID | Condición | Respuesta |
|---|---|---|
| `EX-001` | El rol no es vendedor | `400`: solo los roles de tipo vendedor pueden llevar comisión |
| `EX-002` | El rol no existe | `422` |
| `EX-003` | El producto no existe | `422` |
| `EX-004` | El producto está retirado | `422`: no se configuran comisiones sobre un producto retirado |
| `EX-005` | El producto no es FTD | `422`: las comisiones afftrack solo se declaran sobre la membresía `BECA → BECA` |
| `EX-006` | Ya hay un escalón vivo del mismo rol, producto y límite | `409` |

**`EX-001` a `EX-004` responden como en `RF-CM-001`**, con los mismos códigos, para que quien ya conoce el alta de tasas no aprenda otra. **`EX-005` es la nueva**, y se distingue de `EX-003` y `EX-004` por lo mismo que ellas se distinguen entre sí: quien envía un producto que existe, vivo, y no FTD, se ha equivocado de producto, no de catálogo.

---

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-001` | Rol obligatorio | El rol es obligatorio. |
| `VAL-002` | Producto obligatorio | El producto es obligatorio. |
| `VAL-003` | Límite obligatorio, entero y mayor que cero | El límite debe ser un número entero mayor que cero. |
| `VAL-004` | Valor obligatorio y no negativo | El valor por FTD es obligatorio y no puede ser negativo. |
| `VAL-005` | Decimales del valor según la moneda del producto | El valor por FTD no admite más decimales que los de la moneda del producto. |

`VAL-001` a `VAL-004` se devuelven **todos juntos**; `VAL-005` necesita el producto y se comprueba después de él.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-209` | Se registra un escalón de un rol vendedor sobre un producto FTD, y la respuesta trae el rol y el producto resueltos, el límite, el valor y **cuánto paga al alcanzarse** |
| `CA-CM-210` | Se admiten **varios escalones del mismo rol y producto con límites distintos** |
| `CA-CM-211` | Se rechaza el segundo escalón vivo del mismo rol, producto y límite con `409`; se admite cuando el primero está retirado, y **dos altas simultáneas** dejan uno y un `409` |
| `CA-CM-212` | Se rechaza un producto **que no es FTD** —un upgrade de otra pareja, y un bot— con `EX-005`, y se distingue del inexistente y del retirado |
| `CA-CM-213` | Se rechaza un rol inexistente (`422`) y uno que no es vendedor (`400`), distinguidos |
| `CA-CM-214` | Se rechazan un límite cero, negativo o decimal y un valor negativo, **todos juntos** con los campos ausentes; se admite un valor **cero** |
| `CA-CM-215` | Se rechaza un valor con más decimales de los de la moneda del producto, y se admite el mismo con los correctos |
| `CA-CM-216` | El alta queda en la auditoría de cambios; sin `afftrack-rates:create`, se rechaza |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un escalón mayor paga menos por FTD que uno menor (50 × 9.000 y 60 × 7.000) | Se admite. El cierre paga **el mayor límite alcanzado**, aunque pague menos en total. No hay regla que lo prohíba, y `cm.md` no la pidió |
| El producto deja de ser FTD después —cambia el origen o el destino— | No puede: `RN-PM-001` y el alta de productos no corrigen origen ni destino |
| Se registra una tasa **por venta** sobre el mismo producto FTD | La rechaza `RF-CM-001` (`RN-CM-037`), no esta operación |
| El rol deja de ser vendedor después | El escalón permanece; `RN-CM-001` se comprueba al registrar, como en las tasas |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 29-09-2026 | Primera versión, con la comisión afftrack ([`requirements/cm.md`](../../../requirements/cm.md) v0.22.0 §5.8). Criterios `CA-CM-209` a `CA-CM-216`. | Responsable del proyecto |
