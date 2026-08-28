# SPEC — `RF-PM-003` Consultar el detalle de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-003` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |
| Enmendada el | 28-08-2026 — ver §15 |

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
- Devolver **el motivo por el que se retiró**, cuando el producto está retirado.

### 4.2 No incluye

- **El historial de cambios del producto, y quién hizo cada uno.** Qué cambió, cuándo y **a manos de quién** vive en la auditoría, que tiene sus propias consultas (`RF-SP-011`). El Art. V.7 mantiene las columnas de actor fuera de la tabla a propósito, y traer ese dato aquí obligaría a duplicarlo o a leer el almacén de evidencia de otro módulo.
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
| Producto | Identificador, código, tipo, nombre, descripción, **icono**, precio con su moneda, **vigencia en días** y estado |
| Membresía destino | En los upgrades: código, nombre y **nivel**. Vacía en los bots |
| Marca de retiro | Si está retirado, desde cuándo y **con qué motivo** |
| Marcas temporales | Cuándo se creó y cuándo se modificó por última vez. **Sin actor**: quién lo hizo vive en la auditoría |

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

### FA-002 — Producto de bot

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
| `CA-PM-025` | El sistema devuelve la membresía destino **vacía y presente** en un producto de bot |
| `CA-PM-026` | El sistema devuelve un producto retirado marcándolo como tal y con su fecha de retiro, en lugar de responder que no existe |
| `CA-PM-027` | El sistema responde que el recurso no existe ante un identificador que no corresponde a ningún producto |
| `CA-PM-028` | El sistema rechaza un identificador con formato inválido como dato inválido, y no como recurso no encontrado |
| `CA-PM-029` | El sistema rechaza la consulta a un actor sin el permiso de lectura de productos |
| `CA-PM-080` | El sistema devuelve el **motivo del retiro** de un producto retirado a cualquier actor con `products:read`, sin exigir permiso de auditoría |
| `CA-PM-081` | El sistema **no devuelve quién** creó, corrigió ni retiró el producto, ni siquiera resuelto desde la auditoría |
| `CA-PM-082` | El sistema devuelve el precio como **número**, con los decimales que declara su moneda y no con la escala de la columna: `49.99` en una moneda de dos decimales, no `49.9900` |

## 13. Casos límite

- **Identificador con forma válida pero inexistente:** «no existe». Es distinto del anterior y los dos deben probarse: uno es un dato mal escrito, el otro un recurso ausente.
- **Identificador no canónico:** una forma laxa del identificador debe rechazarse como dato inválido y no resolverse en silencio. Es el mismo hueco que `RF-SP-018` tuvo abierto durante dos días.
- **Upgrade cuyo destino cambió de nivel:** la cadena de membresías se reordena al insertar un eslabón (`RN-SP-007`), de modo que el nivel del destino **no es estable en el tiempo**. El detalle devuelve el nivel **actual**, no el que tenía cuando se creó el producto.
- **Producto retirado cuyo destino sigue vivo:** se devuelve con normalidad; retirar el producto no toca la membresía.
- **Precio con cuatro decimales en una moneda de dos:** no puede ocurrir, porque `RN-PM-007` lo impide al escribir. Si ocurriera —una carga directa en la base—, el detalle devuelve **lo almacenado** y no lo redondea: redondear aquí escondería el dato inválido en lugar de mostrarlo.

## 14. Preguntas abiertas

Ninguna. Las tres se resolvieron el 26-08-2026, antes de aprobar la especificación.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El detalle devuelve el motivo del retiro? | **Sí, a quien tenga `products:read`.** Delante de un producto retirado, «por qué» es la pregunta que se hace todo el mundo, y obligar a cambiar de pantalla para responderla convierte la auditoría en un trámite. **La consecuencia se asume y queda escrita**: `products:read` alcanza así a un dato que en la auditoría acota `audit:read-deletions`, de modo que ese permiso deja de acotar el motivo **de un producto** —sigue acotando el de todo lo demás y la línea de tiempo completa—. **El listado no lo lleva** (`CA-PM-077`): uno a uno es una consulta, en bloque es una exportación de decisiones comerciales. Esta resolución **enmienda el motivo** con el que se aprobó la resolución 3 de `RF-PM-002`, que decía que el motivo no viajaba en el catálogo; la decisión de aquella —los retirados no exigen permiso propio— no cambia, su justificación sí |
| 2 | ¿Devuelve quién lo creó y quién lo retiró? | **No.** El Art. V.7 mantiene las columnas de actor fuera de las tablas a propósito: quién hizo qué es evidencia y vive en los registros de auditoría. Traerlo aquí obligaría a duplicar el dato en `products` —y entonces habría dos verdades— o a que este módulo consulte el almacén de evidencia de otro, que es justo lo que `modules.md` §7 impide. El motivo sí viaja porque **es un dato del retiro**, no del actor |
| 3 | ¿El precio viaja como número o como texto formateado? | **Como número.** El formato lo decide quien lo muestra, y un importe formateado no se puede sumar: facturación y comisiones tendrían que deshacerlo para operar. **Con los decimales de su moneda, no con la escala de la columna**: `49.99` en una moneda de dos decimales y no `49.9900`, porque la escala de `numeric(14,4)` es una decisión de almacenamiento y no algo que el contrato deba exponer. **Consecuencia asumida y declarada**: un número JSON pasa por coma flotante de doble precisión en cualquier cliente JavaScript, de modo que sumar importes en el navegador puede desviarse un céntimo. El importe que cuenta es siempre el del servidor; el día que exista facturación, ningún total calculado en el cliente puede ser el que se cobre |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.2.0 | 26-08-2026 | **Aprobada.** El detalle devuelve el **motivo del retiro** a quien tenga `products:read` —con la consecuencia declarada de que ese permiso alcanza a un dato que la auditoría acota, y con el listado quedando fuera—, **no devuelve autoría** —el Art. V.7 la mantiene en la auditoría a propósito— y el **precio viaja como número**, con los decimales de su moneda y no con la escala de la columna. Tres criterios nuevos, `CA-PM-080` a `CA-PM-082`, y un caso límite sobre el precio que no se redondea al leer. | Responsable del proyecto |
| 0.1.0 | 26-08-2026 | Redacción inicial, con tres preguntas abiertas. | Responsable técnico |
| 0.3.0 | 27-08-2026 | El detalle devuelve la **vigencia en días** (`RN-PM-015`), y **vacía y presente** cuando el producto no caduca — un campo ausente sería indistinguible de uno que el cliente no conoce. | Responsable del proyecto |
| 0.3.0 | 28-08-2026 | **La respuesta gana el icono** (`RN-PM-016`) y el tipo `SERVICIO` pasa a llamarse `BOT`. Ninguna de las dos cosas cambia el comportamiento de esta consulta: el icono viaja como un campo más —nulo y presente cuando no lo hay, por el mismo criterio que el destino y la vigencia— y el renombrado solo cambia el valor que se lee y por el que se filtra. **El contrato publicado cambia**, de modo que la copia del frontend queda vieja. | Responsable técnico |
