# TASKS — `RF-AC-005` Eliminar categoría

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-005` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 17-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 17-09-2026; queda el Pull Request y la enmienda de `RF-AC-016`/`RF-AC-033` |
| Issue | Pendiente de crear |
| Rama | `feature/academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **Mover `DeletionReason` de `modules/products/domain/models` a `shared/audit`** y actualizar los `import` de `PM`; sin cambio de comportamiento | — | `ProductDeletionIT`, `PackageDeletionIT` y `ProductCommentDeleteIT` en verde; la regla de ArchUnit sigue en verde | **Hecha el 17-09-2026** |
| `T-02` | `application/DeleteCourseCategoryRequest` — `reason` | `RF-AC-001` · `T-07` | El contrato lo declara obligatorio | **Hecha el 17-09-2026** |
| `T-03` | `CourseCategory.delete(ahora)`, `estaRetirada()`, `instantanea(courseIds)` | `RF-AC-001` · `T-04` | Unitaria: `delete` sobre una retirada falla; la instantánea lleva `deleted_at` nulo y los identificadores | **Hecha el 17-09-2026** |
| `T-04` | `CourseCategoryQueryRepository.findAliveCourseIdsOf(categoryId)` — **vacío hasta `RF-AC-016`**, con la nota en el código | `RF-AC-001` · `T-06` | Integración: lista vacía; la firma existe | **Hecha el 17-09-2026** |
| `T-05` | `domain/service/DeleteCourseCategoryService`, con `DeletionReason` de `shared/`: motivo antes de consultar, fila con `FOR UPDATE` en cualquier estado, `404`/`409`, instantánea, marca, auditoría `LOGICAL` | `T-01`, `T-02`, `T-03`, `T-04` | `CA-AC-028`, `CA-AC-029`, `CA-AC-030`, `CA-AC-031` | **Hecha el 17-09-2026** |
| `T-06` | `interfaces/CourseCategoryController`: `POST /api/v1/course-categories/{id}/deletion`, `@PreAuthorize("hasAuthority('course-categories:delete')")`, `204` | `T-05` | La ruta entra en `EndpointPermissionsIT` | **Hecha el 17-09-2026** |
| `T-07` | Pruebas de API (`CourseCategoryDeletionIT`) de los seis criterios y la carrera de dos retiros en `CourseCategoryConcurrencyIT` | `T-06` | `CA-AC-028` a `CA-AC-033`; `CA-AC-029` comprueba que el motivo inválido no cuesta ni una sentencia | **Hecha el 17-09-2026** |
| `T-08` | Documentación OpenAPI. **La prosa dice** que es `POST` con motivo, que no arrastra ni rechaza por tener cursos, que la clasificación se conserva y que el nombre queda libre | `T-06` | El contrato declara `204`, `400`, `401`, `403`, `404`, `409` | **Hecha el 17-09-2026** |
| `T-09` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-07` | La fila de `RF-AC-005` refleja el estado | **Hecha el 17-09-2026** |

## 2. Orden de ejecución

**`T-01` primero y solo**, con las suites de `PM` en verde antes de escribir una clase de `AC`: es una refactorización de otro módulo y conviene que quede en su propio commit. `T-02`, `T-03` y `T-04` son independientes; `T-05` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-028`, `CA-AC-031` | `T-03`, `T-05`, `T-07` |
| `CA-AC-029`, `CA-AC-030` | `T-05`, `T-07` |
| `CA-AC-032` | `T-04`, `T-07` — **real desde `RF-AC-016` y `RF-AC-033`** |
| `CA-AC-033` | `T-05`, `T-07` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `CA-AC-032` es trivial hasta que existan la clasificación (`RF-AC-016`) y el aula (`RF-AC-033`); los dos la enmiendan | 17-09-2026 | Responsable técnico | Abierto |
| 2 | `T-01` toca `PM` mientras `MV` construye sobre el mismo árbol: avisar antes de mover el archivo | 17-09-2026 | Responsable técnico | **Cerrado** el 17-09-2026: avisado a las dos sesiones de `MV`; `VoidReason` de `MV` se deja a propósito como copia y no se fusiona, porque anular no es eliminar |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [x] Todos los criterios de aceptación con prueba automatizada en verde.
- [x] `mvn verify` en verde en local, **incluidas las tres suites de retiro de `PM` con `DeletionReason` en `shared/`**.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.

**Verificación (17-09-2026):** `CourseCategoryDeletionIT` (6) y el retiro doble de `CourseCategoryConcurrencyIT`, en verde; `ProductDeletionIT`, `PackageDeletionIT` y `ProductCommentDeleteIT` en verde con `DeletionReason` en `shared/audit`, y `DeletionReasonTest` movida con él.
