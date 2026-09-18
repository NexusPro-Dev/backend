# PLAN — `RF-AC-026` Subir o reemplazar la portada de un módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-026` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`CoverUploader` con `CourseModule`.** El tercer cliente del colaborador de `RF-AC-014`: el módulo implementa `HasCover`, el servicio lo resuelve por `(courseId, moduleId)` con `findAliveByIdInCourseForUpdate` y le pasa a `CoverUploader` su repositorio, su lector y `course_modules` como entidad de auditoría.

## 2. Cambios de esquema

**Ninguno.** Las restricciones de `course_modules.cover_image_id` llegaron con `V28`.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `CourseModule.asignarPortada`, `HasCover` | `AC` |
| `domain/service` | `UploadCourseModuleCoverService` sobre `CoverUploader` | `AC` |
| `interfaces` | `CourseModuleController` — `PUT /api/v1/courses/{courseId}/modules/{moduleId}/cover` | `AC` |

## 4. Contrato de API

`PUT /api/v1/courses/{courseId}/modules/{moduleId}/cover` — `courses:update` — `multipart/form-data`. `200` con `CourseModuleDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-004`, `EX-002` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`UPDATE` de `course_modules` con `cover_image_id` antes y después.

## 7. Transaccionalidad

La de `RF-AC-006` §7 sobre `course_modules`.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Bloquear también el curso** | Nada del curso cambia; el orden padre → hijo solo hace falta a quien toca los dos |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| — | Ninguno propio: el colaborador ya está probado por dos clientes | |

## 11. Estrategia de prueba

- **Integración de API** (`CourseModuleCoverIT`): los cuatro criterios.
