# TASKS — `RF-AC-029` Editar lección

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-029` |
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
| `T-01` | `application/UpdateLessonRequest` con los siete `Patchable` e `informaAlgo()` | `RF-AC-028` · `T-06` | Unitaria: ausente, nulo y valor se distinguen, `open` incluido | Pendiente |
| `T-02` | `Lesson.update(...)`: pareja resultante por `LessonContent` **antes** de aplicar; diff con `content_length` | `RF-AC-028` · `T-02` | Unitaria: las cuatro combinaciones de la pareja, el `400` que no aplica nada, los nulos, sin cambios | Pendiente |
| `T-03` | `LessonRepository.existsAliveTitleInModuleForOther` y `findAliveByIdInModuleForUpdate` | `RF-AC-028` · `T-03` | Integración: el propio no choca; la de otro módulo devuelve vacío | Pendiente |
| `T-04` | `domain/service/UpdateLessonService` | `T-01` a `T-03` | `CA-AC-099`, `CA-AC-100`, `CA-AC-103`, `CA-AC-104` | Pendiente |
| `T-05` | `interfaces/LessonController`: `PATCH …/lessons/{lessonId}`, `@PreAuthorize("hasAuthority('courses:update')")` | `T-04` | `CA-AC-101`, `CA-AC-102`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-06` | Pruebas de API (`LessonUpdateIT`) de los seis criterios, con la lectura del detalle del módulo y del curso tras cambiar la duración | `T-05` | `CA-AC-099` a `CA-AC-104` | Pendiente |
| `T-07` | Documentación OpenAPI. **La prosa dice** que el tipo se corrige y el contenido resultante tiene que casar, que el contenido se vacía en cualquier estado y qué cuesta, y que el contenido se audita por longitud | `T-05` | El contrato declara `200`, `400`, `401`, `403`, `404`, `409` | Pendiente |
| `T-08` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-06` | La fila de `RF-AC-029` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01` a `T-03` independientes; `T-04` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-099`, `CA-AC-103`, `CA-AC-104` | `T-02`, `T-04`, `T-06` |
| `CA-AC-100` | `T-02`, `T-04`, `T-06` |
| `CA-AC-101`, `CA-AC-102` | `T-01`, `T-03`, `T-05`, `T-06` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Dos huecos del catálogo anotados en `spec.md` §14 para el responsable del proyecto —la lección activa y vacía se enseña vacía; administración no lee el contenido sin editar—; no bloquean la construcción | 18-09-2026 | Responsable del proyecto | Abierto |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
