# TASKS — `RF-AC-002` Consultar categorías

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-002` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 17-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `application/ListCourseCategoriesRequest` con validación conjunta, `CourseCategorySortField`, `CourseCategoryItem` y `CourseCategoryPageResponse` | `RF-AC-001` · `T-07` | Unitaria: `sort` fuera de la lista y `includeDeleted` no booleano se recogen **juntos** | Pendiente |
| `T-02` | `CourseCategoryQueryRepository.search` y `count`: la sentencia de página con `courseCount` como subconsulta escalar —literal `0` hasta `RF-AC-016`, con la nota en el código— y los tres órdenes con desempate por `id` | `RF-AC-001` · `T-06` | Integración: tres categorías con órdenes `1, 0, 0` salen `0` (la más antigua), `0`, `1`; `name` ordena sin acentos; `count` no une nada | Pendiente |
| `T-03` | `domain/service/ListCourseCategoriesService` | `T-01`, `T-02` | `CA-AC-010`, `CA-AC-012`, `CA-AC-013` | Pendiente |
| `T-04` | `interfaces/CourseCategoryController`: `GET /api/v1/course-categories`, `@PreAuthorize("hasAuthority('course-categories:read')")` | `T-03` | `CA-AC-011`, `CA-AC-015`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-05` | Pruebas de API (`CourseCategoryListIT`) de los seis criterios, con el contador de sentencias para `CA-AC-014` | `T-04` | `CA-AC-010` a `CA-AC-015`; página de uno y de veinte cuestan dos sentencias | Pendiente |
| `T-06` | Documentación OpenAPI. **La prosa dice** que el orden por omisión es el declarado, que `courseCount` cuenta vivos y no ofrecidos, y que `coverImageUrl` es presente y nula | `T-04` | El contrato declara `200`, `400`, `401`, `403` y los tres valores de `sort` | Pendiente |
| `T-07` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-05` | La fila de `RF-AC-002` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01` y `T-02` son independientes; `T-03` las junta. Todo depende de que `RF-AC-001` haya creado la tabla y el módulo.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-010`, `CA-AC-012`, `CA-AC-013` | `T-02`, `T-03`, `T-05` |
| `CA-AC-011` | `T-02`, `T-04`, `T-05` |
| `CA-AC-014` | `T-02`, `T-05` |
| `CA-AC-015` | `T-01`, `T-04`, `T-05` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `CA-AC-010` es trivial hasta `RF-AC-016`: `courseCount` es cero en todas. Ese requerimiento la enmienda con la subconsulta real y los tres casos —vivos, inactivos, retirados— | 17-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
