# PLAN — `RF-AC-021` Quitar la visibilidad de un curso a una membresía

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-021` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`DeclassifyCourseService` con otra fila.** Nada que decidir: curso bloqueado, fila por su clave, `DELETE`, `ASSOCIATION` sin motivo, relectura con `offerable` recalculado por `CourseOfferability` con la cuenta nueva.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/service` | `RevokeCourseVisibilityService` | `AC` |
| `interfaces` | `CourseController` — `DELETE /api/v1/courses/{courseId}/memberships/{membershipId}` | `AC` |

## 4. Contrato de API

`DELETE /api/v1/courses/{courseId}/memberships/{membershipId}` — `courses:update`. Sin cuerpo → `200` con `CourseDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001`, `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`DeletionEvent` `ASSOCIATION` de `course_memberships`, `reason` nulo, `entity_id` del curso, instantánea con la pareja y el código.

## 7. Transaccionalidad

`@Transactional`. Curso con `FOR UPDATE`, la fila, `DELETE`, auditoría, relectura.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Rechazar quitar la última** | Es la forma de esconder sin desactivar (`spec.md` §2); y el detalle enseña el efecto |
| **`POST …/deletion` con motivo** | Asociación: `ASSOCIATION` sin motivo |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El aula sigue enseñando el curso** por una caché de la lista | No hay caché: la lista se lee en cada petición del aula (`RF-AC-033`); `CA-AC-143` lee el aula después |

## 11. Estrategia de prueba

- **Integración de API** (`CourseVisibilityIT`): los cuatro criterios; **la que define el requerimiento es `CA-AC-143`**, con el aula leída después —hasta `RF-AC-033`, sobre el detalle—.
