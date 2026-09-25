# TASKS — `RF-AC-020` Dar visibilidad de un curso a una membresía

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-020` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/ajustes-academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración **`V45__ac_visibilidad.sql`**: `course_memberships` con clave compuesta, la clave foránea hacia `memberships`, e `ix_course_memberships_membership` | `RF-AC-008` · `T-01` | Integración: aplica; la pareja repetida y una membresía inexistente se rechazan | Pendiente |
| `T-02` | `domain/models/CourseMembership` y su clave; `CourseMembershipRepository` + `Jpa…` con traducción de la clave primaria | `T-01` | Integración: el `INSERT` duplicado llega como `BusinessRuleException` | Pendiente |
| `T-03` | **Enmienda de `CourseQueryRepository`**: `findMembershipsOf` con `JOIN memberships`, `countMembershipsOf`, `findMembershipIdsOf`, y la subconsulta de membresías en las sentencias de listado y de cursos de la categoría | `T-01` | Integración: cuatro columnas por membresía; la cuenta por fila | Pendiente |
| `T-04` | `application/GrantCourseVisibilityRequest`; `domain/service/GrantCourseVisibilityService` con `MembershipCatalog` | `T-02` | `CA-AC-135`, `CA-AC-136`, `CA-AC-137` | Pendiente |
| `T-05` | **`CourseOfferability` y los lectores enmendados**: la cuenta real de membresías en el detalle, el listado y la categoría; `memberships` en el detalle; `membership_ids` en la instantánea de `DeleteCourseService` | `T-03` | `CA-AC-138`, `CA-AC-139` | Pendiente |
| `T-06` | `interfaces/CourseController`: `POST /api/v1/courses/{courseId}/memberships`, `@PreAuthorize("hasAuthority('courses:update')")`, `201` | `T-04` | La ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-07` | Pruebas de API (`CourseVisibilityIT`, la carrera en `CourseConcurrencyIT`) y los casos nuevos en `CourseDetailIT`, `CourseListIT`, `CourseDeletionIT` | `T-05`, `T-06` | `CA-AC-135` a `CA-AC-140` | Pendiente |
| `T-08` | Documentación OpenAPI. **La prosa dice** que es una lista y no un nivel mínimo, que sin lista el curso no se ofrece, que no exige el estado, y que el `422` es de la membresía | `T-06` | El contrato declara `201`, `400`, `401`, `403`, `404`, `409`, `422` | Pendiente |
| `T-09` | Actualizar la matriz de `docs/requirements.md`, `docs/api/index.md`, las specs de `RF-AC-008`, `RF-AC-010` y `RF-AC-013`, y `modelo-datos.md` §5.3 (la clave foránea hacia `memberships`, de diseñada a escrita) | `T-07` | Las filas reflejan el estado | Pendiente |

## 2. Orden de ejecución

`T-01` → `T-02`/`T-03` → `T-04`/`T-05` → `T-06` → `T-07`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-135`, `CA-AC-136`, `CA-AC-137` | `T-02`, `T-04`, `T-07` |
| `CA-AC-138` | `T-03`, `T-05`, `T-07` — **con los bloques 3 completos** |
| `CA-AC-139` | `T-03`, `T-05`, `T-07` |
| `CA-AC-140` | `T-02`, `T-07` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Los números de migración se asignan al construir: `V26` si nadie se adelanta | 18-09-2026 | Responsable técnico | Abierto |
| 2 | `CA-AC-138` necesita un módulo ofrecible, que es del bloque 3 | 18-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad, `docs/api/index.md`, las tres specs y `modelo-datos.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
