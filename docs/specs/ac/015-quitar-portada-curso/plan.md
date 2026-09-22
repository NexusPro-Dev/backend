# PLAN — `RF-AC-015` Quitar la portada de un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-015` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`RemoveCourseCategoryCoverService` con `Course`, sobre un `CoverRemover` extraído**, hermano de `CoverUploader` (`RF-AC-014`): si no había, nada; si había, vaciar, volcar, borrar, auditar. `HasCover` gana `quitarPortada(ahora)`.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `Course.quitarPortada(ahora)`; `HasCover.quitarPortada` | `AC` |
| `domain/service` | **`CoverRemover`**; `RemoveCourseCoverService`; `RemoveCourseCategoryCoverService` refactorizado para usarlo | `AC` |
| `interfaces` | `CourseController` — `DELETE /api/v1/courses/{id}/cover` | `AC` |

## 4. Contrato de API

`DELETE /api/v1/courses/{id}/cover` — `courses:update`. `200` con `CourseDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`UPDATE` de `courses` con `cover_image_id`, solo si había.

## 7. Transaccionalidad

La de `RF-AC-007` §7 sobre `courses`.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Tres servicios copiados** | Como en `RF-AC-014` |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La refactorización cambia `RF-AC-007`** | `CourseCategoryCoverIT` en verde antes de seguir |

## 11. Estrategia de prueba

- **Unitaria**: `Course.quitarPortada`; `CoverRemover` con dobles.
- **Integración de API** (`CourseCoverIT`): los tres criterios.
