# TASKS — `RF-MV-006` Consultar los movimientos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-006` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| Plan | [`plan.md`](plan.md) v0.2.0 |
| `plan.md` aprobado el | 17-09-2026 |
| Estado | **En revisión** — `T-01` a `T-11` `Hecha` el 17-09-2026; `T-12` a `T-15` (§1.1) `Pendiente` |
| Issue | [#66](https://github.com/NexusPro-Dev/backend/issues/66) |
| Rama | `feature/venta-de-productos`; la enmienda del 21-09-2026, en `feature/filtro-por-tipo-de-movimiento` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V15`**: `ix_movements_occurred_at` sobre `movements (occurred_at DESC, id DESC)` | — | Existe con las dos columnas en ese orden y sentido | **Hecha** — 17-09-2026 |
| `T-02` | `ListMovementsRequest` — página, tamaño y los seis filtros; estado y código normalizados a mayúsculas | — | `code` en minúsculas llega al repositorio en mayúsculas | **Hecha** — 17-09-2026 |
| `T-03` | `MovementResponse` — la fila con `type` y `confirmedAt`, **sin `role`**; `confirmedAt` nulable con `types`, `sellers` con `@JsonInclude(ALWAYS)` | — | El contrato dice que `confirmedAt` puede ser nulo y que `sellers` es obligatoria | **Hecha** — 17-09-2026 |
| `T-04` | `MovementRepository`: `MovementFilter`, `MovementRow`, `findAll` y `countAll` (este último devuelve `BoundedCount`) | `T-02` | El puerto no conoce la petición HTTP | **Hecha** — 17-09-2026 |
| `T-05` | `JpaMovementRepository`: **un** predicado para las dos sentencias; el vendedor por `EXISTS`; el código por igualdad; el rango semiabierto; el conteo con `LIMIT techo + 1` | `T-04` | La página y el total filtran igual. Una venta con dos líneas del mismo vendedor **cuenta una vez** | **Hecha** — 17-09-2026 |
| `T-06` | `ListMovementsService`: valida estado y rango **juntos**, pagina, cuenta acotado, pide los vendedores con `findSellersOf` y mapea | `T-05` | Estado inventado y `from > to` en la misma petición devuelven **dos** errores en una respuesta | **Hecha** — 17-09-2026 |
| `T-07` | `MovementController`: `GET /api/v1/movements` con `@PreAuthorize("hasAuthority('movements:read')")`, documentado | `T-06` | **No** entra en la lista blanca de `EndpointPermissionsIT` | **Hecha** — 17-09-2026 |
| `T-08` | `MovementsIT`: `CA-MV-068` a `CA-MV-081` | `T-07` | `CA-MV-069` con un actor que **tiene** movimientos propios; la forma de la fila sobre el JSON en crudo | **Hecha** — 17-09-2026 |
| `T-09` | `MovementsBoundedCountIT`: `CA-MV-082` con `nexus.pagination.count-limit=3` | `T-07` | Cuatro movimientos: total 3 e inexacto; dos: total 2 y exacto | **Hecha** — 17-09-2026 |
| `T-10` | Regenerar el contrato OpenAPI y releer la prosa del `GET` nuevo | `T-08` | `docs/api/openapi.json` declara `confirmedAt` con `types` y no con `nullable` | **Hecha** — 17-09-2026 |
| `T-11` | Enmiendas: `requirements/mv.md` §4.1 (nombre, fila y control de cambios), `requirements.md` (fila, indicadores y control de cambios), `modelo-datos.md` (el índice) | `T-10` | `RF-MV-006` deja de llamarse «Consultar ventas» en los dos catálogos | **Hecha** — 17-09-2026 |

### 1.1 El filtro por tipo — 21-09-2026

Enmienda de hecho (Art. I.7) sobre un requerimiento construido: `spec.md` 0.2.0 y `plan.md` 0.2.0 **antes** del código. Sin migración.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-12` | `ListMovementsRequest` gana `type`, normalizado a mayúsculas como el estado; `MovementFilter` lo lleva; `filtroGlobal` lo compara con `mt.code` **en el mismo predicado** de la página y el conteo | — | Con `type` puesto, el total cuenta lo que la página devuelve | `Pendiente` |
| `T-13` | `ListMovementsService` valida `type` contra el catálogo con `findTypeByCode` —`VAL-005`— **junto** con el estado y el rango | `T-12` | Tres parámetros mal escritos, tres errores en una respuesta | `Pendiente` |
| `T-14` | `MovementController`: el parámetro documentado en la `@Operation` —códigos vigentes, `400` si no existe—; `MovementsIT`: `CA-MV-119` con un **segundo tipo sembrado en la prueba**, en minúsculas, combinado con el estado, y el inexistente junto con el estado | `T-13` | La prueba deja `movement_types` como lo encontró | `Pendiente` |
| `T-15` | Contrato OpenAPI regenerado y prosa releída; `docs/api/index.md`; matriz de `requirements.md` | `T-14` | `openapi.json` declara `type` en `GET /api/v1/movements` | `Pendiente` |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-068`, `CA-MV-069`, `CA-MV-070` | `T-07`, `T-08` |
| `CA-MV-071` | `T-01`, `T-05`, `T-08` |
| `CA-MV-072` a `CA-MV-078` | `T-05`, `T-06`, `T-08` |
| `CA-MV-079`, `CA-MV-080`, `CA-MV-081` | `T-03`, `T-08` |
| `CA-MV-082` | `T-05`, `T-09` |
| `CA-MV-119` | `T-12`, `T-13`, `T-14` — 21-09-2026 |

---

## 3. Desviaciones respecto del plan

**El predicado se arma por partes y no con `CAST(:param) IS NULL`**, que es lo que se escribió primero: un identificador nulo enlazado sin tipo es lo que PostgreSQL no sabe convertir, y la forma que ya funciona —la clase `Filtro` de `JpaAuditQueryRepository`, un predicado por filtro añadido solo cuando su valor viene— se copia tal cual. Sigue escrito una vez para la página y el conteo, que es lo que el plan exige.

**Una prueba ajena se corrigió de paso**: `UserLifecycleIT.elTelefonoDeLaEmpresaSeCambiaYSeVacia` fallaba según el orden de la suite porque leía «el último» asiento de auditoría por `occurred_at`, y los dos asientos de una misma operación lo comparten. Pasa a contar el asiento por su contenido. No es de este requerimiento; se anota para que no parezca un cambio sin motivo.

**`DevelopmentSeedIT` quedó fuera de la corrida** (`-Dit.test='!DevelopmentSeedIT'`): un cambio sin confirmar y ajeno a esta rama sobre `semilla-productos.sql` renombra `RENOVAR_BECA` y la prueba sigue borrando por el código viejo. Declarado, no corregido aquí.

---

## 4. Definición de terminado

- [x] `./mvnw clean verify` en verde — 1845 de integración, 0 fallos (con `DevelopmentSeedIT` excluida, ver §3).
- [x] Los quince criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements/mv.md`, `requirements.md` y `modelo-datos.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
