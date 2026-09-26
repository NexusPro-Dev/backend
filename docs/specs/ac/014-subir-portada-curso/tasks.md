# TASKS — `RF-AC-014` Subir o reemplazar la portada de un curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-014` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 18-09-2026 |
| Estado | **Hecha** — todas las tareas `Hecha` el 25-09-2026; queda el Pull Request |
| Issue | Pendiente de crear |
| Rama | `feature/ajustes-academia` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `HasCover`, `Course.asignarPortada`; **`CoverUploader`** extraído de `UploadCourseCategoryCoverService`, que pasa a usarlo | `RF-AC-006` · `T-04` | `CourseCategoryCoverIT` sigue en verde; unitaria de `CoverUploader` con dobles: el orden insertar → apuntar → volcar → borrar → auditar | Hecha |
| `T-02` | `domain/service/UploadCourseCoverService` sobre `CoverUploader` | `T-01` | `CA-AC-172`, `CA-AC-173`, `CA-AC-174` | Hecha |
| `T-03` | `interfaces/CourseController`: `PUT /api/v1/courses/{id}/cover`, `multipart`, `@PreAuthorize("hasAuthority('courses:update')")` | `T-02` | `CA-AC-175`; la ruta entra en `EndpointPermissionsIT` | Hecha |
| `T-04` | Pruebas de API (`CourseCoverIT`) | `T-03` | `CA-AC-172` a `CA-AC-175` | Hecha |
| `T-05` | Documentación OpenAPI, con la prosa de `RF-AC-006` sobre el curso y «sin condición de estado ni contenido» | `T-03` | El contrato declara `200`, `400`, `401`, `403`, `404` | Hecha |
| `T-06` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-04` | La fila de `RF-AC-014` refleja el estado | Hecha |

## 2. Orden de ejecución

Lineal, tras `RF-AC-006`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-172` a `CA-AC-174` | `T-01`, `T-02`, `T-04` |
| `CA-AC-175` | `T-03`, `T-04` |

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
