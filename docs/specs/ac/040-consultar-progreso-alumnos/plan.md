# PLAN — `RF-AC-040` Consultar el progreso de los alumnos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-040` |
| Especificación | [`spec.md`](spec.md), aprobada el 09-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 09-10-2026 |

---

## 1. Enfoque

**El alcance, de `RF-AC-041`**: `ProgressAudience` da «todos» o un conjunto de personas, y el instructor entra en el SQL como **`c.instructor_id = :actor`**. El predicado de la página es:

```sql
(:todos OR p.user_id IN (:personas) OR c.instructor_id = :actor)
```

escrito por partes en Java —sin `CAST(:x) IS NULL`— por la trampa de los parámetros nulos sin tipo de `RF-MV-008`.

**Una sentencia agrupa y calcula.** Las filas alumno–curso salen de `lesson_progress` unida a `lessons`, `course_modules` y `courses`, agrupadas; el avance de cada una suma, sobre **las lecciones ofrecibles hoy del curso** (el `LECCION_OFRECIBLE` y el módulo activo y vivo que el aula ya usa), lo visto acotado a la duración —la duración entera si está completada— (`RN-AC-023`). El total, otra sentencia con el mismo predicado. La identidad de los alumnos de la página, **una** llamada a `UserCatalog.findAll`. **Tres sentencias y un puerto**, fijas.

**El porcentaje y `completed`, en `LessonProgress`**: el SQL devuelve las sumas y Java redondea, para que la división viva donde vive en el aula y en el detalle.

## 2. Cambios de esquema

Ninguno: `V92` siembra `courses:list-progress` e `ix_lesson_progress_lesson`.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `LessonProgressRepository.search(filtro, offset, size)` y `count(filtro)` | `AC` |
| `domain/service` | **`ListStudentProgressService`** | `AC` |
| `application` | **`ListStudentProgressRequest`**, **`StudentProgressItem`**, **`StudentProgressPageResponse`** | `AC` |
| `interfaces` | `CourseProgressController` — `GET /api/v1/courses/progress` | `AC` |

## 4. Contrato de API

`GET /api/v1/courses/progress?userId=&courseId=&completed=&page=&size=` — `courses:list-progress`.

```json
{
  "content": [ {
    "student": { "id": "…", "username": "ana", "fullName": "Ana Ruiz" },
    "course": { "id": "…", "title": "Velas", "status": "ACTIVO", "deleted": false },
    "percent": 47, "completedLessons": 2, "lessonCount": 5,
    "watchedSeconds": 512, "totalSeconds": 1090, "completed": false,
    "firstOpenedAt": "…", "lastActivityAt": "…" } ],
  "totalElements": 1, "totalPages": 1, "page": 0, "size": 20, "totalIsExact": true
}
```

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `courses:list-progress` |

`/courses/progress` es literal: Spring lo prefiere a `/courses/{id}` (`CA-AC-193`, el mismo argumento que `/available`).

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:list-progress')")`; el alcance, dentro. Entra en `PERMISO_DE_CADA_OPERACION` y en `ALCANCE_PROPIO` de `IntegrationTestBase` si la suite lo exige: el listado responde a cualquier rol que lo porte y filtra.

## 6. Auditoría

No audita.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

## 8. Impacto sobre otros módulos y documentos

- `security.md` §6: un listado más que **declara su alcance** (D-22), con el de `RN-MV-031` más el instructor.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Una fila por alumno con todos sus cursos dentro** | La página no se podría filtrar ni ordenar por curso |
| **Incluir a quien no empezó** | No hay fila; habría que cruzar la cartera con los cursos que se le abren, y eso es otra pregunta |
| **Orden elegible** | Nadie lo pidió; se añade con su tripleta si se pide |
| **Guardar el avance por curso** | Cambia sin que el alumno haga nada (`RN-AC-023`) |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La suma cuenta lecciones de otro curso** | La subconsulta de ofrecibles se une por `course_id` de la fila; `CA-AC-258` con dos cursos |
| 2 | **El alcance se escapa por el instructor** | `c.instructor_id = :actor` es del curso de **la fila**; `CA-AC-259` |
| 3 | **N+1 de identidades** | `findAll` una vez; `CA-AC-262` cuenta sentencias |

## 11. Estrategia de prueba

- **Integración de API** (`StudentProgressListIT`): `CA-AC-258` a `CA-AC-262`, con el contador de sentencias de Hibernate.
