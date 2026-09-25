# PLAN — `RF-AC-038` Quitar la visibilidad de un curso a un servicio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-038` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 25-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 25-09-2026 |

---

## 1. Enfoque

**`DeclassifyCourseService` con otra fila** (`RF-AC-017`). Curso bloqueado, la fila por su clave compuesta, `DELETE`, `DeletionEvent` `ASSOCIATION` con motivo nulo, relectura del detalle. **`DELETE` sin cuerpo y `200` con el curso.** El código del producto para la instantánea se lee **antes de borrar**, por el mismo `JOIN` de la lectura del detalle: no cruza el puerto, porque no hay regla que comprobar.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `CourseProductRepository.find` y `delete` (de `RF-AC-037`) | `AC` |
| `domain/service` | `RevokeCourseProductService` | `AC` |
| `interfaces` | `CourseController` — `DELETE /api/v1/courses/{courseId}/products/{productId}` | `AC` |

## 4. Contrato de API

`DELETE /api/v1/courses/{courseId}/products/{productId}` — `courses:update`. Sin cuerpo → `200` con `CourseDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001`, `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`DeletionEvent` `ASSOCIATION` de `course_products`, `reason` nulo, `entity_id` del curso, instantánea `{ course_id, product_id, product_code }`.

## 7. Transaccionalidad

`@Transactional`. El curso con `FOR UPDATE`, la fila, la instantánea, el `DELETE`, la auditoría, la relectura.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Rechazar quitar el último** | Es la forma de cerrar un curso sin desactivarlo, como `RF-AC-021` con la membresía |
| **Resolver el código del producto por el puerto** | No hay regla: solo un dato para la instantánea, y el `JOIN` de lectura ya lo trae |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Dos peticiones simultáneas borran la misma fila** y las dos auditan | El bloqueo del curso las ordena; la segunda no encuentra la fila (`CA-AC-226`) |

## 11. Estrategia de prueba

- **Integración de API** (`CourseProductVisibilityIT`): los cuatro criterios, en la misma suite que el alta, como `RF-AC-017`.
