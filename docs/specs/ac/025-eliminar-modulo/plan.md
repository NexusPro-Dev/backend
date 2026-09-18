# PLAN — `RF-AC-025` Eliminar módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-025` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`DeleteCourseService` un nivel abajo, reutilizando `CourseTreeRetirement`.** El colaborador nació en `RF-AC-013` con la firma que este necesita —«retira estos módulos con sus lecciones, con este motivo y este instante»— y aquí se le pasa un solo módulo. **Una sola forma de arrastrar**, y la prueba de que se reutiliza es que este plan no describe el arrastre.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `DeleteCourseModuleRequest` — `reason` | `AC` |
| `domain/models` | `CourseModule.delete(ahora)`, `estaRetirado()`, `instantanea(lessonIds)` | `AC` |
| `domain/repository` | `CourseModuleRepository.findByIdInCourseForUpdate(courseId, moduleId)` —cualquier estado— | `AC` |
| `domain/service` | `DeleteCourseModuleService`, sobre `CourseTreeRetirement` | `AC` |
| `interfaces` | `CourseModuleController` — `POST …/modules/{moduleId}/deletion` | `AC` |

## 4. Contrato de API

`POST /api/v1/courses/{courseId}/modules/{moduleId}/deletion` — `courses:update`. `{ "reason": "…" }` → `204`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-003` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |
| `409` | `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")` (`spec.md` §14.1).

## 6. Auditoría

`DeletionEvent` `LOGICAL` de `course_modules` con el motivo y la instantánea —módulo y `lesson_ids`—, y uno por lección arrastrada.

## 7. Transaccionalidad

`@Transactional`. El módulo del curso con `FOR UPDATE` en cualquier estado; sus lecciones vivas con `FOR UPDATE`; el `UPDATE` y la baja del módulo; un `UPDATE` de lecciones y una baja por fila.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`courses:delete`** | `spec.md` §14.1 |
| **Reescribir el arrastre aquí** | `CourseTreeRetirement` existe para esto (`RF-AC-013` §10.3) |
| **Desactivar el curso si era el último módulo activo** | `RN-AC-009` |
| **Bloquear también el curso** | Nada del curso cambia; y bloquearlo aquí invertiría el orden padre → hijo que `RF-AC-013` sigue, que es el interbloqueo clásico |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Carrera con el retiro del curso** | Los dos bloquean el módulo; el segundo lo ve retirado. `CA-AC-118` cubre el retiro doble del módulo; el cruce con el del curso, `CourseConcurrencyIT` |

## 11. Estrategia de prueba

- **Integración de API** (`CourseModuleDeletionIT`): los cinco criterios; **la que define el requerimiento es `CA-AC-114`**, el arrastre con una fila por lección.
- **Concurrencia**: dos retiros del módulo; retiro del módulo contra retiro del curso.
