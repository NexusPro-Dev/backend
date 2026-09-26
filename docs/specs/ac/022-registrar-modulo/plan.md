# PLAN — `RF-AC-022` Registrar módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-022` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**El alta del curso con el padre en la ruta, y cuatro literales del bloque 2 que se convierten en sentencias.**

La entidad `CourseModule` hereda la forma de `Course` sin instructor ni dificultad; el caso de uso bloquea el curso vivo, comprueba el título dentro del curso y escribe. Lo que este plan añade de verdad son **las lecturas que el bloque 2 dejó vacías con nota**: la subconsulta de `moduleCount` en `RF-AC-009`, `findModulesOf` en el detalle de `RF-AC-010`, `countActiveModulesOf` en `RF-AC-012` y `findAliveModulesForUpdate` en el arrastre de `RF-AC-013` — cada una sustituye su literal por la sentencia que su nota anticipaba, y el criterio que estaba bloqueado se habilita.

**`ModuleOfferability` nace aquí**, hermano pequeño de `CourseOfferability`: retirado → inactivo → sin lección activa **con contenido** (el tercer motivo dice «con contenido» desde el 18-09-2026, `ac.md` §5.2.7). `CourseOfferability` pasa a recibir **cuántos módulos ofrecibles** hay, que es la cuenta que `ModuleOfferability` hace por módulo; hasta `RF-AC-028` ningún módulo tiene lecciones y ninguno es ofrecible, y el último motivo del curso sigue diciendo la verdad.

## 2. Cambios de esquema

**Una migración.** Los números se asignan al construir: tras `V21`/`V22` de los cursos, `V23` salvo que otra tanda se adelante.

### `V23__ac_modulos.sql`

```sql
CREATE TABLE course_modules (
    id                     uuid         PRIMARY KEY,
    course_id              uuid         NOT NULL,
    title                  varchar(150) NOT NULL,
    short_description      varchar(300) NULL,
    long_description       text         NULL,
    presentation_video_url varchar(500) NULL,
    display_order          integer      NOT NULL,
    status                 varchar(20)  NOT NULL DEFAULT 'INACTIVO',
    cover_image_id         uuid         NULL,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    deleted_at             timestamptz  NULL,
    CONSTRAINT fk_course_modules_course        FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT ck_course_modules_status        CHECK (status IN ('ACTIVO','INACTIVO')),
    CONSTRAINT ck_course_modules_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_course_modules_presentation_video_url
        CHECK (presentation_video_url IS NULL OR presentation_video_url ~ '^https?://[^[:space:]]+$')
);
CREATE UNIQUE INDEX uq_course_modules_title
    ON course_modules (course_id, f_unaccent(lower(title))) WHERE deleted_at IS NULL;
CREATE INDEX ix_course_modules_course
    ON course_modules (course_id, display_order, id) WHERE deleted_at IS NULL;
```

- **`uq_course_modules_title` lleva `course_id` delante**: único dentro del curso, y la carrera la muerde el índice.
- **`ix_course_modules_course` es el índice del árbol** (`ac.md` §8.9): sostiene el detalle del curso en su orden y la cuenta de activos; parcial sobre vivos porque los retirados solo los lee el detalle de administración, que ya paga el recorrido.
- **`course_modules` y no `modules`**, que en este proyecto nombra otra cosa. **Sin `ON DELETE`**: nada se borra.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `CourseModule` (entidad), reutilizando `CourseStatus` y `VideoUrl`; **`ModuleOfferability`** | `AC` |
| `domain/models` | `CourseOfferability` pasa a recibir la cuenta de módulos **ofrecibles** en lugar de un cero literal | `AC` |
| `domain/repository` | `CourseModuleRepository` + `Jpa…`: `save` con traducción de `uq_course_modules_title`, `existsAliveTitleInCourse`, `existsAliveTitleInCourseForOther`, `findAliveByIdForUpdate`, `findByIdForUpdate`, `flush` | `AC` |
| `domain/repository` | `CourseModuleQueryRepository` + `Jpa…`: `findDetail(moduleId)` con su curso, y `findLessonsOf` —vacío hasta `RF-AC-028`— | `AC` |
| `domain/repository` | **`CourseQueryRepository`, enmendado**: la subconsulta de `moduleCount`, `findModulesOf(courseId)` en orden, `countActiveModulesOf`, `findAliveModulesForUpdate` — sustituyen sus literales | `AC` |
| `domain/service` | `RegisterCourseModuleService`; **`CourseModuleDetailReader`** (`ENTIDAD = "course_modules"`): módulo → lecciones → ofrecibilidad → motivo de retiro, para las seis operaciones que devuelven el módulo; `CourseDetailReader` y `CourseTreeRetirement`, enmendados | `AC` |
| `application` | `RegisterCourseModuleRequest`, `CourseModuleDetailResponse` (con `LessonSummary`, de `RF-AC-010`) | `AC` |
| `interfaces` | **`CourseModuleController`** — `POST /api/v1/courses/{courseId}/modules` | `AC` |

**El módulo tiene su propio controlador y su propio lector de detalle**, y no cuelgan de los del curso: son seis operaciones con su forma de respuesta, y meterlas en `CourseController` lo llevaría a veinte métodos.

## 4. Contrato de API

`POST /api/v1/courses/{courseId}/modules` — `courses:update`.

```json
{ "title": "Fundamentos", "shortDescription": "…", "longDescription": "…",
  "presentationVideoUrl": "https://…", "displayOrder": 0 }
```

`201` con `CourseModuleDetailResponse` y cabecera `Location` (`/api/v1/courses/{courseId}/modules/{id}`):

```json
{
  "id": "…", "courseId": "…", "title": "Fundamentos",
  "shortDescription": "…", "longDescription": "…", "presentationVideoUrl": "https://…",
  "displayOrder": 0, "status": "INACTIVO", "coverImageUrl": null,
  "durationSeconds": 0, "lessons": [],
  "offerable": false, "offerableReason": "El módulo está inactivo.",
  "createdAt": "…", "updatedAt": "…"
}
```

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-006` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |
| `409` | `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")` — el módulo es parte del curso (`ac.md` §7).

## 6. Auditoría

`ChangeEvent` `CREATE` de `course_modules` con la instantánea del módulo, `course_id` incluido.

## 7. Transaccionalidad

`@Transactional`. **Cuatro sentencias**: el curso con `FOR UPDATE`, el título dentro del curso, `INSERT`, auditoría; y **una más** para releer el módulo.

## 8. Impacto sobre otros módulos

**Ninguno.** Dentro de `AC`, **cuatro requerimientos construidos se enmiendan** (Art. I.7): `RF-AC-009`, `RF-AC-010`, `RF-AC-012` y `RF-AC-013`, cada uno con su fila de control de cambios al construir y su criterio habilitado.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Devolver el curso entero** | `spec.md` §14.1 |
| **Un solo controlador para curso, módulo y lección** | Veinte métodos en una clase; tres recursos, tres controladores |
| **`course_id` corregible** | `RN-AC-019`: orden, unicidad y ofrecibilidad viven dentro del curso |
| **Índice sin `display_order`** | El árbol se lee siempre en orden; el índice lo devuelve ordenado sin `sort` |
| **`ModuleOfferability` dentro de `CourseOfferability`** | Dos preguntas —¿se ofrece el módulo?, ¿se ofrece el curso?— con dos listas de motivos; el detalle enseña las dos |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **Se sustituye un literal y se olvida otro** de los cuatro del bloque 2 | Los cuatro están listados en §3 y cada uno tiene su criterio (`CA-AC-081` a `CA-AC-083`, y `CA-AC-064` de `RF-AC-012` que `RF-AC-024` habilita) |
| 2 | **El bloqueo del curso se olvida** y un retiro simultáneo deja un módulo vivo bajo un curso retirado | `findAliveByIdForUpdate` del curso en el caso de uso; caso límite de §13 |
| 3 | **Dos altas simultáneas** con el mismo título | Índice parcial compuesto; `CA-AC-084` |

## 11. Estrategia de prueba

- **Unitarias**: `CourseModule` (recorte, vacías → nulas, video), **`ModuleOfferability`** (los tres motivos en orden), `CourseOfferability` con módulos ofrecibles y sin ellos.
- **Integración de API** (`CourseModulesIT`): `CA-AC-076` a `CA-AC-080`; la carrera en `CourseModuleConcurrencyIT` (`CA-AC-084`).
- **De las enmiendas**: `CA-AC-081` en `CourseListIT`, `CA-AC-082` en `CourseDetailIT` —con el contador de sentencias—, `CA-AC-083` en `CourseDeletionIT`; las tres suites del bloque 2 ganan casos y siguen en verde.
