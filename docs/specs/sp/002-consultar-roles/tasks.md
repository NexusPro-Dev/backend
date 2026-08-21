# TASKS — `RF-SP-002` Consultar roles

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-002` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md) |
| `plan.md` aprobado el | 21-08-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/consultar-roles` |
| Aprobadas por | Pendiente |

!!! info "Qué va en este documento"

    **En qué pasos, en qué orden y cómo se verifica cada uno.**

    **Prueba de pertenencia:** si no puede marcarse como hecho, no es una tarea.

    **Es la fuente de verdad de las tareas.** El Issue de GitHub coordina y enlaza aquí; no la sustituye ni la duplica. Si las dos listas discrepan, manda este archivo.

    No se escribe hasta que `plan.md` esté aprobado, y ninguna tarea se ejecuta hasta que este documento lo esté (Art. I.6).

---

## 1. Tareas

Este requerimiento estrena la mecánica de paginación de todo el sistema —`T-02` y `T-03`—, de modo que dos de sus tareas tienen alcance muy superior al endpoint que las motiva. Se hacen primero y se prueban por separado, porque todo listado posterior las hereda.

**Prerrequisitos:** `V1` a `V7` (`RF-SP-010` y `RF-SP-001`) aplicadas, y la infraestructura de `shared/error` y `shared/security` de `RF-SP-001` integrada.

| # | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración `V8__create_role_search_index.sql`: `ix_roles_busqueda`, GIN de trigramas multicolumna sobre `f_unaccent(lower(code))` y `f_unaccent(lower(name))`, no parcial | — | `mvn flyway:info` la lista aplicada; el índice se crea sin error, lo que confirma que `f_unaccent` es indexable | Pendiente |
| `T-02` | `shared/api/PageResponse<T>`: envoltura con `content`, `page`, `size`, `totalElements`, `totalPages` y `totalIsExact`, sin serializar el `Page` de Spring Data | — | Prueba unitaria de serialización: el JSON tiene exactamente esos seis campos y no depende de ningún tipo del framework | Pendiente |
| `T-03` | `shared/api/PageRequestFactory` y la configuración `nexus.pagination.default-size` y `max-size`, sin usar `spring.data.web.pageable.max-page-size` | — | Prueba unitaria: `size = 101` es **rechazado**, no recortado; `page` negativa también; los valores por omisión salen de la configuración | Pendiente |
| `T-04` | `application`: `ListRolesQuery`, `RoleListItem`, el enum cerrado `RoleSortField` y el puerto `RoleQueryRepository` | — | Prueba unitaria: `RoleSortField` resuelve los seis campos de la lista blanca y rechaza cualquier otro antes de construir consulta alguna | Pendiente |
| `T-05` | `infrastructure/JpaRoleQueryRepository`: predicado por filtros presentes, `LEFT JOIN` al padre, proyección con `cb.construct` sobre once columnas y desempate por `id` en todo ordenamiento | `T-01`, `T-04` | Prueba de integración: una página de veinte roles con padre ejecuta **dos** sentencias como máximo, ninguna sobre `role_permissions`, y el rol raíz aparece con padre nulo | Pendiente |
| `T-06` | Búsqueda: recorte del término, escape de `\`, `%` y `_`, parámetro enlazado y normalización con `f_unaccent` en la base de datos, nunca en Java | `T-05` | Pruebas de integración: `administracion` encuentra `Administración`, `100%` no devuelve el catálogo entero, y el `EXPLAIN` muestra el recorrido de `ix_roles_busqueda` | Pendiente |
| `T-07` | Conteo: misma función de predicado para datos y total, conteo sin el `LEFT JOIN` y omitido cuando el resultado lo hace deducible | `T-05` | Prueba de integración: filtro y conteo no pueden divergir, y una primera página incompleta no dispara la sentencia de conteo | Pendiente |
| `T-08` | `application/ListRolesService` con `@Transactional(readOnly = true)` | `T-06`, `T-07` | Prueba de integración: la transacción es de solo lectura y un intento de escritura desde este camino falla | Pendiente |
| `T-09` | `api/ListRolesRequest` con Bean Validation de `VAL-001` a `VAL-004`, y `api/RoleListItemResponse` reutilizando `RoleSummaryResponse` para el padre | `T-08` | Prueba de API: los cuatro `400` se evalúan y se devuelven **juntos** en `errors`, cada uno con su `error_code` y su campo | Pendiente |
| `T-10` | `api/RoleController`: añade `GET /api/v1/roles` con el permiso `roles:read` declarado sobre el método | `T-09` | Prueba de API: la consulta autorizada devuelve `200` con la envoltura de `T-02`; sin el permiso, `403` | Pendiente |
| `T-11` | Pruebas de API e integración de los criterios de aceptación de `spec.md` §12 | `T-10` | La suite cubre `CA-SP-009` a `CA-SP-015`, `CA-SP-147` y `CA-SP-148` | Pendiente |
| `T-12` | Pruebas de los casos límite de `spec.md` §13 y de las decisiones de `plan.md` §11: página fuera de rango, padre inexistente, ordenamiento arbitrario, ordenamiento con valores repetidos y número de sentencias | `T-10` | `sort=(select 1),asc` devuelve `400` sin llegar a la base de datos; recorrer todas las páginas ordenando por `status` devuelve cada rol exactamente una vez | Pendiente |
| `T-13` | Documentación OpenAPI del endpoint: los ocho parámetros, la envoltura paginada y los estados `400`, `401`, `403` y `500` | `T-11` | El contrato publicado coincide con el comportamiento real (Art. VIII.6) | Pendiente |
| `T-14` | Actualizar la matriz de trazabilidad de `docs/requirements.md` | `T-11` | La fila de `RF-SP-002` refleja el estado y enlaza esta tripleta | Pendiente |

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

## 2. Orden de ejecución

```mermaid
graph LR
    T01[T-01] --> T05[T-05]
    T04[T-04] --> T05 --> T06[T-06]
    T05 --> T07[T-07]
    T06 --> T08[T-08]
    T07 --> T08 --> T09[T-09] --> T10[T-10]
    T02[T-02] --> T09
    T03[T-03] --> T09
    T10 --> T11[T-11] --> T13[T-13]
    T11 --> T14[T-14]
    T10 --> T12[T-12]
```

`T-01` a `T-04` no dependen entre sí y pueden ir en paralelo. `T-02` y `T-03` son infraestructura compartida: conviene integrarlas en su propio Pull Request, porque las heredan todos los listados posteriores.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea que lo cubre |
|---|---|
| `CA-SP-009` | `T-02`, `T-07`, `T-11` |
| `CA-SP-010` | `T-05`, `T-11` |
| `CA-SP-011` | `T-05`, `T-11` |
| `CA-SP-012` | `T-05`, `T-11` |
| `CA-SP-013` | `T-05`, `T-11` |
| `CA-SP-014` | `T-03`, `T-09`, `T-11` |
| `CA-SP-015` | `T-10`, `T-11` |
| `CA-SP-147` | `T-01`, `T-06`, `T-11` |
| `CA-SP-148` | `T-05`, `T-11` |

Los casos límite de `spec.md` §13 los cubre `T-12`. Las reglas de ArchUnit de `RF-SP-001` cubren también este requerimiento y no se añade ninguna: no toca `domain`.

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `T-01` falla si `V1__create_shared_functions.sql` (`RF-SP-010`) no está aplicada. Es el momento correcto de enterarse y no requiere acción previa más allá del orden de integración | 21-08-2026 | Responsable técnico | Abierto |
| 2 | La estrategia de conteo exacto de `T-07` **no debe heredarse** en `RF-SP-011` a `RF-SP-014`, donde las tablas crecen sin límite (`plan.md` §4 y §8). No bloquea estas tareas; sí condiciona las de aquellos | 21-08-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

El requerimiento no está terminado hasta cumplir **todas** las condiciones de la constitución §16:

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] Los endpoints nuevos declaran su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
