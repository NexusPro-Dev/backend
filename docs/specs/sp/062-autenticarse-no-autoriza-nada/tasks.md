# TASKS — `RF-SP-062` Autenticarse no autoriza nada

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-062` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 21-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/autenticarse-no-autoriza-nada` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `security.md` 0.67.0 (`RN-SEG-015`, §4.4 con los once, la nota de reparto y las públicas), `requirements/sp.md` 1.68.0, `mv.md` 0.30.0, `pm.md` 0.42.0 | — | Los documentos dicen el código antes de que el código exista | Pendiente |
| `T-02` | Esta tripleta | `T-01` | Aprobada | Pendiente |
| `T-03` | Las diez tripletas de §4 del plan: nota de Art. I.7 en spec y plan, y el código en las líneas normativas | `T-02` | Ninguna tripleta afirma «sin permiso» como norma | Pendiente |
| `T-04` | `V31__sp_autenticarse_no_autoriza_nada.sql`: los once con literal, reparto por tipo, cuatro guardas | `T-03` | `CA-SP-725`, `CA-SP-726` | Pendiente |
| `T-05` | Once `@PreAuthorize` en `UserController`, `AuthController`, `MovementController` y `PackagePurchaseController`, con la prosa de `403` y sin «sin permiso» | `T-04` | `CA-SP-724` | Pendiente |
| `T-06` | `EndpointPermissionsIT`: `SIN_PERMISO_A_PROPOSITO` → `PUBLICAS` (catorce), contraste con `SecurityConfig`, las once en `PERMISO_DE_CADA_OPERACION` | `T-05` | `CA-SP-723`, `CA-SP-727` | Pendiente |
| `T-07` | `OwnScopePermissionsIT`: las once rutas sin el permiso son `403`; `GET /users/{id}/broker-accounts` con el permiso y sin estructura, `404` | `T-05` | `CA-SP-724`, `CA-SP-728` | Pendiente |
| `T-08` | Recuentos y reparto: `PermissionsSeedIT` (124, veinte de `users`, `ADMIN` 118, `CLIENTE` ocho, fuerza comercial once, literales), `PermissionIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT`; `PermissionSplitMigrationIT` con los roles creados antes de `V31`; `OpenApiContractIT` con las once | `T-04`, `T-05` | `CA-SP-725`, `CA-SP-726`, `CA-SP-729` | Pendiente |
| `T-09` | Las suites que llaman a las once rutas con `user(id)` a secas conceden la autoridad (plan §3); `DevelopmentSeedIT` afirma `CA-SP-730` | `T-05` | Suite completa en verde | Pendiente |
| `T-10` | Contrato regenerado y comparado —ninguna forma cambia, once `x-required-permission` nuevas—; `api/index.md` | `T-09` | `CA-SP-729` | Pendiente |
| `T-11` | Matriz de `docs/requirements.md`, estados de esta tripleta, aviso a la sesión de frontend con los once códigos | `T-10` | La fila refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01` a `T-03` son documentos y van antes que cualquier código, como manda el proyecto y como `RF-SP-060` hizo. `T-04` sola, después `T-05` y `T-06` juntas —la anotación y la prueba que la vigila—, y `T-07` a `T-09` en cualquier orden. `T-10` y `T-11` cierran.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-723`, `CA-SP-727` | `T-06` |
| `CA-SP-724` | `T-05`, `T-07` |
| `CA-SP-725`, `CA-SP-726` | `T-04`, `T-08` |
| `CA-SP-728` | `T-07` |
| `CA-SP-729` | `T-08`, `T-10` |
| `CA-SP-730` | `T-09` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | **`AC` sigue con `courses:update` compartido** (tramo 3 de `RF-SP-060`, bloqueado por su bloque 4). No afecta a esta regla: sus operaciones ya exigen permiso | 21-09-2026 | Responsable técnico de `AC` | **Abierto** |
| 2 | **El frontend debe sincronizar el contrato** antes de que `V31` llegue a un entorno compartido: sin los once códigos en su tabla, mostraría el `403` de `GET /users/me` como error | 21-09-2026 | Sesión de frontend | **Abierto** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local y CI en verde en el PR.
- [ ] Ninguna operación autenticada sin `@PreAuthorize`; `PUBLICAS` coincide con `SecurityConfig`.
- [ ] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
