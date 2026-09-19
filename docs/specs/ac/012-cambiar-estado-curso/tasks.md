# TASKS — `RF-AC-012` Cambiar el estado de un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-012` |
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
| `T-01` | `application/ChangeCourseStatusRequest` — `status` con `VAL-002` | `RF-AC-008` · `T-09` | El contrato lo declara obligatorio y con dominio | **Hecha el 18-09-2026** |
| `T-02` | `Course.activate`, `deactivate`, `tieneDescripcionCorta`, `tieneDescripcionLarga` | `RF-AC-008` · `T-05` | Unitaria: sin cambio devuelve falso y no mueve `updatedAt` | **Hecha el 18-09-2026** |
| `T-03` | `CourseQueryRepository.countActiveModulesOf(courseId)` — **cero hasta `RF-AC-022`**, con la nota | `RF-AC-008` · `T-08` | Integración: devuelve cero; la firma existe | **Hecha el 18-09-2026** |
| `T-04` | `domain/service/ChangeCourseStatusService`: fila viva con `FOR UPDATE`, mismo estado sin escribir, los tres `409` juntos al activar, auditoría, relectura | `T-01` a `T-03` | `CA-AC-065`, `CA-AC-066`, `CA-AC-067`, `CA-AC-068` | **Hecha el 18-09-2026** |
| `T-05` | `interfaces/CourseController`: `PATCH /api/v1/courses/{id}/status`, `@PreAuthorize("hasAuthority('courses:update')")` | `T-04` | `CA-AC-069`; la ruta entra en `EndpointPermissionsIT` | **Hecha el 18-09-2026** |
| `T-06` | Pruebas de API (`CourseStatusIT`) de los seis criterios, con `CA-AC-064` **deshabilitado con motivo** hasta `RF-AC-024`, y la doble activación en `CourseConcurrencyIT` | `T-05` | `CA-AC-065` a `CA-AC-069` en verde; `CA-AC-064` escrito y marcado | **Hecha el 18-09-2026** |
| `T-07` | Documentación OpenAPI. **La prosa dice** qué exige activar y que los motivos van juntos, que no exige membresías, que desactivar no exige nada, y que vaciar después no desactiva | `T-05` | El contrato declara `200`, `400`, `401`, `403`, `404`, `409` | **Hecha el 18-09-2026** |
| `T-08` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-06` | La fila de `RF-AC-012` refleja el estado | **Hecha el 18-09-2026** |

## 2. Orden de ejecución

`T-01`, `T-02` y `T-03` son independientes; `T-04` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-064` | `T-03`, `T-04`, `T-06` — **habilitado por `RF-AC-024`** |
| `CA-AC-065`, `CA-AC-066`, `CA-AC-067`, `CA-AC-068` | `T-02`, `T-04`, `T-06` |
| `CA-AC-069` | `T-01`, `T-05`, `T-06` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Ningún curso puede activarse hasta que existan módulos activos (`RF-AC-022`, `RF-AC-024`): `CA-AC-064` se escribe deshabilitado y ese requerimiento lo habilita | 18-09-2026 | Responsable técnico | **Cerrado** el 19-09-2026: `CA-AC-064` habilitado con `RF-AC-024` (`CourseStatusIT.activarConUnModuloActivo`) |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde, **salvo `CA-AC-064`, bloqueado y declarado**.
- [x] `mvn verify` en verde en local.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
