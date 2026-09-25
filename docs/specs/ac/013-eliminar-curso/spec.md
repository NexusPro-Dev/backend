# SPEC — `RF-AC-013` Eliminar curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-013` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Retirar un curso que fue un error o que ya no se enseña, **con motivo**, llevándose consigo lo que cuelga de él y sin que desaparezca lo que decía contener.

## 2. Contexto

Es `RF-AC-005` con la mitad que la categoría no tenía: **el arrastre**. `RN-AC-018` dice que retirar un curso retira sus módulos y lecciones vivos, con el mismo motivo y en la misma transacción, **con un registro de eliminación por fila**: un módulo o una lección no existen fuera de su curso (`RN-AC-019`), y dejarlos vivos bajo un curso retirado sería una entidad viva colgando de nada. Sus clasificaciones, recomendaciones y visibilidades **se conservan y dejan de verse**; **donde figure como recomendado, deja de enseñarse** en el aula (`RN-AC-011`).

**Hasta que existan módulos y lecciones no hay nada que arrastrar**, y el arrastre lo construye entero el bloque 3 sobre la forma que este requerimiento fija (`tasks.md`, bloqueo 1). Hereda de `RF-PM-022` la distinción entre «no existe» y «ya está retirado», el motivo antes que nada, y que el estado no se toca al retirar: el registro dice si estaba publicado.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Retira el curso |

## 4. Alcance

### 4.1 Incluye

- Retirar lógicamente un curso vivo, en cualquier estado, con motivo.
- **Arrastrar** sus módulos y lecciones vivos, con el mismo motivo y un registro cada uno.
- Conservar sus clasificaciones, recomendaciones y visibilidades.
- Registrar la baja con la instantánea del curso **y los identificadores** de sus categorías, membresías, cursos recomendados y módulos.

### 4.2 No incluye

- **Retirar las categorías ni los cursos recomendados.** El curso no manda sobre ellos.
- **Borrar la portada** ni las de sus módulos: las filas retiradas las siguen señalando.
- **Revivir** un curso retirado. Se crea otro; el título queda libre.
- **Retirar módulos ya retirados** de nuevo: el arrastre alcanza a los vivos.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-011` | Un curso retirado deja de recomendarse donde figure | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Retiro lógico con motivo y registro por fila; **retirar un curso arrastra módulos y lecciones**; lo retirado no se corrige | `requirements/ac.md` §5.1 |
| `RN-AC-019` | Un módulo no existe fuera de su curso; por eso se arrastra | `requirements/ac.md` §5.1 |
| Art. V.13 | El motivo es obligatorio y viaja con la instantánea | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál | Ruta. Curso **vivo** |
| `reason` | Sí | Por qué | Con contenido tras recortar; hasta 500 |

### 6.2 Salida

`204`. El curso retirado se consulta por `RF-AC-010`, con su motivo y su árbol marcado.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `courses:delete`; curso vivo; motivo con contenido.

**Postcondiciones:** `deleted_at` puesto en el curso y **nada más de su fila cambia** —`status` incluido—; `deleted_at` puesto en cada módulo y lección que estaba vivo; relaciones intactas; `audit_deletion_log` con una fila `LOGICAL` para el curso —motivo e instantánea con los identificadores de sus relaciones y módulos— y **una por cada módulo y lección arrastrados**, con el mismo motivo y su propia instantánea.

## 8. Flujo principal

1. Llega la petición con el motivo.
2. El sistema valida el motivo **antes de cualquier consulta** (`VAL-002`, `VAL-003`).
3. El sistema resuelve el curso **en cualquier estado**, bloqueándolo: si no existe, `EX-001`; si ya está retirado, `EX-002`.
4. El sistema toma la instantánea del curso —con los identificadores de categorías, membresías, recomendados y módulos—, marca `deleted_at`, y registra la baja.
5. El sistema resuelve los módulos vivos y sus lecciones vivas, los marca con el mismo instante, y registra la baja de cada uno con el mismo motivo.
6. Todo en la misma transacción. Devuelve `204`.

## 9. Flujos alternativos

### FA-001 — El curso está `ACTIVO` y se ofrece

**Comportamiento:** se retira igual. Sale del aula en el acto, junto con su árbol; sus categorías y las membresías no cambian; **quien lo tenía como recomendado deja de enseñarlo**.

### FA-002 — El curso no tiene módulos, o los que tenía ya estaban retirados

**Comportamiento:** se retira sin arrastrar nada: un registro de eliminación, el del curso.

## 10. Excepciones

### EX-001 — El curso no existe

**Respuesta del sistema:** `404` — *«No existe un curso con ese identificador.»*

### EX-002 — El curso ya está retirado

**Respuesta del sistema:** `409` — *«El curso ya está retirado.»* Se distingue del inexistente por lo mismo que en `RF-AC-005`.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Motivo presente y con contenido | El motivo de la eliminación es obligatorio. |
| `VAL-003` | Motivo de hasta 500 caracteres | El motivo no puede exceder 500 caracteres. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-070` | El sistema retira el curso con `204`; la fila conserva `status`, `instructor_id` y `cover_image_id`, y sus clasificaciones, recomendaciones y visibilidades **permanecen** |
| `CA-AC-071` | El sistema rechaza con `400` un motivo ausente, vacío, de solo espacios o de más de 500, **sin consultar nada** |
| `CA-AC-072` | El sistema responde `404` al inexistente y `409` al **ya retirado**, distinguiéndolos |
| `CA-AC-073` | `audit_deletion_log` tiene la fila `LOGICAL` del curso con el motivo, el actor y la instantánea **con los identificadores** de categorías, membresías, recomendados y módulos, y `deleted_at` nulo dentro |
| `CA-AC-074` | **Sus módulos y lecciones vivos quedan retirados** con el mismo instante, con **una fila de auditoría cada uno** y el mismo motivo; los que ya estaban retirados no se tocan — **construido por el bloque 3** |
| `CA-AC-075` | El curso retirado desaparece del listado salvo `includeDeleted`, su detalle trae el motivo y el árbol marcado, y **deja de aparecer como recomendado** en el aula; su título **puede reutilizarse**; dos retiros simultáneos dejan un `204`, un `409` y **una** fila del curso |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Retirar un curso con módulos retirados y lecciones vivas dentro de ellos | No puede ocurrir: retirar un módulo arrastra sus lecciones (`RF-AC-025`). El arrastre mira módulos vivos y sus lecciones vivas |
| Retirar mientras otro registra una lección en él | El bloqueo del curso los ordena: si el retiro gana, el alta de la lección recibe `404` por curso retirado |
| Dos retiros simultáneos | El bloqueo los ordena; el segundo recibe `409` |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Un registro de eliminación por fila arrastrada, o solo el del curso con el árbol dentro? | **Uno por fila.** El Art. V.13 pide registro por entidad retirada, y una lección retirada tiene que poder encontrarse en la auditoría por su propio identificador, no buscando dentro de la instantánea de su curso. La instantánea del curso lleva **además** los identificadores de sus módulos |
| 2 | ¿Se borran las recomendaciones que lo tenían de previo? | **No.** Se conservan y el aula no las enseña (`RN-AC-011`); administración las ve en el detalle del otro curso con `offerable: false`, y decide si las retira |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 18-09-2026 | Redacción inicial. Hereda `RF-AC-005` y le añade **el arrastre de módulos y lecciones con un registro por fila**, que fija la forma y que el bloque 3 construye. Las relaciones se conservan; el recomendado retirado deja de enseñarse en el aula. | Responsable técnico |
| 0.2.0 | 18-09-2026 | **Construida** (`CourseDeletionIT` (6), la carrera en `CourseConcurrencyIT`) **con `CA-AC-074` bloqueado**: `CourseTreeRetirement` existe y no recorre nada hasta `RF-AC-022`; la instantánea lleva las cuatro listas de identificadores, hoy vacías. La parte del aula de `CA-AC-075` queda para `RF-AC-034`. | Responsable técnico |
| 0.3.0 | 25-09-2026 | **Enmienda de `RF-AC-016`, construida** (Art. I.7): La instantánea del retiro lleva los `category_ids` reales, y las filas de clasificación permanecen. Probado en `CourseClassificationIT` (`CA-AC-129`). | Responsable técnico |
