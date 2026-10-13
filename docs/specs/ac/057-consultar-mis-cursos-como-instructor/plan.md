# PLAN — `RF-AC-057` Consultar los cursos que dicto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-057` |
| Especificación | [`spec.md`](spec.md), aprobada el 13-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 13-10-2026 |

---

## 1. Enfoque

Una consulta nueva en `CourseQueryRepository`, `findTaughtBy(instructorId)`, que lee `id`, `title` y `status` de los cursos no retirados del instructor, por `display_order` y título; un servicio pequeño, `ListTaughtCoursesService`, que la llama con el actor; y la ruta en `CourseController`. **Sin paginar**: un instructor dicta pocos cursos, y el selector los quiere todos.

## 2. Cambios de esquema

Ninguno propio: `V104` (`RF-AC-056` · `T-01`) siembra `courses:list-own`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/repository | `CourseQueryRepository.findTaughtBy`, `JpaCourseQueryRepository` |
| domain/service | **`ListTaughtCoursesService`** |
| application | **`TaughtCourseItem`** |
| interfaces | `CourseController` — `GET /api/v1/courses/mine` |

## 4. Contrato de API

`GET /api/v1/courses/mine` — `courses:list-own`. `200`, `401`, `403`. La ruta literal `/mine` gana a `/{id}`.

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:list-own')")`. Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

Ninguna.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Abrir `GET /courses` con `courses:teach`** | Es el catálogo entero de administración, con lo retirado y lo de otros |
| **Un filtro `instructorId` en `GET /courses`** | Seguiría pidiendo `courses:read` |

## 9. Estrategia de prueba

Integración de API en `TaughtCoursesIT`: `CA-AC-315`, `CA-AC-316`.
