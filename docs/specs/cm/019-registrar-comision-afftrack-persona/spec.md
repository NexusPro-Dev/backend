# SPEC — `RF-CM-019` Registrar la comisión afftrack de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-019` |
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

Declarar que **una persona concreta tiene su propia escala** de FTD sobre un producto —«al reunir 40, 9.000 por cada uno»—, **desde una fecha y hasta otra**, en lugar de la de su rol.

---

## 2. Contexto

Es a los escalones de rol ([`RF-CM-015`](../015-registrar-comision-afftrack-rol/spec.md)) lo que la tasa personalizada ([`RF-CM-006`](../006-registrar-tasa-personalizada/spec.md)) es a la tasa de rol: **una excepción negociada con una persona**, sin rol, con vigencia. **Y como `RF-CM-006`, reúne sus cuatro operaciones** —registrar, consultar, corregir y retirar— bajo un solo número, porque se comportan distinto de las de rol por la misma razón: **la vigencia**.

**La escala de una persona sustituye entera la de su rol** (`RN-CM-039`), por decisión del responsable del proyecto. **Basta un escalón vigente** sobre ese producto el día del cierre para que la persona cobre **solo** por los suyos: sus escalones y los de su rol **no se mezclan**. Quien registra uno tiene que saberlo — un único escalón personal de 40 deja a la persona sin el de 60 de su rol.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Un escalón vigente por persona, producto y límite, cada día** | Varios límites vigentes a la vez son **la escala**; el mismo límite dos veces el mismo día haría indeterminado qué se paga |
| **Se corrigen el límite, el valor y el fin de vigencia** | La persona, el producto y el inicio no: son lo que el escalón es, como en `RF-CM-006` |
| **El listado dice qué escala rige en una fecha** | Filtrando por persona, producto y fecha se ve **la escala que el cierre aplicaría ese día** |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administración | Registra, consulta, corrige y retira (`user-afftrack-rates:create`, `read`, `update`, `delete`) |

---

## 4. Alcance

### 4.1 Incluye

- **Registrar** un escalón de una persona sobre un producto FTD, con límite, valor y vigencia.
- **Consultar** los escalones de persona, filtrables por persona, producto y **vigentes en una fecha**, con las retiradas si se piden.
- **Corregir** el límite, el valor o el fin de vigencia.
- **Retirar** con motivo.
- Dejar constancia de todo en la auditoría.

### 4.2 No incluye

- Cambiar la persona, el producto o el inicio de vigencia.
- **Exigir que la persona sea vendedora**: como `RF-CM-006` `FA-004`, se admite y rige.
- **Liquidar**: `RF-CM-020`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-002`, `RN-CM-010` | El producto existe y no está retirado |
| `RN-CM-036`, `RN-CM-037` | El producto es FTD, y no se corrige |
| `RN-CM-038` | Límite entero mayor que cero; valor cero o más, en la moneda del producto |
| `RN-CM-009` | Inicio obligatorio; fin opcional y no anterior al inicio |
| `RN-CM-039` | Un escalón vigente por persona, producto y límite cada día; la escala de la persona sustituye la del rol |
| `RN-CM-005` | Retiro lógico con motivo |

---

## 6. Datos

### 6.1 Entrada

**Registrar**

| Dato | Obligatorio | Restricción |
|---|---|---|
| Persona | Sí | Existe |
| Producto | Sí | Existe, no retirado, FTD |
| Límite | Sí | Entero mayor que cero |
| Valor por FTD | Sí | Cero o más, en los decimales de la moneda del producto |
| Desde | Sí | Fecha |
| Hasta | No | Nulo = indefinidamente; no anterior a «desde» |

**Consultar:** persona, producto, **vigentes en** —una fecha—, incluir retiradas, página. Todos opcionales.

**Corregir:** límite, valor, hasta. **Al menos uno**; «hasta» enviado vacío **quita el fin** (`RF-CM-006` `FA-003`).

**Retirar:** motivo obligatorio.

### 6.2 Salida

Por escalón: identificador; persona —identificador, nombre de usuario y nombre—; producto —identificador, código, nombre y moneda—; límite; valor por FTD; cuánto paga al alcanzarse; desde; hasta; y, en los retirados, desde cuándo lo están. Retirar responde sin cuerpo.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor porta el permiso de la operación |
| Postcondición | Registrar: el escalón rige en los cierres cuyo día cae en su vigencia, y **desde entonces la escala del rol deja de aplicarse a esa persona sobre ese producto** |
| Postcondición | Retirar: libera sus días, y si era el último vigente, **vuelve a aplicarse la escala del rol** |

---

## 8. Flujo principal

**Registrar:** se validan los datos; se comprueba la persona, el producto —existe, vivo, FTD— y el valor contra la moneda; se comprueba que ningún día choque con otro escalón vivo de la misma persona, producto y límite; se registra y se audita.

**Consultar:** se devuelven por persona, producto, límite ascendente y «desde» descendente.

**Corregir:** se comprueba que existe y está vivo; se aplica lo que llegó comprobando el choque de vigencias con el límite y el fin resultantes; se audita si cambió algo.

**Retirar:** se comprueba que existe y está vivo; se retira con el motivo.

---

## 9. Flujos alternativos

### FA-001 — Cambiar la escala de alguien a partir de una fecha

Como `RF-CM-006` `FA-002`: se cierra la vigencia de los escalones actuales y se registran los nuevos desde el día siguiente.

### FA-002 — La persona no tiene escala propia en el día del cierre

Se le aplica la de su rol (`RN-CM-039`). Una escala personal **vencida** no la sustituye.

### FA-003 — Escala personal y escala de rol con límites distintos

Solo cuenta la personal, aunque la del rol tenga un límite que la persona alcanzaría y la suya no. Es lo que `RN-CM-039` decide, y §2 lo advierte.

---

## 10. Excepciones

| ID | Condición | Respuesta |
|---|---|---|
| `EX-001` | La persona no existe | `422` |
| `EX-002` | Otro escalón vivo de la misma persona, producto y límite cubre algún día de este | `409`, también cuando lo causa una petición simultánea |
| `EX-003` | El producto no existe | `422` |
| `EX-004` | El producto está retirado | `422` |
| `EX-005` | El producto no es FTD | `422` |
| `EX-006` | Corregir o retirar un escalón que no existe | `404`; en corregir, **uno retirado se trata como inexistente** |
| `EX-007` | Retirar uno ya retirado | `409` |
| `EX-008` | Se intenta corregir la persona, el producto o el inicio | `400`, **se rechaza y no se ignora** |

---

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-001` | Persona, producto, límite, valor y «desde» obligatorios al registrar | El campo es obligatorio. |
| `VAL-002` | Límite entero mayor que cero | El límite debe ser un número entero mayor que cero. |
| `VAL-003` | Valor no negativo; no se vacía al corregir | El valor por FTD no puede ser negativo ni vaciarse. |
| `VAL-004` | «Hasta» no anterior a «desde» | El fin de vigencia no puede ser anterior a su inicio. |
| `VAL-005` | Decimales del valor según la moneda del producto | El valor por FTD no admite más decimales que los de la moneda del producto. |
| `VAL-006` | Corregir sin nada corregible | Debe enviarse al menos un campo corregible. |
| `VAL-007` | Motivo del retiro obligatorio y dentro de la longitud admitida | El motivo del retiro es obligatorio. |
| `VAL-008` | Filtros de la consulta bien formados | |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-231` | Se registra un escalón de una persona sobre un producto FTD con su vigencia; la respuesta trae persona y producto resueltos y cuánto paga al alcanzarse |
| `CA-CM-232` | Se admiten varios límites vigentes a la vez para la misma persona y producto, y el mismo límite en **vigencias consecutivas** |
| `CA-CM-233` | Se rechaza con `409` el mismo límite con un día en común, también con **dos altas simultáneas**; se admite sobre otro producto o tras retirar el primero |
| `CA-CM-234` | Se rechaza un producto que no es FTD, uno inexistente y uno retirado, distinguidos; y una persona inexistente |
| `CA-CM-235` | Se rechazan todos juntos los obligatorios, un límite no positivo, un valor negativo y un fin anterior al inicio; se rechaza un valor con decimales de más |
| `CA-CM-236` | El listado filtra por persona, producto y **vigentes en una fecha**, y en ese caso devuelve exactamente los escalones que el cierre de ese día aplicaría a esa persona |
| `CA-CM-237` | Se corrigen el límite, el valor y el fin —y enviar el fin vacío lo quita—; corregir el límite o el fin a una vigencia que choca responde `409` |
| `CA-CM-238` | Corregir la persona, el producto o el inicio se rechaza; corregir uno retirado responde `404`; sin cambios, no se escribe ni audita |
| `CA-CM-239` | Se retira con motivo; retirar dos veces responde `409`; retirado, sus días quedan libres |
| `CA-CM-240` | Las cuatro operaciones exigen su permiso y quedan auditadas; el listado no hace una consulta por fila |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un escalón personal de cero | Se admite: sustituye la escala del rol y **no paga**, pero consume los FTD al alcanzarse. Es la forma de dejar a alguien sin comisión afftrack sobre un producto |
| Un escalón personal con «desde» futuro | No rige hasta que el día del cierre caiga en su vigencia; hasta entonces, la del rol |
| La persona deja de vender | El escalón sigue y rige, como en `RF-CM-006` §13 |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 29-09-2026 | Primera versión, con la comisión afftrack ([`requirements/cm.md`](../../../requirements/cm.md) v0.22.0 §5.8). Criterios `CA-CM-231` a `CA-CM-240`. | Responsable del proyecto |
