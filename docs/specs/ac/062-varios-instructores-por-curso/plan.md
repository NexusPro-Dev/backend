# PLAN — `RF-AC-062` Varios instructores por curso

| Campo | Valor |
|---|---|
| Requerimientos | `RF-AC-062` |
| Especificación | [`spec.md`](spec.md), aprobada el 13-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 13-10-2026 |

---

## 1. Enfoque

**Escribir.** El curso es una entidad JPA, y la lista es suya. **Una colección de valores** sobre `course_instructors` (`@ElementCollection`), que el agregado comprueba y reemplaza: `Course.create` la recibe y `Course.update` la sustituye y devuelve el diff de `instructor_ids`. `RN-AC-033` —no vacía— vive en el agregado, como el resto de la forma. **`InstructorVerifier` se aplica a cada uno**, como en la clase en vivo, con `instructorIds` como campo del error. Al editar, **solo a los que no estaban**: reasignar al mismo nunca repitió la comprobación.

**Leer.** Las lecturas del curso salen de `JpaCourseQueryRepository` con un bloque único de columnas. **Los instructores entran en ese bloque como una subconsulta agregada en JSON** —identificador, usuario, nombre y apellido, por nombre completo—, de modo que **ni el detalle ni el listado ganan sentencias**, y `CourseRow` cambia tres columnas por una lista. El filtro `instructorId` pasa a un `EXISTS` sobre `course_instructors`, igual que el filtro por categoría.

**El progreso.** `ProgressAudience.reaches` recibe la lista; el alcance del listado de `JpaLessonProgressRepository` pasa a `EXISTS`.

## 2. Cambios de esquema

`V106`:

1. `course_instructors` (`course_id`, `user_id`, `created_at`), clave compuesta, claves foráneas sin `ON DELETE`, índice por `user_id`, **rellenada con `courses.instructor_id`**.
2. `courses` pierde `instructor_id`, `fk_courses_instructor` e `ix_courses_instructor`.
3. Guarda: ningún curso sin instructor.

Sin permisos.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/models | `Course` (la colección, crear, corregir, la instantánea) |
| domain/repository | `CourseQueryRepository.CourseRow`, `JpaCourseQueryRepository`, `JpaLessonProgressRepository` |
| domain/service | `RegisterCourseService`, `UpdateCourseService`, `GetStudentCourseProgressService`, `ProgressAudience` |
| application | `RegisterCourseRequest`, `UpdateCourseRequest`, `CourseDetailResponse`, `CourseItem`, `ClassroomCourseResponse`, `ClassroomCatalogResponse` |
| interfaces | La prosa de `CourseController` |
| semilla | `semilla-academia.sql` escribe la lista |

## 4. Contrato de API

**Cambio incompatible**, sin rutas nuevas: `instructorId` → `instructorIds` al registrar y editar; `instructor` → `instructors` en las cuatro lecturas. El filtro `instructorId` del listado conserva el nombre y cambia el sentido a pertenencia.

## 5. Autorización

Sin cambios.

## 6. Auditoría

`CREATE` de `courses` con `instructor_ids` —ordenados— en la instantánea, en lugar de `instructor_id`; `UPDATE` con `instructor_ids` antes y después cuando la lista cambia como conjunto.

## 7. Transaccionalidad

La de registrar y editar, sin cambios: la colección se escribe al volcar el agregado.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Conservar `instructor_id` como principal y añadir una lista de adicionales** | Dos fuentes para lo mismo, y el responsable pidió «lo mismo» que en la clase en vivo |
| **Escribir la lista por SQL nativo desde el servicio**, como la clase en vivo | El curso sí es entidad JPA: la regla de no vacía y el diff son del agregado |
| **Cargar los instructores en una segunda consulta por página** | Una subconsulta agregada no suma sentencias y deja `CourseRow` completo en todas las lecturas |
| **Permitir cero instructores** | El curso dice quién lo enseña desde `RF-AC-008`; la clase admite cero porque administración la gobierna, y el curso no tiene nada que su instructor gobierne |

## 9. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El frontend lee `instructor`** | Cambio incompatible avisado con el contrato |
| 2 | **Las suites que cuentan sentencias** del alta o la corrección | La colección es perezosa y solo se toca al escribir; se ajusta lo que cambie y se dice |

## 10. Estrategia de prueba

Integración de API: `CourseInstructorsIT` (`CA-AC-324` a `CA-AC-326`, `CA-AC-329`); el aula y el progreso en sus suites (`CA-AC-327`, `CA-AC-328`); las suites del curso, con `instructorIds`; `CourseTest` del agregado.
