# PLAN — `RF-AC-014` Subir o reemplazar la portada de un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-014` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`UploadCourseCategoryCoverService` con `Course`.** La secuencia es la misma, y la parte que se comparte de verdad —`AcademyImage.de`, `AcademyImageRepository`, `CambioDePortada`— ya existe. Para no escribir tres servicios idénticos, **el caso de uso se extrae a un colaborador `CoverUploader`** parametrizado por «quién tiene la portada» —una interfaz mínima `HasCover { asignarPortada(UUID, OffsetDateTime) }` que `CourseCategory`, `Course` y `CourseModule` implementan— y cada servicio le pasa su repositorio, su lector y su entidad de auditoría.

## 2. Cambios de esquema

**Ninguno.** `fk_courses_cover_image` y `uq_courses_cover_image` llegaron con `V28`.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `Course.asignarPortada(nueva, ahora)`, `HasCover` | `AC` |
| `domain/service` | **`CoverUploader`** —insertar, apuntar, volcar, borrar la anterior, auditar—; `UploadCourseCoverService` sobre él; `UploadCourseCategoryCoverService` refactorizado para usarlo | `AC` |
| `interfaces` | `CourseController` — `PUT /api/v1/courses/{id}/cover` | `AC` |

## 4. Contrato de API

`PUT /api/v1/courses/{id}/cover` — `courses:update` — `multipart/form-data`, parte `file`. `200` con `CourseDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-004`, `EX-002` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`UPDATE` de `courses` con `cover_image_id` antes y después.

## 7. Transaccionalidad

La de `RF-AC-006` §7 sobre `courses`.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Tres servicios copiados** | La secuencia de siete pasos con su orden de clave foránea se escribiría tres veces; `CoverUploader` es la única forma de que las tres portadas no diverjan |
| **`CoverUploader` en `shared/`** | Depende de `AcademyImage` y del repositorio de `AC`; es del módulo. `PM` tiene su copia y se queda con ella |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La refactorización de `UploadCourseCategoryCoverService` cambia su comportamiento** | `CourseCategoryCoverIT` en verde antes de escribir el servicio del curso |

## 11. Estrategia de prueba

- **Unitaria**: `Course.asignarPortada`; `CoverUploader` con dobles.
- **Integración de API** (`CourseCoverIT`): los cuatro criterios.
