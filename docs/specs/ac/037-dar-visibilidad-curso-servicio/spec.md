# SPEC — `RF-AC-037` Dar visibilidad de un curso a un servicio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-037` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que **quien tiene un servicio pueda estudiar el curso**: añadir un producto `BOT` a la lista de los que lo abren.

## 2. Contexto

Nace el 25-09-2026 por decisión del responsable del proyecto (`ac.md` §5.2.8): **que sea el curso quien declare quién lo puede ver, por el producto que se le proporciona como servicio**. Es `RF-AC-020` con otro lado: aquella lista dice qué **niveles** abren el curso, esta dice qué **servicios**, y **las dos se suman** (`RN-AC-020`). Un curso se abre a quien tiene vigente una de sus membresías **o** uno de sus servicios.

**Solo abre un producto de tipo `BOT`**: es el que «da derecho a una prestación» (`RN-PM-001`) y el que se llamó `SERVICIO` hasta el 28-08-2026. Un upgrade abre un nivel y el nivel ya tiene su lista; un paquete no se posee, se poseen sus productos. **El producto se comprueba por la interfaz que `PM` publica** (`ProductCatalog`, D-25), que hasta hoy no dice el tipo: este requerimiento **amplía `ProductCatalog` con una lectura** —la que lo dice—, como `RF-CM-001` amplió la suya. Con eso `AC` pasa a depender de `PM` (`ac.md` §1.4, §3), en un solo sentido.

Es el requerimiento que **crea `course_products`** y **enmienda cuatro lecturas ya construidas**: la ofrecibilidad (`RF-AC-008`, `CourseOfferability`), el listado y el detalle de cursos (`RF-AC-009`, `RF-AC-010`), y la instantánea del retiro (`RF-AC-013`). Con un servicio, un curso `ACTIVO` con un módulo ofrecible **se ofrece por primera vez**, aunque no tenga ninguna membresía — que hasta hoy era imposible.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Da la visibilidad |

## 4. Alcance

### 4.1 Incluye

- Añadir la pareja curso–producto, con el producto existente, `BOT` y no retirado, y la pareja nueva.
- Crear `course_products`.
- Ampliar `ProductCatalog` con la lectura del tipo.
- **Enmendar** `RF-AC-008`, `RF-AC-009`, `RF-AC-010` y `RF-AC-013`.
- Devolver el curso en la forma del detalle, con sus servicios resueltos —identificador, código, nombre— y `offerable` recalculado.

### 4.2 No incluye

- **Comprobar quién tiene ese servicio.** Es del aula (`RF-AC-033` a `RF-AC-035`), y es lo que exige de `SP` la interfaz de productos vigentes (`ac.md` §3). Este requerimiento no pregunta por ninguna persona.
- **Exigir que el curso esté activo, ni que el servicio esté a la venta.** La lista se arma antes de publicar las dos cosas.
- **Vender el curso.** El curso no tiene precio; se vende el servicio, en `PM` (`ac.md` §1.4).
- **Dar varios servicios de una vez.** Una pareja por petición, como toda relación.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-020` | Lista explícita de productos `BOT`; existente y no retirado al añadir; sin repetir; se suma a las membresías | `requirements/ac.md` §5.1 |
| `RN-AC-015` | La ofrecibilidad cambia en la siguiente lectura: basta una membresía **o un servicio** | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Lo retirado no recibe visibilidad | `requirements/ac.md` §5.1 |
| `RN-PM-001` | El tipo de un producto no cambia nunca: lo comprobado al añadir sigue siendo cierto | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso | Sí | Cuál | Ruta. Curso **vivo**, en cualquier estado |
| `productId` | Sí | Qué servicio lo abre | Existe en `PM`, **de tipo `BOT`**, **no retirado**; activo o inactivo |

### 6.2 Salida

`201` con el curso en la forma del detalle, `products` con el nuevo —identificador, código, nombre— y `offerable` recalculado.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; curso vivo; producto `BOT` no retirado; pareja inexistente.

**Postcondiciones:** existe la fila `(course_id, product_id)`; `audit_change_log` tiene una fila `CREATE` de `course_products` con el curso como entidad; si el curso estaba `ACTIVO`, con descripciones y con un módulo ofrecible, **desde ahora se ofrece**.

## 8. Flujo principal

1. Llega la petición con el curso en la ruta y el producto en el cuerpo.
2. El sistema valida la forma (`VAL-001`, `VAL-002`).
3. El sistema resuelve el curso **vivo**, bloqueándolo (`EX-001`).
4. El sistema resuelve el producto por la interfaz de `PM` (`EX-002`, `EX-003`).
5. El sistema comprueba que la pareja no existe (`EX-004`).
6. Inserta la fila, registra la creación, y devuelve `201` con el detalle.

El paso 5 tiene su red en la clave primaria compuesta, como `RF-AC-016`.

## 9. Flujos alternativos

### FA-001 — Es el primer servicio de un curso `ACTIVO` y armado, sin membresías

**Comportamiento:** el detalle vuelve con `offerable: true`. El aula lo enseña desde ahora.

### FA-002 — El curso ya tiene membresías

**Comportamiento:** se añade; las dos listas conviven. `offerable` no cambia por esto: ya tenía con qué abrirse.

### FA-003 — El servicio está `INACTIVO` en `PM`

**Comportamiento:** se añade. Nadie lo puede comprar hoy, de modo que el curso no se abre a nadie nuevo por él; a quien ya lo tenga vigente, sí.

## 10. Excepciones

### EX-001 — El curso no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un curso vivo con ese identificador.»*

### EX-002 — El producto no existe o está retirado

**Respuesta del sistema:** `422` — *«El producto indicado no existe o está retirado.»* Un dato del cuerpo que no sirve, como la categoría de `RF-AC-016`.

### EX-003 — El producto no es un servicio

**Respuesta del sistema:** `422` — *«Solo un servicio abre un curso: el producto {código} es un upgrade de membresía.»* **Se distingue de `EX-002` por el código**: el producto existe y quien lo eligió se equivocó de producto, no de identificador, y el mensaje le dice qué eligió. Si lo que quería era abrir el curso a un nivel, eso es `RF-AC-020`.

### EX-004 — El curso ya tiene ese servicio

**Respuesta del sistema:** `409` — *«El servicio {código} ya abre este curso.»* Nombra el producto por su código, como `RF-AC-020` nombra la membresía.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador del curso con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | `productId` presente y con formato válido | El producto es obligatorio. |
| `VAL-003` | Ningún campo desconocido | El cuerpo de la petición contiene campos no admitidos. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-216` | El sistema da la visibilidad con `201` y devuelve el detalle con el servicio en `products` —identificador, código, nombre—; un curso `INACTIVO` la recibe igual, y un servicio `INACTIVO` se añade igual |
| `CA-AC-217` | El sistema rechaza con `409` la pareja repetida, **nombrando el servicio por su código**; con `422` `EX-002` un producto inexistente o retirado; con `422` `EX-003` un upgrade de membresía, **con otro código**; con `404` un curso inexistente o retirado; y con `400` `productId` ausente o mal formado, o un campo de más |
| `CA-AC-218` | El sistema registra una fila `CREATE` de `course_products` en `audit_change_log` con el curso como entidad, el actor y la pareja con el código del producto |
| `CA-AC-219` | **Enmienda de `RF-AC-008` (`RN-AC-015`)**: un curso `ACTIVO`, con las dos descripciones y un módulo activo con lección activa con contenido, **sin membresías**, pasa de `offerable: false` —con el motivo *«El curso no tiene ninguna membresía ni ningún servicio que lo abra.»*— a **`offerable: true`** al recibir su primer servicio; en el detalle, en el listado y en los cursos de su categoría |
| `CA-AC-220` | **Enmienda de `RF-AC-010` y `RF-AC-013`**: el detalle del curso trae `products` en una sentencia más, **incluido el servicio que se retiró después en `PM`**, y la instantánea del retiro del curso lleva `product_ids`, cuyas filas **permanecen** |
| `CA-AC-221` | Dos altas simultáneas de la misma pareja dejan **una** fila y un `409` |
| `CA-AC-222` | Sin `courses:update` responde `403` aunque el actor porte `products:update` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El servicio se retira en `PM` después de añadirlo | La fila permanece y **sigue abriendo el curso** a quien lo tenga vigente (`RN-AC-020`); el detalle lo sigue enseñando, porque es la verdad de la lista |
| El servicio no caduca (`validity_days` nulo) | Quien lo compró lo tiene para siempre, y el curso le queda abierto para siempre. Es lo que el servicio vende |
| Un paquete que incluye el servicio | No se añade el paquete —no es `BOT`—; quien compra el paquete **posee el servicio** y el curso se le abre por él |
| El mismo servicio abre varios cursos | Se admite: una fila por curso. Es la forma natural de «este servicio incluye estos cursos» |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se amplía `ProductView` con el tipo, o se añade una lectura? | **Una lectura**, `ProductCatalog.findKind`: `CM` consume `ProductView` y no necesita el tipo, y la norma de la interfaz desde `findPrice` es **una interfaz por lectura** (su propio Javadoc). |
| 2 | ¿El servicio se resuelve por el puerto o por `JOIN`? | **Por el puerto al escribir, por `JOIN` al leer** (`RF-AC-020` §14.1): que sea `BOT` es una regla y cruza por `ProductCatalog`; leer código y nombre en el detalle es el precedente de `RF-PM-002`. |
| 3 | ¿Tiene permiso propio en el reparto de `RF-SP-060`? | **Sí, cuando llegue el tramo 3**: `V28` sembró `courses:assign-membership` y `courses:revoke-membership`, y este requerimiento necesitará `courses:assign-product` y `courses:revoke-product`, que **no existen**. Hasta el tramo 3 todo `AC` va con `courses:update` (`ac.md` §7), y crear dos códigos que ninguna ruta exige sería sembrar por adelantado. Queda escrito para quien haga el reparto. |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 25-09-2026 | Redacción inicial, por decisión del responsable del proyecto del mismo día (`ac.md` §5.2.8): el curso declara **qué servicios lo abren**, además de qué membresías. Solo `BOT`; existente y no retirado al añadir; `422` distinto para el upgrade; `409` que nombra el servicio. **Amplía `ProductCatalog`** y crea `course_products`; enmienda la ofrecibilidad, el listado, el detalle y el retiro del curso. | Responsable técnico |
