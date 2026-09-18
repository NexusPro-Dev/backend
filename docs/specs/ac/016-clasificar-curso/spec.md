# SPEC — `RF-AC-016` Clasificar un curso en una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-016` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que el curso **se encuentre en un cajón**: ponerlo en una categoría, y en cuantas haga falta.

## 2. Contexto

Es la primera de las tres relaciones del curso (`ac.md` §1.1), y la más simple: **libre y sin repetir** (`RN-AC-010`). Un curso está en cero o más categorías; la pareja no se repite; no se clasifica en una categoría retirada ni un curso retirado. **Un curso sin categoría se ofrece igual**: la categoría es un filtro del catálogo, no una condición, y por eso nada de esto toca el estado ni la ofrecibilidad.

Es el requerimiento que **crea `course_category_items`** y **construye las enmiendas que el bloque 1 y el 2 dejaron declaradas**: `courseCount` de `RF-AC-002` y `RF-AC-003`, los cursos del detalle de la categoría (`RF-AC-003`), los identificadores de la instantánea de `RF-AC-005`, `categories` y el filtro por categoría de `RF-AC-009`, `categories` del detalle de `RF-AC-010` y `category_ids` de la instantánea de `RF-AC-013`. Cada una está declarada en su spec (Art. I.7) y aquí se cierra.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Clasifica el curso |

## 4. Alcance

### 4.1 Incluye

- Añadir la pareja curso–categoría, con las dos vivas.
- Crear `course_category_items`.
- **Enmendar** los seis requerimientos que declararon la clasificación.
- Devolver el curso en la forma del detalle, con sus categorías.

### 4.2 No incluye

- **Un orden del curso dentro de la categoría.** El orden es global (`RN-AC-002`, decisión del 17-09-2026).
- **Clasificar desde la categoría** («ponme estos cursos»). Una sola dirección: desde el curso, que es donde se arma.
- **Clasificar en varias de una vez.** Una pareja por petición, como toda relación del sistema.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-010` | La clasificación es libre y no se repite; ni categoría ni curso retirados; un curso sin categoría se ofrece igual | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Lo retirado no se clasifica | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Curso | Sí | Cuál | Ruta. Curso **vivo**, en cualquier estado |
| `categoryId` | Sí | En qué cajón | Categoría **viva** |

### 6.2 Salida

`201` con el curso en la forma del detalle (`RF-AC-010`), con `categories` incluyendo la nueva.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:update`; curso vivo; categoría viva; pareja inexistente.

**Postcondiciones:** existe la fila `(course_id, category_id)`; `audit_change_log` tiene una fila `CREATE` de `course_category_items` con el curso como entidad; el listado y el detalle de la categoría cuentan el curso.

## 8. Flujo principal

1. Llega la petición con el curso en la ruta y la categoría en el cuerpo.
2. El sistema valida la forma (`VAL-001`, `VAL-002`).
3. El sistema resuelve el curso **vivo**, bloqueándolo (`EX-001`).
4. El sistema resuelve la categoría **viva** (`EX-002`).
5. El sistema comprueba que la pareja no existe (`EX-003`).
6. Inserta la fila, registra la creación, y devuelve `201` con el detalle.

El paso 5 tiene su red en la clave primaria compuesta: la carrera la muerde el motor y el repositorio la traduce al mismo `409`.

## 9. Flujos alternativos

### FA-001 — El curso está `INACTIVO` o no se ofrece

**Comportamiento:** se clasifica igual. La clasificación no depende del estado ni lo cambia.

### FA-002 — El curso ya está en otras categorías

**Comportamiento:** se añade una más. No hay tope.

## 10. Excepciones

### EX-001 — El curso no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un curso vivo con ese identificador.»*

### EX-002 — La categoría no existe o está retirada

**Respuesta del sistema:** `422` — *«La categoría indicada no existe o está retirada.»* Es un dato del cuerpo que no sirve, como la moneda en `PM`; se distingue del `404` del curso, que es la ruta.

### EX-003 — El curso ya está en esa categoría

**Respuesta del sistema:** `409` — *«El curso ya está clasificado en la categoría {nombre}.»* **Nombra la categoría**, como `PM` nombra el upgrade que ya está.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador del curso con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | `categoryId` presente y con formato válido | La categoría es obligatoria. |
| `VAL-003` | Ningún campo desconocido | El cuerpo de la petición contiene campos no admitidos. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-124` | El sistema clasifica el curso con `201` y devuelve el detalle con la categoría en `categories` —identificador, nombre, color, icono—; un curso `INACTIVO` se clasifica igual y su estado no cambia |
| `CA-AC-125` | El sistema rechaza con `409` la pareja repetida, **nombrando la categoría**; con `422` una categoría inexistente o retirada; con `404` un curso inexistente o retirado; y con `400` `categoryId` ausente o mal formado |
| `CA-AC-126` | El sistema registra una fila `CREATE` de `course_category_items` en `audit_change_log` con el curso como entidad, el actor y la pareja en la instantánea |
| `CA-AC-127` | **Enmienda de `RF-AC-002`, `RF-AC-003` y `RF-AC-005`**: `courseCount` cuenta los cursos vivos —un inactivo cuenta, un retirado no—; el detalle de la categoría trae sus cursos vivos en orden con `offerable`, cuesta una sentencia más con cursos, y el retirado no aparece; la instantánea del retiro de la categoría lleva `course_ids` |
| `CA-AC-128` | **Enmienda de `RF-AC-009` y `RF-AC-010`**: cada fila del listado trae `categories` vivas —una retirada no—, en una sentencia por página que no crece con ella; `categoryId` acota de verdad; y el detalle del curso trae `categories` |
| `CA-AC-129` | **Enmienda de `RF-AC-013`**: la instantánea del retiro del curso lleva `category_ids`, y las filas de clasificación **permanecen** tras el retiro |
| `CA-AC-130` | Dos clasificaciones simultáneas de la misma pareja dejan **una** fila y un `409` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| La categoría se retira después | La fila permanece y deja de contar y de enseñarse (`RN-AC-010`); el detalle del curso no la trae |
| Clasificar en una categoría y retirarla en la misma milésima | La clave foránea y el bloqueo del curso no lo ordenan —la categoría no se bloquea—; la fila puede quedar apuntando a una categoría retirada, que es exactamente el estado del caso anterior. Se acepta |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se bloquea la categoría al clasificar? | **No.** Nada de la categoría cambia, y el estado que resultaría de la carrera con su retiro es legítimo (§13) |
| 2 | ¿La auditoría es un `UPDATE` del curso con `categories` antes y después, como decía `RN-AC-010`? | **No: un `CREATE` de la fila con el curso como entidad**, que es el precedente de `RF-PM-023` y lo que el Art. V.13 prevé para las asociaciones (`ASSOCIATION` al quitar). Un `UPDATE` con la lista entera antes y después crecería con cada categoría y diría lo mismo peor. `RN-AC-010` se precisa en `ac.md` v0.6.0 |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. La relación más simple del curso: libre, sin repetir, sin efecto sobre el estado. `422` para la categoría del cuerpo y `409` que la nombra. **Crea la tabla y cierra seis enmiendas declaradas** por los bloques 1 y 2. La auditoría sigue el precedente de `PM` (§14.2). | Responsable técnico |
