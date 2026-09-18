# TASKS — `RF-AC-013` Eliminar curso

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-013` |
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
| `T-01` | `application/DeleteCourseRequest` — `reason` | `RF-AC-008` · `T-09` | El contrato lo declara obligatorio | **Hecha el 18-09-2026** |
| `T-02` | `Course.delete(ahora)`, `estaRetirado()`, `instantanea(...)` con las cuatro listas de identificadores | `RF-AC-008` · `T-05` | Unitaria: `delete` sobre un retirado devuelve falso; la instantánea lleva `status`, `instructor_id`, las cuatro listas y `deleted_at` nulo | **Hecha el 18-09-2026** |
| `T-03` | `CourseQueryRepository.findAliveModulesForUpdate(courseId)` con sus lecciones — **vacío hasta `RF-AC-022`**, con la nota | `RF-AC-008` · `T-08` | Integración: lista vacía; la firma existe | **Hecha el 18-09-2026** |
| `T-04` | **`CourseTreeRetirement`**: marca cada módulo y cada lección con el instante y registra una baja `LOGICAL` por fila con el motivo; hoy recorre una lista vacía | `T-03` | Unitaria con dobles: por cada fila que recibe, un `UPDATE` y una baja | **Hecha el 18-09-2026** |
| `T-05` | `domain/service/DeleteCourseService`: motivo antes de consultar, fila con `FOR UPDATE` en cualquier estado, `404`/`409`, instantánea, marca, baja del curso, arrastre | `T-01`, `T-02`, `T-04` | `CA-AC-070`, `CA-AC-071`, `CA-AC-072`, `CA-AC-073` | **Hecha el 18-09-2026** |
| `T-06` | `interfaces/CourseController`: `POST /api/v1/courses/{id}/deletion`, `@PreAuthorize("hasAuthority('courses:delete')")`, `204` | `T-05` | La ruta entra en `EndpointPermissionsIT` | **Hecha el 18-09-2026** |
| `T-07` | Pruebas de API (`CourseDeletionIT`) con `CA-AC-074` **deshabilitado con motivo**, y el retiro doble en `CourseConcurrencyIT` | `T-06` | `CA-AC-070` a `CA-AC-073` y `CA-AC-075`; `CA-AC-071` comprueba que el motivo inválido no cuesta ni una sentencia | **Hecha el 18-09-2026** |
| `T-08` | Documentación OpenAPI. **La prosa dice** que es `POST` con motivo, que arrastra módulos y lecciones con un registro cada uno, que las relaciones se conservan y el recomendado deja de enseñarse, y que el título queda libre | `T-06` | El contrato declara `204`, `400`, `401`, `403`, `404`, `409` | **Hecha el 18-09-2026** |
| `T-09` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-07` | La fila de `RF-AC-013` refleja el estado | **Hecha el 18-09-2026** |

## 2. Orden de ejecución

`T-01`, `T-02` y `T-03` son independientes; `T-04` sobre `T-03`; `T-05` junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-070`, `CA-AC-073` | `T-02`, `T-05`, `T-07` |
| `CA-AC-071`, `CA-AC-072` | `T-05`, `T-07` |
| `CA-AC-074` | `T-03`, `T-04`, `T-07` — **habilitado por `RF-AC-022` y `RF-AC-028`** |
| `CA-AC-075` | `T-05`, `T-07` — **la parte del aula, desde `RF-AC-034`** |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | No hay módulos ni lecciones que arrastrar hasta `RF-AC-022` y `RF-AC-028`: `CA-AC-074` se escribe deshabilitado y el bloque 3 lo habilita sobre `CourseTreeRetirement` | 18-09-2026 | Responsable técnico | Abierto |
| 2 | «Deja de aparecer como recomendado en el aula» (`CA-AC-075`) exige `RF-AC-018` y `RF-AC-034` | 18-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [x] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde, **salvo `CA-AC-074`, bloqueado y declarado**.
- [x] `mvn verify` en verde en local.
- [x] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [x] El endpoint declara su permiso.
- [x] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [x] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
