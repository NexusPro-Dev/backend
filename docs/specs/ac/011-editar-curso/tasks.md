# TASKS — `RF-AC-011` Editar curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-011` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 18-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `application/UpdateCourseRequest` con los siete `Patchable` e `informaAlgo()` | `RF-AC-008` · `T-09` | Unitaria: ausente, nulo y valor se distinguen en los siete | **Hecha el 18-09-2026** |
| `T-02` | `Course.update(...)`: aplica lo que viene, valida con el mensaje de cada `VAL-`, devuelve el mapa de cambios y avanza `updatedAt` solo si no está vacío | `RF-AC-008` · `T-05` | Unitaria: los casos de `plan.md` §11 | **Hecha el 18-09-2026** |
| `T-03` | `CourseRepository.existsAliveTitleForOther(title, id)` | `RF-AC-008` · `T-07` | Integración: el propio no choca; otro vivo sí; un retirado no | **Hecha el 18-09-2026** |
| `T-04` | **`InstructorVerifier`** extraído de `RegisterCourseService` y usado por las dos operaciones | `RF-AC-008` · `T-10` | `CoursesIT` sigue en verde con la extracción; unitaria con dobles de los dos puertos: los tres `422` | **Hecha el 18-09-2026** |
| `T-05` | `domain/service/UpdateCourseService`: validación conjunta antes de consultar, fila viva con `FOR UPDATE`, título contra otros, instructor por `InstructorVerifier`, escritura y auditoría solo si algo cambió, relectura | `T-01` a `T-04` | `CA-AC-057`, `CA-AC-060`, `CA-AC-061`, `CA-AC-062` | **Hecha el 18-09-2026** |
| `T-06` | `interfaces/CourseController`: `PATCH /api/v1/courses/{id}`, `@PreAuthorize("hasAuthority('courses:update')")` | `T-05` | `CA-AC-058`, `CA-AC-059`, `CA-AC-063`; la ruta entra en `EndpointPermissionsIT` | **Hecha el 18-09-2026** |
| `T-07` | Pruebas de API (`CourseUpdateIT`) de los ocho criterios, con **`CA-AC-214`** montado sobre un curso ofrecido | `T-06` | `CA-AC-057` a `CA-AC-063`, `CA-AC-214` | **Hecha el 18-09-2026** |
| `T-08` | Documentación OpenAPI. **La prosa dice** que es parcial y sin inmutables, qué se vacía y que vaciar no desactiva, que reasignar repite la comprobación del alta, y que el estado, la portada y las relaciones tienen sus endpoints | `T-06` | El contrato declara `200`, `400`, `401`, `403`, `404`, `409`, `422` | **Hecha el 18-09-2026** |
| `T-09` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-07` | La fila de `RF-AC-011` refleja el estado | **Hecha el 18-09-2026** |

## 2. Orden de ejecución

`T-01` a `T-04` son independientes; `T-05` las junta. `T-04` toca `RegisterCourseService`: su suite en verde antes de seguir.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-057`, `CA-AC-062` | `T-02`, `T-05`, `T-07` |
| `CA-AC-058`, `CA-AC-059`, `CA-AC-063` | `T-01`, `T-02`, `T-06`, `T-07` |
| `CA-AC-060` | `T-03`, `T-05`, `T-07` |
| `CA-AC-061` | `T-04`, `T-05`, `T-07` |
| `CA-AC-214` | `T-05`, `T-07` — **con la cuenta de `CourseOfferability` de `RF-AC-008` · `T-06` enmendada** |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno | | | |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
