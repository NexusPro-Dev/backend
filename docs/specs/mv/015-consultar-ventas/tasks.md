# TASKS — `RF-MV-015` Consultar las ventas de mi alcance

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-015` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 21-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/ventas-de-mi-alcance` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V32`**: siembra `movements:list-sales` (`01a0c143-2c00-700c-9c4f-5e7ad7000008`) y lo da a **todo rol por su tipo**, los tres tipos; guardas 125 / `SUPERADMIN` 125 / `ADMIN` 119 / cero parejas sin padre | — | `PermissionsSeedIT`, `PermissionIT`, `JpaPermissionQueryRepositoryIT` y `ListPermissionsServiceIT` cuentan 125; `CLIENTE` lo porta | `Pendiente` |
| `T-02` | `SP` publica **`CommercialReach`** (`users/application`): `Reach reachOf(UUID)`, con `Kind` `EVERYTHING` \| `NETWORK` \| `OWN` y el conjunto de vendedores en `NETWORK`, **con el actor dentro** | — | Javadoc con la precedencia y la razón de vivir en `SP` (`architecture.md` §15.2) | `Pendiente` |
| `T-03` | `JpaCommercialReach`: tipos de rol vivos del actor con el predicado de `JpaEffectivePermissions`; el subárbol con la `WITH RECURSIVE` de `RF-SP-057` (vigente, `UNION`); precedencia en Java | `T-02` | `CommercialReachIT`: los tres `Kind`; funcionario+vendedor → `EVERYTHING`; vendedor+consumidor → `NETWORK`; sin roles → `OWN`; rol retirado o inactivo no cuenta; el conjunto trae raíz, hijos y nietos y **no** al que tiene `ended_at`; sin subordinados → solo la raíz | `Pendiente` |
| `T-04` | `ListSalesRequest`; `MovementRepository.SalesFilter`, `findSales` y `countSales`; `JpaMovementRepository` con **un** predicado (`mt.code = 'VENTA'`, el alcance de `plan.md` §4.4, los filtros) | `T-02` | Página y conteo sobre la misma `Filtro`; `EXISTS` sobre las líneas, una fila por venta | `Pendiente` |
| `T-05` | `ListSalesService`: valida junto, pide el alcance, **corta sin consultar** con `userId` fuera del alcance, pagina, cuenta acotado, vendedores con `findSellersOf`, mapea a `MovementResponse` | `T-03`, `T-04` | El corte se prueba con el repositorio doblado: fuera del alcance no se llama | `Pendiente` |
| `T-06` | `MovementController`: `GET /api/v1/movements/sales` con `@PreAuthorize("hasAuthority('movements:list-sales')")`, documentado: qué ve cada tipo de rol, qué es `userId`, por qué fuera del alcance es vacío, que ni `movements:read` ni `list-own` abren | `T-05` | La ruta en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT` | `Pendiente` |
| `T-07` | `SalesIT`: `CA-MV-122` a `CA-MV-131` sobre el árbol de `plan.md` §1 —manager, dos directores, agentes bajo cada uno, un consumidor, un funcionario, un vendedor suelto y uno con `ended_at`— con una venta por vendedor | `T-06` | Cada nivel ve **exactamente** su conjunto; `userId` fuera del alcance vacío; `403` con `movements:read` y con `movements:list-own` | `Pendiente` |
| `T-08` | `SalesBoundedCountIT`: `CA-MV-132` con `nexus.pagination.count-limit=3` | `T-06` | Total 3 e inexacto por encima; exacto por debajo | `Pendiente` |
| `T-09` | Contrato OpenAPI regenerado y prosa releída; `docs/api/index.md`; matriz e indicadores de `requirements.md`; ficha de `RF-MV-015` en `mv.md` a `En desarrollo`; issue | `T-07` | `openapi.json` declara la ruta con `x-required-permission: movements:list-sales` y **ninguna forma nueva** | `Pendiente` |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-122` a `CA-MV-126` | `T-03`, `T-04`, `T-05`, `T-07` |
| `CA-MV-127`, `CA-MV-128` | `T-03`, `T-05`, `T-07` |
| `CA-MV-129` | `T-04`, `T-05`, `T-07` |
| `CA-MV-130` | `T-01`, `T-06`, `T-07` |
| `CA-MV-131` | `T-04`, `T-07` |
| `CA-MV-132` | `T-04`, `T-08` |

---

## 3. Desviaciones respecto del plan

Ninguna todavía.

---

## 4. Bloqueos declarados

1. **La comprobación de arquitectura de `ADR-005`** —que todo listado declare su alcance— no se construye aquí y sigue pendiente. No bloquea este requerimiento; bloquea que D-22 se dé por cerrada.
2. **El detalle de una venta ajena de mi red** no existe (`RF-MV-007`): la fila se ve y no se abre. Declarado en `spec.md` §14.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los once criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements/mv.md`, `requirements.md` y `api/index.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
