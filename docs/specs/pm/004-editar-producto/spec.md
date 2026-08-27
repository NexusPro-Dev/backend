# SPEC — `RF-PM-004` Editar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-004` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |
| Enmendada el | 27-08-2026 — ver §15 |

---

## 1. Objetivo

Corregir lo que se puede corregir de un producto, sin reescribir lo que ya se vendió.

## 2. Contexto

Un producto se equivoca de nombre, se le escapa una falta en la descripción o cambia de precio. Sin este requerimiento la única salida sería retirarlo y crear otro, lo que rompe la referencia de todo lo que apunte al producto anterior.

**Lo que no se puede corregir es lo que define qué se compró.** El **tipo**, el **código** y la **membresía destino** quedan fuera: cambiarlos convierte lo comprado en otra cosa, y quien pagó por subir a un nivel se encontraría con un derecho distinto del que adquirió. Quien necesite otro destino registra otro producto y retira el anterior.

**El precio se puede cambiar siempre, y eso obliga a la compra futura.** Resuelto el 26-08-2026: corregir el precio nunca reescribe lo vendido, porque **cada compra guardará el importe que se pagó** en el momento de comprar. Es una condición que este requerimiento **impone a un módulo que todavía no existe**, y por eso queda escrita aquí y en `requirements/pm.md` §1.4: si la compra leyera el precio del producto, esta operación pasaría a reescribir el pasado sin que nadie la hubiera tocado.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Corrige los datos comerciales de un producto |

## 4. Alcance

### 4.1 Incluye

- Corregir el **nombre**, la **descripción**, el **precio**, la **moneda** y la **vigencia**.
- Aplicar solo lo que llegue: lo que no se envía no se toca.
- Dejar constancia en la auditoría de cambios de **qué cambió**, con su valor anterior y el nuevo.

### 4.2 No incluye

- **Cambiar el tipo** (`RN-PM-001`), **el código** (`RN-PM-013`) ni **la membresía destino**. Ver §2.
- **Exigir un motivo del cambio.** El Art. V.13 solo lo obliga en las eliminaciones, y la auditoría ya registra qué cambió, de cuánto a cuánto, quién y cuándo. Resuelto el 26-08-2026.
- **Activar o desactivar**, que es `RF-PM-005`: el estado no es un dato comercial, es una decisión de publicación.
- **Retirar el producto**, que es `RF-PM-006`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-001` | Dos tipos, y el tipo es inmutable | `requirements/pm.md` §5.1 |
| `RN-PM-013` | El código no se libera nunca, y es inmutable | `requirements/pm.md` §5.1 |
| `RN-PM-005` | Nombre único entre los vivos | `requirements/pm.md` §5.1 |
| `RN-PM-006` | El precio es mayor que cero | `requirements/pm.md` §5.1 |
| `RN-PM-007` | El precio respeta los decimales de su moneda | `requirements/pm.md` §5.1 |
| `RN-PM-008` | La moneda debe estar activa al declararla | `requirements/pm.md` §5.1 |
| `RN-PM-015` | La vigencia se mide en días y es opcional | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del producto | Sí | Cuál se corrige | Debe existir y no estar retirado |
| Nombre | No | Nombre nuevo | Único entre los vivos (`RN-PM-005`); **no admite vaciarse** |
| Descripción | No | Descripción nueva | **Sí admite vaciarse**, porque es opcional |
| Precio | No | Precio nuevo | Mayor que cero y con los decimales de su moneda |
| Moneda | No | Moneda nueva | Debe existir y estar activa |
| Vigencia | No | Vigencia nueva, en días | Mayor que cero. **Sí admite vaciarse**, y hacerlo convierte el producto en uno que no caduca |

**Ausente y vacío no son lo mismo.** No enviar un campo significa «déjalo como está»; enviarlo vacío significa «bórralo», y solo la descripción lo admite. Confundir los dos estados hace que corregir un nombre borre la descripción sin que nadie lo pida.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Producto | El producto con los cambios ya aplicados, en la misma forma que devuelve `RF-PM-003` |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de productos.
- El producto existe y no está retirado.

**Postcondiciones**

- El producto queda con los valores nuevos y **conserva su identificador**, su tipo y su destino.
- La auditoría de cambios contiene un evento con **solo los campos que cambiaron**, cada uno con su valor anterior y el nuevo.

## 8. Flujo principal

1. El actor envía el identificador y los campos que quiere corregir.
2. El sistema comprueba que el producto existe y no está retirado.
3. El sistema valida cada campo recibido según su regla.
4. Si llega un nombre, el sistema comprueba que no lo tiene ya otro producto vivo.
5. Si llega un precio o una moneda, el sistema comprueba la moneda y los decimales.
6. El sistema aplica los cambios y emite el evento de auditoría con lo que efectivamente cambió.
7. El sistema devuelve el producto corregido.

## 9. Flujos alternativos

### FA-001 — Petición sin ningún cambio efectivo

**Cuándo ocurre:** el actor envía los mismos valores que el producto ya tiene.

1. El sistema responde con normalidad.
2. **No emite evento de auditoría**: un cambio que no cambió nada no es un cambio, y registrarlo llena la línea de tiempo de ruido que oculta lo que sí ocurrió.

### FA-002 — Solo se corrige la descripción

**Cuándo ocurre:** llega únicamente la descripción, quizá vacía.

1. El nombre, el precio y la moneda **no se tocan** ni se revalidan.
2. La descripción queda vacía si así llegó.

## 10. Excepciones

### EX-001 — Producto inexistente o retirado

**Condición:** el identificador no corresponde a ningún producto, o corresponde a uno retirado.
**Respuesta del sistema:** rechaza la corrección. Un producto retirado **no se corrige**: lo que se retiró debe quedar como estaba para que lo que lo referencie siga diciendo la verdad.

### EX-002 — Nombre ya en uso

**Condición:** el nombre nuevo lo tiene otro producto vivo.
**Respuesta del sistema:** rechaza la corrección indicando el campo duplicado, y no aplica **ninguno** de los cambios enviados.

### EX-003 — Moneda inexistente o inactiva

**Condición:** la moneda nueva no existe o está desactivada.
**Respuesta del sistema:** rechaza la corrección entera.

### EX-004 — Se intenta cambiar el tipo, el código o el destino

**Condición:** la petición trae el tipo, el código o la membresía destino.
**Respuesta del sistema:** **rechaza la petición**, y no ignora los campos en silencio. Ignorarlos haría creer al actor que el cambio se aplicó.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | El nombre no admite vaciarse | El nombre del producto no puede quedar vacío. |
| `VAL-003` | Longitud del nombre y de la descripción | El valor excede la longitud admitida. |
| `VAL-004` | Precio mayor que cero | El precio debe ser mayor que cero. |
| `VAL-005` | Decimales del precio según su moneda | El precio no admite más decimales que los de su moneda. |
| `VAL-006` | El tipo, el código y el destino no se admiten | El tipo, el código y la membresía destino no se pueden modificar. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-030` | El sistema corrige el nombre, la descripción, el precio y la moneda, y conserva el identificador, el tipo y el destino |
| `CA-PM-031` | El sistema aplica **solo los campos enviados** y deja intactos los ausentes |
| `CA-PM-032` | El sistema distingue un campo **ausente** de uno enviado **vacío**: vaciar la descripción la borra, y enviar el nombre vacío se rechaza |
| `CA-PM-033` | El sistema rechaza la petición que trae el tipo, el código o la membresía destino, en lugar de ignorarlos |
| `CA-PM-034` | El sistema rechaza un nombre que ya tiene otro producto vivo, y **no aplica ninguno** de los demás cambios enviados |
| `CA-PM-035` | El sistema rechaza una moneda inactiva y no aplica ningún cambio |
| `CA-PM-036` | El sistema rechaza corregir un producto retirado |
| `CA-PM-037` | El sistema registra en la auditoría de cambios **solo los campos que cambiaron**, cada uno con su valor anterior y el nuevo |
| `CA-PM-038` | El sistema **no registra evento** cuando la petición no cambia nada |
| `CA-PM-039` | El sistema rechaza la corrección a un actor sin el permiso de modificación de productos |
| `CA-PM-083` | El sistema **corrige un producto inactivo**, que es el estado en el que nace: sin esto no habría forma de ponerle la descripción que `RF-PM-005` exige para publicarlo |
| `CA-PM-084` | El sistema **no exige motivo** para corregir, ni siquiera al cambiar el precio |
| `CA-PM-094` | El sistema corrige la **vigencia**, y **vaciarla** convierte el producto en uno que no caduca |

## 13. Casos límite

- **Nombre igual al actual:** no es un duplicado consigo mismo. La unicidad debe excluir al propio producto, o corregir la descripción sin tocar el nombre acabaría rechazándose.
- **Nombre que solo difiere en mayúsculas o acentos del actual:** sí es un cambio —`Plan Oro` a `Plan oro`— y debe admitirse, porque el choque es contra **otros** productos, no contra uno mismo.
- **Cambiar la moneda sin cambiar el precio:** el importe se reinterpreta en la moneda nueva y **su valor no se convierte**. Debe quedar escrito que el sistema no hace conversión de divisa: cambiar de moneda es declarar que ese número siempre estuvo en la otra.
- **Precio con más decimales de los que admite la moneda nueva:** cambiar moneda y precio a la vez obliga a validar el precio contra la moneda **nueva**, no contra la anterior.
- **Dos correcciones simultáneas del mismo producto:** la última debe quedar entera, y no una mezcla de las dos.

## 14. Preguntas abiertas

Ninguna. Dos se resolvieron el 26-08-2026 y **las otras dos quedaron respondidas por la aprobación de `RF-PM-001`**, que es lo que ocurre cuando una decisión anterior alcanza a una spec posterior: no se vuelven a preguntar, se anotan con lo que las cerró.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se puede cambiar el precio de un producto ya vendido? | **Sí, y la compra guardará el precio que se pagó.** Cada venta copiará el importe en el momento de comprar, de modo que corregir el catálogo **nunca reescribe el pasado** y esta operación sigue siendo una corrección de verdad. Es una **condición impuesta a un módulo que todavía no existe**, y por eso queda escrita también en `requirements/pm.md` §1.4: si la compra leyera el precio del producto, `RF-PM-004` pasaría a reescribir facturas sin que nadie la hubiera tocado. Se descartaron congelar el precio de lo vendido —llena el catálogo de productos casi idénticos y convierte cada cambio de precio en un alta y un retiro— y versionar el precio con vigencia, que es una tabla más y la puerta de entrada de las promociones que §1.3 deja fuera |
| 2 | ¿Cambiar el precio exige motivo? | **No.** El Art. V.13 solo lo obliga en las eliminaciones, y la auditoría de cambios ya registra **qué** cambió, **de cuánto a cuánto**, **quién** y **cuándo**. Lo único que falta es el «por qué», y exigirlo en cada corrección de una coma es el camino más corto para que ese campo se llene de «ajuste» y deje de significar nada |
| 3 | ¿Se puede corregir un producto inactivo? | **Sí, y no era opcional.** Lo cerró `RN-PM-012` al aprobarse `RF-PM-001`: si el producto **nace inactivo**, prohibir corregirlo en ese estado dejaría a todo producto nuevo sin forma de recibir la descripción que `RN-PM-014` exige para publicarlo. La pregunta se hizo cuando el estado inicial todavía estaba abierto; al cerrarse aquel, esta dejó de tener dos salidas |
| 4 | Si el producto lleva código, ¿se puede corregir? | **No: es inmutable** (`RN-PM-013`, aprobada con `RF-PM-001`). El código es la referencia desde la que una factura dirá qué se vendió; corregirlo rompería exactamente aquello para lo que existe. Se suma al tipo y al destino en `EX-004`, `VAL-006` y `CA-PM-033`: la petición que lo traiga **se rechaza**, no se ignora |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.2.0 | 26-08-2026 | **Aprobada.** El precio se puede corregir siempre, y eso **impone una condición a la compra futura**: cada venta guardará el importe que se pagó, o esta operación pasaría a reescribir el pasado. **No se exige motivo** para corregir. Las otras dos preguntas las cerró la aprobación de `RF-PM-001`: corregir un producto inactivo no solo se admite —es imprescindible, porque es el estado en el que nace—, y el **código es inmutable**, de modo que se suma al tipo y al destino entre lo que la petición no puede traer. Dos criterios nuevos, `CA-PM-083` y `CA-PM-084`. | Responsable del proyecto |
| 0.1.0 | 26-08-2026 | Redacción inicial, con cuatro preguntas abiertas. | Responsable técnico |
| 0.3.0 | 27-08-2026 | La **vigencia** se suma a lo corregible (`RN-PM-015`), con el mismo criterio que el precio: corregirla no reescribe lo vendido **porque cada compra guardará la vigencia que compró**. Es el tercer campo que admite vaciarse —junto con la descripción—, y vaciarlo convierte el producto en uno que no caduca. `CA-PM-094`. | Responsable del proyecto |
