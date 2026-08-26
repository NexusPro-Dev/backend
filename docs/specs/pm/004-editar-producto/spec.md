# SPEC — `RF-PM-004` Editar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-004` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Corregir lo que se puede corregir de un producto, sin reescribir lo que ya se vendió.

## 2. Contexto

Un producto se equivoca de nombre, se le escapa una falta en la descripción o cambia de precio. Sin este requerimiento la única salida sería retirarlo y crear otro, lo que rompe la referencia de todo lo que apunte al producto anterior.

**Lo que no se puede corregir es lo que define qué se compró.** El **tipo** y la **membresía destino** quedan fuera: cambiarlos convierte lo comprado en otra cosa, y quien pagó por subir a un nivel se encontraría con un derecho distinto del que adquirió. Quien necesite otro destino registra otro producto y retira el anterior.

**El precio es el caso delicado.** Cambiarlo no reescribe nada mientras no existan ventas, y el día que existan tendrá que decidirse qué precio recuerda cada una. Este requerimiento no lo resuelve: lo declara.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Corrige los datos comerciales de un producto |

## 4. Alcance

### 4.1 Incluye

- Corregir el **nombre**, la **descripción**, el **precio** y la **moneda**.
- Aplicar solo lo que llegue: lo que no se envía no se toca.
- Dejar constancia en la auditoría de cambios de **qué cambió**, con su valor anterior y el nuevo.

### 4.2 No incluye

- **Cambiar el tipo** (`RN-PM-001`) ni **la membresía destino**. Ver §2.
- **Activar o desactivar**, que es `RF-PM-005`: el estado no es un dato comercial, es una decisión de publicación.
- **Retirar el producto**, que es `RF-PM-006`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-001` | Dos tipos, y el tipo es inmutable | `requirements/pm.md` §5.1 |
| `RN-PM-005` | Nombre único entre los vivos | `requirements/pm.md` §5.1 |
| `RN-PM-006` | El precio es mayor que cero | `requirements/pm.md` §5.1 |
| `RN-PM-007` | El precio respeta los decimales de su moneda | `requirements/pm.md` §5.1 |
| `RN-PM-008` | La moneda debe estar activa al declararla | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del producto | Sí | Cuál se corrige | Debe existir y no estar retirado |
| Nombre | No | Nombre nuevo | Único entre los vivos (`RN-PM-005`); **no admite vaciarse** |
| Descripción | No | Descripción nueva | **Sí admite vaciarse**, porque es opcional |
| Precio | No | Precio nuevo | Mayor que cero y con los decimales de su moneda |
| Moneda | No | Moneda nueva | Debe existir y estar activa |

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

### EX-004 — Se intenta cambiar el tipo o el destino

**Condición:** la petición trae el tipo o la membresía destino.
**Respuesta del sistema:** **rechaza la petición**, y no ignora los campos en silencio. Ignorarlos haría creer al actor que el cambio se aplicó.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | El nombre no admite vaciarse | El nombre del producto no puede quedar vacío. |
| `VAL-003` | Longitud del nombre y de la descripción | El valor excede la longitud admitida. |
| `VAL-004` | Precio mayor que cero | El precio debe ser mayor que cero. |
| `VAL-005` | Decimales del precio según su moneda | El precio no admite más decimales que los de su moneda. |
| `VAL-006` | El tipo y el destino no se admiten | El tipo y la membresía destino no se pueden modificar. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-030` | El sistema corrige el nombre, la descripción, el precio y la moneda, y conserva el identificador, el tipo y el destino |
| `CA-PM-031` | El sistema aplica **solo los campos enviados** y deja intactos los ausentes |
| `CA-PM-032` | El sistema distingue un campo **ausente** de uno enviado **vacío**: vaciar la descripción la borra, y enviar el nombre vacío se rechaza |
| `CA-PM-033` | El sistema rechaza la petición que trae el tipo o la membresía destino, en lugar de ignorarlos |
| `CA-PM-034` | El sistema rechaza un nombre que ya tiene otro producto vivo, y **no aplica ninguno** de los demás cambios enviados |
| `CA-PM-035` | El sistema rechaza una moneda inactiva y no aplica ningún cambio |
| `CA-PM-036` | El sistema rechaza corregir un producto retirado |
| `CA-PM-037` | El sistema registra en la auditoría de cambios **solo los campos que cambiaron**, cada uno con su valor anterior y el nuevo |
| `CA-PM-038` | El sistema **no registra evento** cuando la petición no cambia nada |
| `CA-PM-039` | El sistema rechaza la corrección a un actor sin el permiso de modificación de productos |

## 13. Casos límite

- **Nombre igual al actual:** no es un duplicado consigo mismo. La unicidad debe excluir al propio producto, o corregir la descripción sin tocar el nombre acabaría rechazándose.
- **Nombre que solo difiere en mayúsculas o acentos del actual:** sí es un cambio —`Plan Oro` a `Plan oro`— y debe admitirse, porque el choque es contra **otros** productos, no contra uno mismo.
- **Cambiar la moneda sin cambiar el precio:** el importe se reinterpreta en la moneda nueva y **su valor no se convierte**. Debe quedar escrito que el sistema no hace conversión de divisa: cambiar de moneda es declarar que ese número siempre estuvo en la otra.
- **Precio con más decimales de los que admite la moneda nueva:** cambiar moneda y precio a la vez obliga a validar el precio contra la moneda **nueva**, no contra la anterior.
- **Dos correcciones simultáneas del mismo producto:** la última debe quedar entera, y no una mezcla de las dos.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | **¿Se puede cambiar el precio de un producto que ya se vendió?** Hoy no hay ventas y la respuesta es «sí, sin consecuencias». El día que existan, o la venta guarda el precio que pagó —y entonces esto sigue siendo libre— o lo lee del producto —y entonces cambiar el precio reescribe el pasado—. Decidirlo ahora es lo que evita tener que migrar datos después | Responsable del proyecto | **Abierta** |
| 2 | **¿Cambiar el precio exige motivo?** El Art. V.13 solo lo obliga en las eliminaciones. Un precio es una decisión comercial que alguien querrá poder explicar; exigirlo en cada corrección de una coma sería ruido | Responsable del proyecto | **Abierta** |
| 3 | **¿Se puede corregir un producto inactivo?** Parece que sí —es justo cuando conviene prepararlo—, pero conviene confirmarlo, porque también permite cambiarle el precio a algo que estuvo publicado y volverlo a publicar como si fuera lo mismo | Responsable del proyecto | **Abierta** |
| 4 | **Si la pregunta 2 de `RF-PM-001` se resuelve con código, ¿el código se puede corregir?** En `SP` el código de un rol se corrige y el de una membresía no. Aquí el código sería la referencia estable desde facturación, y corregirlo rompería justo eso | Responsable del proyecto | **Abierta** |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
