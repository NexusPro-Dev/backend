# TASKS — `RF-AC-004` Editar categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-004` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 17-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 17-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `application/UpdateCourseCategoryRequest` con los cinco `Patchable` e `informaAlgo()` | `RF-AC-001` · `T-07` | Unitaria: ausente, nulo y valor se distinguen en los cinco; cuerpo vacío no informa nada | **Hecha el 17-09-2026** |
| `T-02` | `CourseCategory.update(...)`: aplica lo que viene, valida con el mensaje de cada `VAL-`, normaliza el color **antes** de comparar, devuelve el mapa de cambios y avanza `updatedAt` solo si no está vacío | `RF-AC-001` · `T-04` | Unitaria: los casos de `plan.md` §11 | **Hecha el 17-09-2026** |
| `T-03` | `CourseCategoryRepository.existsAliveNameForOther(name, id)` | `RF-AC-001` · `T-05` | Integración: el propio identificador no choca; otro vivo sí; un retirado no | **Hecha el 17-09-2026** |
| `T-04` | `domain/service/UpdateCourseCategoryService`: fila viva con `FOR UPDATE`, nombre contra otros si viene, escritura y auditoría `UPDATE` solo si algo cambió, relectura del detalle | `T-01`, `T-02`, `T-03` | `CA-AC-021`, `CA-AC-024`, `CA-AC-025`, `CA-AC-026` | **Hecha el 17-09-2026** |
| `T-05` | `interfaces/CourseCategoryController`: `PATCH /api/v1/course-categories/{id}`, `@PreAuthorize("hasAuthority('course-categories:update')")` | `T-04` | `CA-AC-022`, `CA-AC-023`; la ruta entra en `EndpointPermissionsIT` | **Hecha el 17-09-2026** |
| `T-06` | Pruebas de API (`CourseCategoryUpdateIT`) de los siete criterios, incluida la lectura del listado después de reordenar | `T-05` | `CA-AC-021` a `CA-AC-027` | **Hecha el 17-09-2026** |
| `T-07` | Documentación OpenAPI. **La prosa dice** que es parcial, que solo la descripción se vacía, que el color se normaliza, que la portada tiene sus propios endpoints y que reordenar no mueve a las demás | `T-05` | El contrato declara `200`, `400`, `401`, `403`, `404`, `409` | **Hecha el 17-09-2026** |
| `T-08` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-06` | La fila de `RF-AC-004` refleja el estado | **Hecha el 17-09-2026** |

## 2. Orden de ejecución

`T-01`, `T-02` y `T-03` son independientes; `T-04` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-021`, `CA-AC-026` | `T-02`, `T-04`, `T-06` |
| `CA-AC-022`, `CA-AC-023` | `T-01`, `T-05`, `T-06` |
| `CA-AC-024` | `T-03`, `T-04`, `T-06` |
| `CA-AC-025` | `T-02`, `T-04`, `T-06` |
| `CA-AC-027` | `T-04`, `T-06` — usa el listado de `RF-AC-002` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `CA-AC-027` necesita `RF-AC-002` construido para leer el listado reordenado | 17-09-2026 | Responsable técnico | **Cerrado** el 17-09-2026: se construyeron en el mismo pase |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.

**Verificación (17-09-2026):** `CourseCategoryUpdateIT` (7) y los seis casos de `update` en `CourseCategoryTest`, en verde.
