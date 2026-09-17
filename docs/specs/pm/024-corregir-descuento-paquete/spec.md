# SPEC — `RF-PM-024` Corregir el descuento de un producto del paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-024` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Cambiar la rebaja de un producto que ya está en el paquete **sin sacarlo y volverlo a meter**.

## 2. Contexto

`RN-PM-038` fija una fila por pareja, y eso hace necesaria esta operación: sin ella, corregir un descuento sería desasociar y asociar, dos filas de auditoría de eliminación y creación donde hubo una corrección. Las cotas son **las mismas del alta** (`RN-PM-037`), contra el precio **de hoy** del producto, y el cambio se audita como cualquier corrección (`RN-PM-042`).

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Corrige el descuento |

## 4. Alcance

### 4.1 Incluye

- Corregir la **forma**, el **valor**, o los dos, del descuento de un producto que está en un paquete vivo.
- Devolver el paquete entero con su cuenta rehecha.

### 4.2 No incluye

- **Cambiar el producto** de la fila: es otra fila.
- **Corregir varios** en una petición.
- **Corregir sobre un paquete retirado**: `404`, como toda escritura sobre lo retirado.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-037` | El descuento no deja al producto por debajo de cero; contra el precio de hoy | `requirements/pm.md` §5.1 |
| `RN-PM-042` | La corrección se audita con antes y después | `requirements/pm.md` §5.1 |
| `RN-PM-036` | La respuesta trae la cuenta rehecha | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador del paquete y del producto | Sí | Qué fila | Ruta. Paquete vivo; producto asociado a él |
| `discountType` | Sí | Forma nueva | `PORCENTAJE` o `FIJO` |
| `discountValue` | Sí | Valor nuevo | Las cotas de `RN-PM-037` |

**Los dos son obligatorios: no es una corrección parcial.** Forma y valor son un solo dato —`DiscountValue`— y corregir uno sin el otro no significa nada: un `15` que era porcentaje no es un fijo de `15`. El verbo sigue siendo `PATCH` por la forma del módulo, y la razón de las dos cosas está en §14.1.

### 6.2 Salida

`200` con el paquete entero en la forma del detalle.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `packages:update`; paquete vivo; el producto está en el paquete; el descuento nuevo dentro de cota contra el precio de hoy.

**Postcondiciones:** la fila tiene la forma y el valor nuevos y `updated_at` avanzado si algo cambió; `audit_change_log` tiene la fila `UPDATE` con antes y después; el paquete vale la nueva suma.

## 8. Flujo principal

1. Llega la petición.
2. El sistema valida la forma (§11).
3. El sistema resuelve el paquete **vivo**, bloqueándolo (`EX-001`), y la fila del producto en él (`EX-002`).
4. El sistema comprueba la cota contra el precio **de hoy** del producto (`EX-003`).
5. Si nada cambió de valor, devuelve el detalle sin escribir.
6. Escribe, audita y devuelve `200`.

**No mira el estado del producto.** Un producto inactivo dentro del paquete puede corregirse: la fila existe y el descuento es del paquete. Lo que decide si se ofrece es la oferta.

## 9. Flujos alternativos

### FA-001 — Cambiar de porcentaje a fijo

**Comportamiento:** la fila pasa a `FIJO` con su valor; la auditoría registra `{type: {before, after}, value: {before, after}}`.

### FA-002 — El precio del producto bajó desde que se asoció

**Comportamiento:** la cota se comprueba contra el precio **de hoy**: un fijo que entró cuando el producto valía 100 y hoy vale 50 **no se puede corregir a 60**; sí a 50 o menos. Es donde el hueco temporal de `RN-PM-037` se cierra solo, sin que nadie lo persiga.

## 10. Excepciones

### EX-001 — El paquete no existe o está retirado

**Respuesta del sistema:** `404`.

### EX-002 — El producto no está en el paquete

**Respuesta del sistema:** `404` — *«Ese producto no está en el paquete.»* No distingue «no existe» de «no está aquí»: la fila que se corrige es la pareja, y la pareja no existe.

### EX-003 — El descuento deja al producto por debajo de cero

**Respuesta del sistema:** `409`, con el mismo mensaje que `RF-PM-023` `EX-006`, nombrando el precio.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Forma y valor presentes | La forma del descuento y su valor son obligatorios. |
| `VAL-003` | Forma en el dominio; porcentaje `0..100` con dos decimales | La forma del descuento debe ser PORCENTAJE o FIJO, y el porcentaje debe estar entre 0 y 100. |
| `VAL-004` | Valor no negativo y con los decimales admitidos | El valor del descuento no puede ser negativo ni tener más decimales que su moneda. |
| `VAL-005` | Ningún campo desconocido — ni `productId` | El cuerpo de la petición contiene campos no admitidos. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-315` | El sistema corrige el valor, la forma o los dos, y devuelve el paquete con `priceInPackage` y los totales **rehechos** |
| `CA-PM-316` | El sistema rechaza con `409` un fijo **un céntimo mayor** que el precio **de hoy**, también cuando el precio bajó después de asociar |
| `CA-PM-317` | El sistema rechaza con `400` forma o valor ausentes: no es parcial |
| `CA-PM-318` | El sistema responde `404` al producto que **no está** en el paquete y al paquete **retirado** |
| `CA-PM-319` | Sin cambio de valor responde `200` sin avanzar `updatedAt` ni auditar; con cambio deja la fila `UPDATE` con antes y después de **forma y valor** |
| `CA-PM-320` | Un producto **inactivo** dentro del paquete se corrige igual, y el paquete sigue `offerable: false` por él |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Corregir a `FIJO` igual al precio | Pasa: el producto queda a cero |
| Corregir el mismo valor en la otra forma (`10` % ↔ `10` fijo) | Es un cambio: forma distinta, precio dentro del paquete distinto |
| Dos correcciones simultáneas | El bloqueo del paquete las ordena |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿`PATCH` parcial —solo el valor— o los dos obligatorios? | **Los dos obligatorios**, y el verbo sigue siendo `PATCH` por la forma del módulo. Forma y valor son un solo dato: corregir el valor sin decir la forma obliga al servidor a asumir la que había, y un `15` que era porcentaje pasaría a fijo sin que nadie lo dijera al primer despiste. Se descartó `PUT` para no tener un verbo distinto en la única corrección del módulo que no es parcial |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-09-2026 | Redacción inicial. **Forma y valor obligatorios**: son un solo dato, y corregir uno sin el otro invita al despiste. La cota se comprueba contra el precio **de hoy**, que es donde el hueco temporal de `RN-PM-037` se cierra solo. El producto inactivo dentro del paquete se corrige igual. | Responsable técnico |
| 0.2.0 | 15-09-2026 | **Construida** (`PackageDiscountIT`). Sin enmiendas de comportamiento. El registro `UPDATE` lleva `product_id`, `type` y `value` con su antes y su después, y como `entity_id` el del paquete, igual que la asociación. | Responsable técnico |
