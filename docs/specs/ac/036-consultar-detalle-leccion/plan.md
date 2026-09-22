# PLAN — `RF-AC-036` Consultar el detalle de una lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-036` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`LessonDetailReader` gana la rama de la retirada, y este requerimiento le pone la ruta.** El lector existe desde `RF-AC-028` para las escrituras, que solo devuelven vivas; hoy aprende a leer **en cualquier estado** con la pertenencia en la sentencia —`l.id = :lessonId AND l.module_id = :moduleId AND m.course_id = :courseId`— y a pedir el motivo a `DeletionReasonReader` si hay `deleted_at`, como `CourseCategoryDetailReader` hace desde el bloque 1. `LessonResponse` gana `deletedAt` y `deletionReason` con `NON_NULL`: las escrituras no cambian ni un byte de lo que devuelven.

**Sin objetos de ofrecibilidad y sin puerto**: administración lee la fila.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `LessonQueryRepository`: **`findDetail(courseId, moduleId, lessonId)`** en cualquier estado, con la pertenencia en el `WHERE`; el `findDetail(lessonId)` de las escrituras se apoya en él o convive | `AC` |
| `domain/service` | `LessonDetailReader`, enmendado: la retirada con `DeletionReasonReader` (`ENTIDAD = "lessons"`); **`GetLessonService`** | `AC` |
| `application` | `LessonResponse`, enmendada: `deletedAt` y `deletionReason` `NON_NULL` | `AC` |
| `interfaces` | `LessonController` — `GET /api/v1/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}` | `AC` |
| `shared/audit` | `DeletionReasonReader`, sin cambio | `shared` |

## 4. Contrato de API

`GET /api/v1/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}` — `courses:read`. `200` con `LessonResponse` (`RF-AC-028` §4), más `deletedAt` y `deletionReason` si está retirada.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `courses:read` |
| `404` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:read')")`. Es la primera ruta de `LessonController` con `courses:read`: las escrituras van con `courses:update`.

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Una** sentencia; **una más** con motivo (`CA-AC-212`).

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **El contenido en el detalle del curso** | `spec.md` §14.1 |
| **Un lector nuevo para no tocar el de las escrituras** | Dos formas de leer una lección; la retirada es una rama, no otro lector |
| **Solo vivas, como las escrituras** | `spec.md` §14.2 |
| **Ruta plana `/lessons/{id}`** | El bloque 3 anidó todo lo del curso y la ruta afirma la pertenencia; la plana es del aula, que tiene otra forma |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **`deletedAt` viaja nulo en las escrituras** y cambia su JSON | `NON_NULL`; `CA-AC-209` compara con el alta campo a campo |
| 2 | **La pertenencia se comprueba en Java después de leer por identificador** y una lección ajena se lee antes de rechazarse | Los tres identificadores en el `WHERE`; `CA-AC-211` |

## 11. Estrategia de prueba

- **Integración de API** (`LessonDetailIT`): los cinco criterios; **la que define el requerimiento es `CA-AC-210`**, la arrastrada con el motivo del retiro del curso.
- **De sentencias**: `CA-AC-212`, viva y retirada.
- **De forma**: `CA-AC-209`, el JSON del alta y del detalle campo a campo.
