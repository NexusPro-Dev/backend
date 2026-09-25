# PLAN — `RF-AC-035` Consultar el contenido de una lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-035` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**Una sentencia que trae los tres niveles con sus entradas, y tres objetos que deciden.** `findOfferedLesson(courseId, lessonId)` une `lessons` → `course_modules` → `courses` por identificadores y devuelve, además de la lección entera con su contenido, lo que `LessonOfferability`, `ModuleOfferability` y `CourseOfferability` necesitan de cada nivel —estado, retiro, contenido; estado, retiro, cuántas lecciones ofrecibles; estado, retiro, descripciones, cuántas membresías, cuántos módulos ofrecibles—. `ClassroomLessonReader` pregunta a los tres en orden y **cualquier «no» es el mismo `404`**. La regla no está en el `WHERE`: está en los objetos que administración usa (`RF-AC-033` §14.1).

**El acceso, después y solo si hace falta.** Si la lección está abierta, se devuelve sin preguntar nada más. Si no, el puerto da la vigente, `findMembershipsOf` da la lista del curso —la misma lectura de `RF-AC-010`, con código, nombre y color por `JOIN memberships`—, y `StudentAccess` decide. El «no» es un `ForbiddenException` **con la lista como miembro de extensión**, que es lo único que este requerimiento pide a `shared`: el constructor con extensiones que `DomainException` ya admite y `ForbiddenException` todavía no expone.

**El contenido no se toca.** `content` sale de la columna al JSON sin pasar por `LessonContent` ni por nadie: el objeto de valor valida al escribir (`RF-AC-028`), y leer no valida.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `LessonQueryRepository`: **`findOfferedLesson(courseId, lessonId)`** — la lección con su contenido y las entradas de ofrecibilidad de los tres niveles, en una sentencia | `AC` |
| `domain/service` | **`ClassroomLessonReader`**: los tres objetos, la abierta sin puerto, el puerto y la lista para la cerrada, `StudentAccess`, el `403` con la lista; **`GetClassroomLessonService`** | `AC` |
| `application` | **`ClassroomLessonResponse`** (con `content`) | `AC` |
| `interfaces` | `ClassroomController` — `GET /api/v1/courses/available/{courseId}/lessons/{lessonId}` | `AC` |
| `shared/error` | **`ForbiddenException`** gana el constructor `(errorCode, message, errors, extensions)` que `DomainException` ya admite; `GlobalExceptionHandler` ya copia las extensiones al `ProblemDetail` | `shared` |

## 4. Contrato de API

`GET /api/v1/courses/available/{courseId}/lessons/{lessonId}` — `courses:learn`.

`200`:

```json
{
  "id": "…", "courseId": "…", "moduleId": "…",
  "type": "TEXTO", "title": "Los cuerpos", "description": null,
  "content": "# Los cuerpos\n\nUna vela tiene…",
  "durationSeconds": 18, "displayOrder": 1, "open": false
}
```

`403` de membresía (`EX-002`):

```json
{
  "type": "about:blank", "title": "Forbidden", "status": 403,
  "detail": "Tu membresía no abre este curso.", "instance": "/api/v1/courses/available/…/lessons/…",
  "errorCode": "EX-002", "errors": [],
  "memberships": [{ "id": "…", "code": "ORO", "name": "Oro", "color": "D4AF37" }],
  "correlationId": "…"
}
```

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `courses:learn` (`AUTH-002`, sin `memberships`) |
| `403` | `EX-002`, con `memberships` |
| `404` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:learn')")` para entrar; la membresía, dentro. Los dos `403` se distinguen por `errorCode` (`CA-AC-208`).

## 6. Auditoría

No audita. **Ni el acceso denegado**: no es un intento de intrusión sino un alumno mirando lo que no tiene, y registrarlo llenaría la auditoría con lo que el catálogo invita a hacer.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Una sentencia** para la abierta y el `404`; **dos** para la cerrada, más la del puerto (`CA-AC-207`).

## 8. Impacto sobre otros módulos

**`shared/error`**: un constructor más en `ForbiddenException`, sin cambio para quien ya la usa.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Comprobar el acceso antes de la ofrecibilidad** | `403` sobre un curso que no se ofrece diría que hay algo detrás (`spec.md` §2) |
| **Las membresías solo en el mensaje del `403`** | `spec.md` §14.1 |
| **Un `EXISTS` con la vigente como parámetro** | `StudentAccess` decide para las tres vistas; la lista se necesita igual para el `403` |
| **Auditar el `403`** | §6 |
| **Sanear el Markdown al servirlo** | `ac.md` §5.2.4: no hay qué sanear en un JSON; es obligación del frontend |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La sentencia de los tres niveles olvida una entrada** y un objeto decide con un dato de menos | `CA-AC-205` prueba cada motivo de cada nivel por separado |
| 2 | **La lección de otro curso se devuelve** porque la sentencia une por lección y no comprueba el curso | El `JOIN` a `courses` lleva `c.id = :courseId`; `CA-AC-205` lo prueba |
| 3 | **El `403` de permiso lleva `memberships` vacío** y el frontend lo pinta como «ninguna membresía lo abre» | El de permiso no pasa por el lector; `CA-AC-208` comprueba que el miembro **no está** |

## 11. Estrategia de prueba

- **Unitarias**: `StudentAccess.lessonAccessible` (de `RF-AC-033`); `LessonOfferability` (de `RF-AC-034`).
- **Integración de API** (`ClassroomLessonIT`): los siete criterios; **la que define el requerimiento es `CA-AC-204`**, el `403` con la lista, y **`CA-AC-206`**, el orden.
- **De sentencias**: `CA-AC-207`, abierta, cerrada y `404`.
