# SPEC — `RF-AC-005` Eliminar categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-005` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** — **construida el 17-09-2026** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Retirar un cajón del catálogo que fue un error o que ya no se usa, **con motivo**, sin tocar los cursos que contenía.

## 2. Contexto

Es `RF-PM-022` para categorías y hereda su razonamiento: eliminación **lógica con motivo** (Art. V.13, `RN-AC-018`), el motivo y la instantánea a `audit_deletion_log`, y la distinción entre «no existe» y «ya está retirada». **Lo propio es que no arrastra nada**: la categoría es un filtro del catálogo, y retirar un filtro no puede dejar nada roto. Sus cursos siguen vivos, siguen ofreciéndose —un curso sin categoría se ofrece igual (`RN-AC-010`)— y sus filas de clasificación **se conservan** y dejan de contar. Es la única de las cuatro entidades del módulo cuyo retiro no baja a nadie (`requirements/ac.md` §5.1, `RN-AC-018`).

**Y por eso nunca se rechaza por tener cursos.** Exigir que el cajón esté vacío obligaría a desclasificar uno a uno para poder retirar, y desclasificar es lo que el retiro ya hace en efecto. El motivo es la barrera, como en todo retiro del sistema.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Retira la categoría |

## 4. Alcance

### 4.1 Incluye

- Retirar lógicamente una categoría viva, tenga o no cursos, con motivo.
- Conservar sus filas de clasificación.
- Registrar la baja con la instantánea de la categoría **y la lista de identificadores de los cursos que tenía clasificados**.

### 4.2 No incluye

- **Retirar ni desclasificar los cursos.** Siguen donde están, y se ofrecen igual.
- **Borrar la portada.** La imagen se queda señalada por la categoría retirada, que el detalle sigue devolviendo con su `coverImageUrl`; no es una baja de la imagen sino de la categoría.
- **Revivir** una categoría retirada. Se crea otra; el nombre queda libre.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-010` | Un curso sin categoría se ofrece igual; la clasificación de una retirada deja de contar | `requirements/ac.md` §5.1 |
| `RN-AC-018` | Retiro lógico con motivo y registro; retirar una categoría **no arrastra nada** | `requirements/ac.md` §5.1 |
| Art. V.13 | El motivo es obligatorio y viaja con la instantánea | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál | Ruta. Categoría **viva** |
| `reason` | Sí | Por qué | Con contenido, tras recortar; hasta 500 |

### 6.2 Salida

`204`. Nada que devolver: la categoría retirada se consulta por `RF-AC-003`, con su motivo.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `course-categories:delete`; categoría viva; motivo con contenido.

**Postcondiciones:** `deleted_at` puesto y **nada más de la fila cambia** —`cover_image_id` incluido—; filas de clasificación intactas; cursos intactos; `audit_deletion_log` con `LOGICAL`, el motivo y la instantánea con los identificadores de los cursos.

## 8. Flujo principal

1. Llega la petición con el motivo.
2. El sistema valida el motivo **antes de cualquier consulta** (`VAL-002`).
3. El sistema resuelve la categoría **en cualquier estado**, bloqueándola: si no existe, `EX-001`; si ya está retirada, `EX-002`.
4. El sistema toma la instantánea —categoría e identificadores de sus cursos—, marca `deleted_at`, y registra la baja en la misma transacción.
5. Devuelve `204`.

## 9. Flujos alternativos

### FA-001 — La categoría tiene cursos ofrecidos

**Comportamiento:** se retira igual. Los cursos siguen ofreciéndose; en el aula dejan de aparecer bajo ese cajón y aparecen bajo sus otras categorías, o sin ninguna.

### FA-002 — La categoría tiene portada

**Comportamiento:** se retira igual, y la portada se queda: el detalle de una retirada la sigue devolviendo.

## 10. Excepciones

### EX-001 — La categoría no existe

**Respuesta del sistema:** `404` — *«No existe una categoría con ese identificador.»*

### EX-002 — La categoría ya está retirada

**Respuesta del sistema:** `409` — *«La categoría ya está retirada.»* **Se distingue** del inexistente por lo mismo que en `RF-PM-022`: el catálogo devuelve las retiradas a quien tiene `course-categories:read`, no hay nada que ocultar, y quien retira dos veces merece saber que la primera funcionó.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Motivo presente y con contenido | El motivo de la eliminación es obligatorio. |
| `VAL-003` | Motivo de hasta 500 caracteres | El motivo no puede exceder 500 caracteres. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-028` | El sistema retira la categoría con `204`, y la fila conserva `cover_image_id` y sus **filas de clasificación** |
| `CA-AC-029` | El sistema rechaza con `400` un motivo ausente, vacío, de solo espacios o de más de 500, **sin consultar nada** |
| `CA-AC-030` | El sistema responde `404` a la inexistente y `409` a la **ya retirada**, distinguiéndolas |
| `CA-AC-031` | `audit_deletion_log` tiene la fila `LOGICAL` con el motivo, el actor y la instantánea **con los identificadores de los cursos** que tenía, y `deleted_at` nulo dentro de la instantánea |
| `CA-AC-032` | Sus cursos siguen vivos, con su estado, y **se siguen ofreciendo** en el aula sin ese cajón; la categoría retirada desaparece del listado salvo `includeDeleted` y su detalle trae el motivo |
| `CA-AC-033` | El nombre de la retirada **puede reutilizarse** en un alta; dos retiros simultáneos dejan un `204` y un `409` y **una** fila de auditoría |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Retirar una categoría vacía | Se retira igual: es un error que se corrige retirando |
| Retirar la **única** categoría de un curso | El curso queda sin categoría y **se ofrece igual** (`RN-AC-010`): el catálogo del alumno lo enseña sin cajón |
| Dos retiros simultáneos | El bloqueo los ordena; el segundo recibe `409` |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se borran las filas de clasificación al retirar? | **No.** Son lo que el cajón contenía, y la instantánea las lleva además. Borrarlas dejaría una categoría retirada que dice «tuve cursos» sin decir cuáles |
| 2 | ¿Se rechaza si tiene cursos? | **No.** Es un filtro; retirarlo no rompe nada, y el motivo ya es la barrera (§2) |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 17-09-2026 | Redacción inicial. Hereda `RF-PM-022` entero; lo propio es que **no arrastra nada ni se rechaza por tener cursos**: la categoría es un filtro y sus cursos se ofrecen igual sin ella. Las filas de clasificación permanecen y la instantánea lleva los identificadores de los cursos. | Responsable técnico |
| 0.2.0 | 17-09-2026 | **Construida** (`CourseCategoryDeletionIT`, `CourseCategoryConcurrencyIT`). Una precisión de Art. I.7 al construir: **el motivo largo es `VAL-003` y no `VAL-002`**, porque `DeletionReason` —ya en `shared/audit`— distingue el ausente del largo con dos códigos, y son los mismos que `PM` publica desde `RF-PM-006`. `CA-AC-029` comprueba que el motivo inválido no cuesta ni una sentencia. | Responsable técnico |
