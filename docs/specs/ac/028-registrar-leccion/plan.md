# PLAN — `RF-AC-028` Registrar lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-028` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**El alta del módulo un nivel más abajo, con el contenido como objeto de valor y la última tanda de literales del bloque 2 convertida en sentencias.**

`Lesson` hereda la forma de `CourseModule`; lo nuevo es **`LessonContent`**, el objeto de valor que sabe que un `VIDEO` lleva una URL y un `TEXTO` lleva texto, y que el alta y la corrección (`RF-AC-029`) comparten para que «el tipo manda» se decida en un solo sitio. Con `lessons` escrita, **`ModuleOfferability` recibe por fin lecciones ofrecibles** —activas, vivas y **con contenido**, que es la cuenta que `RN-AC-015` pide desde el 18-09-2026— y `CourseOfferability` deja de ver siempre cero módulos ofrecibles: `RN-AC-015` queda cerrada.

## 2. Cambios de esquema

**Una migración.** `V24` salvo que otra tanda se adelante.

### `V24__ac_lecciones.sql`

```sql
CREATE TABLE lessons (
    id               uuid         PRIMARY KEY,
    module_id        uuid         NOT NULL,
    type             varchar(20)  NOT NULL,
    title            varchar(150) NOT NULL,
    description      text         NULL,
    content          text         NULL,
    duration_minutes integer      NOT NULL,
    display_order    integer      NOT NULL,
    open             boolean      NOT NULL DEFAULT false,
    status           varchar(20)  NOT NULL DEFAULT 'INACTIVO',
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    deleted_at       timestamptz  NULL,
    CONSTRAINT fk_lessons_module        FOREIGN KEY (module_id) REFERENCES course_modules (id),
    CONSTRAINT ck_lessons_type          CHECK (type IN ('VIDEO','TEXTO')),
    CONSTRAINT ck_lessons_video_content CHECK (type <> 'VIDEO' OR content IS NULL OR content ~ '^https?://[^[:space:]]+$'),
    CONSTRAINT ck_lessons_duration      CHECK (duration_minutes > 0),
    CONSTRAINT ck_lessons_status        CHECK (status IN ('ACTIVO','INACTIVO')),
    CONSTRAINT ck_lessons_display_order CHECK (display_order >= 0)
);
CREATE UNIQUE INDEX uq_lessons_title
    ON lessons (module_id, f_unaccent(lower(title))) WHERE deleted_at IS NULL;
CREATE INDEX ix_lessons_module
    ON lessons (module_id, display_order, id) WHERE deleted_at IS NULL;
```

- **`ck_lessons_video_content` tiene tres ramas y ninguna evalúa a `NULL`**: cuando es video y hay contenido, es una URL; un `TEXTO` no se mira. Es la lección de `ck_deletion_reason` (`requirements.md` v0.31.0).
- **Una sola columna `content`** y no `video_url` más `body` (`ac.md` §8.7).
- **`open` con `DEFAULT false`**: la demostración se declara, no se hereda.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `Lesson` (entidad), `LessonType`; **`LessonContent`** —`de(tipo, texto)`: recorta, vacío → nulo, y si `VIDEO` valida por `VideoUrl`— | `AC` |
| `domain/models` | `ModuleOfferability` recibe la cuenta de lecciones **ofrecibles** —activas, vivas, con contenido—; `CourseOfferability` deja de recibir cero | `AC` |
| `domain/repository` | `LessonRepository` + `Jpa…`: `save` con traducción de `uq_lessons_title`, `existsAliveTitleInModule`, `existsAliveTitleInModuleForOther`, `findAliveByIdForUpdate`, `findByIdForUpdate`, `flush` | `AC` |
| `domain/repository` | `LessonQueryRepository` + `Jpa…`: `findDetail(lessonId)` con su módulo y su curso | `AC` |
| `domain/repository` | **`CourseModuleQueryRepository` y `CourseQueryRepository`, enmendados**: `findLessonsOf(moduleId)`, `findLessonsOfModules(moduleIds)` en una sentencia, la subconsulta de `lessonCount`, `countActiveLessonsOf`, y las lecciones en `findAliveModulesForUpdate` | `AC` |
| `domain/service` | `RegisterLessonService`; **`LessonDetailReader`** (`ENTIDAD = "lessons"`), para las cuatro operaciones que devuelven la lección; `CourseModuleDetailReader`, `CourseDetailReader` y `CourseTreeRetirement`, enmendados | `AC` |
| `application` | `RegisterLessonRequest`, **`LessonResponse`** (con `content`) | `AC` |
| `interfaces` | **`LessonController`** — `POST /api/v1/courses/{courseId}/modules/{moduleId}/lessons` | `AC` |

**La instantánea de auditoría de una lección lleva `content_length` y no `content`** (`spec.md` §14.1): `Lesson.instantanea()` lo hace así, y la de eliminación (`RF-AC-031`) es la única que lleva el contenido entero.

## 4. Contrato de API

`POST /api/v1/courses/{courseId}/modules/{moduleId}/lessons` — `courses:update`.

```json
{ "type": "VIDEO", "title": "Qué es una vela", "description": "…",
  "content": "https://…", "durationMinutes": 12, "displayOrder": 0, "open": true }
```

`201` con `LessonResponse` y cabecera `Location`:

```json
{
  "id": "…", "moduleId": "…", "courseId": "…",
  "type": "VIDEO", "title": "Qué es una vela", "description": "…",
  "content": "https://…", "durationMinutes": 12, "displayOrder": 0,
  "open": true, "status": "INACTIVO",
  "createdAt": "…", "updatedAt": "…"
}
```

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-008` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |
| `409` | `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`ChangeEvent` `CREATE` de `lessons` con la instantánea —`module_id`, tipo, título, `content_length`, duración, orden, `open`, estado—.

## 7. Transaccionalidad

`@Transactional`. **Cuatro sentencias**: el módulo del curso con `FOR UPDATE`, el título dentro del módulo, `INSERT`, auditoría; **una más** para releer la lección.

## 8. Impacto sobre otros módulos

**Ninguno.** Dentro de `AC`, **cuatro requerimientos se enmiendan** (Art. I.7): `RF-AC-009`, `RF-AC-010`, `RF-AC-013` y `RF-AC-022`, cada uno con su fila de control de cambios al construir.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Dos columnas para el contenido** | `ac.md` §8.7: un contenido, y el tipo dice cómo leerlo |
| **El Markdown entero en la auditoría de cambios** | `spec.md` §14.1 |
| **Devolver el módulo o el curso al registrar una lección** | Lo que administración quiere ver es la lección con su contenido, que ninguna otra lectura de administración lleva |
| **Bloquear el curso además del módulo** | La cadena de bloqueos va de abajo arriba: el retiro del módulo bloquea el módulo, y el del curso bloquea el curso y luego sus módulos; bloquear aquí los dos invertiría el orden en la carrera con `RF-AC-013` y sería el interbloqueo clásico |
| **Sanear el Markdown** | No hay qué sanear: el backend no lo sirve como HTML (`ac.md` §5.2.4) |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Interbloqueo** entre el alta de una lección (bloquea módulo) y el retiro del curso (bloquea curso, luego módulos) | El orden de bloqueo es siempre padre → hijo en quien bloquea más de uno, y el alta bloquea solo al padre inmediato; `CourseConcurrencyIT` gana el caso |
| 2 | **`ck_lessons_video_content` evalúa a `NULL`** y acepta lo que no debe | Las tres ramas son predicados con valor; prueba de esquema con `VIDEO` + texto sin esquema → rechazado |
| 3 | **La duración del módulo suma lecciones inactivas** | La suma es sobre activas vivas, escrita en los lectores y probada en `CA-AC-090` |

## 11. Estrategia de prueba

- **Unitarias**: `Lesson` y **`LessonContent`** (video válido, video mal formado, texto cualquiera, vacío → nulo); `ModuleOfferability` con lecciones; `CourseOfferability` ofrecible por primera vez.
- **Integración de API** (`LessonsIT`): `CA-AC-085` a `CA-AC-089`; la carrera y el interbloqueo en `LessonConcurrencyIT` (`CA-AC-093`).
- **De las enmiendas**: `CA-AC-090` en `CourseListIT` y `CourseModulesIT`, `CA-AC-091` en `CourseDetailIT`, `CA-AC-092` en `CourseDeletionIT`.
