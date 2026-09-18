# TASKS — `RF-AC-034` Consultar el detalle de un curso como alumno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-034` |
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
| `T-01` | `domain/models/LessonOfferability`; `ModuleOfferability` pasa a contar por él — refactorización sin cambio de resultado | `RF-AC-028` · `T-02` | Unitaria: los cuatro casos; las pruebas de `ModuleOfferability` siguen en verde | Pendiente |
| `T-02` | `CourseQueryRepository`: `findOfferedDetail(id)` con instructor y entradas de `CourseOfferability`; `findRecommendedOf` con dificultad y portada | `RF-AC-033` · `T-01` | Integración: un curso inactivo se devuelve con sus entradas y el lector lo descarta | Pendiente |
| `T-03` | `application/ClassroomCourseResponse` con `RecommendedCourseItem`, `ClassroomModuleItem`, `ClassroomLessonItem`; `currentMembership`, `coverImageUrl` y los videos presentes y nulos | — | El contrato declara las formas anidadas sin `status` ni `offerable` | Pendiente |
| `T-04` | `ClassroomCourseReader` y `GetClassroomCourseService`: puerto, curso o `404`, relaciones y árbol, filtro por los tres objetos, sumas, `StudentAccess` | `T-01`, `T-02`, `T-03`, `RF-AC-033` · `T-02` | `CA-AC-195` a `CA-AC-199` | Pendiente |
| `T-05` | `ClassroomController`: `GET /api/v1/courses/available/{id}`, `@PreAuthorize("hasAuthority('courses:learn')")` | `T-04`, `RF-AC-033` · `T-06` | `CA-AC-201`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-06` | Pruebas de API (`ClassroomCourseDetailIT`) de los siete criterios, con el contador de sentencias | `T-05` | `CA-AC-195` a `CA-AC-201` | Pendiente |
| `T-07` | **Enmendar `RF-AC-013`**: `CA-AC-075` deja de estar bloqueado; su `tasks.md` cierra el bloqueo 2 y `CourseDeletionIT` gana el caso del recomendado retirado en el aula | `T-06` | El curso retirado no aparece en `recommendedCourses` | Pendiente |
| `T-08` | Documentación OpenAPI. **La prosa dice** que solo viaja lo ofrecido y sin estados, que el curso cerrado se enseña entero con `accessible` y `memberships` como invitación, que el `404` no distingue «no se ofrece» de «no existe», y que el contenido se pide por lección | `T-05` | El contrato declara `200`, `400`, `401`, `403`, `404` | Pendiente |
| `T-09` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-06` | La fila de `RF-AC-034` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01` primero, con las unitarias de `ModuleOfferability` como red; `T-02` y `T-03` en paralelo; `T-04` las junta.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-195`, `CA-AC-196` | `T-02`, `T-04`, `T-06` |
| `CA-AC-197` | `T-01`, `T-04`, `T-06` |
| `CA-AC-198` | `T-04`, `T-06` |
| `CA-AC-199` | `T-02`, `T-04`, `T-06`, `T-07` |
| `CA-AC-200` | `T-04`, `T-06` |
| `CA-AC-201` | `T-05`, `T-06` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| — | Ninguno | | | |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
