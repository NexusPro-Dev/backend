# PLAN — `RF-AC-017` Desclasificar un curso de una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-017` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`DissociatePackageProductService` con otra fila.** Curso bloqueado, la fila por su clave compuesta, `DELETE`, `DeletionEvent` `ASSOCIATION` con motivo nulo, relectura del detalle. **`DELETE` sin cuerpo y `200` con el curso**, como `RF-PM-025`: no hay motivo que transportar, y quien acaba de quitar quiere ver el resultado.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `CourseCategoryItemRepository.find` y `delete` (de `RF-AC-016`) | `AC` |
| `domain/service` | `DeclassifyCourseService` | `AC` |
| `interfaces` | `CourseController` — `DELETE /api/v1/courses/{courseId}/categories/{categoryId}` | `AC` |

## 4. Contrato de API

`DELETE /api/v1/courses/{courseId}/categories/{categoryId}` — `courses:update`. Sin cuerpo → `200` con `CourseDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001`, `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`DeletionEvent` `ASSOCIATION` de `course_category_items`, `reason` nulo —`ck_deletion_reason` lo admite en `ASSOCIATION`—, `entity_id` del curso, instantánea `{ course_id, category_id, category_name }`.

## 7. Transaccionalidad

`@Transactional`. El curso con `FOR UPDATE`, la fila, el `DELETE`, la auditoría, la relectura.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`POST …/deletion` con motivo** | No hay motivo que dar a una asociación; el Art. V.13 tiene `ASSOCIATION` para esto |
| **`204` sin cuerpo** | `RF-PM-025` devuelve el padre, y el frontend lo necesita para repintar |
| **Rechazar si la categoría está retirada** | Sería impedir limpiar lo que el retiro dejó |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Dos peticiones simultáneas borran la misma fila** y las dos auditan | El bloqueo del curso las ordena; la segunda no encuentra la fila (`CA-AC-134`) |

## 11. Estrategia de prueba

- **Integración de API** (`CourseClassificationIT`): los cuatro criterios, en la misma suite que el alta.
- **Concurrencia**: dos retiros de la pareja.
