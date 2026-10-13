# PLAN — `RF-AC-058` a `RF-AC-061` La portada de una clase en vivo, y la clase sin curso

| Campo | Valor |
|---|---|
| Requerimientos | `RF-AC-058` a `RF-AC-061` |
| Especificación | [`spec.md`](spec.md), aprobada el 13-10-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 13-10-2026 |

---

## 1. Enfoque

**Instructores.** Una tabla de lista, `live_session_instructors`, escrita como las listas de acceso: reemplazar es borrar y escribir. La fila de la clase deja de traer el curso y su instructor y **trae el conjunto de sus instructores**, leído en la misma lectura; `deInstructor(actor)` mira ese conjunto. El listado propio filtra con un `EXISTS` sobre la tabla. Cada instructor se valida con **`InstructorVerifier`** —el mismo del curso—, con el campo `instructorIds` en el error.

**Portada.** **`CoverUploader`** ya sube una portada de academia sin saber de qué entidad se trata, con la secuencia de la clave foránea en orden: guardar la imagen, apuntar, volcar, borrar la anterior, auditar. La clase no es una entidad JPA —es SQL nativo—, así que el caso de uso le pasa **un `HasCover` hecho a mano** que apunta la portada con un `UPDATE` y devuelve el `CambioDePortada`. Quitar sigue la forma de `products:remove-cover`: poner la columna a nulo, borrar la imagen y auditar si había. Todo con la fila bloqueada.

**Sin curso.** `course_id` se quita de la tabla y del código: peticiones, respuestas, filtros, auditoría y la comprobación del curso en `LiveSessionSupport`. `RF-AC-057` se retira entero: ruta, servicio, consulta y permiso.

## 2. Cambios de esquema

`V105`:

1. `live_session_instructors` (`live_session_id`, `user_id`, `created_at`), clave compuesta, sin `ON DELETE`; **rellenada con el instructor del curso** de cada clase que colgaba de uno.
2. `live_sessions` pierde `course_id` —con su clave foránea y su índice— y gana `cover_image_id` hacia `academy_images`, único.
3. Los cuatro permisos —`set-cover` y `remove-cover` a `SUPERADMIN` y `ADMIN`; `set-cover-own` y `remove-cover-own` donde está `courses:teach`—, el retiro de `courses:list-own` con su reparto, y las descripciones de los `-own` de las clases, que hablaban del curso. Guardas: catálogo 240 y el reparto.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| domain/repository | `LiveSessionRepository` (instructores, portada), `LiveSessionQueryRepository`, `JpaLiveSessionRepository`; se retira `CourseQueryRepository.findTaughtBy` |
| domain/service | `LiveSessionSupport` (instructores, el detalle), `ScheduleLiveSessionService`, `UpdateLiveSessionService`, `ListLiveSessionsService`, `LiveClassroomService`; **`LiveSessionCoverService`** (nuevo); se retira `ListTaughtCoursesService` |
| application | `LiveSessionRequests`, `LiveSessionDetailResponse`, `LiveSessionResponses`; se retira `TaughtCourseItem` |
| interfaces | `LiveSessionController` (cuatro rutas de portada), `LiveClassroomController`; se retira `TaughtCourseController` |

## 4. Contrato de API

Cuatro rutas nuevas, una retirada (`GET /courses/mine`). **Cambio incompatible**: `courseId`, `removeCourse` y `course` desaparecen de las clases en vivo. Lo nuevo: `instructorIds`, `instructors`, `coverImageUrl`, el filtro `instructorId`.

## 5. Autorización

Cada ruta con su `@PreAuthorize`, y las cuatro en `PERMISO_DE_CADA_OPERACION`; `GET /courses/mine` sale de ella.

## 6. Auditoría

`UPDATE` de `live_sessions`: la portada con el diff de `cover_image_id` (`CambioDePortada`); los instructores, en la instantánea de corregir, como `instructorIds`.

## 7. Transaccionalidad

La de programar y corregir, sin cambios. Las de portada, `@Transactional` con la fila bloqueada; el archivo se valida antes.

## 8. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Una columna `instructor_id`** | El responsable pidió más de uno |
| **La portada en la misma petición de programar** | El responsable la quiso opcional; y así es en el curso y el producto |
| **La portada bajo `live-sessions:update`** | Un permiso por ruta (`RN-SEG-014`), como el producto |

## 9. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **El frontend lee `course`** | Cambio incompatible avisado con el contrato |
| 2 | **Una clase de un curso cuyo instructor ya no porta `courses:teach`** | `V105` lo copia igual: la regla se comprueba al asignar |

## 10. Estrategia de prueba

Integración de API con el doble de Zoom: `LiveSessionInstructorsIT` (`CA-AC-317` a `CA-AC-319`, `CA-AC-322`), `LiveSessionCoverIT` (`CA-AC-320`, `CA-AC-321`), y la migración y los permisos (`CA-AC-323`); las suites de clases en vivo existentes, sin curso; `EndpointPermissionsIT` y las que cuentan el catálogo.
