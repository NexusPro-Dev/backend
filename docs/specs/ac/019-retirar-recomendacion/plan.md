# PLAN — `RF-AC-019` Retirar una recomendación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-019` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`DeclassifyCourseService` con otra fila.** Curso bloqueado, fila por clave compuesta, `DELETE`, `ASSOCIATION` sin motivo, relectura. El recomendado no se lee: la pareja se resuelve por sus dos identificadores y basta.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/service` | `WithdrawRecommendationService` | `AC` |
| `interfaces` | `CourseController` — `DELETE /api/v1/courses/{courseId}/recommendations/{recommendedCourseId}` | `AC` |

## 4. Contrato de API

`DELETE /api/v1/courses/{courseId}/recommendations/{recommendedCourseId}` — `courses:update`. Sin cuerpo → `200` con `CourseDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001`, `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`DeletionEvent` `ASSOCIATION` de `course_recommendations`, `reason` nulo, `entity_id` del curso, instantánea con la pareja.

## 7. Transaccionalidad

`@Transactional`. Curso con `FOR UPDATE`, la fila, `DELETE`, auditoría, relectura.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Exigir que el recomendado esté vivo** | Impediría limpiar lo colgado (`spec.md` §2) |
| **Retirar la inversa a la vez** | Son dos decisiones; quien quiera las dos hace dos peticiones |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| — | Ninguno propio | |

## 11. Estrategia de prueba

- **Integración de API** (`CourseRecommendationIT`): los cuatro criterios, en la misma suite que el alta.
