# PLAN — `RF-AC-027` Quitar la portada de un módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-027` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`CoverRemover` con `CourseModule`.** El tercer cliente del colaborador de `RF-AC-015`; el módulo se resuelve por `(courseId, moduleId)`.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `CourseModule.quitarPortada` | `AC` |
| `domain/service` | `RemoveCourseModuleCoverService` sobre `CoverRemover` | `AC` |
| `interfaces` | `CourseModuleController` — `DELETE /api/v1/courses/{courseId}/modules/{moduleId}/cover` | `AC` |

## 4. Contrato de API

`DELETE /api/v1/courses/{courseId}/modules/{moduleId}/cover` — `courses:update`. `200` con `CourseModuleDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`UPDATE` de `course_modules` con `cover_image_id`, solo si había.

## 7. Transaccionalidad

La de `RF-AC-007` §7 sobre `course_modules`.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| — | Ninguna propia | |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| — | Ninguno propio | |

## 11. Estrategia de prueba

- **Integración de API** (`CourseModuleCoverIT`): los tres criterios.
