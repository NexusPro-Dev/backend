# PLAN — `RF-AC-020` Dar visibilidad de un curso a una membresía

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-020` |
| Especificación | [`spec.md`](spec.md), en revisión desde el 18-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 18-09-2026 |

---

## 1. Enfoque

**`RF-AC-016` con la membresía en lugar de la categoría, y el puerto de `SP` en lugar del repositorio propio.** La fila, el bloqueo, el `409` traducido y la auditoría son los mismos; la diferencia es que la membresía se resuelve por **`MembershipCatalog.find`** —el puerto que `PM` pidió el 27-08-2026— y que su resolución en las lecturas es un `JOIN memberships` de cuatro columnas. Y es el requerimiento que **sustituye el último cero literal de `CourseOfferability`**: la cuenta de membresías.

## 2. Cambios de esquema

**Una migración.** `V26` salvo que otra tanda se adelante.

### `V26__ac_visibilidad.sql`

```sql
CREATE TABLE course_memberships (
    course_id     uuid        NOT NULL,
    membership_id uuid        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_course_memberships PRIMARY KEY (course_id, membership_id),
    CONSTRAINT fk_course_memberships_course     FOREIGN KEY (course_id)     REFERENCES courses (id),
    CONSTRAINT fk_course_memberships_membership FOREIGN KEY (membership_id) REFERENCES memberships (id)
);
CREATE INDEX ix_course_memberships_membership ON course_memberships (membership_id);
```

- **`fk_course_memberships_membership` cruza hacia `SP`** y se declara (`modelo-datos.md` §5.3): la segunda clave foránea de `AC` hacia `memberships`, la primera fuera de `PM`.
- **`ix_course_memberships_membership`** sostiene el aula: «qué cursos abre la membresía vigente de quien mira» (`RF-AC-033`).

## 3. Componentes afectados

| Capa | Elemento | Módulo |
|---|---|---|
| `domain/models` | `CourseMembership` (clave compuesta `CourseMembershipId`); `CourseOfferability` recibe la cuenta real de membresías | `AC` |
| `domain/repository` | `CourseMembershipRepository` + `Jpa…`: `save` con traducción de `pk_course_memberships`, `exists`, `find`, `delete` | `AC` |
| `domain/repository` | **Enmendado** `CourseQueryRepository`: `findMembershipsOf(courseId)` con `JOIN memberships` (id, code, name, color), `countMembershipsOf`, `findMembershipIdsOf`; y la subconsulta de membresías en las sentencias de listado y de cursos de la categoría | `AC` |
| `domain/service` | `GrantCourseVisibilityService` | `AC` |
| `application` | `GrantCourseVisibilityRequest` — `membershipId`; `MembershipRef` (de `RF-AC-010`) | `AC` |
| `interfaces` | `CourseController` — `POST /api/v1/courses/{courseId}/memberships` | `AC` |
| `SP` | `MembershipCatalog`, **sin cambio**: ya publica lo que hace falta | `SP` |

## 4. Contrato de API

`POST /api/v1/courses/{courseId}/memberships` — `courses:update`. `{ "membershipId": "…" }` → `201` con `CourseDetailResponse`.

| Código | Cuándo |
|---|---|
| `400` | `VAL-001` a `VAL-003` |
| `401` / `403` | Sin sesión / sin `courses:update` |
| `404` | `EX-001` |
| `409` | `EX-003` |
| `422` | `EX-002` |

## 5. Autorización

`@PreAuthorize("hasAuthority('courses:update')")`.

## 6. Auditoría

`ChangeEvent` `CREATE` de `course_memberships`, `entity_id` del curso, instantánea `{ course_id, membership_id, membership_code }`.

## 7. Transaccionalidad

`@Transactional`. El curso con `FOR UPDATE`, la membresía por el puerto, la pareja, `INSERT`, auditoría, relectura.

## 8. Impacto sobre otros módulos

**Ninguno en código**: `MembershipCatalog` ya existe. Dentro de `AC`, se enmiendan `RF-AC-008` (`CourseOfferability`), `RF-AC-010` y `RF-AC-013`.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Nivel mínimo** | Decisión del responsable del proyecto (`ac.md` §5.2.2) |
| **Abrir en cascada los superiores** | Sería el nivel mínimo con otro nombre |
| **Resolver la membresía por `JOIN` también al escribir** | Una regla —tiene que existir— no se decide con un `JOIN`; cruza por el puerto (D-25) |
| **Dar varias de una vez** | Una pareja por petición, como toda relación |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **`CourseOfferability` sigue recibiendo cero** membresías en alguna lectura | La cuenta entra en las tres sentencias que la necesitan —detalle, listado, cursos de la categoría— y `CA-AC-138` lo prueba en el detalle; `RF-AC-033` en el aula |
| 2 | **El aula cuenta con la lista** y `ix_course_memberships_membership` no existe | Nace aquí, con la tabla |

## 11. Estrategia de prueba

- **Unitaria**: `CourseOfferability` con membresías y sin ellas, en su orden.
- **Integración de API** (`CourseVisibilityIT`): `CA-AC-135` a `CA-AC-139`; la carrera en `CourseConcurrencyIT` (`CA-AC-140`). **La que define el requerimiento es `CA-AC-138`**: el curso que se ofrece por primera vez, y que `PLATINO` no abre lo de `ORO`.
