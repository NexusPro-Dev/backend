# PLAN — `RF-AC-029` Editar lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-029` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`RF-AC-023` con la pareja resultante.** La mecánica es la de siempre; lo propio es que `Lesson.update` **compone `(tipo, contenido)` con lo que viene y lo que había, lo pasa por `LessonContent` antes de aplicar nada**, y solo entonces toca los campos — para que un `400` no deje medio cambio en una fila bloqueada. Es exactamente lo que `ProductPackage.update` hace con la vigencia (`RF-PM-020`).

## 2. Cambios de esquema

**Ninguno.** `ck_lessons_video_content` es la red.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `application` | `UpdateLessonRequest` — siete `Patchable` (`type` como `Patchable<LessonType>`, `open` como `Patchable<Boolean>`); `informaAlgo()` | `AC` |
| `domain/models` | `Lesson.update(...)`: pareja resultante por `LessonContent` **antes** de aplicar; el diff del contenido lleva `content_length` antes y después | `AC` |
| `domain/repository` | `LessonRepository.existsAliveTitleInModuleForOther`, `findAliveByIdInModuleForUpdate(courseId, moduleId, lessonId)` | `AC` |
| `domain/service` | `UpdateLessonService` | `AC` |
| `interfaces` | `LessonController` — `PATCH /api/v1/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}` | `AC` |

## 4. Contrato de API

`PATCH /api/v1/courses/{courseId}/modules/{moduleId}/lessons/{lessonId}` — `courses:update`. `200` con `LessonResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-006` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-002` |
| `409` | `EX-001` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`UPDATE` de `lessons` con solo lo que cambió; `content` como `{ before: longitud, after: longitud }`.

## 7. Transaccionalidad

`@Transactional`. La lección de su módulo y curso con `FOR UPDATE`; la pareja resultante; el título contra otras si viene; escritura y auditoría si cambió; relectura.

## 8. Impacto sobre otros módulos

**Ninguno.**

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Tipo inmutable** | Un video que se transcribe a texto es un caso real; y la pareja resultante lo resuelve sin regla extra |
| **Vaciar el contenido desactiva** | `spec.md` §14.1 |
| **Validar el contenido solo si viene** | Cambiar el tipo sin contenido nuevo dejaría un `VIDEO` con un Markdown dentro; `ck_lessons_video_content` lo mordería como `500` |
| **Auditar el texto entero** | `RF-AC-028` §14.1 |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Se aplica el título antes de validar la pareja** y el `400` deja medio cambio | La pareja se valida primero en `Lesson.update`, con unitaria que lo comprueba |
| 2 | **El nulo de `open` se trata como ausente** | `Patchable<Boolean>` con su deserializador; `CA-AC-101` |

## 11. Estrategia de prueba

- **Unitaria**: `Lesson.update` — la pareja resultante en sus cuatro combinaciones, el `400` que no aplica nada, los nulos, sin cambios.
- **Integración de API** (`LessonUpdateIT`): los seis criterios; **la que define el requerimiento es `CA-AC-100`**.
