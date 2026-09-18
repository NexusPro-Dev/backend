# PLAN — `RF-AC-031` Eliminar lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-031` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`DeleteCourseCategoryService` con otra entidad y la instantánea completa.** Cinco pasos, sin arrastre. `Lesson.instantaneaCompleta()` —con el contenido— es distinta de `instantanea()` —con su longitud—, y solo el retiro la usa.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `DeleteLessonRequest` — `reason` | `AC` |
| `domain/models` | `Lesson.delete(ahora)`, `estaRetirada()`, `instantaneaCompleta()` | `AC` |
| `domain/repository` | `LessonRepository.findByIdInModuleForUpdate(courseId, moduleId, lessonId)` —cualquier estado— | `AC` |
| `domain/service` | `DeleteLessonService` | `AC` |
| `interfaces` | `LessonController` — `POST …/lessons/{lessonId}/deletion` | `AC` |

## 4. Contrato de API

`POST /api/v1/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}/deletion` — `courses:update`. `{ "reason": "…" }` → `204`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-003` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |
| `409` | `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`DeletionEvent` `LOGICAL` de `lessons` con el motivo y la instantánea **completa**.

## 7. Transaccionalidad

`@Transactional`. La lección con `FOR UPDATE` en cualquier estado, el `UPDATE`, la baja. **Tres sentencias.**

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **La longitud en lugar del contenido en la instantánea** | `spec.md` §14.1 |
| **Desactivar el módulo si era la última activa** | `RN-AC-009` |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Alguien usa `instantanea()` en el retiro** y el contenido se pierde de la auditoría | `CA-AC-121` comprueba el texto entero en el registro |

## 11. Estrategia de prueba

- **Unitaria**: `Lesson.delete`; `instantaneaCompleta` con el contenido y `instantanea` con la longitud.
- **Integración de API** (`LessonDeletionIT`): los cinco criterios; **la que define el requerimiento es `CA-AC-121`**.
