# PLAN — `RF-AC-003` Consultar el detalle de una categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-003` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 17-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |

---

## 1. Enfoque

**El detalle del producto (`RF-PM-003`) más una lista, resuelta en una segunda sentencia fija.**

La categoría se lee en una sentencia; sus cursos vivos, en otra —`course_category_items` unida a `courses` con `deleted_at IS NULL`, ordenada por `display_order, id`—; el motivo del retiro, en una tercera **solo si está retirada**, con `DeletionReasonReader` de `shared/audit`, como en `PM`. **La lista no se hace con `LEFT JOIN` en la primera sentencia**: multiplicaría la fila de la categoría por curso y habría que desduplicar en Java una fila que tiene una portada y una descripción larga.

**`offerable` de cada curso lo calcula `CourseOfferability`**, el objeto de dominio que nace en el bloque 3 (`RN-AC-015`) y que este detalle **consumirá** cuando exista. Hasta entonces la fila del curso lleva `offerable: false` literal, y el requerimiento que construya `CourseOfferability` enmienda esta lectura (Art. I.7) para que la segunda sentencia traiga lo que la cuenta necesita.

## 2. Cambios de esquema

**Ninguno.** El orden de los cursos usa `courses.display_order`, que `RF-AC-008` crea, y `ix_course_category_items_category`, que `RF-AC-016` crea con su tabla.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `CourseCategoryQueryRepository.findDetail(id)` —ya nace en `RF-AC-001`— y **`findAliveCoursesOf(categoryId)`**: identificador, título, estado, orden, en orden; **vacío hasta `RF-AC-016`**, con la nota en el código | `AC` |
| `domain/service` | `GetCourseCategoryService`: detalle, cursos, motivo si retirada | `AC` |
| `application` | `CourseCategoryDetailResponse` —ya nace en `RF-AC-001`— gana `deletedAt` y `deletionReason` (`NON_NULL`) y `CategoryCourseItem` para cada curso | `AC` |
| `interfaces` | `CourseCategoryController` — `GET /api/v1/course-categories/{id}` | `AC` |
| `shared/audit` | `DeletionReasonReader`, **sin cambio**: la lectura estrecha del motivo por entidad e identificador ya existe para `PM` | `shared` |

## 4. Contrato de API

`GET /api/v1/course-categories/{id}` — `course-categories:read`.

```json
{
  "id": "…", "name": "Trading", "description": "…",
  "color": "1E88E5", "icon": "chart-line", "displayOrder": 0,
  "coverImageUrl": "/api/v1/academy-images/…",
  "courseCount": 2,
  "courses": [
    { "id": "…", "title": "Introducción al trading", "status": "ACTIVO", "displayOrder": 0, "offerable": true },
    { "id": "…", "title": "Gestión del riesgo", "status": "INACTIVO", "displayOrder": 1, "offerable": false }
  ],
  "createdAt": "…", "updatedAt": "…",
  "deletedAt": "…", "deletionReason": "…"
}
```

`deletedAt` y `deletionReason` **solo si está retirada** (`NON_NULL`); `coverImageUrl` y `description` **siempre**, nulos si no hay.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `course-categories:read` |
| `404` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('course-categories:read')")`.

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Una sentencia** si no tiene cursos, **dos** con cursos —la segunda se cortocircuita cuando `courseCount` de la primera es cero—, **una más** con motivo. `CA-AC-019` las cuenta.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **`LEFT JOIN` a los cursos en la sentencia de la categoría** | Multiplica la fila de la categoría —con su descripción larga— por curso, y obliga a desduplicar |
| **Traer los cursos retirados con una bandera** | `spec.md` §14.1 |
| **Traer `offerableReason` por curso** | Exportación de fallos; el detalle del curso lo dice |
| **Paginar los cursos** | Decenas como mucho, y reordenar necesita la lista entera |
| **Calcular `offerable` en SQL** | Una sola cuenta, en Java, en `CourseOfferability`, como `PackageOfferability` |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Se olvida enmendar este detalle** cuando nazca `CourseOfferability` | `CA-AC-016` exige `offerable` real y queda en la matriz como criterio bloqueado hasta el bloque 3; `ac.md` §6.1 lo declara |
| 2 | **`DeletionReasonReader` devuelve vacío** para una retirada sembrada sin registro | `deletionReason` nulo y presente, sin `500` (caso límite de §13) |

## 11. Estrategia de prueba

- **Integración de API** (`CourseCategoryDetailIT`): los cinco criterios; **la que define el requerimiento es `CA-AC-017`**, la del retirado que no aparece y el inactivo que sí — trivial hasta `RF-AC-016`, que la enmienda.
- **De sentencias**: `CA-AC-019` con y sin cursos, y con motivo.
- **Contrato**: `deletedAt` y `deletionReason` declarados como opcionales; `courses` y `coverImageUrl` siempre presentes.
