# TASKS — `RF-SP-061` Consultar los clientes de un vendedor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-061` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 21-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/clientes-de-un-vendedor` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración `V30__sp_semilla_permiso_users_read_clients.sql`: `users:read-clients` con literal `…5e7ad0000026`, asociado a `SUPERADMIN` y `ADMIN`, guardas de 113 y de 2 | — | `CA-SP-722`; las cuatro suites del catálogo | Pendiente |
| `T-02` | `ClientSellerRepository` y `JpaClientSellerRepository`: `SellerClientRow`, `countClientsOf` y `findClientsOf` con el filtro por origen, sin eliminados, `created_at DESC, username` | — | `CA-SP-714`, `CA-SP-715`, `CA-SP-719`, `CA-SP-721` | Pendiente |
| `T-03` | `ClientOrigin` (el filtro con `VAL-001`) y `SellerClientItem` en `users/application` | — | `CA-SP-714`, `CA-SP-715` | Pendiente |
| `T-04` | `GetSellerClientsService`: `mine` y `of`, con `Pagination` y el `404` | `T-02`, `T-03` | `CA-SP-716`, `CA-SP-717`, `CA-SP-718` | Pendiente |
| `T-05` | `UserController`: `GET /api/v1/users/me/clients` **sin `@PreAuthorize`** y con el motivo escrito; `GET /api/v1/users/{id}/clients` con `users:read-clients`; prosa OpenAPI de las dos —qué es la cartera, por qué lleva `id` y estado, qué significa `linkedAt` en una fila migrada, que hoy solo hay `REGISTRO` y por qué— | `T-04` | `CA-SP-714`, `CA-SP-718` | Pendiente |
| `T-06` | `EndpointPermissionsIT`: `/me/clients` en `SIN_PERMISO_A_PROPOSITO` con su motivo; `/{id}/clients` en `PERMISO_DE_CADA_OPERACION` | `T-05` | `CA-SP-722` | Pendiente |
| `T-07` | Recuentos: `PermissionsSeedIT` (113, quince de `users`, literal de `V30`, `ADMIN` 107), `PermissionIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT`; `OpenApiContractIT` con la extensión de la ruta | `T-01`, `T-05` | `CA-SP-722` | Pendiente |
| `T-08` | `SellerClientsIT`: `CA-SP-714` a `CA-SP-721`, con una fila `HOTLINK` a mano, un cliente desactivado, uno eliminado, un director encima y un agente subordinado | `T-06` | Los ocho criterios | Pendiente |
| `T-09` | Contrato regenerado y comparado —solo altas: dos rutas y `PageResponseSellerClientItem`—; `api/index.md` con su fila | `T-08` | El diff del contrato no toca ninguna forma existente | Pendiente |
| `T-10` | Matriz de `docs/requirements.md` y los estados de esta tripleta; avisar a la sesión de frontend (R-45) | `T-09` | La fila de `RF-SP-061` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01` a `T-03` no dependen entre sí y pueden ir juntas. `T-04` y `T-05` encima. `T-06` y `T-07` son lo que `RN-SEG-014` exige de toda ruta nueva y se hacen **antes** de escribir la suite del endpoint, porque si la inyectividad falla, es aquí donde se ve por qué. `T-08` al final, y `T-09` y `T-10` cierran.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-714` | `T-02`, `T-03`, `T-05`, `T-08` |
| `CA-SP-715` | `T-02`, `T-03`, `T-08` |
| `CA-SP-716`, `CA-SP-717` | `T-04`, `T-08` |
| `CA-SP-718` | `T-04`, `T-05`, `T-08` |
| `CA-SP-719`, `CA-SP-721` | `T-02`, `T-08` |
| `CA-SP-720` | `T-08` |
| `CA-SP-722` | `T-01`, `T-06`, `T-07` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | **Las filas `HOTLINK` no las escribe nadie** hasta `RF-MV-011` y `RF-MV-013`. **No bloquea**: el contrato y el filtro ya las contemplan, y la prueba las inserta a mano | 21-09-2026 | Responsable del proyecto | **Abierto** |
| 2 | **`backend-ff` construye el bloque 4 de `AC`** sobre `feature/academia` con `V25`–`V27` reservadas. `V30` no se cruza con ellas; al hacer `pull` verá el catálogo en 113 | 21-09-2026 | Responsable técnico | **Abierto** |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local y CI en verde en el PR.
- [ ] `/me/clients` consta en `EndpointPermissionsIT` con su motivo, y `/{id}/clients` con su código.
- [ ] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
