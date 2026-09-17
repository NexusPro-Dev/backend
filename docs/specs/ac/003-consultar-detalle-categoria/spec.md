# SPEC — `RF-AC-003` Consultar el detalle de una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-003` |
| Módulo | `AC` — Academia |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Ver una categoría entera —lo suyo y **qué cursos tiene, en su orden y con si se ofrecen**— para reordenarla, corregirla o decidir retirarla.

## 2. Contexto

Es `RF-PM-003` para categorías, y hereda su razonamiento: se devuelve **también una retirada**, con su motivo leído de la auditoría, porque quien tiene `course-categories:read` ve el catálogo completo y no hay nada que ocultar. **Lo propio es la lista de cursos**: la categoría es un cajón, y abrirlo es ver qué contiene. Cada curso viene con su estado y con `offerable`, que es lo que administración necesita para saber si el cajón que el alumno verá está lleno o vacío — un cajón con cinco cursos inactivos se enseña vacío.

**Hasta que existan los cursos (`RF-AC-008`) y la clasificación (`RF-AC-016`), `courses` viaja vacío y `courseCount` es cero**; y hasta que exista la ofrecibilidad (`RN-AC-015`, bloque 3 de [`requirements/ac.md` §6.1](../../../requirements/ac.md)), `offerable` de cada curso es falso. Los tres requerimientos enmiendan esta lectura (Art. I.7) y así queda declarado en §15.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Consulta una categoría |

## 4. Alcance

### 4.1 Incluye

- Devolver la categoría por identificador, **viva o retirada**, con todos sus campos y `coverImageUrl`.
- **Sus cursos vivos** clasificados en ella, en el orden de los cursos (`RN-AC-002`), cada uno con identificador, título, estado, orden y `offerable`.
- `courseCount`, que es el tamaño de esa lista.
- Si está retirada: `deletedAt` y el **motivo** del retiro.

### 4.2 No incluye

- **Los cursos retirados** que tuvo: su clasificación se conserva y no se enseña (`RN-AC-018`). Se ven en el catálogo de cursos con `includeDeleted`.
- **Los módulos y lecciones** de cada curso: es el detalle del curso (`RF-AC-010`).
- **`offerableReason`** por curso: uno a uno es el detalle del curso.
- **Paginar los cursos**: una categoría tiene decenas de cursos como mucho, y la lista completa es lo que se necesita para reordenar.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-AC-002` | Los cursos salen en su orden global, con desempate por identificador | `requirements/ac.md` §5.1 |
| `RN-AC-004` | `coverImageUrl` presente y nula, también en una retirada | `requirements/ac.md` §5.1 |
| `RN-AC-010` | Cuentan los cursos vivos clasificados en ella | `requirements/ac.md` §5.1 |
| `RN-AC-015` | `offerable` por curso se calcula, no se guarda | `requirements/ac.md` §5.1 |
| `RN-AC-018` | La retirada se devuelve con su motivo | `requirements/ac.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál | Ruta; UUID |

### 6.2 Salida

`200` con: `id`, `name`, `description` (presente y nula), `color`, `icon`, `displayOrder`, `coverImageUrl` (presente y nula), `courseCount`, `courses[]` —cada uno `id`, `title`, `status`, `displayOrder`, `offerable`—, `createdAt`, `updatedAt`, y **solo si está retirada** `deletedAt` y `deletionReason`.

Es **la misma forma** que devuelve el alta (`RF-AC-001`) y la corrección (`RF-AC-004`), para que el frontend tenga una sola pantalla de categoría.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `course-categories:read`. **Postcondiciones:** ninguna.

## 8. Flujo principal

1. Llega el identificador.
2. El sistema valida su forma (`VAL-001`).
3. El sistema resuelve la categoría **en cualquier estado** con sus cursos vivos en orden: si no existe, `EX-001`.
4. Si está retirada, lee el motivo de la auditoría de eliminación.
5. Devuelve `200`.

## 9. Flujos alternativos

### FA-001 — La categoría está retirada

**Comportamiento:** se devuelve con `deletedAt` y `deletionReason`, y **con sus cursos vivos igual**: siguen clasificados en ella, y verlos es parte de entender qué se retiró. En el aula ya no aparecen bajo ese cajón.

### FA-002 — La categoría no tiene cursos

**Comportamiento:** `courses` vacío y `courseCount` cero. No es un error.

## 10. Excepciones

### EX-001 — La categoría no existe

**Respuesta del sistema:** `404` — *«No existe una categoría con ese identificador.»*

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-AC-016` | El sistema devuelve la categoría con sus campos, `coverImageUrl` presente y nula cuando no tiene, y sus **cursos vivos** en el orden global con desempate por identificador, cada uno con `status` y `offerable` |
| `CA-AC-017` | Un curso **retirado** que estuvo clasificado **no aparece** y no cuenta; uno **inactivo** aparece con su estado y cuenta |
| `CA-AC-018` | La categoría **retirada** se devuelve con `deletedAt` y `deletionReason`, y con sus cursos vivos; la **inexistente** responde `404`; un identificador mal formado, `400` |
| `CA-AC-019` | La lectura cuesta **dos** sentencias —la categoría con sus cursos, y nada más— y **una más** con el motivo de retiro; sin cursos cuesta **una** |
| `CA-AC-020` | Sin `course-categories:read` responde `403` aunque el actor porte `courses:read` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Dos cursos con el mismo orden | Desempata el identificador: el más antiguo primero |
| Categoría retirada cuyo registro de eliminación no existe | `deletionReason` nulo y presente, sin `500`: el detalle no depende de la auditoría para responder, como en `RF-PM-003` |
| Curso clasificado y **ofrecido** en otra categoría | Aparece aquí igual, con `offerable` verdadero: la ofrecibilidad es del curso, no de la pareja |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se traen los cursos retirados con una bandera? | **No.** La clasificación de un retirado se conserva por si hay que entender la historia, y para eso está el catálogo de cursos con `includeDeleted`. Aquí se enseña lo que el cajón contiene hoy |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 17-09-2026 | Redacción inicial. Hereda `RF-PM-003` —retirada con motivo, `404` al inexistente— y **trae los cursos vivos en orden con su `offerable`**, porque abrir un cajón es ver qué contiene y si se ofrece. **Declara tres enmiendas futuras** (Art. I.7): `RF-AC-008` y `RF-AC-016` llenan `courses`, y el bloque 3 de `ac.md` §6.1 pone `offerable` real; hasta entonces la lista es vacía y la bandera es falsa. | Responsable técnico |
