# PLAN — `RF-AC-035` Consultar el contenido de una lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-035` |
| Especificación | [`spec.md`](spec.md), aprobada el 26-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026; reescrito el 26-09-2026 |

---

## 1. Enfoque

**Una sentencia que trae los tres niveles con sus entradas, y tres objetos que deciden.** `findClassroomLesson(courseId, lessonId)` une `lessons` → `course_modules` → `courses` por identificadores —con `c.id = :curso` en el `JOIN`— y devuelve la lección entera con su contenido, y lo que `LessonOfferability`, `ModuleOfferability` y `CourseOfferability` necesitan de cada nivel, **más las dos cuentas de llaves del curso**. El servicio pregunta a los tres en orden y **cualquier «no» es el mismo `404`**. La regla no está en el `WHERE`.

**El acceso, después y solo si hace falta.** Abierta o curso sin llaves: se devuelve sin preguntar nada más. Si no, `StudentKeys` da lo del alumno, `findMembershipsOf` y `findProductsOf` dan las listas del curso, y `StudentAccess` decide. El «no» es un `ForbiddenException` **con las dos listas como miembros de extensión**: `ForbiddenException` gana el constructor con extensiones que `DomainException` ya admite.

**El contenido no se toca.** `content` sale de la columna al JSON.

## 2. Cambios de esquema

**`V47`** siembra `lessons:learn` (ver `RF-AC-034` · plan §2).

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/repository` | `LessonQueryRepository`: **`findClassroomLesson(courseId, lessonId)`** — la lección con su contenido, las entradas de ofrecibilidad de los tres niveles y las cuentas de llaves del curso, en una sentencia | `AC` |
| `domain/service` | **`GetClassroomLessonService`**: los tres objetos, la abierta y la gratuita sin puertos, `StudentKeys` y las listas para la cerrada, `StudentAccess`, el `403` con las listas | `AC` |
| `application` | **`ClassroomLessonResponse`** (con `content`) | `AC` |
| `interfaces` | `ClassroomController` — `GET /api/v1/courses/available/{courseId}/lessons/{lessonId}` | `AC` |
| `shared/error` | **`ForbiddenException`** gana `(errorCode, message, errors, extensions)` | `shared` |

## 4. Contrato de API

`GET /api/v1/courses/available/{courseId}/lessons/{lessonId}` — `lessons:learn`.

`200`:

```json
{
  "id": "…", "courseId": "…", "moduleId": "…",
  "type": "TEXTO", "title": "Los cuerpos", "description": null,
  "content": "# Los cuerpos\n\nUna vela tiene…",
  "durationSeconds": 180, "displayOrder": 1, "open": false
}
```

`403` de llave (`EX-002`):

```json
{
  "type": "about:blank", "title": "Forbidden", "status": 403,
  "detail": "Ni tu membresía ni tus servicios abren este curso.",
  "errorCode": "EX-002", "errors": [],
  "memberships": [{ "id": "…", "code": "ORO", "name": "Oro", "color": "D4AF37" }],
  "products": [{ "id": "…", "code": "BOT_VELAS", "name": "Bot de velas" }]
}
```

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` |
| `401` / `403` | Sin sesión / sin `lessons:learn` (`AUTH-002`, sin listas) |
| `403` | `EX-002`, con `memberships` y `products` |
| `404` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('lessons:learn')")` para entrar; las llaves, dentro. Entra en `PERMISO_DE_CADA_OPERACION`.

## 6. Auditoría

No audita, **ni el acceso denegado**: es un alumno mirando lo que no tiene.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **Una sentencia** para la abierta, la gratuita y el `404`; **tres** para la cerrada, más las de los puertos (`CA-AC-207`).

## 8. Impacto sobre otros módulos

**`shared/error`**: un constructor más en `ForbiddenException`, sin cambio para quien ya la usa.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Comprobar el acceso antes de la ofrecibilidad** | `403` sobre un curso que no se ofrece diría que hay algo detrás |
| **Las llaves solo en el mensaje del `403`** | `spec.md` §14.1 |
| **Preguntar a los puertos siempre** | Dos sentencias de `SP` por cada demostración para no usarlas |
| **Auditar el `403`** | §6 |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **La sentencia de los tres niveles olvida una entrada** | `CA-AC-205` prueba cada motivo de cada nivel |
| 2 | **La lección de otro curso se devuelve** | El `JOIN` a `courses` lleva `c.id = :curso`; `CA-AC-205` |
| 3 | **El `403` de permiso lleva listas vacías** | El de permiso no pasa por el servicio; `CA-AC-208` comprueba que el miembro no está |

## 11. Estrategia de prueba

- **Integración de API** (`ClassroomLessonIT`): los ocho criterios; **la que define el requerimiento es `CA-AC-204`**, el `403` con las listas, y **`CA-AC-206`**, el orden.
- **De sentencias**: `CA-AC-207`, abierta, gratuita, cerrada y `404`.
