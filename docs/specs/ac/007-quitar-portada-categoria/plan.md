# PLAN — `RF-AC-007` Quitar la portada de una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-007` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`RemovePackageCoverService` con otra entidad.** `CourseCategory.quitarPortada(ahora)` devuelve `CambioDePortada.ninguno()` si no había —sin `updatedAt`, sin auditoría— o el diff con la anterior; el caso de uso vuelca, borra y audita solo en el segundo caso. Nada que decidir.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `CourseCategory.quitarPortada(ahora)` | `AC` |
| `domain/service` | `RemoveCourseCategoryCoverService` | `AC` |
| `interfaces` | `CourseCategoryController` — `DELETE /api/v1/course-categories/{id}/cover` | `AC` |

## 4. Contrato de API

`DELETE /api/v1/course-categories/{id}/cover` — `course-categories:update`. Sin cuerpo → `200` con `CourseCategoryDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `course-categories:update` |
| `404` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('course-categories:update')")`.

## 6. Auditoría

`UPDATE` de `course_categories` con `cover_image_id` antes y después, solo si había.

## 7. Transaccionalidad

`@Transactional`. La categoría con `FOR UPDATE`; si había, `UPDATE` con volcado, `DELETE` de la imagen, auditoría; relectura.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`204`** | `spec.md` §14.1 |
| **`404` sin portada** | «Quítala» sobre nada ya consiguió lo que quería; el precedente de `PM` |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Se borra la imagen antes de volcar** | El orden está escrito; `CA-AC-168` |

## 11. Estrategia de prueba

- **Unitaria**: `quitarPortada` con y sin anterior.
- **Integración de API** (`CourseCategoryCoverIT`): los cuatro criterios, en la misma suite que la subida.
