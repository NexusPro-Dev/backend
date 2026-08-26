# SPEC — `RF-PM-003` Consultar el detalle de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-003` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Ver todo lo que el sistema sabe de un producto, incluido su retiro.

## 2. Contexto

El listado de `RF-PM-002` responde «qué hay»; esta consulta responde «qué es exactamente esto». Es la pantalla desde la que se decide corregir un precio, desactivar una oferta o entender por qué un producto dejó de venderse, y por eso trae cosas que el listado no lleva.

**El destino llega resuelto.** Cuando el producto es un upgrade, el detalle devuelve el código, el nombre y el **nivel** de la membresía a la que lleva: un detalle que obliga a una segunda llamada para ser legible no es un detalle.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Consulta un producto antes de corregirlo o retirarlo |
| Fuerza comercial | Consulta un producto para explicárselo a un cliente |

## 4. Alcance

### 4.1 Incluye

- Devolver un producto por su identificador, con todos sus datos.
- Resolver la membresía destino de los upgrades, con su nivel.
- Indicar si el producto está retirado y desde cuándo.

### 4.2 No incluye

- **El historial de cambios del producto.** Quién cambió qué y cuándo vive en la auditoría, que tiene sus propias consultas (`RF-SP-011`).
- **Cuántas veces se ha vendido.** No existen las ventas.
- **Los otros productos que llevan al mismo destino.** El detalle habla de un producto, no del catálogo; esa pregunta la responde el filtro de `RF-PM-002`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-010` | El producto no desaparece: el retiro es lógico | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del producto | Sí | Cuál se consulta | Debe tener forma de identificador válido |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Producto | Identificador, tipo, nombre, descripción, precio con su moneda y estado |
| Membresía destino | En los upgrades: código, nombre y **nivel**. Vacía en los servicios |
| Marca de retiro | Si está retirado y desde cuándo |
| Marcas temporales | Cuándo se creó y cuándo se modificó por última vez |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de lectura de productos.

**Postcondiciones**

- Ninguna: la consulta no modifica nada.

## 8. Flujo principal

1. El actor pide un producto por su identificador.
2. El sistema comprueba que el identificador tiene forma válida.
3. El sistema devuelve el producto con su destino resuelto si es un upgrade.

## 9. Flujos alternativos

### FA-001 — Producto retirado

**Cuándo ocurre:** el producto está eliminado lógicamente.

1. El sistema **lo devuelve igual**, marcado como retirado y con la fecha.
2. No responde «no existe»: existió, se vendió quizá, y ocultarlo impediría entender qué era.

### FA-002 — Producto de servicio

**Cuándo ocurre:** el producto no es un upgrade.

1. La membresía destino llega **vacía y presente**, no ausente. Un campo que falta es indistinguible de uno que el cliente no conoce.

## 10. Excepciones

### EX-001 — Producto inexistente

**Condición:** no hay ningún producto con ese identificador.
**Respuesta del sistema:** responde que el recurso no existe.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-023` | El sistema devuelve el producto con todos sus datos y su precio con la moneda en que está expresado |
| `CA-PM-024` | El sistema resuelve la membresía destino de un upgrade con su código, su nombre y su nivel |
| `CA-PM-025` | El sistema devuelve la membresía destino **vacía y presente** en un producto de servicio |
| `CA-PM-026` | El sistema devuelve un producto retirado marcándolo como tal y con su fecha de retiro, en lugar de responder que no existe |
| `CA-PM-027` | El sistema responde que el recurso no existe ante un identificador que no corresponde a ningún producto |
| `CA-PM-028` | El sistema rechaza un identificador con formato inválido como dato inválido, y no como recurso no encontrado |
| `CA-PM-029` | El sistema rechaza la consulta a un actor sin el permiso de lectura de productos |

## 13. Casos límite

- **Identificador con forma válida pero inexistente:** «no existe». Es distinto del anterior y los dos deben probarse: uno es un dato mal escrito, el otro un recurso ausente.
- **Identificador no canónico:** una forma laxa del identificador debe rechazarse como dato inválido y no resolverse en silencio. Es el mismo hueco que `RF-SP-018` tuvo abierto durante dos días.
- **Upgrade cuyo destino cambió de nivel:** la cadena de membresías se reordena al insertar un eslabón (`RN-SP-007`), de modo que el nivel del destino **no es estable en el tiempo**. El detalle devuelve el nivel **actual**, no el que tenía cuando se creó el producto.
- **Producto retirado cuyo destino sigue vivo:** se devuelve con normalidad; retirar el producto no toca la membresía.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | **¿El detalle devuelve el motivo del retiro?** El motivo se exige al eliminar (Art. V.13) y vive en la auditoría de eliminación, que tiene su propio permiso (`audit:read-deletions`). Traerlo aquí lo pone al alcance de cualquiera con lectura de productos; no traerlo obliga a ir a otra pantalla para saber por qué se retiró algo | Responsable del proyecto | **Abierta** |
| 2 | **¿Devuelve quién lo creó y quién lo retiró?** Mismo dilema, y con la particularidad de que el Art. V.7 mantiene esos datos **fuera** de la tabla a propósito: viven en la auditoría | Responsable técnico | **Abierta** |
| 3 | **¿El precio viaja como número o como texto ya formateado?** Como número deja el formato al cliente, que es lo correcto; como texto evita que dos interfaces redondeen distinto un importe de cuatro decimales. Afecta a todo lo que después muestre precios | Responsable técnico | **Abierta** |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
