# SPEC — `RF-PM-006` Eliminar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-006` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Retirar del catálogo lo que fue un error o ya no existe, sin perder la constancia de que existió.

## 2. Contexto

Desactivar sirve para dejar de vender algo temporalmente (`RF-PM-005`). Eliminar es otra cosa: es decir que ese producto **no debió estar ahí** o que ya no forma parte de la oferta de la empresa. Un producto duplicado por error, un servicio que se dejó de prestar, un upgrade creado con el destino equivocado.

**La fila no desaparece** (`RN-PM-010`). El día que existan compras, cada una tendrá que poder decir qué se compró y a qué precio, y eso es imposible si el producto se borró de verdad. Por eso el retiro es lógico y **exige motivo** (Art. V.13): el motivo, con el estado completo de lo retirado, viaja al registro de eliminación.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Retira un producto del catálogo |

## 4. Alcance

### 4.1 Incluye

- Retirar un producto exigiendo un motivo escrito.
- Dejarlo fuera de la oferta y fuera del cómputo de `RN-PM-004`, liberando su destino.
- Registrar la eliminación con el motivo y el estado completo de lo retirado.

### 4.2 No incluye

- **Borrarlo físicamente.** Ninguna operación de este módulo borra una fila de producto.
- **Restaurar un producto retirado.** No existe deshacer: quien se equivoque registra uno nuevo.
- **Desactivarlo temporalmente**, que es `RF-PM-005` y es lo que casi siempre se quería hacer.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-009` | Solo se ofrece lo activo | `requirements/pm.md` §5.1 |
| `RN-PM-010` | El producto no desaparece: el retiro es lógico y con motivo | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del producto | Sí | Cuál se retira | Debe existir y no estar ya retirado |
| Motivo | **Sí** | Por qué se retira | Texto libre, sin catálogo de códigos, con longitud mínima efectiva: no admite espacios en blanco |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Confirmación | Que el producto quedó retirado. **No devuelve el producto**: lo que se acaba de retirar no es algo que el sistema deba seguir ofreciendo a quien lo pidió |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de eliminación de productos.
- El producto existe y no está retirado.

**Postcondiciones**

- El producto queda marcado como retirado, con la fecha del retiro.
- El producto deja de ofrecerse y su destino queda libre para otro upgrade.
- El registro de eliminación contiene el **motivo** y el **estado completo** del producto tal como estaba antes de retirarse.

## 8. Flujo principal

1. El actor envía el identificador y el motivo.
2. El sistema comprueba que el motivo tiene contenido y que el producto existe y no está ya retirado.
3. El sistema **captura el estado completo del producto antes de tocar nada**.
4. El sistema marca el producto como retirado.
5. El sistema registra la eliminación con el motivo y la instantánea capturada.
6. El sistema confirma el retiro.

## 9. Flujos alternativos

### FA-001 — El producto estaba activo

**Cuándo ocurre:** se retira un producto que estaba a la venta.

1. **El estado no se toca.** El producto queda retirado conservando el estado que tenía, para que el registro diga en qué situación estaba cuando se retiró.
2. Deja de ofrecerse igualmente: es el retiro lo que lo saca de la oferta, no el estado.

## 10. Excepciones

### EX-001 — Producto inexistente

**Condición:** no hay ningún producto con ese identificador.
**Respuesta del sistema:** responde que el recurso no existe.

### EX-002 — Producto ya retirado

**Condición:** el producto ya estaba retirado.
**Respuesta del sistema:** rechaza la operación. **No es idempotente a propósito**: retirar dos veces con dos motivos distintos dejaría el segundo motivo escrito sobre un hecho que ocurrió antes y por otra razón.

### EX-003 — Motivo ausente o vacío

**Condición:** no llega motivo, o llega solo con espacios.
**Respuesta del sistema:** rechaza la operación y **no retira nada**.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Motivo obligatorio y con contenido | El motivo de la eliminación es obligatorio. |
| `VAL-003` | Longitud del motivo | El motivo excede la longitud admitida. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-048` | El sistema retira un producto y este deja de ofrecerse, conservando su fila |
| `CA-PM-049` | El sistema rechaza el retiro sin motivo, y no retira nada |
| `CA-PM-050` | El sistema rechaza un motivo que solo contiene espacios |
| `CA-PM-051` | El sistema registra la eliminación con el motivo y el **estado completo** del producto anterior al retiro |
| `CA-PM-052` | El sistema **no modifica el estado** del producto al retirarlo: el registro dice si estaba activo o inactivo |
| `CA-PM-053` | El sistema libera el destino de un upgrade retirado, de modo que otro upgrade hacia ese nivel puede activarse |
| `CA-PM-054` | El sistema libera el **nombre** de un producto retirado, de modo que otro producto puede usarlo |
| `CA-PM-055` | El sistema rechaza retirar un producto ya retirado |
| `CA-PM-056` | El sistema responde que el recurso no existe ante un identificador que no corresponde a ningún producto |
| `CA-PM-057` | El sistema rechaza el retiro a un actor sin el permiso de eliminación de productos |

## 13. Casos límite

- **La instantánea se captura antes de tocar nada.** Si se capturara después, el registro no diría qué era el producto sino qué quedó de él. Es el mismo orden que `RF-SP-029` fijó al eliminar una persona.
- **Retirar el único upgrade hacia un nivel:** se admite. Que un nivel deje de poder comprarse es una decisión comercial, no un estado inválido.
- **Dos retiros simultáneos del mismo producto:** uno debe quedar y el otro ser rechazado como «ya retirado», con un solo registro de eliminación. Dos registros con dos motivos para un único hecho es evidencia contradictoria.
- **Retirar y registrar otro con el mismo nombre en carrera:** el nombre se libera al retirar; el alta simultánea debe resolverse por la unicidad y no dejar dos vivos con el mismo nombre.
- **Motivo muy largo:** se rechaza por longitud, no se recorta. Un motivo truncado dice algo distinto de lo que se escribió.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | **¿Se puede retirar un producto activo, o hay que desactivarlo primero?** Exigir desactivar primero convierte un descuido en dos pasos deliberados —y retirar es irreversible—. Permitirlo directo evita una fricción que nadie agradece cuando el producto es un duplicado creado hace un minuto | Responsable del proyecto | **Abierta** |
| 2 | **El día que existan compras, ¿se podrá retirar un producto ya vendido?** Retirarlo no borra lo vendido, pero sí cambia lo que ve quien consulte su compra. La alternativa —prohibirlo— dejaría el catálogo lleno de productos que nadie puede quitar | Responsable del proyecto | **Abierta** |
| 3 | **¿El retiro emite además evento de seguridad?** No concede ni quita privilegios, de modo que por el catálogo de `security.md` §8.1 no correspondería. Se registra porque retirar algo del catálogo comercial es la operación más destructiva del módulo | Responsable técnico | **Abierta** |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
