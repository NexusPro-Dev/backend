# TASKS — `RF-AC-009` Consultar cursos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-AC-009` |
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
| `T-01` | `application/ListCoursesRequest` con validación conjunta, `CourseSortField`, `CourseItem` con `CategoryRef`, `CoursePageResponse` | `RF-AC-008` · `T-09` | Unitaria: `sort` fuera de lista, `difficulty` y `status` fuera de dominio se recogen **juntos** | Pendiente |
| `T-02` | `CourseQueryRepository.search` y `count`: la sentencia de página con `JOIN users`, las dos subconsultas de cuentas —literal `0` hasta `RF-AC-022`/`RF-AC-028`— y el predicado de `categoryId` —`false` hasta `RF-AC-016`—, con las notas en el código; los tres órdenes con desempate por `id` | `RF-AC-008` · `T-08` | Integración: tres cursos con órdenes `1, 0, 0` salen `0` (el más antiguo), `0`, `1`; `title` ordena sin acentos; `count` no une nada; `categoryId` devuelve vacío | Pendiente |
| `T-03` | `CourseQueryRepository.findCategoriesOf(courseIds)` — **vacío hasta `RF-AC-016`**, con la nota; se cortocircuita con lista vacía | `RF-AC-008` · `T-08` | Integración: lista vacía; la firma existe | Pendiente |
| `T-04` | `domain/service/ListCoursesService`: filtros canónicos, página, categorías agrupadas, `CourseOfferability` por fila, total | `T-01`, `T-02`, `T-03` | `CA-AC-044`, `CA-AC-046`, `CA-AC-047`, `CA-AC-050` | Pendiente |
| `T-05` | `interfaces/CourseController`: `GET /api/v1/courses`, `@PreAuthorize("hasAuthority('courses:read')")` | `T-04` | `CA-AC-045`, `CA-AC-049`; la ruta entra en `EndpointPermissionsIT` | Pendiente |
| `T-06` | Pruebas de API (`CourseListIT`) de los siete criterios, con el contador de sentencias para `CA-AC-048` | `T-05` | `CA-AC-044` a `CA-AC-050`; página de uno y de veinte cuestan dos sentencias | Pendiente |
| `T-07` | Documentación OpenAPI. **La prosa dice** el orden por omisión, que las cuentas son de vivos, que `offerable` no filtra, que `categoryId` devuelve vacío hasta que exista la clasificación, y que el nombre del instructor es el actual | `T-05` | El contrato declara `200`, `400`, `401`, `403` y los tres valores de `sort` | Pendiente |
| `T-08` | Actualizar la matriz de `docs/requirements.md` y `docs/api/index.md` | `T-06` | La fila de `RF-AC-009` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01`, `T-02` y `T-03` son independientes; `T-04` las junta. Todo depende de que `RF-AC-008` haya creado la tabla, el `JOIN` del instructor y `CourseOfferability`.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-AC-044`, `CA-AC-047` | `T-02`, `T-03`, `T-04`, `T-06` — **completos desde `RF-AC-016`, `RF-AC-022` y `RF-AC-028`** |
| `CA-AC-045` | `T-02`, `T-05`, `T-06` |
| `CA-AC-046`, `CA-AC-050` | `T-02`, `T-04`, `T-06` |
| `CA-AC-048` | `T-02`, `T-06` |
| `CA-AC-049` | `T-01`, `T-05`, `T-06` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `categories`, `moduleCount` y `lessonCount` son literales hasta `RF-AC-016`, `RF-AC-022` y `RF-AC-028`, que enmiendan `T-02` y `T-03` y completan `CA-AC-044` y `CA-AC-047` | 18-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde, **con `CA-AC-044` y `CA-AC-047` marcados como parciales hasta sus enmiendas**.
- [ ] `mvn verify` en verde en local.
- [ ] El endpoint declara su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, prosa incluida.
- [ ] Matriz de trazabilidad y `docs/api/index.md` actualizados.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
