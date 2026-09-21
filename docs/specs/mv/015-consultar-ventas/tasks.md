# TASKS — `RF-MV-015` Consultar las ventas de mi alcance

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-015` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 21-09-2026 |
| Estado | **En revisión** — `T-01` a `T-09` `Hecha` el 21-09-2026; `T-10` y `T-11` (§1.1) `Pendiente` |
| Issue | Pendiente de crear |
| Rama | `feature/ventas-de-mi-alcance` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V32`**: siembra `movements:list-sales` (`01a0c143-2c00-700c-9c4f-5e7ad7000008`) y lo da a **todo rol por su tipo**, los tres tipos; guardas 125 / `SUPERADMIN` 125 / `ADMIN` 119 / cero parejas sin padre | — | `PermissionsSeedIT`, `PermissionIT`, `JpaPermissionQueryRepositoryIT` y `ListPermissionsServiceIT` cuentan 125; `CLIENTE` lo porta | **Hecha** — 21-09-2026 |
| `T-02` | `SP` publica **`CommercialReach`** (`users/application`): `Reach reachOf(UUID)`, con `Kind` `EVERYTHING` \| `NETWORK` \| `OWN` y el conjunto de vendedores en `NETWORK`, **con el actor dentro** | — | Javadoc con la precedencia y la razón de vivir en `SP` (`architecture.md` §15.2) | **Hecha** — 21-09-2026 |
| `T-03` | `JpaCommercialReach`: tipos de rol vivos del actor con el predicado de `JpaEffectivePermissions`; el subárbol con la `WITH RECURSIVE` de `RF-SP-057` (vigente, `UNION`); precedencia en Java | `T-02` | `CommercialReachIT`: los tres `Kind`; funcionario+vendedor → `EVERYTHING`; vendedor+consumidor → `NETWORK`; sin roles → `OWN`; rol retirado o inactivo no cuenta; el conjunto trae raíz, hijos y nietos y **no** al que tiene `ended_at`; sin subordinados → solo la raíz | **Hecha** — 21-09-2026 (`CommercialReachIT`, 6) |
| `T-04` | `ListSalesRequest`; `MovementRepository.SalesFilter`, `findSales` y `countSales`; `JpaMovementRepository` con **un** predicado (`mt.code = 'VENTA'`, el alcance de `plan.md` §4.4, los filtros) | `T-02` | Página y conteo sobre la misma `Filtro`; `EXISTS` sobre las líneas, una fila por venta | **Hecha** — 21-09-2026 |
| `T-05` | `ListSalesService`: valida junto, pide el alcance, **corta sin consultar** con `userId` fuera del alcance, pagina, cuenta acotado, vendedores con `findSellersOf`, mapea a `MovementResponse` | `T-03`, `T-04` | El corte se prueba por HTTP (`CA-MV-127`): fuera del alcance la respuesta es la página vacía con el total exacto en cero, que es lo que el corte produce y la sentencia no | **Hecha** — 21-09-2026 |
| `T-06` | `MovementController`: `GET /api/v1/movements/sales` con `@PreAuthorize("hasAuthority('movements:list-sales')")`, documentado: qué ve cada tipo de rol, qué es `userId`, por qué fuera del alcance es vacío, que ni `movements:read` ni `list-own` abren | `T-05` | La ruta en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT` | **Hecha** — 21-09-2026 |
| `T-07` | `SalesIT`: `CA-MV-122` a `CA-MV-131` sobre el árbol de `plan.md` §1 —manager, dos directores, agentes bajo cada uno, un consumidor, un funcionario, un vendedor suelto y uno con `ended_at`— con una venta por vendedor | `T-06` | Cada nivel ve **exactamente** su conjunto; `userId` fuera del alcance vacío; `403` con `movements:read` y con `movements:list-own` | **Hecha** — 21-09-2026 (`SalesIT`, 11) |
| `T-08` | `SalesBoundedCountIT`: `CA-MV-132` con `nexus.pagination.count-limit=3` | `T-06` | Total 3 e inexacto por encima; exacto por debajo | **Hecha** — 21-09-2026 (`SalesBoundedCountIT`, 2) |
| `T-09` | Contrato OpenAPI regenerado y prosa releída; `docs/api/index.md`; matriz e indicadores de `requirements.md`; ficha de `RF-MV-015` en `mv.md` a `En desarrollo`; issue | `T-07` | `openapi.json` declara la ruta con `x-required-permission: movements:list-sales` y **ninguna forma nueva** | **Hecha** — 21-09-2026 |

### 1.1 Método de pago y comprobante — 21-09-2026

Enmienda de hecho (Art. I.7), `spec.md` 0.2.0 y `plan.md` 0.2.0 **antes** del código, el mismo día que la segunda enmienda de `RF-MV-008` · §1.3.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-10` | `ListSalesRequest` y `SalesFilter` ganan `paymentMethodId` y `code` (a mayúsculas); `filtroDeVentas` los aplica después del alcance; `ListSalesService` los pasa | — | Un comprobante fuera del alcance no devuelve nada | `Pendiente` |
| `T-11` | `MovementController`: los dos parámetros documentados; `SalesIT`: `CA-MV-136`; contrato regenerado; `docs/api/index.md` y matriz | `T-10` | `openapi.json` declara los dos en `GET /api/v1/movements/sales` | `Pendiente` |

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
| `CA-MV-136` | `T-10`, `T-11` — 21-09-2026 |

---

## 3. Desviaciones respecto del plan

**El corte «fuera del alcance, sin consultar» se prueba por HTTP y no con el repositorio doblado** (`T-05`): la respuesta observable —página vacía, total cero y exacto— es la del corte y no la de una sentencia sin filas, y una prueba unitaria con un doble habría duplicado en `MV` una regla que ya está escrita en un solo sitio. `IntegrationTestBase.ALCANCE_PROPIO` y `reponerAlcancePropio` ganan `movements:list-sales`: las suites que cuentan «lo que todo rol recibe por su tipo» lo descuentan junto con los once de `V31`.

**`CommercialReach` devuelve la red como conjunto y el predicado usa `IN (:red)`**, tal como el plan lo asumió (§3.1); el disparador de revisión por tamaño queda en `plan.md` §10.

---

## 4. Bloqueos declarados

1. **La comprobación de arquitectura de `ADR-005`** —que todo listado declare su alcance— no se construye aquí y sigue pendiente. No bloquea este requerimiento; bloquea que D-22 se dé por cerrada.
2. **El detalle de una venta ajena de mi red** no existe (`RF-MV-007`): la fila se ve y no se abre. Declarado en `spec.md` §14.

---

## 5. Definición de terminado

- [x] `./mvnw clean verify` en verde — 435 unitarias y 1759 de integración, 0 fallos.
- [x] Los once criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements/mv.md`, `requirements.md` y `api/index.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
