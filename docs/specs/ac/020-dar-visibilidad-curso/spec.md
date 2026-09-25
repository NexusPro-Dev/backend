# SPEC — `RF-AC-020` Dar visibilidad de un curso a una membresía

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-020` |
| Módulo | `AC` — Academia |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 25-09-2026 |

---

## 1. Objetivo

Que **un nivel abra el curso**: añadir una membresía a la lista de las que lo abren.

## 2. Contexto

Es la relación que decide **quién estudia** (`RN-AC-012`), y la decisión que la define se tomó el 17-09-2026 (`ac.md` §5.2.2): **una lista explícita y no un nivel mínimo**. Un curso de `ORO` no lo abre `PLATINO` salvo que `PLATINO` esté en su lista, y quien quiera «este nivel y los superiores» los añade uno a uno. **Sin lista, el curso no se ofrece** —ni entero ni sus lecciones abiertas— (`RN-AC-015`): es el cuarto motivo de la ofrecibilidad desde el 18-09-2026 —el tercero es «sin descripción»—, y este requerimiento es el que lo cierra. Con una membresía, un curso `ACTIVO` con un módulo ofrecible **se ofrece por primera vez**.

La membresía es de `SP`, y se comprueba por la interfaz que `SP` publica desde el 27-08-2026 (`MembershipCatalog`, D-25). **No exige que el curso esté activo**: la lista se arma antes de publicar, como las categorías. Es el requerimiento que **crea `course_memberships`** y construye las enmiendas que declararon la visibilidad: `memberships` del detalle (`RF-AC-010`), `membership_ids` de la instantánea (`RF-AC-013`) y la cuenta de membresías de `CourseOfferability` (`RF-AC-008`).

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Da la visibilidad |

## 4. Alcance

### 4.1 Incluye

- Añadir la pareja curso–membresía, con la membresía existente en `SP` y la pareja nueva.
- Crear `course_memberships`.
- **Enmendar** `RF-AC-008` (la cuenta de `CourseOfferability`), `RF-AC-010` y `RF-AC-013`.
- Devolver el curso en la forma del detalle, con sus membresías resueltas —código, nombre, color— y `offerable` recalculado.

### 4.2 No incluye

- **Abrir los niveles superiores en cascada.** Es una lista (`ac.md` §5.2.2).
- **Comprobar quién tiene esa membresía.** Es del aula (`RF-AC-033`).
- **Exigir el estado activo.**

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-012` | Lista explícita; membresía existente; sin repetir; sin lista no se ofrece | `requirements/ac.md` §5.1 |
| `RN-AC-015` | La ofrecibilidad cambia en la siguiente lectura | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Lo retirado no recibe visibilidad | `requirements/ac.md` §5.1 |
| `RN-SP-008` | La membresía es inmutable y no se retira: la fila no tiene otro lado que muera | `requirements/sp.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso | Sí | Cuál | Ruta. Curso **vivo**, en cualquier estado |
| `membershipId` | Sí | Qué nivel lo abre | Existe en `SP` (`MembershipCatalog`) |

### 6.2 Salida

`201` con el curso en la forma del detalle, `memberships` con la nueva —identificador, código, nombre, color— y `offerable` recalculado.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; curso vivo; membresía existente; pareja inexistente.

**Postcondiciones:** existe la fila `(course_id, membership_id)`; `audit_change_log` tiene una fila `CREATE` de `course_memberships` con el curso como entidad; si el curso estaba `ACTIVO` con un módulo ofrecible, **desde ahora se ofrece**.

## 8. Flujo principal

1. Llega la petición con el curso en la ruta y la membresía en el cuerpo.
2. El sistema valida la forma (`VAL-001`, `VAL-002`).
3. El sistema resuelve el curso **vivo**, bloqueándolo (`EX-001`).
4. El sistema resuelve la membresía por la interfaz de `SP` (`EX-002`).
5. El sistema comprueba que la pareja no existe (`EX-003`).
6. Inserta la fila, registra la creación, y devuelve `201` con el detalle.

## 9. Flujos alternativos

### FA-001 — Es la primera membresía de un curso `ACTIVO` y armado

**Comportamiento:** el detalle vuelve con `offerable: true`. El aula lo enseña desde ahora.

### FA-002 — Es la primera membresía de un curso `INACTIVO`

**Comportamiento:** se añade; `offerable: false` por «inactivo», que va antes en el orden.

## 10. Excepciones

### EX-001 — El curso no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un curso vivo con ese identificador.»*

### EX-002 — La membresía no existe

**Respuesta del sistema:** `422` — *«La membresía indicada no existe.»* Como la moneda del producto (`RN-PM-008`): un dato de `SP` que no sirve.

### EX-003 — El curso ya tiene esa membresía

**Respuesta del sistema:** `409` — *«La membresía {código} ya abre este curso.»* Nombra la membresía por su código.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador del curso con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | `membershipId` presente y con formato válido | La membresía es obligatoria. |
| `VAL-003` | Ningún campo desconocido | El cuerpo de la petición contiene campos no admitidos. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-135` | El sistema da la visibilidad con `201` y devuelve el detalle con la membresía en `memberships` —identificador, código, nombre, color—; un curso `INACTIVO` la recibe igual |
| `CA-AC-136` | El sistema rechaza con `409` la pareja repetida, **nombrando la membresía por su código**; con `422` una membresía inexistente; con `404` un curso inexistente o retirado; y con `400` `membershipId` ausente o mal formado |
| `CA-AC-137` | El sistema registra una fila `CREATE` de `course_memberships` en `audit_change_log` con el curso como entidad, el actor y la pareja con el código de la membresía |
| `CA-AC-138` | **Cierra `RN-AC-015`**: un curso `ACTIVO` con un módulo activo con lección activa pasa de `offerable: false` «sin membresías» a **`offerable: true`** al recibir su primera membresía, y **la lista es explícita**: un curso con `ORO` no se abre a `PLATINO` |
| `CA-AC-139` | **Enmienda de `RF-AC-010` y `RF-AC-013`**: el detalle del curso trae `memberships` resueltas en una sentencia más, y la instantánea del retiro lleva `membership_ids` |
| `CA-AC-140` | Dos altas simultáneas de la misma pareja dejan **una** fila y un `409` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Dar la membresía de nivel más alto de la cadena | Se admite; no abre a nadie más que a quien la tenga vigente |
| Dar la membresía `BECA` (el suelo) | Se admite: abre el curso a todo el que tenga nivel, que desde `RN-SP-018` es todo el mundo |
| La membresía existe pero nadie la tiene | Se admite; el curso se ofrece a nadie hoy, y el detalle no lo distingue |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se comprueba la membresía por el puerto o por `JOIN`? | **Por el puerto al escribir, por `JOIN` al leer**: la misma regla del instructor (`RF-AC-008` §3). Dar la membresía es una regla —tiene que existir— y cruza por `MembershipCatalog`; leer sus cuatro columnas en el detalle es el precedente de `RF-PM-002` |
| 2 | ¿La clave foránea a `memberships` se declara? | **Sí** (`ac.md` §8.5), por lo mismo que `products.target_membership_id`: la frontera es la del código |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. La lista explícita de `ac.md` §5.2.2: membresía por `MembershipCatalog`, `422` si no existe, `409` que la nombra. **Cierra `RN-AC-015`** —con una membresía, un curso armado se ofrece por primera vez— y construye las enmiendas de `RF-AC-008`, `RF-AC-010` y `RF-AC-013`. | Responsable técnico |
| 0.2.0 | 18-09-2026 | **Enmienda de Art. I.7 (18-09-2026)**: `RN-AC-015` gana dos motivos por decisión del responsable del proyecto —«sin descripción» en el curso y «sin contenido» en la lección—. «Sin membresías» pasa de tercer a **cuarto** motivo; nada más cambia. | Responsable técnico |
| 0.3.0 | 25-09-2026 | **Enmienda declarada por `RF-AC-037`** (Art. I.7), sin cambio de comportamiento propio: las membresías **ya no son la única llave** —un servicio `BOT` también abre el curso (`RN-AC-020`)—, y el cuarto motivo de la ofrecibilidad pasa a decir *«El curso no tiene ninguna membresía ni ningún servicio que lo abra.»*. `CA-AC-138` se lee con ese texto y con un curso **sin servicios**. Si `RF-AC-037` se construye antes, la cuenta de membresías llega a un `CourseOfferability` que ya suma las dos. | Responsable técnico |
| 0.4.0 | 25-09-2026 | **Aprobada** por el responsable del proyecto al pedir su construcción, con dos enmiendas del mismo día: la de `RF-AC-037` (0.3.0) y **la de `RF-AC-008`** —el alta admite `membershipIds` y deja las filas con esta misma escritura—. `CA-AC-138` se prueba con un curso **sin servicios**. | Responsable técnico |
