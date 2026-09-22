# TASKS — `RF-AC-026` Subir o reemplazar la portada de un módulo

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-026` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `CourseModule.asignarPortada` e implementación de `HasCover` | `RF-AC-014` · `T-01`, `RF-AC-022` · `T-02` | Unitaria | Pendiente |
| `T-02` | `domain/service/UploadCourseModuleCoverService` sobre `CoverUploader` | `T-01` | `CA-AC-179`, `CA-AC-180`, `CA-AC-181` | Pendiente |
| `T-03` | `interfaces/CourseModuleController`: `PUT …/modules/{moduleId}/cover`, `multipart`, `@PreAuthorize("hasAuthority('courses:update')")` | `T-02` | `CA-AC-182`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-04` | Pruebas de API (`CourseModuleCoverIT`) | `T-03` | `CA-AC-179` a `CA-AC-182` | Pendiente |
| `T-05` | Documentación OpenAPI, con la prosa de `RF-AC-006` sobre el módulo | `T-03` | El contrato declara `200`, `400`, `401`, `403`, `404` | Pendiente |
| `T-06` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-04` | La fila de `RF-AC-026` refleja el estado | Pendiente |

## 2. Orden de ejecución

Lineal, tras `RF-AC-014` y `RF-AC-022`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-179` a `CA-AC-181` | `T-01`, `T-02`, `T-04` |
| `CA-AC-182` | `T-03`, `T-04` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno | | | |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
