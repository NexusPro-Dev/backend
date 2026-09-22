# TASKS — `RF-AC-010` Consultar el detalle de un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-010` |
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
| `T-01` | `CourseQueryRepository`: las cinco lecturas de relaciones y árbol con su firma definitiva, **devolviendo vacío** hasta su requerimiento, cada una con la nota de qué sentencia la sustituye | `RF-AC-008` · `T-08` | Integración: las cinco devuelven vacío; las firmas existen | **Hecha el 18-09-2026** |
| `T-02` | `application/CourseDetailResponse`: `CategoryRef`, `RecommendedCourseRef`, `MembershipRef`, `ModuleDetail`, `LessonSummary`; `deletedAt` y `deletionReason` `NON_NULL` | `RF-AC-008` · `T-09` | El contrato declara las cinco formas anidadas y las listas siempre presentes | **Hecha el 18-09-2026** |
| `T-03` | `CourseDetailReader`: la escalera en su orden definitivo, la suma de duraciones y la cuenta de lecciones sobre lo vivo, `CourseOfferability` del curso y por módulo, motivo por `DeletionReasonReader` si retirado; `GetCourseService` | `T-01`, `T-02` | `CA-AC-051`, `CA-AC-052`, `CA-AC-053` | **Hecha el 18-09-2026** |
| `T-04` | `interfaces/CourseController`: `GET /api/v1/courses/{id}`, `@PreAuthorize("hasAuthority('courses:read')")` | `T-03` | `CA-AC-055`; la ruta entra en `EndpointPermissionsIT` | **Hecha el 18-09-2026** |
| `T-05` | Pruebas de API (`CourseDetailIT`) de los seis criterios, con el contador de sentencias y la comparación de forma con el alta | `T-04` | `CA-AC-051` a `CA-AC-056` | **Hecha el 18-09-2026** |
| `T-06` | Documentación OpenAPI. **La prosa dice** que se devuelve también un retirado, que es la lectura de administración y devuelve todo con estados y marcas, el orden de `offerableReason`, y que el contenido de las lecciones no viaja | `T-04` | El contrato declara `200`, `400`, `401`, `403`, `404` | **Hecha el 18-09-2026** |
| `T-07` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-05` | La fila de `RF-AC-010` refleja el estado | **Hecha el 18-09-2026** |

## 2. Orden de ejecución

`T-01` y `T-02` son independientes; `T-03` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-051`, `CA-AC-053` | `T-01`, `T-03`, `T-05` — **las listas, reales desde sus requerimientos** |
| `CA-AC-052` | `T-03`, `T-05` |
| `CA-AC-054` | `T-03`, `T-05` |
| `CA-AC-055` | `T-04`, `T-05` |
| `CA-AC-056` | `T-02`, `T-05` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Las cuatro listas y el árbol viajan vacíos hasta `RF-AC-016`, `RF-AC-018`, `RF-AC-020`, `RF-AC-022` y `RF-AC-028`; cada uno enmienda `T-01` y `CA-AC-054` | 18-09-2026 | Responsable técnico | Abierto — **el árbol es real desde el 19-09-2026** (`RF-AC-022`, `RF-AC-028`): módulos y lecciones se leen siempre, y `CA-AC-054` cuenta **dos** sentencias sin módulos, tres con ellos y una más con motivo; las tres listas de relaciones siguen vacías hasta el bloque 4 |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde, **con `CA-AC-051` parcial hasta sus enmiendas**.
- [x] `mvn verify` en verde en local.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
