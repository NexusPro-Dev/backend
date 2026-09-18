# PLAN — `RF-AC-018` Recomendar un curso previo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-018` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`RF-AC-016` con el otro lado en la misma tabla.** La fila es `CourseRecommendation` con clave compuesta; el caso de uso bloquea el curso, lee el recomendado **sin bloquear** —`findAliveById`, sin `FOR UPDATE`—, comprueba la pareja y escribe. `VAL-003` —no a sí mismo— se decide en el DTO antes de consultar, y el esquema lo repite con `ck_course_recommendations_not_self`. La lectura de `recommendedCourses` en el detalle es un `JOIN courses` sobre la misma tabla con lo que `CourseOfferability` necesita, para que cada recomendado salga con su `offerable`.

## 2. Cambios de esquema

**Una migración.** `V27` salvo que otra tanda se adelante.

### `V27__ac_recomendaciones.sql`

```sql
CREATE TABLE course_recommendations (
    course_id             uuid        NOT NULL,
    recommended_course_id uuid        NOT NULL,
    created_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_course_recommendations PRIMARY KEY (course_id, recommended_course_id),
    CONSTRAINT fk_course_recommendations_course      FOREIGN KEY (course_id)             REFERENCES courses (id),
    CONSTRAINT fk_course_recommendations_recommended FOREIGN KEY (recommended_course_id) REFERENCES courses (id),
    CONSTRAINT ck_course_recommendations_not_self CHECK (course_id <> recommended_course_id)
);
```

- **`ck_course_recommendations_not_self` es lo único de `RN-AC-011` que cabe en el motor**: la aciclicidad no se exige y el retiro del recomendado se mira en el dominio.
- **Sin índice sobre `recommended_course_id`**: «¿quién me tiene de previo?» no es una lectura de esta tanda (`RF-AC-010` §4.2); el día que lo sea, es un índice.

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `CourseRecommendation` (clave compuesta `CourseRecommendationId`) | `AC` |
| `domain/repository` | `CourseRecommendationRepository` + `Jpa…`: `save` con traducción de la clave primaria, `exists`, `find`, `delete`; `CourseRepository.findAliveById` **sin bloqueo** | `AC` |
| `domain/repository` | **Enmendado** `CourseQueryRepository`: `findRecommendedOf(courseId)` con `JOIN courses` y lo que la ofrecibilidad necesita, en el orden global; `findRecommendedIdsOf` | `AC` |
| `domain/service` | `RecommendCourseService` | `AC` |
| `application` | `RecommendCourseRequest` — `recommendedCourseId`; `RecommendedCourseRef` (de `RF-AC-010`) | `AC` |
| `interfaces` | `CourseController` — `POST /api/v1/courses/{courseId}/recommendations` | `AC` |

## 4. Contrato de API

`POST /api/v1/courses/{courseId}/recommendations` — `courses:update`. `{ "recommendedCourseId": "…" }` → `201` con `CourseDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-004` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |
| `409` | `EX-003` |
| `422` | `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`ChangeEvent` `CREATE` de `course_recommendations`, `entity_id` del curso, instantánea `{ course_id, recommended_course_id, recommended_title }`.

## 7. Transaccionalidad

`@Transactional`. El curso con `FOR UPDATE`, el recomendado sin bloqueo, la pareja, `INSERT`, auditoría, relectura.

## 8. Impacto sobre otros módulos

**Ninguno.** Dentro de `AC`, se enmiendan `RF-AC-010` y `RF-AC-013`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Comprobar ciclos** | Un recorrido por alta para prohibir lo que no rompe nada (`ac.md` §5.1) |
| **Bloquear el recomendado** | Interbloqueo con la recomendación inversa simultánea (`spec.md` §14.1) |
| **Un prerrequisito que bloquee la entrada** | Decisión del responsable del proyecto: exige progreso, que no existe |
| **Un orden propio de las recomendaciones** | `ac.md` §8.4: el orden global de los cursos |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **`A → B` y `B → A` simultáneas se interbloquean** | El recomendado no se bloquea; `CA-AC-150` corre las dos a la vez |
| 2 | **El aula enseña un recomendado que no se ofrece** | `findRecommendedOf` trae lo que `CourseOfferability` necesita, y el aula filtra por ella (`RF-AC-034`); `CA-AC-149` |

## 11. Estrategia de prueba

- **Integración de API** (`CourseRecommendationIT`): `CA-AC-145` a `CA-AC-149`; las dos carreras en `CourseConcurrencyIT` (`CA-AC-150`).
- **De las enmiendas**: `CA-AC-149` en `CourseDetailIT` y `CourseDeletionIT`; la mitad del aula, desde `RF-AC-034`.
